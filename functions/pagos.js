/*
 * Cuidale App v2.1 – Billetera (colección `billetera`), recargas con Mercado Pago
 * (Checkout Pro) y cobro de servicios.
 *
 * Modelo de datos:
 *   billetera/{uid}  Documento único por usuario (id = uid del dueño de la billetera).
 *                     { uid, saldo, actualizadoAt, movimientos: [ { id, tipoMovimiento,
 *                       monto, saldoResultante, referencia, descripcion, timestamp,
 *                       estado, emisorUid, receptorUid, paymentId, preferenceId } ] }
 *
 *   tipoMovimiento:
 *     - "recarga": abona saldo (Checkout Pro de Mercado Pago). Estado: pendiente → aprobada/rechazada.
 *     - "pago":    transfiere saldo entre billeteras (pago del representante al cuidador).
 *                  monto negativo para quien paga, positivo para quien recibe.
 *     - "retiro":  retiro de saldo (monto negativo). Función lista; payout por implementar.
 *
 *   cobros/{solicitudId}  { solicitudId, cuidadorUid, representanteUid, monto, horas,
 *                           comentario, estado, saldoSuficiente, ... }
 *   Estados de un cobro: pendiente | requiere_confirmacion | pagado | rechazado
 *
 * Secretos requeridos (firebase functions:secrets:set NOMBRE):
 *   MP_ACCESS_TOKEN     Access Token de la aplicación en Mercado Pago Developers.
 *   MP_WEBHOOK_SECRET   Clave secreta generada al configurar los Webhooks.
 */
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const crypto = require("crypto");

const MP_ACCESS_TOKEN = defineSecret("MP_ACCESS_TOKEN");
const MP_WEBHOOK_SECRET = defineSecret("MP_WEBHOOK_SECRET");

// Credenciales OAuth de Mercado Pago Connect. En modo prueba no es obligatorio
// configurarlas: el flujo cae a un modo de compatibilidad que vincula la cuenta
// de la propia aplicación. Para producción, basta con setearlas y cambiar el
// Access Token (MP_ACCESS_TOKEN) por el token real.
const MP_CLIENT_ID = defineSecret("MP_CLIENT_ID");
const MP_USER_ID = defineSecret("MP_USER_ID");
// Client Secret de la MISMA aplicación de Mercado Pago que MP_CLIENT_ID y
// MP_ACCESS_TOKEN (firebase functions:secrets:set MP_CLIENT_SECRET).
const MP_CLIENT_SECRET = defineSecret("MP_CLIENT_SECRET");

// OAuth de Mercado Pago Connect:
//  - Mercado Pago solo acepta Redirect URI https, por eso se usa la función
//    `retornoOauthMp`. Esa misma URL se registra en Mercado Pago Developers →
//    tu aplicación → Configuración avanzada → URLs de redireccionamiento:
//    https://southamerica-west1-cuidale-app.cloudfunctions.net/retornoOauthMp
//  - La función canjea el código en el backend y luego abre la app con el
//    deep link MP_OAUTH_DEEPLINK (declarado en AndroidManifest, MpOauthActivity).
const MP_OAUTH_DEEPLINK = "cuidaleapp://oauthmp";
const OAUTH_ESTADO_TTL_MS = 10 * 60 * 1000; // el state de OAuth vence en 10 minutos

function mpOauthRedirectUri() {
  return functionUrl("retornoOauthMp");
}

/*
 * El modo prueba se detecta automáticamente según la credencial: si el Access
 * Token empieza con "TEST-", todo opera contra el sandbox. Al pasar a
 * producción solo hay que cambiar MP_ACCESS_TOKEN por el token real.
 */
function mpModoPrueba() {
  // Permite forzar el modo vía entorno (p.ej. MP_MODO_PRUEBA=false en producción
  // si la credencial productiva también empezara con APP_USR-).
  const forzado = process.env.MP_MODO_PRUEBA;
  if (forzado !== undefined) return String(forzado) === "true";
  const token = String(MP_ACCESS_TOKEN.value() || "");
  // Las credenciales de prueba de Mercado Pago usan prefijo TEST- o APP_USR-.
  return token.startsWith("TEST-") || token.startsWith("APP_USR-");
}

const REGION = "southamerica-west1";
const MP_API = "https://api.mercadopago.com";
const MONTO_MIN_RECARGA = 1000;
const MONTO_MAX_RECARGA = 1000000;
const MONTO_MIN_RETIRO = 1000;
const MONTO_MAX_RETIRO_POR_CARGO = 200000;
const RETIROS_MAX_POR_DIA = 5;
const COMISION_RETIRO_PCT = 0.02;
const COMISION_RETIRO_TOPE = 5000;
const COBRO_ACTIVO = ["pendiente", "requiere_confirmacion"];
const RUN_OPTIONS = { memory: "512MiB", timeoutSeconds: 60 };

const db = () => admin.firestore();
const FieldValue = admin.firestore.FieldValue;
// Firestore no permite FieldValue.serverTimestamp() dentro de arreglos; para los
// movimientos embebidos se usa un Timestamp concreto calculado en el servidor.
const ahora = () => admin.firestore.Timestamp.now();

// ---------------------------------------------------------------- utilidades

function requireAuth(req) {
  if (!req.auth || !req.auth.uid) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  return req.auth.uid;
}

function toPesos(value, campo) {
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0 || Math.round(n) !== n) {
    throw new HttpsError("invalid-argument", `${campo} debe ser un número entero mayor que cero (CLP).`);
  }
  return n;
}

function nuevoMovimientoId() {
  return crypto.randomUUID();
}

function projectId() {
  return process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT || admin.app().options.projectId;
}

function functionUrl(name) {
  return `https://${REGION}-${projectId()}.cloudfunctions.net/${name}`;
}

/* Devuelve el rol del usuario autenticado (representante | cuidador | ""). */
async function rolDeUsuario(uid) {
  try {
    const q = await db().collection("usuarios").where("uid", "==", uid).limit(1).get();
    return q.empty ? "" : String(q.docs[0].data().rol || "");
  } catch (_) {
    return "";
  }
}

/* Crea una notificación in-app; el trigger sendPushNotification de index.js envía el push. */
async function notificar(uid, tipo, emisor, data) {
  try {
    const q = await db().collection("usuarios").where("uid", "==", uid).limit(1).get();
    if (q.empty) return;
    const ref = q.docs[0].ref.collection("notificaciones").doc();
    const clean = {};
    for (const [k, v] of Object.entries(data || {})) clean[k] = String(v);
    await ref.set({
      id: ref.id, tipo, emisor: String(emisor || ""), receptor: String(uid),
      timestamp: Date.now(), leida: false, data: clean
    });
  } catch (e) {
    console.error("notificar error:", e);
  }
}

/* Lee la billetera de un usuario dentro de una transacción. */
async function leerBilletera(tx, uid) {
  const ref = db().collection("billetera").doc(uid);
  const snap = await tx.get(ref);
  const data = snap.exists ? snap.data() : {};
  return {
    ref,
    saldo: Number(data.saldo || 0),
    movimientos: Array.isArray(data.movimientos) ? data.movimientos : []
  };
}

/* Actualiza saldo y agregra un movimiento embebido en la billetera. */
function agregarMovimiento(tx, billetera, mov) {
  tx.set(billetera.ref, {
    uid: billetera.ref.id,
    saldo: billetera.saldo,
    movimientos: [...billetera.movimientos, mov],
    actualizadoAt: FieldValue.serverTimestamp()
  }, { merge: true });
}

function formatoCLP(n) {
  return "$" + Number(n).toLocaleString("es-CL");
}

/* Catálogo de bancos chilenos (código MP → nombre). Verificar códigos vigentes. */
const BANCOS_CHILE = {
  "001": "Banco de Chile",
  "002": "Banco Internacional",
  "009": "Banco Scotiabank",
  "014": "Banco Itaú",
  "016": "Banco Security",
  "028": "Banco BICE",
  "031": "Banco Santander",
  "037": "Banco BCI",
  "049": "Banco Estado",
  "051": "Banco Falabella",
  "055": "Banco Ripley",
  "057": "Banco Consorcio",
  "065": "Banco Desarrollo",
  "072": "Banco Paris",
  "668": "Coopeuch"
};

/* Valida un RUT chileno (cuerpo + dígito verificador). */
function validarRutChileno(rut) {
  const limpio = String(rut || "").replace(/\./g, "").replace(/-/g, "");
  const m = /^(\d{1,8})([0-9kK])$/.exec(limpio);
  if (!m) return false;
  const cuerpo = m[1];
  const dv = m[2].toLowerCase();
  let suma = 0;
  let multiplo = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * multiplo;
    multiplo = multiplo === 7 ? 2 : multiplo + 1;
  }
  const esperado = 11 - (suma % 11);
  const dvEsperado = esperado === 11 ? "0" : esperado === 10 ? "k" : String(esperado);
  return dvEsperado === dv;
}

/* Comisión por retiro: 2% con tope $5.000 CLP. */
function calcularComisionRetiro(monto) {
  return Math.min(Math.round(monto * COMISION_RETIRO_PCT), COMISION_RETIRO_TOPE);
}

/* Convierte un detalle de Mercado Pago en un mensaje amigable. */
function amigableDetalleRetiro(detalle) {
  const d = String(detalle || "").toLowerCase();
  if (
    d.includes("not_found") || d.includes("not found") ||
    d.includes("does not exist") || d.includes("inexistente") ||
    d.includes("invalid receiver") || d.includes("receiver")
  ) {
    return "La cuenta de destino no existe o no está habilitada para recibir retiros. Verifica los datos.";
  }
  if (
    d.includes("account_not_verified") || d.includes("not verified") ||
    d.includes("verification") || d.includes("no verificada")
  ) {
    return "La cuenta de destino no está verificada. Completa la verificación en Mercado Pago.";
  }
  if (d.includes("unauthorized") || d.includes("policy")) {
    return "Mercado Pago aún no habilita retiros para esta integración (política de desembolsos). Activa los Payouts/Connect en tu aplicación y vuelve a intentar.";
  }
  return String(detalle || "No se pudo procesar el retiro.");
}

