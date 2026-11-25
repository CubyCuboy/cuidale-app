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
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const crypto = require("crypto");

const MP_ACCESS_TOKEN = defineSecret("MP_ACCESS_TOKEN");
const MP_WEBHOOK_SECRET = defineSecret("MP_WEBHOOK_SECRET");

// Modo de prueba: mientras sea true, el checkout se abre en el sandbox de Mercado Pago.
const MP_MODO_PRUEBA = true;

const REGION = "southamerica-west1";
const MP_API = "https://api.mercadopago.com";
const MONTO_MIN_RECARGA = 1000;
const MONTO_MAX_RECARGA = 1000000;
const MONTO_MIN_RETIRO = 1000;
const COBRO_ACTIVO = ["pendiente", "requiere_confirmacion"];
const RUN_OPTIONS = { memory: "512MiB", timeoutSeconds: 60 };

const db = () => admin.firestore();
const FieldValue = admin.firestore.FieldValue;

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
      timestamp: FieldValue.serverTimestamp()
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

  const url = MP_MODO_PRUEBA && preferencia.sandbox_init_point ? preferencia.sandbox_init_point : preferencia.init_point;
  return { recargaId, url };
});

/* Página a la que vuelve el navegador después del pago; devuelve al usuario a la app. */
exports.retornoPago = onRequest({ memory: "256MiB" }, (req, res) => {
  const estado = String(req.query.estado || "pendiente").replace(/[^a-z_]/gi, "");
  const recarga = String(req.query.recarga || "").replace(/[^a-z0-9%23]/gi, "");
  const intent = `intent://pago?estado=${estado}&recarga=${recarga}#Intent;scheme=cuidaleapp;package=com.example.cuidaleapp;end`;
  const mensaje = estado === "aprobado" ? "¡Pago aprobado!" : estado === "rechazado" ? "El pago fue rechazado." : "Tu pago está en proceso.";
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(`<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cuidale App</title><style>body{font-family:sans-serif;text-align:center;padding:48px 16px}a{display:inline-block;margin-top:24px;padding:14px 28px;background:#1F4E79;color:#fff;border-radius:24px;text-decoration:none}</style></head>
<body><h2>${mensaje}</h2><p>Puedes volver a Cuidale App.</p><a href="${intent}">Volver a la app</a>
<script>setTimeout(function(){location.href=${JSON.stringify(intent)};},600);</script></body></html>`);
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
            saldoResultante: nuevoSaldo, aprobadaAt: FieldValue.serverTimestamp()
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
              timestamp: FieldValue.serverTimestamp(),
              aprobadaAt: FieldValue.serverTimestamp()
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
 * Retira saldo de la billetera. Mínimo $1.000 CLP.
 * Actualiza el saldo y registra un movimiento tipo "retiro" en estado pendiente.
 * NOTA: la integración real con Mercado Pago (payout a cuenta bancaria) queda
 * marcada como TODO: esta función deja el proceso listo en el libro mayor.
 */
exports.retirarSaldo = onCall(RUN_OPTIONS, async (req) => {
  const uid = requireAuth(req);
  const monto = toPesos(req.data && req.data.monto, "El monto");
  if (monto < MONTO_MIN_RETIRO) {
    throw new HttpsError("invalid-argument", `El retiro mínimo es ${formatoCLP(MONTO_MIN_RETIRO)}.`);
  }

  const resultado = await db().runTransaction(async (tx) => {
    const billetera = await leerBilletera(tx, uid);
    if (billetera.saldo < monto) {
      throw new HttpsError("failed-precondition", "Saldo insuficiente en tu billetera.");
    }
    const nuevoSaldo = billetera.saldo - monto;
    const movimientoId = nuevoMovimientoId();
    billetera.saldo = nuevoSaldo;
    agregarMovimiento(tx, billetera, {
      id: movimientoId,
      tipoMovimiento: "retiro",
      monto: -monto,
      saldoResultante: nuevoSaldo,
      referencia: movimientoId,
      descripcion: "Retiro de billetera",
      estado: "pendiente",
      timestamp: FieldValue.serverTimestamp()
    });
    return { saldo: nuevoSaldo, movimientoId };
  });

  // TODO: integrar payout con Mercado Pago cuando el usuario tenga cuenta bancaria vinculada.
  return { ok: true, saldo: resultado.saldo, movimientoId: resultado.movimientoId };
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
    const timestamp = FieldValue.serverTimestamp();

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
    // Un turno por día (C20-53): si ya existe uno activo, se rechaza.
    const existentes = await db().collection("turnos").where("solicitudId", "==", solicitudId).get();
    const activoHoy = existentes.docs.find((doc) => {
      const t = doc.data();
      return t.fecha === fecha && !["pagado", "rechazado"].includes(t.estado);
    });
    if (activoHoy) {
      throw new HttpsError("already-exists", "Ya existe un turno iniciado para hoy.");
    }

    const monto = Math.round(tarifa * horas);
    const bill = await leerBilletera(tx, sol.representanteUid);
    const saldoRep = bill.saldo;

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
Object.defineProperty(exports, "_test", { value: { firmaValida, toPesos }, enumerable: false });
