// Pruebas de los criterios C20-51 a C20-58, recargas con Mercado Pago y retiros (node --test).
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const path = require("path");
const { store, firmar } = require("./harness");

process.env.MP_ACCESS_TOKEN = "TEST-token-de-prueba";
process.env.MP_WEBHOOK_SECRET = "secreto-webhook";
process.env.GCLOUD_PROJECT = "cuidale-app";
const P = require(path.join(__dirname, "..", "pagos.js"));

const REP = "uid_rep";
const CUI = "uid_cui";
const SOL = "sol1";
const call = (fn, uid, data) => fn({ auth: uid ? { uid } : null, data });
const saldo = (uid) => (store.get(`billetera/${uid}`) || {}).saldo || 0;
const movimientos = (uid) => (store.get(`billetera/${uid}`) || {}).movimientos || [];
const notifs = (docId) => [...store.keys()].filter((k) => k.startsWith(`usuarios/${docId}/notificaciones/`)).map((k) => store.get(k).tipo);

beforeEach(() => {
  store.clear();
  store.set("usuarios/hashRep", { uid: REP, rol: "representante", nombre: "Ana" });
  store.set("usuarios/hashCui", { uid: CUI, rol: "cuidador", nombre: "Bruno" });
  store.set(`solicitudes/${SOL}`, { id: SOL, estado: "cerrada", representanteUid: REP, cuidadorUid: CUI, cuidadorNombre: "Bruno", representanteNombre: "Ana", nombrePaciente: "Rosa", pacienteFichaId: "fp1", montoAcordado: 30000, horasAcordadas: 6 });
  store.set(`fichas/${CUI}`, { especializacion: "Adulto mayor" });
  store.set("fichas/fp1", { edad: "85" });
  store.set(`billetera/${REP}`, { uid: REP, saldo: 50000, movimientos: [] });
});

test("C20-51: el cuidador inicia el cobro y queda pendiente para el representante", async () => {
  const r = await call(P.iniciarCobro, CUI, { solicitudId: SOL });
  assert.equal(r.estado, "pendiente");
  assert.equal(r.saldoSuficiente, true);
  assert.equal(store.get(`cobros/${SOL}`).monto, 30000);
  assert.ok(notifs("hashRep").includes("cobro_recibido"));
});

test("C20-52: cambiar monto u horas exige confirmación del representante", async () => {
  const r = await call(P.iniciarCobro, CUI, { solicitudId: SOL, monto: 35000, horas: 7 });
  assert.equal(r.estado, "requiere_confirmacion");
  await assert.rejects(call(P.pagarCobro, REP, { cobroId: SOL }), /confirmar los cambios/);
  await call(P.responderCambiosCobro, REP, { cobroId: SOL, aceptar: true });
  assert.equal(store.get(`cobros/${SOL}`).estado, "pendiente");
});

test("C20-53: no se puede crear un cobro duplicado", async () => {
  await call(P.iniciarCobro, CUI, { solicitudId: SOL });
  await assert.rejects(call(P.iniciarCobro, CUI, { solicitudId: SOL }), (e) => e.code === "already-exists");
});

test("C20-54: no se puede cobrar un servicio inexistente o ajeno", async () => {
  await assert.rejects(call(P.iniciarCobro, CUI, { solicitudId: "no-existe" }), (e) => e.code === "failed-precondition");
  await assert.rejects(call(P.iniciarCobro, "otro_cuidador", { solicitudId: SOL }), (e) => e.code === "failed-precondition");
});

test("C20-55: pago exitoso transfiere el saldo, notifica y finaliza el servicio", async () => {
  await call(P.iniciarCobro, CUI, { solicitudId: SOL });
  await call(P.pagarCobro, REP, { cobroId: SOL });
  assert.equal(saldo(REP), 20000);
  assert.equal(saldo(CUI), 30000);
  assert.equal(saldo(REP) + saldo(CUI), 50000, "la suma de saldos se conserva");
  assert.equal(store.get(`cobros/${SOL}`).estado, "pagado");
  assert.equal(store.has(`solicitudes/${SOL}`), false, "el servicio queda finalizado");
  assert.ok(movimientos(REP).some((m) => m.tipoMovimiento === "pago" && m.monto === -30000));
  assert.ok(movimientos(CUI).some((m) => m.tipoMovimiento === "pago" && m.monto === 30000));
  const hist = [...store.entries()].find(([k]) => k.startsWith("historial_servicios/"))[1];
  assert.equal(hist.montoPagado, 30000);
  assert.ok(notifs("hashCui").includes("pago_realizado"));
  assert.ok(notifs("hashRep").includes("pago_realizado"));
});