/* Lee una cuenta destino verificando que pertenezca al usuario. */
async function leerCuentaDestino(uid, cuentaId) {
  if (!cuentaId) return null;
  const ref = db().collection("cuentas_bancarias").doc(String(cuentaId));
  const snap = await ref.get();
  if (!snap.exists) return null;
  const d = snap.data() || {};
  if (d.uid !== uid) return null;
  return { id: snap.id, ...d };
}

/* Calcula las horas de un turno desde "De HH:mm a HH:mm" (soporta cruce de medianoche). */
function horasDesdeHorario(horarioStr) {
  const m = /de\s*(\d{1,2}):(\d{2})\s*a\s*(\d{1,2}):(\d{2})/i.exec(horarioStr || "");
  if (!m) return null;
  const inicio = parseInt(m[1], 10) * 60 + parseInt(m[2], 10);
  const fin = parseInt(m[3], 10) * 60 + parseInt(m[4], 10);
  let minutos = fin - inicio;
  if (minutos <= 0) minutos += 24 * 60;
  return Math.round((minutos / 60) * 100) / 100;
}

/* Fecha de hoy en formato yyyy-MM-dd (huso de Chile). */
function fechaHoyCL() {
  try {
    const dtf = new Intl.DateTimeFormat("en-CA", {
      timeZone: "America/Santiago", year: "numeric", month: "2-digit", day: "2-digit"
    });
    return dtf.format(new Date());
  } catch (e) {
    return new Date().toISOString().slice(0, 10);
  }
}

// ---------------------------------------------------------------- Mercado Pago

async function mpFetch(path, options) {
  const res = await fetch(`${MP_API}${path}`, {
    ...options,
    headers: {
      "Authorization": `Bearer ${MP_ACCESS_TOKEN.value()}`,
      "Content-Type": "application/json",
      ...(options && options.headers ? options.headers : {})
    }
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    console.error("Mercado Pago error", res.status, JSON.stringify(body));
    throw new Error(`Mercado Pago respondió ${res.status}`);
  }
  return body;
}

/*
 * Valida el encabezado x-signature de un webhook de Mercado Pago.
 * Plantilla firmada: "id:{data.id};request-id:{x-request-id};ts:{ts};" con HMAC-SHA256.
 */
function firmaValida(req, secret) {
  const header = String(req.get("x-signature") || "");
  const requestId = String(req.get("x-request-id") || "");
  const partes = Object.fromEntries(header.split(",").map((p) => p.trim().split("=", 2)));
  const ts = partes.ts;
  const v1 = partes.v1;
  let dataId = String((req.query && req.query["data.id"]) || (req.body && req.body.data && req.body.data.id) || "");
  if (/^[a-z0-9]+$/i.test(dataId)) dataId = dataId.toLowerCase();
  if (!ts || !v1 || !dataId) return false;
  let manifest = `id:${dataId};`;
  if (requestId) manifest += `request-id:${requestId};`;
  manifest += `ts:${ts};`;
  const esperado = crypto.createHmac("sha256", secret).update(manifest).digest("hex");
  try {
    return crypto.timingSafeEqual(Buffer.from(esperado, "hex"), Buffer.from(v1, "hex"));
  } catch (e) {
    return false;
  }
}

// ---------------------------------------------------------------- RECARGAS

/* El usuario solicita recargar su billetera. Devuelve la URL del Checkout Pro. */
exports.crearRecarga = onCall({ ...RUN_OPTIONS, secrets: [MP_ACCESS_TOKEN] }, async (req) => {
  const uid = requireAuth(req);
  const monto = toPesos(req.data && req.data.monto, "El monto");
  if (monto < MONTO_MIN_RECARGA || monto > MONTO_MAX_RECARGA) {
    throw new HttpsError("invalid-argument", `El monto debe estar entre ${formatoCLP(MONTO_MIN_RECARGA)} y ${formatoCLP(MONTO_MAX_RECARGA)}.`);
  }

  // Regla de negocio: solo los representantes pueden cargar saldo a su billetera.
  const rol = await rolDeUsuario(uid);
  if (rol !== "representante") {
    throw new HttpsError("permission-denied", "Solo los representantes pueden recargar la billetera.");
  }

  const recargaId = nuevoMovimientoId();

  // La recarga pendiente se guarda como movimiento embebido en la billetera del usuario.
  await db().runTransaction(async (tx) => {
    const billetera = await leerBilletera(tx, uid);
    agregarMovimiento(tx, billetera, {
      id: recargaId,
      tipoMovimiento: "recarga",
      monto,
      saldoResultante: billetera.saldo,
      referencia: recargaId,
      descripcion: "Recarga con Mercado Pago",
      estado: "pendiente",
      timestamp: ahora()
    });
  });

  const retorno = functionUrl("retornoPago");
  const preferencia = await mpFetch("/checkout/preferences", {
    method: "POST",
    headers: { "X-Idempotency-Key": recargaId },
    body: JSON.stringify({
      items: [{
        id: "recarga_billetera",
        title: "Recarga de billetera Cuidale App",
        quantity: 1,
        currency_id: "CLP",
        unit_price: monto
      }],
      // Permite débito y crédito de cualquier medio soportado por Mercado Pago
      // (no se excluye ningún método/tipo y se habilitan cuotas para crédito).
      payment_methods: {
        excluded_payment_methods: [],
        excluded_payment_types: [],
        installments: 12
      },
      external_reference: `${uid}#${recargaId}`,
      notification_url: functionUrl("webhookMercadoPago"),
      back_urls: {
        success: `${retorno}?estado=aprobado&recarga=${uid}%23${recargaId}`,
        pending: `${retorno}?estado=pendiente&recarga=${uid}%23${recargaId}`,
        failure: `${retorno}?estado=rechazado&recarga=${uid}%23${recargaId}`
      },
      auto_return: "approved",
      metadata: { uid, recarga_id: recargaId }
    })
  });

  await db().runTransaction(async (tx) => {
    const billetera = await leerBilletera(tx, uid);
    const idx = billetera.movimientos.findIndex((m) => m.id === recargaId);
    if (idx >= 0) {
      billetera.movimientos[idx] = { ...billetera.movimientos[idx], preferenceId: preferencia.id };
      tx.set(billetera.ref, { movimientos: billetera.movimientos }, { merge: true });
    }
  });

  const url = mpModoPrueba() && preferencia.sandbox_init_point ? preferencia.sandbox_init_point : preferencia.init_point;
  return { recargaId, url };
});

/* Página a la que vuelve el navegador después del pago; devuelve al usuario a la app.
 * En Android con Chrome se responde con un redirect HTTP 302 al esquema intent://,
 * que es la forma más confiable de abrir la app automáticamente. Para el resto de
 * casos (escritorio, iOS, otros navegadores) se sirve una página HTML con botón y
 * deep link directo como respaldo.
 */
exports.retornoPago = onRequest({ memory: "256MiB" }, (req, res) => {
  const estado = String(req.query.estado || "pendiente").replace(/[^a-z_]/gi, "");
  const recarga = String(req.query.recarga || "").replace(/[^a-z0-9%23]/gi, "");
  const fallbackHtml = `${functionUrl("retornoPago")}?estado=${estado}&recarga=${recarga}&ui=html`;
  const intent = `intent://pago?estado=${estado}&recarga=${recarga}#Intent;scheme=cuidaleapp;package=com.example.cuidaleapp;S.browser_fallback_url=${encodeURIComponent(fallbackHtml)};end`;
  const ua = String(req.get("user-agent") || "");
  const esChromeAndroid = /Android/i.test(ua) && /Chrome\//i.test(ua);

  // En móviles Android con Chrome, un redirect 302 a intent:// abre la app de
  // forma automática (navegación de nivel superior iniciada por el usuario).
  if (esChromeAndroid && req.query.ui !== "html") {
    res.set("Cache-Control", "no-store, no-cache, must-revalidate");
    res.redirect(302, intent);
    return;
  }

  const directo = `cuidaleapp://pago?estado=${estado}&recarga=${recarga}`;
  const directoEscapado = directo.replace(/&/g, "&amp;");
  const mensaje = estado === "aprobado" ? "¡Pago aprobado!" : estado === "rechazado" ? "El pago fue rechazado." : "Tu pago está en proceso.";
  const icono = estado === "aprobado" ? "✅" : estado === "rechazado" ? "❌" : "⏳";
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(`<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cuidale App</title><style>
body{font-family:sans-serif;text-align:center;padding:40px 16px;background:#f4f7fb}
.tarjeta{display:inline-block;max-width:380px;width:100%;background:#fff;border-radius:20px;padding:32px 20px;box-shadow:0 8px 24px rgba(31,78,121,.12)}
.icono{font-size:56px;line-height:1}
h2{margin:14px 0 6px;color:#1F4E79}
p{margin:6px 0;color:#5a6b7d;font-size:15px}
a.btn{display:block;margin-top:22px;padding:14px 20px;background:#1F4E79;color:#fff;border-radius:24px;text-decoration:none;font-weight:bold}
a.link{display:block;margin-top:12px;color:#1F4E79;font-size:14px}
.nota{margin-top:18px;font-size:12px;color:#9aa7b4}
</style></head>
<body><div class="tarjeta"><div class="icono">${icono}</div><h2>${mensaje}</h2><p>Puedes volver a Cuidale App.</p>
<a class="btn" href="${directoEscapado}">Volver a la app</a>
<a class="link" href="${directoEscapado}">Abrir Cuidale App directamente</a>
<p class="nota">Si no se abre automáticamente, toca el botón de arriba.</p>
</div>
<script>
var intent = ${JSON.stringify(intent)};
function lanzar(){ location.href = intent; }
// Se intenta lanzar la app de inmediato y de nuevo tras 1 s por si el navegador
// bloquea la primera navegación programática a esquemas personalizados.
window.addEventListener("load", function(){
  try { lanzar(); } catch(e){}
  setTimeout(function(){ try { lanzar(); } catch(e){} }, 1000);
});
</script></body></html>`);
});

/* Webhook de Mercado Pago: acredita la recarga solo si el pago está aprobado. Idempotente.
 * Soporta dos formatos de external_reference:
 *   - Nuevo: `${uid}#${recargaId}` (movimiento embebido en `billetera/{uid}`).
 *   - Legacy: `${recargaId}` (documento en la colección `recargas`): migra y acredita.
 */
exports.webhookMercadoPago = onRequest({ ...RUN_OPTIONS, secrets: [MP_ACCESS_TOKEN, MP_WEBHOOK_SECRET] }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Método no permitido");
    return;
  }
  if (!firmaValida(req, MP_WEBHOOK_SECRET.value())) {
    console.warn("Webhook con firma inválida");
    res.status(401).send("Firma inválida");
    return;
  }
  const tipo = String((req.body && req.body.type) || req.query.type || "");
  const paymentId = String((req.body && req.body.data && req.body.data.id) || req.query["data.id"] || "");
  if (tipo !== "payment" || !paymentId) {
    res.status(200).send("Ignorado");
    return;
  }

  try {
    const pago = await mpFetch(`/v1/payments/${encodeURIComponent(paymentId)}`, { method: "GET" });
    const ext = String(pago.external_reference || "");

    let uid;
    let recargaId;
    let legacyRef = null;

    if (ext.includes("#")) {
      [uid, recargaId] = ext.split("#", 2);
      if (!uid || !recargaId) {
        res.status(200).send("Sin referencia");
        return;
      }
    } else if (ext) {
      // Compatibilidad: pago creado con el modelo anterior (solo id de recarga).
      legacyRef = db().collection("recargas").doc(ext);
      const legacySnap = await legacyRef.get();
      if (!legacySnap.exists) {
        res.status(200).send("Sin referencia");
        return;
      }
      const legacy = legacySnap.data();
      uid = legacy.uid;
      recargaId = legacy.id || ext;
      if (legacy.estado === "aprobada") {
        res.status(200).send("OK");
        return;
      }
    } else {
      res.status(200).send("Sin referencia");
      return;
    }

    const billeteraRef = db().collection("billetera").doc(uid);

    await db().runTransaction(async (tx) => {
      const billetera = await leerBilletera(tx, uid);
      const idx = billetera.movimientos.findIndex(
        (m) => m.id === recargaId && m.tipoMovimiento === "recarga"
      );
      const mov = idx >= 0 ? billetera.movimientos[idx] : null;

      // Si no existe el movimiento en el modelo nuevo pero hay una recarga legacy,
      // se migra: se crea la billetera (si falta) con su saldo y movimiento.
      let montoRecarga = mov ? Number(mov.monto) : 0;
      if (!montoRecarga && legacyRef) {
        const legacySnap = await tx.get(legacyRef);
        if (legacySnap.exists) montoRecarga = Number(legacySnap.data().monto || 0);
      }
      if (!montoRecarga) return;

      if (mov && mov.estado === "aprobada") return; // ya acreditada

      if (pago.status === "approved") {
        if (Number(pago.transaction_amount) !== montoRecarga || pago.currency_id !== "CLP") {
          if (mov) {
            billetera.movimientos[idx] = {
              ...mov, estado: "revision", paymentId, detalle: "monto o moneda no coinciden"
            };
            tx.update(billeteraRef, { movimientos: billetera.movimientos });
          } else if (legacyRef) {
            tx.update(legacyRef, { estado: "revision", paymentId });
          }
          return;
        }
        const nuevoSaldo = billetera.saldo + montoRecarga;
        if (mov) {
          billetera.movimientos[idx] = {
            ...mov, estado: "aprobada", paymentId,
            saldoResultante: nuevoSaldo, aprobadaAt: ahora()
          };
        } else {
          billetera.movimientos = [
            {
              id: recargaId,
              tipoMovimiento: "recarga",
              monto: montoRecarga,
              saldoResultante: nuevoSaldo,
              referencia: recargaId,
              descripcion: "Recarga con Mercado Pago",
              estado: "aprobada",
              paymentId,
              timestamp: ahora(),
              aprobadaAt: ahora()
            },
            ...billetera.movimientos
          ];
        }
        tx.set(billeteraRef, {
          uid, saldo: nuevoSaldo, movimientos: billetera.movimientos,
          actualizadoAt: FieldValue.serverTimestamp()
        }, { merge: true });
        if (legacyRef) {
          tx.update(legacyRef, { estado: "aprobada", paymentId, aprobadaAt: FieldValue.serverTimestamp() });
        }
      } else if (["rejected", "cancelled", "refunded", "charged_back"].includes(pago.status)) {
        if (mov) {
          billetera.movimientos[idx] = {
            ...mov, estado: "rechazada", paymentId,
            detalle: String(pago.status_detail || pago.status)
          };
          tx.update(billeteraRef, { movimientos: billetera.movimientos });
        } else if (legacyRef) {
          tx.update(legacyRef, { estado: "rechazada", paymentId, detalle: String(pago.status_detail || pago.status) });
        }
      } else {
        if (mov) {
          billetera.movimientos[idx] = { ...mov, paymentId };
          tx.update(billeteraRef, { movimientos: billetera.movimientos });
        } else if (legacyRef) {
          tx.update(legacyRef, { paymentId });
        }
      }
    });

    // Notificar una sola vez cuando la recarga queda aprobada.
    const final = await billeteraRef.get();
    if (final.exists) {
      const mov = (final.data().movimientos || []).find((m) => m.id === recargaId);
      if (mov && mov.estado === "aprobada" && !mov.notificada) {
        const newMovimientos = final.data().movimientos.map((m) =>
          m.id === recargaId ? { ...m, notificada: true } : m
        );
        await billeteraRef.update({ movimientos: newMovimientos });
        await notificar(uid, "recarga_aprobada", "sistema", {
          titulo: "Recarga aprobada",
          texto: `Se abonaron ${formatoCLP(mov.monto)} a tu billetera.`
        });
      }
    } else if (legacyRef) {
      const legacySnap = await legacyRef.get();
      if (legacySnap.exists && legacySnap.data().estado === "aprobada" && !legacySnap.data().notificada) {
        await legacyRef.update({ notificada: true });
        await notificar(uid, "recarga_aprobada", "sistema", {
          titulo: "Recarga aprobada",
          texto: `Se abonaron ${formatoCLP(legacySnap.data().monto || 0)} a tu billetera.`
        });
      }
    }
    res.status(200).send("OK");
  } catch (e) {
    console.error("webhookMercadoPago error:", e);
    res.status(500).send("Error"); // Mercado Pago reintentará
  }
});

