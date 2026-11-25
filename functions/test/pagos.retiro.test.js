// Pruebas de retiros a cuenta bancaria / Mercado Pago (Payouts), comisión 2%
// (tope $5.000), tope por retiro $200.000 y límite de 5 retiros/día.
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const path = require("path");
const { store, firmar } = require("./harness");

process.env.MP_ACCESS_TOKEN = "TEST-token-de-prueba";
process.env.MP_WEBHOOK_SECRET = "secreto-webhook";
process.env.GCLOUD_PROJECT = "cuidale-app";
const P = require(path.join(__dirname, "..", "pagos.js"));

const UID = "uid_usuario";
const CUENTA = "c1";

const call = (fn, uid, data) => fn({ auth: uid ? { uid } : null, data });
const saldo = (uid) => (store.get(`billetera/${uid}`) || {}).saldo || 0;
const movimientos = (uid) => (store.get(`billetera/${uid}`) || {}).movimientos || [];
const notifs = (docId) => [...store.keys()].filter((k) => k.startsWith(`usuarios/${docId}/notificaciones/`)).map((k) => store.get(k).tipo);

function seedCuentaBanco() {
  store.set(`cuentas_bancarias/${CUENTA}`, {
    uid: UID,
    tipoDestino: "banco",
    banco: "049",
    bancoNombre: "Banco Estado",
    tipoCuenta: "corriente",
    numeroCuenta: "12345678",
    titular: "Ana Perez",
    rut: "11111111-1"
  });
}

function seedBilletera(saldoInicial = 50000, movs = []) {
  store.set(`billetera/${UID}`, { uid: UID, saldo: saldoInicial, movimientos: movs });
}

function fetchOk(payload) {
  return async () => ({ ok: true, status: 200, json: async () => payload });
}

function fetchFail() {
  return async () => ({ ok: false, status: 400, json: async () => ({ message: "boom" }) });
}

const resMock = () => {
  const r = { code: 200 };
  r.status = (c) => { r.code = c; return r; };
  r.send = () => r;
  return r;
};

function webhookReq(body, dataId, requestId, ts) {
  return {
    method: "POST",
    query: {},
    body,
    get: (h) => {
      if (h === "x-signature") return firmar(process.env.MP_WEBHOOK_SECRET, dataId, requestId, ts);
      if (h === "x-request-id") return requestId;
      return "";
    }
  };
}

beforeEach(() => {
  store.clear();
  store.set("usuarios/hashUser", { uid: UID, rol: "representante", nombre: "Ana" });
  seedBilletera();
  global.fetch = fetchOk({ id: "payout1" });
});

test("validarRutChileno acepta/rechaza correctamente", () => {
  assert.equal(P._test.validarRutChileno("11.111.111-1"), true);
  assert.equal(P._test.validarRutChileno("12345678-5"), true);
  assert.equal(P._test.validarRutChileno("12345"), false);
  assert.equal(P._test.validarRutChileno("11111111-0"), false);
});

test("calcularComisionRetiro aplica 2% con tope $5.000", () => {
  assert.equal(P._test.calcularComisionRetiro(10000), 200);
  assert.equal(P._test.calcularComisionRetiro(1000), 20);
  assert.equal(P._test.calcularComisionRetiro(500000), 5000); // tope
  assert.equal(P._test.calcularComisionRetiro(100000), 2000);
});

test("registrarCuentaDestino crea cuenta de banco y evita duplicados", async () => {
  const r1 = await call(P.registrarCuentaDestino, UID, {
    tipoDestino: "banco",
    banco: "049",
    tipoCuenta: "corriente",
    numeroCuenta: "12345678",
    titular: "Ana Perez",
    rut: "11111111-1"
  });
  assert.equal(r1.tipoDestino, "banco");
  assert.equal(r1.mascara, "•••• 5678");
  assert.ok(r1.id);
  assert.equal(store.get(`cuentas_bancarias/${r1.id}`).uid, UID);

  const r2 = await call(P.registrarCuentaDestino, UID, {
    tipoDestino: "banco",
    banco: "049",
    tipoCuenta: "corriente",
    numeroCuenta: "12345678",
    titular: "Ana Perez",
    rut: "11111111-1"
  });
  assert.equal(r2.id, r1.id); // no duplica

  await assert.rejects(
    call(P.registrarCuentaDestino, UID, { tipoDestino: "banco", banco: "999", tipoCuenta: "corriente", numeroCuenta: "12345678", titular: "X", rut: "11111111-1" }),
    (e) => e.code === "invalid-argument"
  );
});