test("C20-56 / C20-58: saldo insuficiente rechaza el pago y avisa a ambos", async () => {
  store.set(`billetera/${REP}`, { uid: REP, saldo: 10000, movimientos: [] });
  await call(P.iniciarCobro, CUI, { solicitudId: SOL });
  await assert.rejects(call(P.pagarCobro, REP, { cobroId: SOL }), /Saldo insuficiente/);
  assert.equal(saldo(REP), 10000);
  assert.equal(saldo(CUI), 0);
  assert.ok(notifs("hashRep").includes("pago_saldo_insuficiente"));
  assert.ok(notifs("hashCui").includes("pago_pendiente"));
});

test("C20-57: pagar un servicio ya pagado se rechaza", async () => {
  await call(P.iniciarCobro, CUI, { solicitudId: SOL });
  await call(P.pagarCobro, REP, { cobroId: SOL });
  await assert.rejects(call(P.pagarCobro, REP, { cobroId: SOL }), /ya fue pagado/);
  assert.equal(saldo(REP), 20000);
});

test("Concurrencia: dos pagos simultáneos descuentan una sola vez (CP-20)", async () => {
  await call(P.iniciarCobro, CUI, { solicitudId: SOL });
  const res = await Promise.allSettled([call(P.pagarCobro, REP, { cobroId: SOL }), call(P.pagarCobro, REP, { cobroId: SOL })]);
  assert.equal(res.filter((r) => r.status === "fulfilled").length, 1);
  assert.equal(saldo(REP), 20000);
  assert.equal(saldo(CUI), 30000);
});

test("Seguridad: un tercero no puede pagar ni responder un cobro ajeno", async () => {
  await call(P.iniciarCobro, CUI, { solicitudId: SOL, monto: 40000 });
  await assert.rejects(call(P.pagarCobro, "intruso", { cobroId: SOL }), (e) => e.code === "permission-denied");
  await assert.rejects(call(P.responderCambiosCobro, "intruso", { cobroId: SOL, aceptar: true }), (e) => e.code === "permission-denied");
  await assert.rejects(call(P.iniciarCobro, null, { solicitudId: SOL }), (e) => e.code === "unauthenticated");
});

test("Turno: inicia el cobro del turno con tarifa y notifica al representante", async () => {
  store.set(`solicitudes/${SOL}`, { id: SOL, estado: "cerrada", representanteUid: REP, cuidadorUid: CUI, cuidadorNombre: "Bruno", representanteNombre: "Ana", nombrePaciente: "Rosa", pacienteFichaId: "fp1", tarifaHora: 9000, horarioStr: "De 09:00 a 18:00", estadoAcuerdo: "acordado" });
  store.set(`billetera/${REP}`, { uid: REP, saldo: 100000, movimientos: [] });
  const r = await call(P.iniciarCobroTurno, CUI, { solicitudId: SOL });
  assert.equal(r.estado, "pendiente_confirmacion_rep");
  assert.equal(r.saldoSuficiente, true);
  const turno = store.get(`turnos/${r.turnoId}`);
  assert.equal(turno.montoCalculado, 81000);
  assert.equal(turno.horasTrabajadas, 9);
  assert.equal(store.get(`cobros/${r.turnoId}`).monto, 81000);
  assert.equal(store.get(`solicitudes/${SOL}`).cobroEstado, "pendiente_confirmacion_rep");
  assert.ok(notifs("hashRep").includes("cobro_recibido"));
  assert.equal(store.has(`solicitudes/${SOL}`), true, "el contrato sigue vivo");
});

test("Turno: rechaza un duplicado del mismo día", async () => {
  store.set(`solicitudes/${SOL}`, { id: SOL, estado: "cerrada", representanteUid: REP, cuidadorUid: CUI, tarifaHora: 9000, horarioStr: "De 09:00 a 18:00", estadoAcuerdo: "acordado" });
  await call(P.iniciarCobroTurno, CUI, { solicitudId: SOL });
  await assert.rejects(call(P.iniciarCobroTurno, CUI, { solicitudId: SOL }), (e) => e.code === "already-exists");
});