// ---------------------------------------------------------------- RETIROS

/*
 * Registra un destino de retiro (cuenta bancaria o cuenta Mercado Pago).
 * Se permiten varias cuentas por usuario y NO se exige que la cuenta esté a
 * nombre del usuario ni que el RUT coincida con el del autenticado.
 */
exports.registrarCuentaDestino = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);
  const d = req.data || {};
  const tipoDestino = String(d.tipoDestino || "");
  if (!["banco", "mercado_pago"].includes(tipoDestino)) {
    throw new HttpsError("invalid-argument", "Tipo de destino inválido.");
  }

  const base = { uid, tipoDestino };

  if (tipoDestino === "banco") {
    const banco = String(d.banco || "");
    const tipoCuenta = String(d.tipoCuenta || "").toLowerCase();
    const numeroCuenta = String(d.numeroCuenta || "").replace(/\s+/g, "");
    const titular = String(d.titular || "").trim();
    const rut = String(d.rut || "").trim();
    if (!BANCOS_CHILE[banco]) throw new HttpsError("invalid-argument", "Banco no válido.");
    if (!["corriente", "vista"].includes(tipoCuenta)) throw new HttpsError("invalid-argument", "Tipo de cuenta no válido.");
    if (!/^\d{6,12}$/.test(numeroCuenta)) throw new HttpsError("invalid-argument", "Número de cuenta no válido.");
    if (!titular) throw new HttpsError("invalid-argument", "El titular es obligatorio.");
    if (!validarRutChileno(rut)) throw new HttpsError("invalid-argument", "RUT no válido.");

    base.banco = banco;
    base.bancoNombre = BANCOS_CHILE[banco];
    base.tipoCuenta = tipoCuenta;
    base.numeroCuenta = numeroCuenta;
    base.titular = titular;
    base.rut = rut;
  } else {
    const mpIdentificador = String(d.mpIdentificador || "").trim();
    if (!mpIdentificador) {
      throw new HttpsError("invalid-argument", "Indica el identificador de la cuenta de Mercado Pago.");
    }
    const esEmail = /^[\w.+-]+@[\w-]+\.[\w.]+$/.test(mpIdentificador);
    const esId = /^\d+$/.test(mpIdentificador);
    // Acepta IDs de usuario de Mercado Pago (ej. TESTUSER...) y similares.
    const esAlfa = /^[A-Za-z0-9_-]{4,}$/.test(mpIdentificador);
    if (!esEmail && !esId && !esAlfa) {
      throw new HttpsError("invalid-argument", "Identificador de Mercado Pago no válido.");
    }
    base.mpIdentificador = mpIdentificador;
  }

  // Evitar duplicados exactos.
  const existentes = await db().collection("cuentas_bancarias")
    .where("uid", "==", uid)
    .get();
  const dup = existentes.docs.find((docSnap) => {
    const x = docSnap.data() || {};
    if (x.tipoDestino !== tipoDestino) return false;
    if (tipoDestino === "banco") {
      return x.banco === base.banco && x.numeroCuenta === base.numeroCuenta;
    }
    return x.mpIdentificador === base.mpIdentificador;
  });

  let id;
  if (dup) {
    id = dup.id;
  } else {
    const ref = db().collection("cuentas_bancarias").doc();
    await ref.set({ ...base, creadaAt: FieldValue.serverTimestamp() });
    id = ref.id;
  }

  const mascara = tipoDestino === "banco"
    ? "•••• " + String(base.numeroCuenta).slice(-4)
    : String(base.mpIdentificador);

  return {
    id,
    tipoDestino,
    mascara,
    ...(tipoDestino === "banco"
      ? { bancoNombre: base.bancoNombre, tipoCuenta: base.tipoCuenta }
      : {})
  };
});