test("registrarCuentaDestino acepta cuenta de Mercado Pago", async () => {
  const r = await call(P.registrarCuentaDestino, UID, {
    tipoDestino: "mercado_pago",
    mpIdentificador: "usuario@test.cl"
  });
  assert.equal(r.tipoDestino, "mercado_pago");
  assert.equal(store.get(`cuentas_bancarias/${r.id}`).mpIdentificador, "usuario@test.cl");
});

test("retirarSaldo descuenta monto + comisión y crea payout", async () => {
  seedCuentaBanco();
  const r = await call(P.retirarSaldo, UID, { monto: 10000, cuentaId: CUENTA });
  assert.equal(r.estado, "procesando");
  assert.equal(r.comision, 200);
  assert.ok(r.payoutId);
  assert.equal(saldo(UID), 39800); // 50000 - (10000 + 200)
  const mov = movimientos(UID)[0];
  assert.equal(mov.tipoMovimiento, "retiro");
  assert.equal(mov.estado, "procesando");
  assert.equal(mov.montoNeto, 10000);
  assert.equal(mov.payoutId, "payout1");
});

test("retirarSaldo revierte saldo si el payout falla", async () => {
  seedCuentaBanco();
  global.fetch = fetchFail();
  await assert.rejects(
    call(P.retirarSaldo, UID, { monto: 10000, cuentaId: CUENTA }),
    (e) => e.code === "internal"
  );
  assert.equal(saldo(UID), 50000); // revertido
  assert.equal(movimientos(UID)[0].estado, "rechazada");
  assert.ok(notifs("hashUser").includes("retiro_rechazado"));
});

test("retirarSaldo rechaza sin cuenta registrada", async () => {
  await assert.rejects(
    call(P.retirarSaldo, UID, { monto: 10000, cuentaId: CUENTA }),
    (e) => e.code === "failed-precondition"
  );
});

test("retirarSaldo valida tope por retiro $200.000", async () => {
  seedCuentaBanco();
  await assert.rejects(
    call(P.retirarSaldo, UID, { monto: 200001, cuentaId: CUENTA }),
    (e) => e.code === "invalid-argument"
  );
});

test("retirarSaldo limita a 5 retiros por día", async () => {
  seedCuentaBanco();
  const hoy = new Date().toISOString().slice(0, 10);
  // fechaHoyCL usa huso de Chile; usamos el mismo string como aproximación.
  const movs = Array.from({ length: 5 }, () => ({
    id: "x",
    tipoMovimiento: "retiro",
    estado: "procesando",
    fechaLocal: hoy,
    monto: -1200
  }));
  seedBilletera(100000, movs);
  await assert.rejects(
    call(P.retirarSaldo, UID, { monto: 10000, cuentaId: CUENTA }),
    (e) => e.code === "failed-precondition" && /límite/.test(e.message)
  );
});

test("webhook payout procesado marca retiro aprobada", async () => {
  seedCuentaBanco();
  const iniciado = await call(P.retirarSaldo, UID, { monto: 10000, cuentaId: CUENTA });
  global.fetch = fetchOk({ id: "payout1", status: "processed", external_reference: `${UID}#${iniciado.movimientoId}` });
  const req = webhookReq({ type: "payout", data: { id: "payout1" } }, "payout1", "rid-1", Date.now());
  await P.webhookMercadoPagoPayout(req, resMock());
  assert.equal(movimientos(UID)[0].estado, "aprobada");
  assert.ok(notifs("hashUser").includes("retiro_aprobado"));
});

test("webhook payout rechazado revierte saldo", async () => {
  seedCuentaBanco();
  const iniciado = await call(P.retirarSaldo, UID, { monto: 10000, cuentaId: CUENTA });
  global.fetch = fetchOk({ id: "payout1", status: "rejected", status_detail: "account_invalid", external_reference: `${UID}#${iniciado.movimientoId}` });
  const req = webhookReq({ type: "payout", data: { id: "payout1" } }, "payout1", "rid-1", Date.now());
  await P.webhookMercadoPagoPayout(req, resMock());
  assert.equal(movimientos(UID)[0].estado, "rechazada");
  assert.equal(saldo(UID), 50000); // revertido
  assert.ok(notifs("hashUser").includes("retiro_rechazado"));
});
