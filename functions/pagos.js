/*
 * Cuidale App v2.0 – Billetera, recargas con Mercado Pago (Checkout Pro) y cobro de servicios.
 *
 * Colecciones (solo escritas por estas funciones; la app únicamente lee):
 *   billeteras/{uid}                    { uid, saldo, actualizadoAt }
 *   billeteras/{uid}/movimientos/{id}   { tipo, monto, saldoResultante, referencia, descripcion, timestamp }
 *   recargas/{recargaId}                { uid, monto, estado, preferenceId, paymentId, createdAt, aprobadaAt }
 *   cobros/{solicitudId}                { solicitudId, cuidadorUid, representanteUid, montoAcordado, horasAcordadas,
 *                                         monto, horas, comentario, estado, saldoSuficiente, ... }
 *
 * Estados de un cobro: pendiente | requiere_confirmacion | pagado | rechazado
 * Criterios de aceptación cubiertos: C20-51 a C20-58.
 *
 * Secretos requeridos (firebase functions:secrets:set NOMBRE):
 *   MP_ACCESS_TOKEN     Access Token de la aplicación en Mercado Pago Developers (de prueba).
 *   MP_WEBHOOK_SECRET   Clave secreta generada al configurar los Webhooks.
 */
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const crypto = require("crypto");

const MP_ACCESS_TOKEN = defineSecret("MP_ACCESS_TOKEN");
const MP_WEBHOOK_SECRET = defineSecret("MP_WEBHOOK_SECRET");

// Modo de prueba: mientras sea true, el checkout se abre en el sandbox de Mercado Pago (sin dinero real),
// sin importar si el Access Token de prueba empieza con TEST- o con APP_USR-.
// Cambiar a false solo al pasar a producción con credenciales productivas.
const MP_MODO_PRUEBA = true;

const REGION = "southamerica-west1";
const MP_API = "https://api.mercadopago.com";
const MONTO_MIN_RECARGA = 1000;
const MONTO_MAX_RECARGA = 1000000;
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

/* Agrega un movimiento a la billetera dentro de una transacción. */
function registrarMovimiento(tx, uid, mov) {
  const ref = db().collection("billeteras").doc(uid).collection("movimientos").doc();
  tx.set(ref, { id: ref.id, ...mov, timestamp: FieldValue.serverTimestamp() });
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
 * Plantilla firmada: "id:{data.id};request-id:{x-request-id};ts:{ts};" con HMAC-SHA256 y la clave secreta.
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

  const recargaRef = db().collection("recargas").doc();
  await recargaRef.set({
    id: recargaRef.id, uid, monto, estado: "pendiente",
    createdAt: FieldValue.serverTimestamp()
  });

  const retorno = functionUrl("retornoPago");
  const preferencia = await mpFetch("/checkout/preferences", {
    method: "POST",
    headers: { "X-Idempotency-Key": recargaRef.id },
    body: JSON.stringify({
      items: [{
        id: "recarga_billetera",
        title: "Recarga de billetera Cuidale App",
        quantity: 1,
        currency_id: "CLP",
        unit_price: monto
      }],
      external_reference: recargaRef.id,
      notification_url: functionUrl("webhookMercadoPago"),
      back_urls: {
        success: `${retorno}?estado=aprobado&recarga=${recargaRef.id}`,
        pending: `${retorno}?estado=pendiente&recarga=${recargaRef.id}`,
        failure: `${retorno}?estado=rechazado&recarga=${recargaRef.id}`
      },
      auto_return: "approved",
      metadata: { uid, recarga_id: recargaRef.id }
    })
  });

  await recargaRef.update({ preferenceId: preferencia.id });
  // En modo de prueba se usa el checkout de sandbox; si Mercado Pago no lo entrega, se usa init_point.
  const url = MP_MODO_PRUEBA && preferencia.sandbox_init_point ? preferencia.sandbox_init_point : preferencia.init_point;
  return { recargaId: recargaRef.id, url };
});

/* Página a la que vuelve el navegador después del pago; devuelve al usuario a la app. */
exports.retornoPago = onRequest({ memory: "256MiB" }, (req, res) => {
  const estado = String(req.query.estado || "pendiente").replace(/[^a-z_]/gi, "");
  const recarga = String(req.query.recarga || "").replace(/[^a-z0-9]/gi, "");
  const intent = `intent://pago?estado=${estado}&recarga=${recarga}#Intent;scheme=cuidaleapp;package=com.example.cuidaleapp;end`;
  const mensaje = estado === "aprobado" ? "¡Pago aprobado!" : estado === "rechazado" ? "El pago fue rechazado." : "Tu pago está en proceso.";
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(`<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Cuidale App</title><style>body{font-family:sans-serif;text-align:center;padding:48px 16px}a{display:inline-block;margin-top:24px;padding:14px 28px;background:#1F4E79;color:#fff;border-radius:24px;text-decoration:none}</style></head>
<body><h2>${mensaje}</h2><p>Puedes volver a Cuidale App.</p><a href="${intent}">Volver a la app</a>
<script>setTimeout(function(){location.href=${JSON.stringify(intent)};},600);</script></body></html>`);
});