/* Construye el cuerpo del payout según el tipo de destino (banco / MP). */
function construirBodyPayout(cuenta, monto, uid, movimientoId) {
  const esBanco = cuenta.tipoDestino === "banco";
  const body = {
    amount: monto,
    type: esBanco ? "bank_transfer" : "mercado_pago_account",
    external_reference: `${uid}#${movimientoId}`,
    description: "Retiro Cuidale App",
    // IMPORTANTE: sin esta URL Mercado Pago no notifica el estado del payout
    // y el retiro quedaría "procesando" para siempre.
    notification_url: functionUrl("webhookMercadoPagoPayout")
  };

  if (esBanco) {
    const nombres = String(cuenta.titular || "").trim().split(/\s+/);
    const receiver = {
      bank_account: {
        bank_code: String(cuenta.banco || ""),
        account_type: String(cuenta.tipoCuenta || ""),
        account_number: String(cuenta.numeroCuenta || "")
      }
    };
    if (nombres[0]) receiver.first_name = nombres[0];
    if (nombres.length > 1) receiver.last_name = nombres.slice(1).join(" ");
    if (cuenta.rut) receiver.identification = { type: "RUT", number: String(cuenta.rut) };
    if (cuenta.email) receiver.email = String(cuenta.email);
    body.receiver = receiver;
  } else {
    // Para cuentas de Mercado Pago el receptor se identifica con su user_id
    // (account_id). No se envían first_name/identification vacíos: pueden hacer
    // fallar la creación del payout en sandbox/producción.
    body.receiver = {
      mercado_pago_account: {
        account_id: String(cuenta.accountIdMp || "")
      }
    };
    if (cuenta.email) body.receiver.email = String(cuenta.email);
  }
  return body;
}

/*
 * Retira saldo de la billetera hacia una cuenta bancaria o una cuenta de
 * Mercado Pago usando la API de Payouts. Comisión 2% (tope $5.000), tope por
 * retiro $200.000 y máximo RETIROS_MAX_POR_DIA retiros por día.
 */
