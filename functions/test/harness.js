// Fakes mínimos de firebase-functions y firebase-admin para probar pagos.js en memoria.
const Module = require("module");
const crypto = require("crypto");

class HttpsError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

// ---------------- Firestore en memoria ----------------
const store = new Map(); // path -> data
let autoId = 0;
const SERVER_TS = { __serverTimestamp: true };
const DELETE_SENTINEL = { __deleteSentinel: true };

function clone(v) { return v === undefined ? undefined : JSON.parse(JSON.stringify(v)); }

class DocSnap {
  constructor(ref, data) { this.ref = ref; this.id = ref.id; this._d = data; this.exists = data !== undefined; }
  data() { return clone(this._d); }
  get(f) { return this._d ? this._d[f] : undefined; }
}
class DocRef {
  constructor(path) { this.path = path; this.id = path.split("/").pop(); }
  collection(n) { return new ColRef(`${this.path}/${n}`); }
  async get() { return new DocSnap(this, store.get(this.path)); }
  async set(d, opts) { writeSet(this.path, d, opts); }
  async update(d) { writeUpdate(this.path, d); }
  async delete() { store.delete(this.path); }
}
class Query {
  constructor(path, filters, lim) { this.path = path; this.filters = filters || []; this.lim = lim; }
  where(f, op, v) { return new Query(this.path, [...this.filters, [f, op, v]], this.lim); }
  limit(n) { return new Query(this.path, this.filters, n); }
  async get() {
    const depth = this.path.split("/").length + 1;
    let docs = [...store.entries()].filter(([p]) => p.startsWith(this.path + "/") && p.split("/").length === depth)
      .filter(([, d]) => this.filters.every(([f, , v]) => d[f] === v))
      .map(([p, d]) => new DocSnap(new DocRef(p), d));
    if (this.lim) docs = docs.slice(0, this.lim);
    return { docs, empty: docs.length === 0, size: docs.length };
  }
}
class ColRef extends Query {
  constructor(path) { super(path); }
  doc(id) { return new DocRef(`${this.path}/${id || "auto" + (++autoId)}`); }
}
function resolve(d) {
  const out = {};
  for (const [k, v] of Object.entries(d)) {
    if (v === DELETE_SENTINEL) continue;
    out[k] = v === SERVER_TS ? Date.now() : v;
  }
  return clone(out);
}
function writeSet(path, d, opts) {
  const prev = store.get(path);
  const merged = { ...prev, ...resolve(d) };
  for (const k of Object.keys(d)) {
    if (d[k] === DELETE_SENTINEL) delete merged[k];
  }
  store.set(path, opts && opts.merge && prev ? merged : resolve(d));
}
function writeUpdate(path, d) {
  const prev = store.get(path);
  if (!prev) throw new Error(`NOT_FOUND update ${path}`);
  const merged = { ...prev, ...resolve(d) };
  for (const k of Object.keys(d)) {
    if (d[k] === DELETE_SENTINEL) delete merged[k];
  }
  store.set(path, merged);
}

let txLock = Promise.resolve();
const firestore = {
  collection: (n) => new ColRef(n),
  batch: () => {
    const ops = [];
    return {
      delete: (r) => ops.push(() => store.delete(r.path)),
      set: (r, d, o) => ops.push(() => writeSet(r.path, d, o)),
      update: (r, d) => ops.push(() => writeUpdate(r.path, d)),
      commit: async () => ops.forEach((o) => o())
    };
  },
  // Transacciones serializadas: emula el aislamiento de Firestore.
  runTransaction: (fn) => {
    const run = txLock.then(async () => {
      const writes = []; let wrote = false;
      const tx = {
        get: async (ref) => { if (wrote) throw new Error("read after write in transaction"); return ref.get(); },
        set: (ref, d, o) => { wrote = true; writes.push(() => writeSet(ref.path, d, o)); },
        update: (ref, d) => { wrote = true; writes.push(() => writeUpdate(ref.path, d)); },
        delete: (ref) => { wrote = true; writes.push(() => store.delete(ref.path)); }
      };
      const res = await fn(tx);
      writes.forEach((w) => w());
      return res;
    });
    txLock = run.catch(() => {});
    return run;
  }
};
const admin = {
  firestore: Object.assign(() => firestore, {
    FieldValue: {
      serverTimestamp: () => SERVER_TS,
      delete: () => DELETE_SENTINEL
    },
    Timestamp: { now: () => Date.now() }
  }),
  app: () => ({ options: { projectId: "cuidale-app" } })
};

const fakes = {
  "firebase-functions/v2/https": { onCall: (_o, h) => h, onRequest: (_o, h) => h, HttpsError },
  "firebase-functions/v2/scheduler": { onSchedule: (_o, h) => h },
  "firebase-functions/params": { defineSecret: (n) => ({ value: () => process.env[n] }) },
  "firebase-admin": admin
};
const origLoad = Module._load;
Module._load = (req, ...rest) => fakes[req] || origLoad.call(Module, req, ...rest);

function firmar(secret, dataId, requestId, ts) {
  const m = `id:${dataId};request-id:${requestId};ts:${ts};`;
  return `ts=${ts},v1=` + crypto.createHmac("sha256", secret).update(m).digest("hex");
}

module.exports = { store, HttpsError, firmar };