/* Webhook de Mercado Pago: acredita la recarga solo si el pago está aprobado. Idempotente. */
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
    const recargaId = String(pago.external_reference || "");
    if (!recargaId) {
      res.status(200).send("Sin referencia");
      return;
    }
    const recargaRef = db().collection("recargas").doc(recargaId);

    await db().runTransaction(async (tx) => {
      const snap = await tx.get(recargaRef);
      if (!snap.exists) return;
      const recarga = snap.data();
      if (recarga.estado === "aprobada") return; // ya acreditada: no se abona dos veces

      if (pago.status === "approved") {
        if (Number(pago.transaction_amount) !== Number(recarga.monto) || pago.currency_id !== "CLP") {
          tx.update(recargaRef, { estado: "revision", paymentId, detalle: "monto o moneda no coinciden" });
          return;
        }
        const billeteraRef = db().collection("billeteras").doc(recarga.uid);
        const bSnap = await tx.get(billeteraRef);
        const saldoActual = bSnap.exists ? Number(bSnap.data().saldo || 0) : 0;
        const nuevoSaldo = saldoActual + Number(recarga.monto);
        tx.set(billeteraRef, { uid: recarga.uid, saldo: nuevoSaldo, actualizadoAt: FieldValue.serverTimestamp() }, { merge: true });
        registrarMovimiento(tx, recarga.uid, {
          tipo: "recarga", monto: Number(recarga.monto), saldoResultante: nuevoSaldo,
          referencia: recargaId, descripcion: "Recarga con Mercado Pago"
        });
        tx.update(recargaRef, { estado: "aprobada", paymentId, aprobadaAt: FieldValue.serverTimestamp() });
      } else if (["rejected", "cancelled", "refunded", "charged_back"].includes(pago.status)) {
        tx.update(recargaRef, { estado: "rechazada", paymentId, detalle: String(pago.status_detail || pago.status) });
      } else {
        tx.update(recargaRef, { estado: "pendiente", paymentId });
      }
    });

    const final = await recargaRef.get();
    if (final.exists && final.data().estado === "aprobada" && !final.data().notificada) {
      await recargaRef.update({ notificada: true });
      await notificar(final.data().uid, "recarga_aprobada", "sistema", {
        titulo: "Recarga aprobada",
        texto: `Se abonaron ${formatoCLP(final.data().monto)} a tu billetera.`
      });
    }
    res.status(200).send("OK");
  } catch (e) {
    console.error("webhookMercadoPago error:", e);
    res.status(500).send("Error"); // Mercado Pago reintentará
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

    const billSnap = await tx.get(db().collection("billeteras").doc(sol.representanteUid));
    const saldoRep = billSnap.exists ? Number(billSnap.data().saldo || 0) : 0;

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

    const repRef = db().collection("billeteras").doc(c.representanteUid);
    const cuiRef = db().collection("billeteras").doc(c.cuidadorUid);
    const [repSnap, cuiSnap] = await Promise.all([tx.get(repRef), tx.get(cuiRef)]);
    const saldoRep = repSnap.exists ? Number(repSnap.data().saldo || 0) : 0;
    const saldoCui = cuiSnap.exists ? Number(cuiSnap.data().saldo || 0) : 0;

    // C20-56 / C20-58: saldo insuficiente
    if (saldoRep < c.monto) {
      tx.update(cobroRef, { saldoSuficiente: false, updatedAt: FieldValue.serverTimestamp() });
      return { ok: false, motivo: "saldo_insuficiente", c, saldoRep };
    }

    // C20-55: transferencia atómica
    const nuevoRep = saldoRep - c.monto;
    const nuevoCui = saldoCui + c.monto;
    tx.set(repRef, { uid: c.representanteUid, saldo: nuevoRep, actualizadoAt: FieldValue.serverTimestamp() }, { merge: true });
    tx.set(cuiRef, { uid: c.cuidadorUid, saldo: nuevoCui, actualizadoAt: FieldValue.serverTimestamp() }, { merge: true });
    registrarMovimiento(tx, c.representanteUid, {
      tipo: "pago_servicio", monto: -c.monto, saldoResultante: nuevoRep, referencia: cobroId,
      descripcion: `Pago a ${c.cuidadorNombre || "cuidador"} por el cuidado de ${c.nombrePaciente || "paciente"}`
    });
    registrarMovimiento(tx, c.cuidadorUid, {
      tipo: "cobro_servicio", monto: c.monto, saldoResultante: nuevoCui, referencia: cobroId,
      descripcion: `Pago recibido de ${c.representanteNombre || "representante"}`
    });
    tx.update(cobroRef, { estado: "pagado", saldoSuficiente: true, pagadoAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
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
    const billSnap = await tx.get(db().collection("billeteras").doc(sol.representanteUid));
    const saldoRep = billSnap.exists ? Number(billSnap.data().saldo || 0) : 0;

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