exports.retirarSaldo = onCall({ ...RUN_OPTIONS, secrets: [MP_ACCESS_TOKEN] }, async (req) => {
  const uid = requireAuth(req);
  const monto = toPesos(req.data && req.data.monto, "El monto");
  const cuentaId = String((req.data && req.data.cuentaId) || "");

  if (monto < MONTO_MIN_RETIRO) {
    throw new HttpsError("invalid-argument", `El retiro mínimo es ${formatoCLP(MONTO_MIN_RETIRO)}.`);
  }
  if (monto > MONTO_MAX_RETIRO_POR_CARGO) {
    throw new HttpsError("invalid-argument", `El retiro máximo por operación es ${formatoCLP(MONTO_MAX_RETIRO_POR_CARGO)}.`);
  }

  const cuenta = await leerCuentaDestino(uid, cuentaId);
  if (!cuenta) {
    throw new HttpsError("failed-precondition", "Debes registrar un destino de retiro.");
  }

  // La comisión por retiro (2% tope $5.000) aplica solo al cuidador. El
  // representante retira sin comisión.
  const rol = await rolDeUsuario(uid);
  const aplicaComision = rol !== "representante";
  const comision = aplicaComision ? calcularComisionRetiro(monto) : 0;
  const totalDebito = monto + comision;
  const hoy = fechaHoyCL();

  // Para destinos de Mercado Pago el receptor se identifica con su ID numérico
  // de usuario. Si el registro trae otro formato (correo/nickname) avisamos
  // antes de descontar saldo y evitar errores tipo "not found" de MP.
  if (cuenta.tipoDestino === "mercado_pago") {
    const accountIdMp = String(cuenta.mpUserId || cuenta.mpIdentificador || "").trim();
    if (!/^\d+$/.test(accountIdMp)) {
      throw new HttpsError(
        "failed-precondition",
        "Para retirar a Mercado Pago registra el ID numérico de tu cuenta (el número de usuario, no el correo ni el apodo)."
      );
    }
  }

  const resultado = await db().runTransaction(async (tx) => {
    const billetera = await leerBilletera(tx, uid);
    if (billetera.saldo < totalDebito) {
      throw new HttpsError("failed-precondition", "Saldo insuficiente en tu billetera.");
    }
    const retirosHoy = billetera.movimientos.filter((mv) =>
      mv.tipoMovimiento === "retiro" &&
      mv.fechaLocal === hoy &&
      ["procesando", "aprobada"].includes(mv.estado)
    );
    if (retirosHoy.length >= RETIROS_MAX_POR_DIA) {
      throw new HttpsError("failed-precondition", `Alcanzaste el límite de ${RETIROS_MAX_POR_DIA} retiros por día.`);
    }

    const nuevoSaldo = billetera.saldo - totalDebito;
    const movimientoId = nuevoMovimientoId();
    billetera.saldo = nuevoSaldo;
    const ultimos4 = cuenta.tipoDestino === "banco"
      ? String(cuenta.numeroCuenta || "").slice(-4)
      : "";

    agregarMovimiento(tx, billetera, {
      id: movimientoId,
      tipoMovimiento: "retiro",
      monto: -totalDebito,
      montoNeto: monto,
      comision,
      saldoResultante: nuevoSaldo,
      referencia: movimientoId,
      descripcion: cuenta.tipoDestino === "banco"
        ? `Retiro a cuenta bancaria ${formatoCLP(monto)}`
        : `Retiro a Mercado Pago ${formatoCLP(monto)}`,
      estado: "procesando",
      metodo: cuenta.tipoDestino,
      cuentaId,
      ultimos4,
      fechaLocal: hoy,
      payoutId: null,
      timestamp: ahora()
    });
    return { saldo: nuevoSaldo, movimientoId };
  });

  let payout;
  const body = construirBodyPayout({
    tipoDestino: cuenta.tipoDestino,
    banco: cuenta.banco,
    tipoCuenta: cuenta.tipoCuenta,
    numeroCuenta: cuenta.numeroCuenta,
    titular: cuenta.titular,
    rut: cuenta.rut,
    email: cuenta.email,
    // Para MP se prioriza el user_id obtenido vía Connect (mpUserId); si no,
    // se usa el identificador registrado (número de usuario o TESTUSER…).
    accountIdMp: String(cuenta.mpUserId || cuenta.mpIdentificador || "")
  }, monto, uid, resultado.movimientoId);
    let mpErrorDetalle = "";
    try {
      const resp = await fetch(`${MP_API}/v1/payouts`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${MP_ACCESS_TOKEN.value()}`,
          "Content-Type": "application/json",
          "X-Idempotency-Key": resultado.movimientoId
        },
        body: JSON.stringify(body)
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        console.error("Payout create MP error:", resp.status, JSON.stringify(data));
        mpErrorDetalle = String(data.message || data.error || `Mercado Pago respondió ${resp.status}`);
        throw new Error(mpErrorDetalle);
      }
      payout = data;
    } catch (e) {
      console.error("Payout create error:", e);
      const detalle = amigableDetalleRetiro(mpErrorDetalle || e.message || "No se pudo crear el payout");
      await db().runTransaction(async (tx) => {
        const billetera = await leerBilletera(tx, uid);
        const idx = billetera.movimientos.findIndex((mv) => mv.id === resultado.movimientoId);
        if (idx >= 0) {
          const mov = billetera.movimientos[idx];
          if (mov.estado === "procesando") {
            billetera.saldo += totalDebito;
            billetera.movimientos[idx] = {
              ...mov,
              estado: "rechazada",
              detalle,
              saldoResultante: billetera.saldo
            };
            tx.set(billetera.ref, {
              uid, saldo: billetera.saldo, movimientos: billetera.movimientos,
              actualizadoAt: FieldValue.serverTimestamp()
            }, { merge: true });
          }
        }
      });
      await notificar(uid, "retiro_rechazado", "sistema", {
        titulo: "Retiro rechazado",
        texto: `No se pudo procesar tu retiro de ${formatoCLP(monto)}. ${detalle} Se devolvió el saldo.`
      });
      throw new HttpsError("failed-precondition", detalle);
    }

  // Evalúa el estado inicial del payout. Si ya viene resuelto (aprobado o
  // rechazado), se actualiza el movimiento de inmediato sin depender del
  // webhook. Si queda pending/processing, el webhook (notification_url) lo
  // resolverá después.
  const payoutStatus = String(payout.status || "");
  const payoutDetalle = amigableDetalleRetiro(String(payout.status_detail || payoutStatus));
  let estadoFinal = "procesando";

  await db().runTransaction(async (tx) => {
    const billetera = await leerBilletera(tx, uid);
    const idx = billetera.movimientos.findIndex((mv) => mv.id === resultado.movimientoId);
    if (idx < 0) return;
    const mov = billetera.movimientos[idx];
    const conPayoutId = { ...mov, payoutId: String(payout.id || "") };

    if (["processed", "completed", "approved"].includes(payoutStatus)) {
      billetera.movimientos[idx] = { ...conPayoutId, estado: "aprobada", payoutId: String(payout.id || ""), fechaAcreditado: ahora() };
      tx.set(billetera.ref, { movimientos: billetera.movimientos }, { merge: true });
      estadoFinal = "aprobada";
    } else if (["rejected", "cancelled", "cancelled_partial"].includes(payoutStatus)) {
      const reversion = Math.abs(Number(conPayoutId.monto) || 0);
      billetera.saldo += reversion;
      billetera.movimientos[idx] = {
        ...conPayoutId,
        estado: "rechazada",
        payoutId: String(payout.id || ""),
        detalle: payoutDetalle,
        saldoResultante: billetera.saldo
      };
      tx.set(billetera.ref, {
        uid, saldo: billetera.saldo, movimientos: billetera.movimientos,
        actualizadoAt: FieldValue.serverTimestamp()
      }, { merge: true });
      estadoFinal = "rechazada";
    } else {
      // pending / processing / in_process: se guarda el payoutId y se espera webhook.
      billetera.movimientos[idx] = conPayoutId;
      tx.set(billetera.ref, { movimientos: billetera.movimientos }, { merge: true });
    }
  });

  if (estadoFinal === "rechazada") {
    await notificar(uid, "retiro_rechazado", "sistema", {
      titulo: "Retiro rechazado",
      texto: `El retiro de ${formatoCLP(monto)} fue rechazado por Mercado Pago. Se devolvió el saldo.`
    });
  }

  return {
    estado: estadoFinal,
    movimientoId: resultado.movimientoId,
    payoutId: String(payout.id || ""),
    comision
  };
});

/*
 * Webhook de Mercado Pago para el estado de los Payouts. Acredita el retiro
 * cuando se procesa y revierte el saldo cuando se rechaza/cancela. Idempotente.
 */
exports.webhookMercadoPagoPayout = onRequest({ ...RUN_OPTIONS, secrets: [MP_ACCESS_TOKEN, MP_WEBHOOK_SECRET] }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Método no permitido");
    return;
  }
  if (!firmaValida(req, MP_WEBHOOK_SECRET.value())) {
    console.warn("Webhook payout con firma inválida");
    res.status(401).send("Firma inválida");
    return;
  }
  const tipo = String((req.body && req.body.type) || req.query.type || "");
  const payoutId = String((req.body && req.body.data && req.body.data.id) || req.query["data.id"] || "");
  if (tipo !== "payout" || !payoutId) {
    res.status(200).send("Ignorado");
    return;
  }

  try {
    const payout = await mpFetch(`/v1/payouts/${encodeURIComponent(payoutId)}`, { method: "GET" });
    const ext = String(payout.external_reference || "");
    if (!ext.includes("#")) {
      res.status(200).send("Sin referencia");
      return;
    }
    const [uid, movimientoId] = ext.split("#", 2);
    if (!uid || !movimientoId) {
      res.status(200).send("Sin referencia");
      return;
    }

    const billeteraRef = db().collection("billetera").doc(uid);

    await db().runTransaction(async (tx) => {
      const billetera = await leerBilletera(tx, uid);
      const idx = billetera.movimientos.findIndex(
        (mv) => mv.id === movimientoId && mv.tipoMovimiento === "retiro"
      );
      if (idx < 0) return;
      const mov = billetera.movimientos[idx];
      if (["aprobada", "rechazada"].includes(mov.estado)) return; // idempotente

      if (payout.status === "processed" || payout.status === "completed") {
        billetera.movimientos[idx] = { ...mov, estado: "aprobada", payoutId, fechaAcreditado: ahora() };
        tx.set(billetera.ref, { movimientos: billetera.movimientos }, { merge: true });
      } else if (payout.status === "rejected" || payout.status === "cancelled") {
        const reversion = Math.abs(Number(mov.monto) || 0);
        billetera.saldo += reversion;
        billetera.movimientos[idx] = {
          ...mov,
          estado: "rechazada",
          payoutId,
          detalle: amigableDetalleRetiro(String(payout.status_detail || payout.status)),
          saldoResultante: billetera.saldo
        };
        tx.set(billetera.ref, {
          uid, saldo: billetera.saldo, movimientos: billetera.movimientos,
          actualizadoAt: FieldValue.serverTimestamp()
        }, { merge: true });
      }
    });

    const final = await billeteraRef.get();
    if (final.exists) {
      const mov = (final.data().movimientos || []).find((mv) => mv.id === movimientoId);
      if (mov && (mov.estado === "aprobada" || mov.estado === "rechazada") && !mov.notificada) {
        const nuevos = final.data().movimientos.map((mv) =>
          mv.id === movimientoId ? { ...mv, notificada: true } : mv
        );
        await billeteraRef.update({ movimientos: nuevos });
        const destino = mov.metodo === "banco" ? "cuenta bancaria" : "cuenta de Mercado Pago";
        if (mov.estado === "aprobada") {
          await notificar(uid, "retiro_aprobado", "sistema", {
            titulo: "Retiro aprobado",
            texto: `Se transfirió ${formatoCLP(mov.montoNeto || 0)} a tu ${destino}.`
          });
        } else {
          await notificar(uid, "retiro_rechazado", "sistema", {
            titulo: "Retiro rechazado",
            texto: `Tu retiro de ${formatoCLP(mov.montoNeto || 0)} fue rechazado. Se devolvió el saldo.`
          });
        }
      }
    }

    res.status(200).send("OK");
  } catch (e) {
    console.error("webhookMercadoPagoPayout error:", e);
    res.status(500).send("Error");
  }
});

// ---------------------------------------------------------------- COBROS

/*
 * C20-51 / C20-52 / C20-53 / C20-54: el cuidador inicia el cobro del servicio.
 * data: { solicitudId, monto?, horas?, comentario? }  (si no se envían monto/horas se usan los acordados)
 */
exports.iniciarCobro = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);
  const d = req.data || {};
  const solicitudId = String(d.solicitudId || "");
  if (!solicitudId) throw new HttpsError("invalid-argument", "Falta el servicio a cobrar.");

  const solRef = db().collection("solicitudes").doc(solicitudId);
  const cobroRef = db().collection("cobros").doc(solicitudId); // un cobro por servicio: evita duplicados

  const resultado = await db().runTransaction(async (tx) => {
    const solSnap = await tx.get(solRef);
    // C20-54: servicio inexistente, cancelado o que no pertenece al cuidador
    if (!solSnap.exists || solSnap.data().estado !== "cerrada" || solSnap.data().cuidadorUid !== uid) {
      throw new HttpsError("failed-precondition", "No existe un servicio activo que te vincule con este representante.");
    }
    const sol = solSnap.data();
    const cobroSnap = await tx.get(cobroRef);
    if (cobroSnap.exists) {
      const estado = cobroSnap.data().estado;
      // C20-53: ya existe un cobro activo
      if (COBRO_ACTIVO.includes(estado)) throw new HttpsError("already-exists", "Ya existe un proceso de cobro activo para este servicio.");
      if (estado === "pagado") throw new HttpsError("failed-precondition", "Este servicio ya fue pagado.");
    }

    const montoAcordado = Number(sol.montoAcordado || 0);
    const horasAcordadas = Number(sol.horasAcordadas || 0);
    if (!montoAcordado) throw new HttpsError("failed-precondition", "El servicio no tiene un monto acordado registrado.");

    const monto = d.monto != null ? toPesos(d.monto, "El monto") : montoAcordado;
    const horas = d.horas != null ? Number(d.horas) : horasAcordadas;
    if (!Number.isFinite(horas) || horas < 0) throw new HttpsError("invalid-argument", "Horas inválidas.");
    // C20-52: datos diferentes a los pre-acordados requieren confirmación del representante
    const cambios = monto !== montoAcordado || horas !== horasAcordadas;

    const bill = await leerBilletera(tx, sol.representanteUid);
    const saldoRep = bill.saldo;

    const cobro = {
      id: solicitudId, solicitudId,
      cuidadorUid: uid, cuidadorNombre: String(sol.cuidadorNombre || ""),
      representanteUid: sol.representanteUid, representanteNombre: String(sol.representanteNombre || ""),
      nombrePaciente: String(sol.nombrePaciente || ""),
      montoAcordado, horasAcordadas, monto, horas,
      comentario: String(d.comentario || "").slice(0, 300),
      estado: cambios ? "requiere_confirmacion" : "pendiente",
      saldoSuficiente: saldoRep >= monto,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()
    };
    tx.set(cobroRef, cobro);
    tx.update(solRef, { cobroEstado: cobro.estado });
    return cobro;
  });

  await notificar(resultado.representanteUid, resultado.estado === "requiere_confirmacion" ? "cobro_con_cambios" : "cobro_recibido", uid, {
    titulo: resultado.estado === "requiere_confirmacion" ? "Cobro con cambios por confirmar" : "Nuevo cobro pendiente",
    texto: `${resultado.cuidadorNombre || "Tu cuidador"} solicitó el pago de ${formatoCLP(resultado.monto)}.`,
    cobroId: resultado.id, solicitudId: resultado.solicitudId
  });
  return { cobroId: resultado.id, estado: resultado.estado, saldoSuficiente: resultado.saldoSuficiente };
});

/* C20-52: el representante acepta o rechaza los cambios de monto/horas. data: { cobroId, aceptar } */
exports.responderCambiosCobro = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);
  const cobroId = String((req.data && req.data.cobroId) || "");
  const aceptar = req.data && req.data.aceptar === true;
  const ref = db().collection("cobros").doc(cobroId);

  const cobro = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "El cobro no existe.");
    const c = snap.data();
    if (c.representanteUid !== uid) throw new HttpsError("permission-denied", "Este cobro no te corresponde.");
    if (c.estado !== "requiere_confirmacion") throw new HttpsError("failed-precondition", "Este cobro no tiene cambios por confirmar.");
    const estado = aceptar ? "pendiente" : "rechazado";
    tx.update(ref, { estado, updatedAt: FieldValue.serverTimestamp() });
    tx.update(db().collection("solicitudes").doc(c.solicitudId), { cobroEstado: estado });
    return { ...c, estado };
  });

  await notificar(cobro.cuidadorUid, aceptar ? "cobro_cambios_aceptados" : "cobro_rechazado", uid, {
    titulo: aceptar ? "Cambios del cobro aceptados" : "Cobro rechazado",
    texto: aceptar ? "El representante aceptó los cambios; el cobro quedó pendiente de pago." : "El representante rechazó los cambios. Puedes generar un nuevo cobro.",
    cobroId: cobro.id, solicitudId: cobro.solicitudId
  });
  return { estado: cobro.estado };
});

/*
 * C20-55 / C20-56 / C20-57 / C20-58: el representante paga el cobro desde su billetera.
 * La transferencia es atómica y, si se aprueba, finaliza el servicio. data: { cobroId }
 */
exports.pagarCobro = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);
  const cobroId = String((req.data && req.data.cobroId) || "");
  const cobroRef = db().collection("cobros").doc(cobroId);

  const r = await db().runTransaction(async (tx) => {
    const snap = await tx.get(cobroRef);
    // C20-57: servicio ya finalizado o sin cobro pendiente
    if (!snap.exists) return { ok: false, motivo: "sin_cobro" };
    const c = snap.data();
    if (c.representanteUid !== uid) throw new HttpsError("permission-denied", "Este cobro no te corresponde.");
    if (c.estado === "pagado") return { ok: false, motivo: "ya_pagado", c };
    if (c.estado === "requiere_confirmacion") throw new HttpsError("failed-precondition", "Primero debes confirmar los cambios del cobro.");
    if (c.estado !== "pendiente") return { ok: false, motivo: "sin_cobro", c };

    const repBilletera = await leerBilletera(tx, c.representanteUid);
    const cuiBilletera = await leerBilletera(tx, c.cuidadorUid);
    const saldoRep = repBilletera.saldo;
    const saldoCui = cuiBilletera.saldo;

    // C20-56 / C20-58: saldo insuficiente
    if (saldoRep < c.monto) {
      tx.update(cobroRef, { saldoSuficiente: false, updatedAt: FieldValue.serverTimestamp() });
      return { ok: false, motivo: "saldo_insuficiente", c, saldoRep };
    }

    // C20-55: transferencia atómica entre billeteras (movimientos embebidos).
    const nuevoRep = saldoRep - c.monto;
    const nuevoCui = saldoCui + c.monto;
    const timestamp = ahora();

    const movRep = {
      id: nuevoMovimientoId(),
      tipoMovimiento: "pago",
      monto: -c.monto,
      saldoResultante: nuevoRep,
      referencia: cobroId,
      descripcion: `Pago a ${c.cuidadorNombre || "cuidador"} por el cuidado de ${c.nombrePaciente || "paciente"}`,
      estado: "aprobado",
      emisorUid: c.representanteUid,
      receptorUid: c.cuidadorUid,
      timestamp
    };
    const movCui = {
      id: nuevoMovimientoId(),
      tipoMovimiento: "pago",
      monto: c.monto,
      saldoResultante: nuevoCui,
      referencia: cobroId,
      descripcion: `Pago recibido de ${c.representanteNombre || "representante"}`,
      estado: "aprobado",
      emisorUid: c.representanteUid,
      receptorUid: c.cuidadorUid,
      timestamp
    };

    tx.set(repBilletera.ref, {
      uid: c.representanteUid, saldo: nuevoRep,
      movimientos: [...repBilletera.movimientos, movRep],
      actualizadoAt: timestamp
    }, { merge: true });
    tx.set(cuiBilletera.ref, {
      uid: c.cuidadorUid, saldo: nuevoCui,
      movimientos: [...cuiBilletera.movimientos, movCui],
      actualizadoAt: timestamp
    }, { merge: true });

    tx.update(cobroRef, { estado: "pagado", saldoSuficiente: true, pagadoAt: timestamp, updatedAt: timestamp });
    return { ok: true, c };
  });

  if (!r.ok) {
    if (r.motivo === "saldo_insuficiente") {
      await notificar(uid, "pago_saldo_insuficiente", "sistema", {
        titulo: "Saldo insuficiente",
        texto: `Necesitas ${formatoCLP(r.c.monto)} y tienes ${formatoCLP(r.saldoRep)}. Recarga tu billetera para pagar.`,
        cobroId
      });
      await notificar(r.c.cuidadorUid, "pago_pendiente", uid, {
        titulo: "Pago pendiente",
        texto: "El representante debe recargar su billetera para completar tu pago.",
        cobroId
      });
      throw new HttpsError("failed-precondition", "Saldo insuficiente en tu billetera. Recarga para completar el pago.");
    }
    throw new HttpsError("failed-precondition", r.motivo === "ya_pagado" ? "Este servicio ya fue pagado." : "Este servicio ya no tiene un cobro pendiente.");
  }

  await finalizarServicio(r.c);
  const texto = `Pago de ${formatoCLP(r.c.monto)} realizado correctamente.`;
  await notificar(r.c.cuidadorUid, "pago_realizado", uid, { titulo: "¡Pago recibido!", texto, cobroId, solicitudId: r.c.solicitudId });
  await notificar(r.c.representanteUid, "pago_realizado", "sistema", { titulo: "Pago realizado", texto, cobroId, solicitudId: r.c.solicitudId });
  return { estado: "pagado" };
});

/* Equivalente en servidor de SolicitudViewModel.finalizarServicio(): historial + cierre del servicio. */
async function finalizarServicio(c) {
  try {
    const solRef = db().collection("solicitudes").doc(c.solicitudId);
    const solSnap = await solRef.get();
    if (!solSnap.exists) return;
    const sol = solSnap.data();
    const [cuiFicha, pacFicha] = await Promise.all([
      db().collection("fichas").doc(String(sol.cuidadorUid || "")).get(),
      db().collection("fichas").doc(String(sol.pacienteFichaId || "")).get()
    ]);
    const histRef = db().collection("historial_servicios").doc();
    await histRef.set({
      id: histRef.id,
      solicitudId: c.solicitudId,
      representanteUid: sol.representanteUid || "",
      representanteNombre: sol.representanteNombre || "",
      representanteTelefono: sol.representanteTelefono || "",
      pacienteNombre: sol.nombrePaciente || "",
      pacienteEdad: pacFicha.exists && pacFicha.get("edad") != null ? String(pacFicha.get("edad")) : "",
      cuidadorUid: sol.cuidadorUid || "",
      cuidadorNombre: sol.cuidadorNombre || "",
      cuidadorEspecialidad: (cuiFicha.exists && cuiFicha.get("especializacion")) || "No especificada",
      fechaInicio: sol.fechaInicio || null,
      fechaTermino: admin.firestore.Timestamp.now(),
      aceptadoAt: sol.aceptadoAt || null,
      montoPagado: c.monto,
      cobroId: c.id,
      calificacion: null,
      observacionCalificacion: "",
      eliminadoPorRepresentante: false,
      eliminadoPorCuidador: false
    });
    const posts = await db().collection("postulaciones").where("solicitudId", "==", c.solicitudId).get();
    const batch = db().batch();
    posts.docs.forEach((d) => batch.delete(d.ref));
    batch.delete(solRef);
    await batch.commit();
  } catch (e) {
    console.error("finalizarServicio error:", e);
  }
}

/*
 * Modelo único: contrato multi-turno con tarifa horaria.
 * C20-51 / C20-53 / C20-54: el cuidador finaliza un turno e inicia su cobro.
 * data: { solicitudId, horas?, comentario? }
 * Crea el turno y el cobro borrador SIN cerrar el contrato (la solicitud sigue viva).
 */
exports.iniciarCobroTurno = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);
  const d = req.data || {};
  const solicitudId = String(d.solicitudId || "");
  if (!solicitudId) throw new HttpsError("invalid-argument", "Falta el contrato.");

  const solRef = db().collection("solicitudes").doc(solicitudId);
  const resultado = await db().runTransaction(async (tx) => {
    const solSnap = await tx.get(solRef);
    const sol = solSnap.data();
    if (!solSnap.exists || sol.estado !== "cerrada" || sol.cuidadorUid !== uid) {
      throw new HttpsError("failed-precondition", "No existe un servicio activo que te vincule con este representante.");
    }
    if (sol.estadoAcuerdo && sol.estadoAcuerdo !== "acordado") {
      throw new HttpsError("failed-precondition", "Hay un acuerdo pendiente entre ambas partes. Resuélvelo antes de iniciar un turno.");
    }
    const tarifa = Number(sol.tarifaHora || 0);
    if (!tarifa || tarifa <= 0) {
      throw new HttpsError("failed-precondition", "El contrato no tiene una tarifa horaria definida.");
    }

    let horas = d.horas != null ? Number(d.horas) : NaN;
    if (!Number.isFinite(horas) || horas <= 0 || horas > 1000 || Math.round(horas * 10) !== horas * 10) {
      const calc = horasDesdeHorario(sol.horarioStr || "");
      if (calc == null) throw new HttpsError("invalid-argument", "Las horas del turno no son válidas.");
      horas = calc;
    }

    const fecha = fechaHoyCL();
    const monto = Math.round(tarifa * horas);
    const bill = await leerBilletera(tx, sol.representanteUid);
    const saldoRep = bill.saldo;

    // Un turno por día (C20-53): si ya existe uno activo del mismo cuidador,
    // se reutiliza (idempotencia) actualizando horas/comentario en lugar de
    // rechazar. Solo se rechaza si el turno activo de hoy pertenece a otro
    // cuidador o ya fue pagado/rechazado.
    const existentes = await db().collection("turnos").where("solicitudId", "==", solicitudId).get();
    const activoHoy = existentes.docs.find((doc) => {
      const t = doc.data();
      return t.fecha === fecha && !["pagado", "rechazado"].includes(t.estado);
    });
    if (activoHoy) {
      const existente = activoHoy.data();
      if (existente.cuidadorUid !== uid) {
        throw new HttpsError("already-exists", "Ya existe un turno iniciado para hoy.");
      }
      const turnoId = existente.id;
      tx.update(db().collection("turnos").doc(turnoId), {
        horasTrabajadas: horas,
        tarifaUsada: tarifa,
        montoCalculado: monto,
        comentario: String(d.comentario || "").slice(0, 300),
        saldoSuficiente: saldoRep >= monto,
        updatedAt: FieldValue.serverTimestamp()
      });
      tx.update(db().collection("cobros").doc(turnoId), {
        horas, monto, tarifaHora: tarifa,
        comentario: String(d.comentario || "").slice(0, 300),
        saldoSuficiente: saldoRep >= monto,
        updatedAt: FieldValue.serverTimestamp()
      });
      return { turnoId, representanteUid: sol.representanteUid, monto, saldoSuficiente: saldoRep >= monto };
    }

    const turnoRef = db().collection("turnos").doc();
    const turnoId = turnoRef.id;
    const estado = "pendiente_confirmacion_rep";
    const base = {
      id: turnoId, solicitudId,
      cuidadorUid: uid, cuidadorNombre: String(sol.cuidadorNombre || ""),
      representanteUid: sol.representanteUid, representanteNombre: String(sol.representanteNombre || ""),
      nombrePaciente: String(sol.nombrePaciente || ""),
      fecha, horasTrabajadas: horas, tarifaUsada: tarifa, montoCalculado: monto,
      comentario: String(d.comentario || "").slice(0, 300),
      estado, saldoSuficiente: saldoRep >= monto,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()
    };
    tx.set(turnoRef, {
      ...base,
      confirmaciones: {}, propuestaTarifa: null, propuestaPor: "",
      observaciones: {}, registroInformeId: ""
    });
    // El cobro usa los nombres que lee la app (monto, horas, tarifaHora).
    tx.set(db().collection("cobros").doc(turnoId), {
      id: turnoId, solicitudId, turnoId,
      cuidadorUid: uid, cuidadorNombre: String(sol.cuidadorNombre || ""),
      representanteUid: sol.representanteUid, representanteNombre: String(sol.representanteNombre || ""),
      nombrePaciente: String(sol.nombrePaciente || ""),
      tarifaHora: tarifa, horas, monto,
      comentario: String(d.comentario || "").slice(0, 300),
      estado, saldoSuficiente: saldoRep >= monto,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()
    });
    tx.update(solRef, { cobroEstado: estado });
    return { turnoId, representanteUid: sol.representanteUid, monto, saldoSuficiente: base.saldoSuficiente };
  });

  await notificar(resultado.representanteUid, "cobro_recibido", uid, {
    titulo: "Nuevo cobro pendiente",
    texto: `Tu cuidador solicitó el pago de ${formatoCLP(resultado.monto)}.`,
    cobroId: resultado.turnoId, solicitudId, turnoId: resultado.turnoId
  });
  return {
    cobroId: resultado.turnoId,
    turnoId: resultado.turnoId,
    estado: "pendiente_confirmacion_rep",
    saldoSuficiente: resultado.saldoSuficiente
  };
});

// Expuesto solo para pruebas unitarias (no enumerable: no se despliega como función)
/* ---------- Mercado Pago Connect: conexión de cuenta para retiros ---------- */
// Modelo Marketplace (Mercado Pago Connect):
//  - Cada usuario vincula su cuenta MP mediante OAuth (authorization code).
//  - La plataforma guarda el access_token del vendedor en mp_conexiones/{uid}
//    (solo backend) y registra su user_id como destino de retiro.
//  - Los payouts se crean con el Access Token de la app (MP_ACCESS_TOKEN) y el
//    account_id del vendedor, por lo que al pasar a producción solo hay que
//    cambiar MP_ACCESS_TOKEN por el token real.
//  - Sin MP_CLIENT_ID/MP_CLIENT_SECRET (modo prueba) el flujo usa la cuenta de
//    la propia aplicación como destino (compatibilidad/demo).

// Lee un secreto opcional sin lanzar error si aún no está configurado.
function valorSecreto(param) {
  try { return String(param.value() || "").trim(); } catch (_) { return ""; }
}

function upsertCuentaMp(uid, userId, email) {
  const idResuelto = String(userId || "").trim() || String(email || "").trim();
  // Nunca guardamos un identificador sintético: si no se pudo resolver uno
  // real, la conexión falla y el usuario debe ingresar su ID numérico a mano.
  if (!idResuelto) return Promise.resolve(null);
  return db().collection("cuentas_bancarias")
    .where("uid", "==", uid)
    .where("tipoDestino", "==", "mercado_pago")
    .limit(1).get()
    .then((cuentasQ) => {
      const ref = cuentasQ.empty
        ? db().collection("cuentas_bancarias").doc()
        : cuentasQ.docs[0].ref;
      const idMp = idResuelto;
      return ref.set({
        id: ref.id,
        uid,
        tipoDestino: "mercado_pago",
        banco: "",
        bancoNombre: "Mercado Pago",
        tipoCuenta: "",
        numeroCuenta: "",
        titular: "",
        rut: "",
        mpIdentificador: idMp,
        mpUserId: userId || "",
        mpEmail: email || "",
        mpAppId: valorSecreto(MP_CLIENT_ID),
        creadaAt: FieldValue.serverTimestamp()
      }, { merge: true });
    });
}

/* ¿Están configuradas las credenciales OAuth (client_id + client_secret)? */
function oauthMpConfigurado() {
  return Boolean(valorSecreto(MP_CLIENT_ID) && valorSecreto(MP_CLIENT_SECRET));
}

/*
 * Devuelve la URL de autorización OAuth de Mercado Pago Connect ("" si faltan
 * credenciales). `state` es un valor aleatorio de un solo uso guardado en
 * mp_oauth_estados/{state}; NO se usa el uid para evitar ataques CSRF.
 */
function urlAutorizacionMp(state) {
  if (!oauthMpConfigurado()) return "";
  const params = new URLSearchParams({
    client_id: valorSecreto(MP_CLIENT_ID),
    response_type: "code",
    platform_id: "mp",
    redirect_uri: mpOauthRedirectUri(),
    state
  });
  return `https://auth.mercadopago.com/authorization?${params.toString()}`;
}

/* Intercambia el código de autorización por el token del vendedor (Connect). */
async function canjearCodeOauthMp(code) {
  const clientId = valorSecreto(MP_CLIENT_ID);
  const clientSecret = valorSecreto(MP_CLIENT_SECRET);
  if (!code || !clientId || !clientSecret) return null;
  const resp = await fetch(`${MP_API}/oauth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      grant_type: "authorization_code",
      client_id: clientId,
      client_secret: clientSecret,
      code,
      redirect_uri: mpOauthRedirectUri()
    })
  });
  const j = await resp.json().catch(() => ({}));
  if (!resp.ok || !j.access_token) {
    console.error("MP oauth/token error:", resp.status, JSON.stringify(j));
    return null;
  }
  return {
    accessToken: String(j.access_token),
    refreshToken: String(j.refresh_token || ""),
    userId: String(j.user_id || ""),
    publicKey: String(j.public_key || ""),
    email: String(j.email || "")
  };
}

/* Fallback (demo): resuelve la cuenta MP de la propia aplicación. */
async function resolverCuentaMpApp() {
  let userId = "";
  let email = "";
  try {
    const resp = await fetch(`${MP_API}/users/me`, {
      headers: { Authorization: `Bearer ${MP_ACCESS_TOKEN.value()}` }
    });
    const j = await resp.json();
    if (resp.ok) {
      userId = String(j.id || "");
      email = String(j.email || "");
    }
  } catch (e) {
    console.error("mp users/me error:", e.message);
  }
  return { userId, email };
}

exports.iniciarOauthMp = onCall(
  { ...RUN_OPTIONS, secrets: [MP_CLIENT_ID, MP_CLIENT_SECRET, MP_USER_ID, MP_ACCESS_TOKEN] },
  async (req) => {
    const uid = requireAuth(req);
    if (oauthMpConfigurado()) {
      // state aleatorio de un solo uso, ligado al usuario y con vencimiento.
      const state = crypto.randomBytes(24).toString("hex");
      await db().collection("mp_oauth_estados").doc(state).set({
        uid,
        creadoAt: Date.now(),
        expiraAt: Date.now() + OAUTH_ESTADO_TTL_MS
      });
      const authUrl = urlAutorizacionMp(state);
      return { ok: true, authUrl, requiereRedirect: true };
    }
    // Modo prueba: sin client_id se intenta vincular la cuenta de la app.
    const { userId, email } = await resolverCuentaMpApp();
    const creada = await upsertCuentaMp(uid, userId, email);
    if (!creada) {
      throw new HttpsError("failed-precondition", "No se pudo resolver una cuenta de Mercado Pago. Ingresa manualmente tu ID numérico de usuario.");
    }
    return { ok: true, email, userId };
  }
);

/*
 * Compatibilidad con versiones anteriores de la app. Con OAuth configurado el
 * código se canjea en `retornoOauthMp` (backend), así que aquí NO se aceptan
 * códigos: un código recibido por deep link no se puede ligar de forma segura
 * al usuario (sin state). Sin OAuth configurado (demo) vincula la cuenta de la
 * propia aplicación, como antes.
 */
exports.canjearOauthMp = onCall(
  { ...RUN_OPTIONS, secrets: [MP_CLIENT_ID, MP_CLIENT_SECRET, MP_USER_ID, MP_ACCESS_TOKEN] },
  async (req) => {
    const uid = requireAuth(req);
    if (oauthMpConfigurado()) {
      const cuenta = await db().collection("cuentas_bancarias")
        .where("uid", "==", uid)
        .where("tipoDestino", "==", "mercado_pago")
        .limit(1).get();
      if (!cuenta.empty) return { ok: true };
      throw new HttpsError("failed-precondition", "No se completó la vinculación con Mercado Pago. Vuelve a intentarlo desde Cuentas.");
    }

    // Fallback (demo): intenta vincular la cuenta de la aplicación.
    const { userId, email } = await resolverCuentaMpApp();
    const creadaFallback = await upsertCuentaMp(uid, userId, email);
    if (!creadaFallback) {
      throw new HttpsError("failed-precondition", "No se pudo resolver una cuenta de Mercado Pago. Ingresa manualmente tu ID numérico de usuario.");
    }
    return { ok: true, email, userId };
  }
);

/*
 * Guarda la conexión OAuth del usuario. Los tokens del vendedor van en
 * mp_conexiones/{uid}, colección SIN reglas de lectura para la app (solo
 * backend), y no dentro de la billetera, que el usuario sí puede leer.
 */
async function guardarConexionMp(uid, conexion) {
  await db().collection("mp_conexiones").doc(uid).set({
    uid,
    user_id: conexion.userId,
    access_token: conexion.accessToken,
    refresh_token: conexion.refreshToken,
    public_key: conexion.publicKey,
    actualizadoAt: Date.now()
  });
  // upsertCuentaMp devuelve null solo si no hay un identificador real.
  const resultado = await upsertCuentaMp(uid, conexion.userId, conexion.email);
  return resultado !== null;
}

/* Página/redirect que devuelve al usuario a la app tras el OAuth. */
function responderRetornoOauth(req, res, estado) {
  const query = `estado=${estado}`;
  const fallbackHtml = `${functionUrl("retornoOauthMp")}?${query}&ui=html`;
  const intent = `intent://oauthmp?${query}#Intent;scheme=cuidaleapp;package=com.example.cuidaleapp;S.browser_fallback_url=${encodeURIComponent(fallbackHtml)};end`;
  const ua = String(req.get("user-agent") || "");
  const esChromeAndroid = /Android/i.test(ua) && /Chrome\//i.test(ua);
  res.set("Cache-Control", "no-store, no-cache, must-revalidate");
  if (esChromeAndroid && req.query.ui !== "html") {
    res.redirect(302, intent);
    return;
  }
  const directo = `${MP_OAUTH_DEEPLINK}?${query}`;
  const ok = estado === "ok";
  const mensaje = ok ? "¡Cuenta de Mercado Pago vinculada!" : estado === "cancelado" ? "Vinculación cancelada." : "No se pudo vincular tu cuenta.";
  const icono = ok ? "✅" : "❌";
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(`<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cuidale App</title><style>
body{font-family:sans-serif;text-align:center;padding:40px 16px;background:#f4f7fb}
.tarjeta{display:inline-block;max-width:380px;width:100%;background:#fff;border-radius:20px;padding:32px 20px;box-shadow:0 8px 24px rgba(31,78,121,.12)}
.icono{font-size:56px;line-height:1}
h2{margin:14px 0 6px;color:#1F4E79}
p{margin:6px 0;color:#5a6b7d;font-size:15px}
a.btn{display:block;margin-top:22px;padding:14px 20px;background:#1F4E79;color:#fff;border-radius:24px;text-decoration:none;font-weight:bold}
</style></head>
<body><div class="tarjeta"><div class="icono">${icono}</div><h2>${mensaje}</h2><p>Puedes volver a Cuidale App.</p>
<a class="btn" href="${directo}">Volver a la app</a></div></body></html>`);
}

/*
 * Redirect URI (https) del OAuth de Mercado Pago. Recibe ?code&state, valida el
 * state (existe, no vencido, un solo uso), canjea el código en el backend con
 * el Client Secret y vuelve a la app con cuidaleapp://oauthmp?estado=ok|error.
 * El código nunca pasa por el teléfono.
 */
exports.retornoOauthMp = onRequest(
  { ...RUN_OPTIONS, secrets: [MP_CLIENT_ID, MP_CLIENT_SECRET] },
  async (req, res) => {
    if (req.query.ui === "html") {
      const estadoUi = String(req.query.estado || "error").replace(/[^a-z]/gi, "");
      responderRetornoOauth(req, res, estadoUi);
      return;
    }
    const code = String(req.query.code || "");
    const state = String(req.query.state || "").replace(/[^a-f0-9]/gi, "");
    if (req.query.error || !code || !state) {
      responderRetornoOauth(req, res, req.query.error ? "cancelado" : "error");
      return;
    }
    try {
      const estadoRef = db().collection("mp_oauth_estados").doc(state);
      const uid = await db().runTransaction(async (tx) => {
        const snap = await tx.get(estadoRef);
        if (!snap.exists) return null;
        tx.delete(estadoRef); // un solo uso
        const d = snap.data() || {};
        if (!d.uid || Number(d.expiraAt || 0) < Date.now()) return null;
        return String(d.uid);
      });
      if (!uid) {
        console.warn("retornoOauthMp: state inválido o vencido");
        responderRetornoOauth(req, res, "error");
        return;
      }
      const conexion = await canjearCodeOauthMp(code);
      if (!conexion || !(await guardarConexionMp(uid, conexion))) {
        responderRetornoOauth(req, res, "error");
        return;
      }
      responderRetornoOauth(req, res, "ok");
    } catch (e) {
      console.error("retornoOauthMp error:", e.message);
      responderRetornoOauth(req, res, "error");
    }
  }
);

/* Desvincula la cuenta de Mercado Pago: elimina el destino y limpia la conexión. */
exports.desvincularMp = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);

  // Elimina la cuenta destino tipo Mercado Pago del usuario.
  const cuentasQ = await db().collection("cuentas_bancarias")
    .where("uid", "==", uid)
    .where("tipoDestino", "==", "mercado_pago")
    .limit(10)
    .get();
  const batch = db().batch();
  for (const doc of cuentasQ.docs) {
    batch.delete(doc.ref);
  }

  // Borra los tokens guardados y limpia el campo antiguo de la billetera.
  batch.delete(db().collection("mp_conexiones").doc(uid));
  batch.set(db().collection("billetera").doc(uid), {
    mp_conexion: admin.firestore.FieldValue.delete()
  }, { merge: true });

  await batch.commit();
  return { ok: true };
});

/* Cancela una recarga pendiente (la marca como rechazada en Firestore). */
exports.cancelarRecarga = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);
  const recargaId = String((req.data && req.data.recargaId) || "");
  if (!recargaId) throw new HttpsError("invalid-argument", "Falta la recarga.");

  const ref = db().collection("billetera").doc(uid);
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const data = snap.data();
    const movs = Array.isArray(data.movimientos) ? data.movimientos : [];
    const idx = movs.findIndex(
      (m) => m.id === recargaId && m.tipoMovimiento === "recarga" && m.estado === "pendiente"
    );
    if (idx < 0) return;
    movs[idx] = {
      ...movs[idx],
      estado: "rechazada",
      detalle: "Cancelada por el usuario",
      canceladaAt: ahora()
    };
    tx.update(ref, { movimientos: movs });
  });
  return { ok: true };
});

