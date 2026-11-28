// Pruebas del OAuth de Mercado Pago Connect con Redirect URI https (retornoOauthMp).
const { test, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const path = require("path");
const { store } = require("./harness");

process.env.MP_ACCESS_TOKEN = "APP_USR-token-de-prueba";
process.env.MP_WEBHOOK_SECRET = "secreto-webhook";
process.env.MP_CLIENT_ID = "1881793312307228";
process.env.MP_CLIENT_SECRET = "secreto-cliente";
process.env.GCLOUD_PROJECT = "cuidale-app";
const P = require(path.join(__dirname, "..", "pagos.js"));

const CUI = "uid_cui";
const REDIRECT = "https://southamerica-west1-cuidale-app.cloudfunctions.net/retornoOauthMp";
const UA_ANDROID = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36";
const call = (fn, uid, data) => fn({ auth: uid ? { uid } : null, data });

function fakeReq(query, ua) {
  return { query, get: (h) => (String(h).toLowerCase() === "user-agent" ? ua || "" : "") };
}
function fakeRes() {
  const r = { statusCode: 200, headers: {}, body: "", location: "" };
  r.set = (k, v) => { r.headers[k] = v; return r; };
  r.status = (c) => { r.statusCode = c; return r; };
  r.send = (b) => { r.body = String(b); return r; };
  r.redirect = (c, url) => { r.statusCode = c; r.location = url; return r; };
  return r;
}
const estados = () => [...store.keys()].filter((k) => k.startsWith("mp_oauth_estados/"));

let llamadasToken;
beforeEach(() => {
  store.clear();
  llamadasToken = [];
  global.fetch = async (url, opts) => {
    if (String(url).endsWith("/oauth/token")) {
      llamadasToken.push(JSON.parse(opts.body));
      return { ok: true, json: async () => ({ access_token: "APP_USR-vendedor", refresh_token: "TG-refresh", user_id: 999111, public_key: "APP_USR-pk", email: "" }) };
    }
    throw new Error("fetch inesperado: " + url);
  };
});

async function iniciar() {
  const r = await call(P.iniciarOauthMp, CUI, {});
  const url = new URL(r.authUrl);
  return { r, url, state: url.searchParams.get("state") };
}

test("iniciarOauthMp: URL de autorización con redirect https y state aleatorio (no el uid)", async () => {
  const { r, url, state } = await iniciar();
  assert.equal(r.requiereRedirect, true);
  assert.equal(url.origin + url.pathname, "https://auth.mercadopago.com/authorization");
  assert.equal(url.searchParams.get("client_id"), "1881793312307228");
  assert.equal(url.searchParams.get("redirect_uri"), REDIRECT);
  assert.notEqual(state, CUI);
  assert.ok(state.length >= 32);
  assert.equal(store.get(`mp_oauth_estados/${state}`).uid, CUI);
});

test("retornoOauthMp: canjea el código en el backend, guarda la conexión fuera de la billetera y abre la app", async () => {
  const { state } = await iniciar();
  const res = fakeRes();
  await P.retornoOauthMp(fakeReq({ code: "TG-codigo", state }, UA_ANDROID), res);

  assert.equal(res.statusCode, 302);
  assert.match(res.location, /^intent:\/\/oauthmp\?estado=ok#Intent;scheme=cuidaleapp;/);
  assert.equal(llamadasToken.length, 1);
  assert.equal(llamadasToken[0].redirect_uri, REDIRECT);
  assert.equal(llamadasToken[0].client_secret, "secreto-cliente");
  assert.equal(llamadasToken[0].code, "TG-codigo");

  const con = store.get(`mp_conexiones/${CUI}`);
  assert.equal(con.access_token, "APP_USR-vendedor");
  assert.equal(con.user_id, "999111");
  assert.equal((store.get(`billetera/${CUI}`) || {}).mp_conexion, undefined);
  const cuenta = [...store.entries()].find(([k, d]) => k.startsWith("cuentas_bancarias/") && d.uid === CUI);
  assert.ok(cuenta);
  assert.equal(cuenta[1].mpUserId, "999111");
  assert.equal(estados().length, 0, "el state es de un solo uso");
});

test("retornoOauthMp: un state reutilizado no vincula nada", async () => {
  const { state } = await iniciar();
  await P.retornoOauthMp(fakeReq({ code: "TG-1", state }, UA_ANDROID), fakeRes());
  store.delete(`mp_conexiones/${CUI}`);
  const res = fakeRes();
  await P.retornoOauthMp(fakeReq({ code: "TG-2", state }, UA_ANDROID), res);
  assert.match(res.location, /estado=error/);
  assert.equal(llamadasToken.length, 1);
  assert.equal(store.get(`mp_conexiones/${CUI}`), undefined);
});

test("retornoOauthMp: un state vencido o inventado se rechaza sin llamar a Mercado Pago", async () => {
  const { state } = await iniciar();
  store.set(`mp_oauth_estados/${state}`, { uid: CUI, expiraAt: Date.now() - 1 });
  const res1 = fakeRes();
  await P.retornoOauthMp(fakeReq({ code: "TG-1", state }, UA_ANDROID), res1);
  const res2 = fakeRes();
  await P.retornoOauthMp(fakeReq({ code: "TG-1", state: "abc123" }, UA_ANDROID), res2);
  assert.match(res1.location, /estado=error/);
  assert.match(res2.location, /estado=error/);
  assert.equal(llamadasToken.length, 0);
});

test("retornoOauthMp: si el usuario cancela en Mercado Pago vuelve a la app como cancelado", async () => {
  const res = fakeRes();
  await P.retornoOauthMp(fakeReq({ error: "access_denied", state: "abc" }, UA_ANDROID), res);
  assert.match(res.location, /estado=cancelado/);
});

test("retornoOauthMp: fuera de Chrome Android muestra una página con el botón para volver", async () => {
  const { state } = await iniciar();
  const res = fakeRes();
  await P.retornoOauthMp(fakeReq({ code: "TG-codigo", state }, "Mozilla/5.0 (Windows NT 10.0)"), res);
  assert.equal(res.statusCode, 200);
  assert.match(res.body, /cuidaleapp:\/\/oauthmp\?estado=ok/);
});

test("canjearOauthMp: con OAuth configurado no acepta códigos llegados por deep link", async () => {
  await assert.rejects(call(P.canjearOauthMp, CUI, { code: "TG-de-otra-persona" }), (e) => e.code === "failed-precondition");
  assert.equal(llamadasToken.length, 0);
  assert.equal(store.get(`mp_conexiones/${CUI}`), undefined);
});

test("desvincularMp: borra los tokens guardados y la cuenta destino", async () => {
  const { state } = await iniciar();
  await P.retornoOauthMp(fakeReq({ code: "TG-codigo", state }, UA_ANDROID), fakeRes());
  await call(P.desvincularMp, CUI, {});
  assert.equal(store.get(`mp_conexiones/${CUI}`), undefined);
  assert.ok(![...store.values()].some((d) => d && d.uid === CUI && d.tipoDestino === "mercado_pago"));
});