test("Turno: rechaza con acuerdo pendiente o sin tarifa definida", async () => {
  store.set(`solicitudes/${SOL}`, { id: SOL, estado: "cerrada", representanteUid: REP, cuidadorUid: CUI, tarifaHora: 9000, horarioStr: "De 09:00 a 18:00", estadoAcuerdo: "en_negociacion" });
  await assert.rejects(call(P.iniciarCobroTurno, CUI, { solicitudId: SOL }), (e) => e.code === "failed-precondition");
  store.set(`solicitudes/${SOL}`, { id: SOL, estado: "cerrada", representanteUid: REP, cuidadorUid: CUI, tarifaHora: 0, horarioStr: "De 09:00 a 18:00", estadoAcuerdo: "acordado" });
  await assert.rejects(call(P.iniciarCobroTurno, CUI, { solicitudId: SOL }), (e) => e.code === "failed-precondition");
});

test("Turno: rechaza si el contrato no está activo o pertenece a otro cuidador", async () => {
  store.set(`solicitudes/${SOL}`, { id: SOL, estado: "abierta", representanteUid: REP, cuidadorUid: CUI, tarifaHora: 9000, horarioStr: "De 09:00 a 18:00", estadoAcuerdo: "acordado" });
  await assert.rejects(call(P.iniciarCobroTurno, CUI, { solicitudId: SOL }), (e) => e.code === "failed-precondition");
  store.set(`solicitudes/${SOL}`, { id: SOL, estado: "cerrada", representanteUid: REP, cuidadorUid: CUI, tarifaHora: 9000, horarioStr: "De 09:00 a 18:00", estadoAcuerdo: "acordado" });
  await assert.rejects(call(P.iniciarCobroTurno, "otro_cuidador", { solicitudId: SOL }), (e) => e.code === "failed-precondition");
});

// ---------------- Recargas con Mercado Pago ----------------
function fakeRes() {
  const r = { code: 0, body: "", headers: {} };
  r.status = (c) => { r.code = c; return r; };
  r.send = (b) => { r.body = b; return r; };
  r.set = (k, v) => { r.headers[k] = v; return r; };
  return r;
}
function webhookReq(paymentId, secret) {
  const ts = "1729600000";
  const reqId = "req-123";
  const headers = { "x-signature": firmar(secret, paymentId, reqId, ts), "x-request-id": reqId };
  return { method: "POST", query: { "data.id": paymentId, "type": "payment" }, body: { type: "payment", data: { id: paymentId } }, get: (h) => headers[h.toLowerCase()] };
}

test("Recarga: crea preferencia en Mercado Pago y devuelve la URL de sandbox", async () => {
  let enviado;
  global.fetch = async (url, opts) => { enviado = { url, body: JSON.parse(opts.body) }; return { ok: true, json: async () => ({ id: "pref1", init_point: "https://mp/init", sandbox_init_point: "https://mp/sandbox" }) }; };
  const r = await call(P.crearRecarga, REP, { monto: 20000 });
  assert.equal(r.url, "https://mp/sandbox");
  assert.equal(enviado.body.items[0].currency_id, "CLP");
  assert.equal(enviado.body.items[0].unit_price, 20000);
  assert.ok(enviado.body.external_reference.startsWith(`${REP}#`), "external_reference lleva uid#recargaId");
  assert.ok(enviado.body.payment_methods, "se envían payment_methods (débito/crédito)");
  assert.equal(enviado.body.payment_methods.installments, 12);
  assert.match(enviado.body.notification_url, /southamerica-west1-cuidale-app\.cloudfunctions\.net\/webhookMercadoPago$/);
  const mov = movimientos(REP).find((m) => m.id === r.recargaId);
  assert.ok(mov, "se crea el movimiento pendiente");
  assert.equal(mov.estado, "pendiente");
  assert.equal(mov.tipoMovimiento, "recarga");
  await assert.rejects(call(P.crearRecarga, REP, { monto: 500 }), (e) => e.code === "invalid-argument");
});