/* Scheduler: consulta a Mercado Pago y sanea recargas huérfanas en "pendiente". */
exports.sincronizarRecargasPendientes = onSchedule(
  {
    schedule: "every 12 hours",
    timeZone: "America/Santiago",
    // Cloud Scheduler no está disponible en southamerica-west1; se usa east1.
    region: "southamerica-east1",
    timeoutSeconds: 300,
    secrets: [MP_ACCESS_TOKEN]
  },
  async () => {
    let actualizadas = 0;
    try {
      const docs = await db().collection("billetera")
        .orderBy("actualizadoAt", "desc")
        .limit(400)
        .get();
      const batch = db().batch();
      for (const doc of docs.docs) {
        const uid = doc.id;
        const data = doc.data();
        const movs = Array.isArray(data.movimientos) ? data.movimientos : [];
        let cambio = false;
        for (let i = 0; i < movs.length; i++) {
          const m = movs[i];
          if (m.tipoMovimiento !== "recarga" || m.estado !== "pendiente") continue;
          let nuevo = null;
          const ext = `${uid}#${m.id}`;
          try {
            const search = await mpFetch(
              `/v1/payments/search?external_reference=${encodeURIComponent(ext)}&limit=1`
            );
            const results = (search.results || []);
            if (results.length > 0) {
              const status = String(results[0].status || "");
              const pagoId = String(results[0].id || "");
              if (status === "approved") {
                nuevo = { estado: "aprobada", paymentId: pagoId, aprobadaAt: ahora() };
              } else if (["rejected", "cancelled", "refunded", "charged_back"].includes(status)) {
                nuevo = { estado: "rechazada", paymentId: pagoId, detalle: `MP: ${status}` };
              }
              // "pending"/"in_process" se dejan intactos (aún puede confirmarse).
            }
          } catch (e) {
            console.error("mp payment search error:", e.message);
          }
          if (!nuevo) {
            const ts = m.timestamp ? (m.timestamp.toMillis ? m.timestamp.toMillis() : Date.now()) : 0;
            if (Date.now() - ts > 24 * 60 * 60 * 1000) {
              nuevo = { estado: "rechazada", detalle: "Vencida por falta de confirmación" };
            }
          }
          if (nuevo) {
            movs[i] = { ...m, ...nuevo };
            cambio = true;
            actualizadas++;
          }
        }
        if (cambio) batch.update(doc.ref, { movimientos: movs });
      }
      if (actualizadas > 0) await batch.commit();
    } catch (e) {
      console.error("sincronizarRecargasPendientes error:", e);
    }
    console.log("recargas sincronizadas:", actualizadas);
  }
);

Object.defineProperty(exports, "_test", {
  value: { firmaValida, toPesos, validarRutChileno, calcularComisionRetiro, amigableDetalleRetiro, BANCOS_CHILE },
  enumerable: false
});