test("Recarga: con un token de prueba APP_USR- también abre el sandbox", async () => {
  const anterior = process.env.MP_ACCESS_TOKEN;
  process.env.MP_ACCESS_TOKEN = "APP_USR-token-de-prueba";
  global.fetch = async () => ({ ok: true, json: async () => ({ id: "pref2", init_point: "https://mp/init", sandbox_init_point: "https://mp/sandbox" }) });
  const r = await call(P.crearRecarga, REP, { monto: 20000 });
  process.env.MP_ACCESS_TOKEN = anterior;
  assert.equal(r.url, "https://mp/sandbox");
});

test("Webhook: acredita el pago aprobado una sola vez aunque llegue repetido", async () => {
  store.set(`billetera/${REP}`, {
    uid: REP, saldo: 50000,
    movimientos: [{ id: "rc1", tipoMovimiento: "recarga", monto: 20000, estado: "pendiente", timestamp: 1 }]
  });
  global.fetch = async () => ({ ok: true, json: async () => ({ status: "approved", transaction_amount: 20000, currency_id: "CLP", external_reference: `${REP}#rc1` }) });
  const r1 = fakeRes(); await P.webhookMercadoPago(webhookReq("999", "secreto-webhook"), r1);
  const r2 = fakeRes(); await P.webhookMercadoPago(webhookReq("999", "secreto-webhook"), r2);
  assert.equal(r1.code, 200); assert.equal(r2.code, 200);
  assert.equal(saldo(REP), 70000);
  const mov = movimientos(REP).find((m) => m.id === "rc1");
  assert.equal(mov.estado, "aprobada");
  assert.equal(mov.saldoResultante, 70000);
});

test("Webhook: rechaza firmas inválidas y no acredita montos alterados", async () => {
  store.set(`billetera/${REP}`, {
    uid: REP, saldo: 50000,
    movimientos: [{ id: "rc2", tipoMovimiento: "recarga", monto: 20000, estado: "pendiente", timestamp: 1 }]
  });
  const bad = fakeRes(); await P.webhookMercadoPago(webhookReq("1000", "otra-clave"), bad);
  assert.equal(bad.code, 401);
  global.fetch = async () => ({ ok: true, json: async () => ({ status: "approved", transaction_amount: 1, currency_id: "CLP", external_reference: `${REP}#rc2` }) });
  const r = fakeRes(); await P.webhookMercadoPago(webhookReq("1000", "secreto-webhook"), r);
  assert.equal(movimientos(REP).find((m) => m.id === "rc2").estado, "revision");
  assert.equal(saldo(REP), 50000);
});

test("Webhook: acredita y migra una recarga legacy (external_reference sin uid)", async () => {
  store.set("recargas/rcLegacy", { id: "rcLegacy", uid: REP, monto: 15000, estado: "pendiente" });
  global.fetch = async () => ({ ok: true, json: async () => ({ status: "approved", transaction_amount: 15000, currency_id: "CLP", external_reference: "rcLegacy" }) });
  const r = fakeRes(); await P.webhookMercadoPago(webhookReq("1234", "secreto-webhook"), r);
  assert.equal(r.code, 200);
  assert.equal(saldo(REP), 65000, "se acredita el monto legacy");
  assert.ok(movimientos(REP).some((m) => m.id === "rcLegacy" && m.estado === "aprobada"));
  assert.equal(store.get("recargas/rcLegacy").estado, "aprobada");
});

// ---------------- Retiros ----------------
test("Retiro: valida el mínimo y rechaza saldo insuficiente", async () => {
  await assert.rejects(call(P.retirarSaldo, REP, { monto: 500 }), (e) => e.code === "invalid-argument");
  store.set(`billetera/${REP}`, { uid: REP, saldo: 10000, movimientos: [] });
  await assert.rejects(call(P.retirarSaldo, REP, { monto: 20000 }), (e) => e.code === "failed-precondition");
});

test("Retiro: descuenta el saldo y registra el movimiento", async () => {
  store.set(`billetera/${REP}`, { uid: REP, saldo: 10000, movimientos: [] });
  const r = await call(P.retirarSaldo, REP, { monto: 3000 });
  assert.equal(r.ok, true);
  assert.equal(r.saldo, 7000);
  assert.equal(saldo(REP), 7000);
  const m = movimientos(REP).find((x) => x.id === r.movimientoId);
  assert.equal(m.tipoMovimiento, "retiro");
  assert.equal(m.monto, -3000);
  assert.equal(m.estado, "pendiente");
});
