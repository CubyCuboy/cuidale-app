const { setGlobalOptions } = require("firebase-functions/v2");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const crypto = require("crypto");

setGlobalOptions({ region: "southamerica-west1" });
admin.initializeApp();

const GMAIL_EMAIL = defineSecret("GMAIL_EMAIL");
const GMAIL_PASS = defineSecret("GMAIL_APP_PASSWORD");
const RUT_HMAC_SECRET = defineSecret("RUT_HMAC_SECRET");
const db = admin.firestore();
const auth = admin.auth();

const MAX_ATTEMPTS = 5;
const VERIFICATION_TTL_MS = 30 * 60 * 1000;
const RECOVERY_TTL_MS = 5 * 60 * 1000;
const RUN_OPTIONS = { memory: "512MiB", timeoutSeconds: 120 };

const RATE_LIMIT_MIN_INTERVAL_MS = 60 * 1000;
const RATE_LIMIT_HOURLY_CAP = 8;
const TRASH_TTL_MS = 48 * 60 * 60 * 1000;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const RUT_RE = /^\d{7,8}[0-9K]$/;

async function enforceRateLimit(key, label, allowRenewal = false) {
  const ref = db.collection("rate_limits").doc(key);
  const snap = await ref.get();
  const now = Date.now();
  let timestamps = snap.exists ? (snap.data().timestamps || []) : [];
  timestamps = timestamps.filter((t) => now - t < 60 * 60 * 1000);
  const last = timestamps.length ? Math.max(...timestamps) : 0;
  if (!allowRenewal && now - last < RATE_LIMIT_MIN_INTERVAL_MS) {
    throw new HttpsError("resource-exhausted", `Debes esperar un momento antes de realizar otra ${label}.`);
  }
  if (timestamps.length >= RATE_LIMIT_HOURLY_CAP) {
    throw new HttpsError("resource-exhausted", `Has alcanzado el límite de ${label} por hora. Intenta más tarde.`);
  }
  timestamps.push(now);
  await ref.set({ timestamps });
}

const hash = (value) => crypto.createHash("sha256").update(String(value)).digest("hex");
const rutKey = (rut) => {
  const clean = String(rut || "").replace(/\./g, "").replace(/-/g, "").trim().toUpperCase();
  return crypto.createHmac("sha256", RUT_HMAC_SECRET.value()).update(clean).digest("hex");
};
const transporter = (email, pass) => nodemailer.createTransport({ host: "smtp.gmail.com", port: 465, secure: true, auth: { user: email, pass: pass } });

const normalizeProviderId = (providerId) => {
  const key = String(providerId || "").trim().toLowerCase();
  if (key === "google" || key === "google.com") return "google.com";
  if (key === "microsoft" || key === "microsoft.com") return "microsoft.com";
  if (key === "password" || key === "email") return "email";
  return key;
};

const providerDisplayName = (providerId) => {
  const key = normalizeProviderId(providerId);
  if (key === "google.com") return "Google";
  if (key === "microsoft.com") return "Microsoft";
  if (key === "email") return "Correo y Contraseña";
  return providerId;
};

function mergeLinkedAccount(currentList, providerId, providerName, accountName, verificado, timestamp) {
  const cleanId = normalizeProviderId(providerId);
  const cleanName = providerName || providerDisplayName(cleanId);
  const list = Array.isArray(currentList) ? currentList : [];
  const existing = list.find((it) => it.providerId === cleanId);
  const entry = {
    providerId: cleanId,
    providerName: cleanName,
    accountName: accountName || (existing && existing.accountName) || "",
    linkedAt: existing && existing.linkedAt > 0 ? existing.linkedAt : timestamp,
    verificado: verificado || (existing && existing.verificado === true)
  };
  return list.filter((it) => it.providerId !== cleanId).concat(entry).sort((a, b) => (a.linkedAt || 0) - (b.linkedAt || 0));
}

async function findUserDocByEmailOrUid(email, uid) {
  if (email) {
    const userQuery = await db.collection("usuarios").where("correo", "==", email).limit(1).get();
    if (!userQuery.empty) {
      return { ref: userQuery.docs[0].ref, data: userQuery.docs[0].data(), id: userQuery.docs[0].id };
    }
  }
  if (uid) {
    const uidQuery = await db.collection("usuarios").where("uid", "==", uid).limit(1).get();
    if (!uidQuery.empty) {
      const doc = uidQuery.docs[0];
      const data = doc.data() || {};
      if (!email) return { ref: doc.ref, data, id: doc.id };
      const normalizedEmail = String(email).trim().toLowerCase();
      const linkedMatches = (Array.isArray(data.linkedAccounts) ? data.linkedAccounts : []).some(
        (it) => String((it && it.accountName) || "").trim().toLowerCase() === normalizedEmail
      );
      if (String(data.correo || "").trim().toLowerCase() === normalizedEmail || linkedMatches) {
        return { ref: doc.ref, data, id: doc.id };
      }
    }
  }
  return null;
}

async function moveToTrash(canonicalRef, sourceRef, sourceId, sourceData, reason) {
  try {
    const trashRef = canonicalRef.collection("basura").doc(sourceId);
    await trashRef.set({
      idOriginal: String(sourceId),
      motivo: String(reason || ""),
      movidaA: admin.firestore.FieldValue.serverTimestamp(),
      data: sourceData || {}
    });
  } catch (e) {
    console.error("moveToTrash error:", e);
  }
}

async function deleteTrashEntry(canonicalRef, sourceId) {
  try {
    await canonicalRef.collection("basura").doc(String(sourceId)).delete();
  } catch (e) {
    console.error("deleteTrashEntry error:", e);
  }
}

async function cleanupExpiredTrash(canonicalRef, maxAgeMs) {
  const limit = maxAgeMs || TRASH_TTL_MS;
  try {
    const snap = await canonicalRef.collection("basura").get();
    const now = Date.now();
    const batch = admin.firestore().batch();
    let changes = 0;
    snap.docs.forEach((doc) => {
      const data = doc.data() || {};
      const moved = data.movidaA;
      const ts = moved && moved.toMillis ? moved.toMillis() : 0;
      if (!ts || now - ts > limit) {
        batch.delete(doc.ref);
        changes++;
      }
    });
    if (changes > 0) await batch.commit();
  } catch (e) {
    console.error("cleanupExpiredTrash error:", e);
  }
}

async function cleanupUserDocuments(canonicalRef, canonicalId, email, uid) {
  const ephemeralIds = new Set();
  if (uid) ephemeralIds.add(uid);
  if (email) ephemeralIds.add(email);

  try {
    const snap = await db.collection("usuarios").where("correo", "==", email).get();
    for (const doc of snap.docs) {
      const id = doc.id;
      if (id === canonicalId) continue;
      const data = doc.data() || {};
      const isGhost =
        id === uid ||
        id === email ||
        (data.completado !== true && (data.uid === uid || !data.rut));
      if (isGhost) {
        await moveToTrash(canonicalRef, doc.ref, id, data, "documento_duplicado_o_ghost");
        await doc.ref.delete().catch(() => {});
      }
    }
  } catch (e) {
    console.error("cleanupUserDocuments error:", e);
  }

  try {
    for (const id of ephemeralIds) {
      if (id && id !== canonicalId) {
        const ref = db.collection("usuarios").doc(id);
        const doc = await ref.get();
        if (doc.exists) {
          const data = doc.data() || {};
          const isGhost = data.completado !== true || (data.uid === uid && id !== canonicalId);
          if (isGhost) {
            await moveToTrash(canonicalRef, ref, id, data, "documento_temporal_por_uid_o_email");
            await ref.delete().catch(() => {});
          }
        }
      }
    }
  } catch (e) {
    console.error("cleanup ephemeral docs error:", e);
  }
}

function buildCodeEntry(prefix, code, now, ttl) {
  return {
    tipo: prefix,
    code_hash: hash(code),
    emission_time: now,
    expires_at: now + ttl,
    attempts: 0
  };
}

function getCode(data, prefix) {
  const list = Array.isArray(data && data.codigos) ? data.codigos : [];
  return list.find((it) => it && it.tipo === prefix) || null;
}

function cleanupCodeFields(prefix) {
  const fields = {};
  fields[`${prefix}_code_hash`] = admin.firestore.FieldValue.delete();
  fields[`${prefix}_emission_time`] = admin.firestore.FieldValue.delete();
  fields[`${prefix}_expires_at`] = admin.firestore.FieldValue.delete();
  fields[`${prefix}_attempts`] = admin.firestore.FieldValue.delete();
  fields[`${prefix}_verified`] = admin.firestore.FieldValue.delete();
  return fields;
}

async function sendEmailCode(email, type, uid) {
  const isRecovery = type === "recovery";
  const normalizedEmail = String(email || "").trim().toLowerCase();
  if (!EMAIL_RE.test(normalizedEmail)) {
    throw new HttpsError("invalid-argument", "Correo electrónico no válido.");
  }
  const ttl = isRecovery ? RECOVERY_TTL_MS : VERIFICATION_TTL_MS;
  const prefix = isRecovery ? "recovery" : "verification";
  const subject = isRecovery ? "Código de recuperación" : "Código de verificación";
  const user = await findUserDocByEmailOrUid(email, uid);
  if (!user) throw new HttpsError("not-found", "Usuario no registrado");

  // Reenvío: si ya existe un código del mismo tipo, se reemplaza (se elimina
  // el viejo y se genera uno nuevo) sin chocar con el intervalo mínimo de 60s,
  // pero respetando el límite horario.
  const previous = getCode(user.data, prefix);
  await enforceRateLimit("code_" + normalizedEmail, "solicitud", !!previous);

  const code = Math.floor(100000 + Math.random() * 900000).toString();
  const now = Date.now();
  const entry = buildCodeEntry(prefix, code, now, ttl);

  if (previous) {
    await user.ref.update({ codigos: admin.firestore.FieldValue.arrayRemove(previous) });
  }
  await user.ref.update({ codigos: admin.firestore.FieldValue.arrayUnion(entry) });

  await user.ref.update(cleanupCodeFields(prefix)).catch(() => {});

  try {
    await transporter(GMAIL_EMAIL.value(), GMAIL_PASS.value()).sendMail({
      from: `"Cuidale App" <${GMAIL_EMAIL.value()}>`,
      to: email,
      subject: subject,
      text: `Tu código es: ${code}`,
      html: `<p>Tu código es: <b>${code}</b></p>`
    });
  } catch (mailError) {
    console.error("sendEmailCode mail error:", mailError);
    throw new HttpsError("internal", "No se pudo enviar el correo. Intenta nuevamente en unos minutos.");
  }
  return { success: true, expiresAt: now + ttl };
}

async function verifyEmailCode(email, code, type, uid) {
  const prefix = type === "recovery" ? "recovery" : "verification";
  if (!/^\d{6}$/.test(String(code || ""))) {
    throw new HttpsError("invalid-argument", "El código debe tener 6 dígitos.");
  }
  const user = await findUserDocByEmailOrUid(email, uid);
  if (!user) throw new HttpsError("not-found", "Usuario no encontrado");
  const data = user.data;

  const entry = getCode(data, prefix);
  if (!entry) throw new HttpsError("failed-precondition", "No solicitado");
  if (Date.now() > entry.expires_at) throw new HttpsError("failed-precondition", "Expirado");

  const attempts = (entry.attempts || 0) + 1;
  if (hash(code) !== entry.code_hash) {
    const updatedEntry = { ...entry, attempts };
    await user.ref.update({ codigos: admin.firestore.FieldValue.arrayRemove(entry) }).catch(() => {});
    await user.ref.update({ codigos: admin.firestore.FieldValue.arrayUnion(updatedEntry) });
    if (attempts >= MAX_ATTEMPTS) throw new HttpsError("resource-exhausted", "Máximo de intentos");
    throw new HttpsError("permission-denied", "Código incorrecto");
  }

  const now = Date.now();
  await moveToTrash(user.ref, user.ref, `${prefix}_code`, entry, "codigo_usado");
  await user.ref.update({ codigos: admin.firestore.FieldValue.arrayRemove(entry) }).catch(() => {});
  await deleteTrashEntry(user.ref, `${prefix}_code`);
  await cleanupExpiredTrash(user.ref);

  const updateSuccess = { ...cleanupCodeFields(prefix), updatedAt: admin.firestore.FieldValue.serverTimestamp() };

  if (type === "verification") {
    updateSuccess["verificado"] = true;
    updateSuccess["verification_verified"] = admin.firestore.FieldValue.delete();
    const mergedLinked = mergeLinkedAccount(data.linkedAccounts, "email", "", email, true, now);
    updateSuccess["linkedAccounts"] = mergedLinked;
    await user.ref.update(updateSuccess);
    await cleanupUserDocuments(user.ref, user.id, email, data.uid || null);
  } else {
    updateSuccess["recovery_verified"] = true;
    await user.ref.update(updateSuccess);
  }

  return { success: true };
}

exports.sendRecoveryCode = onCall({ ...RUN_OPTIONS, secrets: [GMAIL_EMAIL, GMAIL_PASS] }, async (req) => {
  return await sendEmailCode(String((req.data && req.data.email) || ""), "recovery", String((req.data && req.data.uid) || ""));
});
exports.verifyRecoveryCode = onCall(RUN_OPTIONS, async (req) => {
  return await verifyEmailCode(req.data.email, req.data.code, "recovery", String(req.data.uid || ""));
});
exports.sendVerificationCode = onCall({ ...RUN_OPTIONS, secrets: [GMAIL_EMAIL, GMAIL_PASS] }, async (req) => {
  return await sendEmailCode(String((req.data && req.data.email) || ""), "verification", String((req.data && req.data.uid) || ""));
});
exports.verifyVerificationCode = onCall(RUN_OPTIONS, async (req) => {
  return await verifyEmailCode(req.data.email, req.data.code, "verification", String(req.data.uid || ""));
});

exports.confirmPasswordReset = onCall(RUN_OPTIONS, async (req) => {
  const { email, newPassword } = req.data || {};
  if (typeof newPassword !== "string" || newPassword.length < 8 ||
      !/[A-Z]/.test(newPassword) || !/[^A-Za-z0-9]/.test(newPassword)) {
    throw new HttpsError("invalid-argument", "La contraseña debe tener al menos 8 caracteres, una mayúscula y un carácter especial.");
  }
  const uid = String((req.data && req.data.uid) || "");
  let targetEmail = String(email || "").trim().toLowerCase();
  const user = await findUserDocByEmailOrUid(targetEmail, uid);
  if (!user) throw new HttpsError("not-found", "Usuario no encontrado");
  if (!targetEmail) {
    targetEmail = String(user.data.correo || "").trim().toLowerCase();
  }
  if (!targetEmail) {
    throw new HttpsError("failed-precondition", "La cuenta no tiene un correo asociado.");
  }
  if (!user.data.recovery_verified) throw new HttpsError("permission-denied", "No verificado");

  const userAuth = await auth.getUserByEmail(targetEmail);
  await auth.updateUser(userAuth.uid, { password: newPassword });

  const leftover = getCode(user.data, "recovery");
  if (leftover) {
    await user.ref.update({ codigos: admin.firestore.FieldValue.arrayRemove(leftover) }).catch(() => {});
  }

  const updateData = {
    ...cleanupCodeFields("recovery"),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  };
  await user.ref.update(updateData);
  await cleanupUserDocuments(user.ref, user.id, email, user.data.uid || null);
  return { success: true };
});

exports.resolveAccountByRut = onCall(
  { ...RUN_OPTIONS, secrets: [RUT_HMAC_SECRET] },
  async (req) => {
    const rut = String((req.data && req.data.rut) || "");
    const clean = rut.replace(/\./g, "").replace(/-/g, "").trim().toUpperCase();
    if (!RUT_RE.test(clean)) {
      throw new HttpsError("invalid-argument", "RUT no válido.");
    }
    await enforceRateLimit("rut_" + clean, "consulta");

    const key = rutKey(clean);
    const query = await db.collection("usuarios").where("rut_key", "==", key).limit(1).get();
    let doc = query.empty ? null : query.docs[0];

    if (!doc) {
      const legacy = crypto.createHash("sha256").update(clean).digest("hex");
      const legacySnap = await db.collection("usuarios").doc(legacy).get();
      if (legacySnap.exists) {
        doc = legacySnap;
        await doc.ref.update({ rut_key: key }).catch(() => {});
      }
    }

    if (!doc) throw new HttpsError("not-found", "RUT no encontrado.");

    const data = doc.data() || {};
    const email = String(data.correo || "");
    if (!email) throw new HttpsError("not-found", "La cuenta no tiene un correo asociado.");
    let hasPassword = Array.isArray(data.linkedAccounts)
      ? data.linkedAccounts.some((a) => a.providerId === "email")
      : false;

    // Cuentas vinculadas o heredadas pueden no tener el campo linkedAccounts con
    // el proveedor "email". Consultamos Firebase Auth para confirmar si la
    // cuenta realmente tiene contraseña (proveedor password).
    if (!hasPassword) {
      try {
        const authUser = await auth.getUserByEmail(email);
        hasPassword = authUser.providerData.some(
          (p) => p.providerId === "password" || p.providerId === "email"
        );
      } catch (e) {
        console.error("resolveAccountByRut: al verificar proveedores:", e);
      }
    }

    return {
      ok: true,
      email,
      hasPassword,
      verified: data.verificado === true,
      rutKey: key
    };
  }
);

exports.maintainRutKey = onDocumentWritten(
  { document: "usuarios/{userId}" },
  async (event) => {
    const after = event.data.after.exists ? event.data.after.data() : null;
    if (!after) return;
    const rut = String(after.rut || "");
    if (!rut || after.rut_key) return;
    await event.data.after.ref.update({ rut_key: rutKey(rut) }).catch(() => {});
  }
);

const LOCAL_NOTIFICATION_TYPES = ["recordatorio_tarea", "tarea_atrasada"];

function buildNotificationContent(type, data) {
  const notifData = data || {};
  const title = notifData.title || notifData.titulo;
  const body = notifData.text || notifData.body || notifData.texto;

  const cases = {
    nuevo_mensaje: {
      title: title || (notifData.emisorNombre ? `Mensaje de ${notifData.emisorNombre}` : "Nuevo Mensaje"),
      body: body || (notifData.chatNombre ? `${notifData.chatNombre}: Has recibido un nuevo mensaje.` : "Has recibido un nuevo mensaje."),
      target: "CHAT_DETAIL"
    },
    solicitud_vinculo: {
      title: title || "Nueva Solicitud de Vínculo",
      body: body || "Un usuario desea conectar contigo para coordinar cuidados.",
      target: "NOTIFICATIONS"
    },
    vinculo_aceptado: {
      title: title || "¡Solicitud Aceptada!",
      body: body || "Tu solicitud fue aceptada. Ya puedes chatear.",
      target: "CHATS"
    },
    postulacion_recibida: {
      title: title || "Nueva Postulación Recibida",
      body: body || "Un cuidador se ha postulado a tu solicitud de servicio.",
      target: "NOTIFICATIONS"
    },
    postulacion_aceptada: {
      title: title || "¡Postulación Aceptada!",
      body: body || "Tu postulación de cuidado fue aceptada.",
      target: "SOLICITUDES"
    },
    servicio_finalizado: {
      title: title || "Servicio Finalizado",
      body: body || "El servicio de cuidado ha sido finalizado.",
      target: "SOLICITUDES"
    },
    tarea_finalizada: {
      title: title || "Tarea Finalizada",
      body: body || "Se ha completado una tarea del servicio.",
      target: "SOLICITUDES"
    },
    tarea_postergada: {
      title: title || "Tarea Postergada",
      body: body || "Se ha postergado una tarea del servicio.",
      target: "SOLICITUDES"
    },
    turno_finalizado: {
      title: title || "Turno Finalizado",
      body: body || "El cuidador ha completado el turno de hoy.",
      target: "SOLICITUDES"
    },
    configuracion_cuenta: {
      title: title || "Gestión de Cuenta",
      body: body || "Se realizó una actualización en la configuración de tu cuenta.",
      target: "NOTIFICATIONS"
    },
    nuevo_usuario_zona: {
      title: title || "Nuevo usuario en tu zona",
      body: body || "Se ha registrado un nuevo usuario cerca de ti.",
      target: "OTHER_FICHA"
    }
  };
  return cases[type] || {
    title: title || "Notificación de CuidaleApp",
    body: body || "Tienes una nueva actualización en la aplicación.",
    target: "MAIN"
  };
}

async function resolveRecipientToken(userId) {
  const userDoc = await db.collection("usuarios").doc(userId).get();
  const directData = userDoc.exists ? userDoc.data() : null;
  if (directData && directData.fcmToken) return { data: directData, ref: userDoc.ref };

  const uidQuery = await db.collection("usuarios").where("uid", "==", userId).limit(50).get();
  let best = null;
  for (const doc of uidQuery.docs) {
    const d = doc.data() || {};
    if (!d.fcmToken) continue;
    if (!best || (d.completado === true && !best.data.completado)) {
      best = { data: d, ref: doc.ref };
    }
  }
  if (best) return best;

  if (!uidQuery.empty) {
    const first = uidQuery.docs[0];
    return { data: first.data() || {}, ref: first.ref };
  }
  return null;
}

exports.sendPushNotification = onDocumentCreated(
  { ...RUN_OPTIONS, document: "usuarios/{userId}/notificaciones/{notifId}" },
  async (event) => {
    const notification = (event.data && typeof event.data.data === "function") ? event.data.data() : null;
    const userId = event.params.userId;
    if (!notification) return;

    try {
      const resolved = await resolveRecipientToken(userId);
      if (!resolved || !resolved.data.fcmToken) {
        console.error("sendPushNotification: sin fcmToken para usuario", userId);
        return;
      }
      const fcmToken = resolved.data.fcmToken;

      const type = notification.tipo || "mensaje";
      const emitter = String(notification.emisor || "");
      const receiver = String(notification.receptor || userId || "");
      if (LOCAL_NOTIFICATION_TYPES.includes(type) && (!receiver || !emitter || receiver === emitter)) {
        return;
      }
      const content = buildNotificationContent(type, notification.data || {});
      const receiverUid = String(notification.receptor || userId || "");
      const notifId = String(event.params.notifId || "");

      let senderName = (notification.data || {}).emisorNombre || "";
      let senderPhoto = (notification.data || {}).emisorFoto || "";
      if (notification.emisor && (!senderName || !senderPhoto)) {
        try {
          const senderQuery = await db.collection("usuarios").where("uid", "==", notification.emisor).limit(1).get();
          if (!senderQuery.empty) {
            const senderData = senderQuery.docs[0].data() || {};
            if (!senderName) senderName = senderData.nombre || "";
            if (!senderPhoto) senderPhoto = senderData.fotoUrl || "";
          }
        } catch (e) {
          console.error("Error enriqueciendo emisor:", e);
        }
      }

      const dataPayload = {
        tipo: String(type),
        emisor: String(notification.emisor || ""),
        receptor: receiverUid,
        TARGET_SCREEN: content.target,
        click_action: "FLUTTER_NOTIFICATION_CLICK",
        notifId: notifId
      };
      if (senderName) dataPayload.emisorNombre = senderName;
      if (senderPhoto) dataPayload.emisorFoto = senderPhoto;

      const notifData = notification.data || {};
      if (typeof notifData === "object") {
        for (const [key, value] of Object.entries(notifData)) {
          if (value !== undefined && value !== null) {
            dataPayload[key] = String(value);
          }
        }
      }


      const message = {
        token: fcmToken,
        data: dataPayload,
        android: {
          priority: "high"
        }
      };

      await admin.messaging().send(message);
    } catch (error) {
      console.error("Error sending push notification:", error);
    }
  }
);

exports.notifyMatchingUsers = onDocumentCreated(
  { ...RUN_OPTIONS, document: "usuarios/{newUserId}" },
  async (event) => {
    const newUser = (event.data && typeof event.data.data === "function") ? event.data.data() : null;
    const newUserId = event.params.newUserId;
    if (!newUser || !newUser.rol || !newUser.comuna) return;

    const targetRol = newUser.rol === "cuidador" ? "representante" : "cuidador";

    try {
      const matches = await db.collection("usuarios")
        .where("rol", "==", targetRol)
        .where("comuna", "==", newUser.comuna)
        .limit(50)
        .get();

      const notificationPromises = matches.docs.map(async (doc) => {
        const userData = doc.data();
        if (userData.fcmToken && doc.id !== newUserId && userData.uid !== newUser.uid) {
          const title = targetRol === "cuidador" ? "¡Nuevo Cuidador en tu zona!" : "¡Nueva Familia busca ayuda!";
          const body = `${newUser.nombre || "Un usuario"} se ha unido cerca de ti. Toca para ver su perfil.`;

          const message = {
            token: userData.fcmToken,
            notification: { title, body },
            data: {
              tipo: "nuevo_usuario_zona",
              uid: String(newUser.uid || newUserId),
              emisor: String(newUser.uid || newUserId),
              TARGET_SCREEN: "OTHER_FICHA"
            },
            android: {
              priority: "high",
              notification: { channelId: "cuidale_alerts", sound: "default" }
            }
          };

          if (userData.uid) {
            await db.collection("usuarios").where("uid", "==", userData.uid).limit(1).get()
              .then((q) => {
                if (!q.empty) {
                  const notifRef = q.docs[0].ref.collection("notificaciones").doc();
                  return notifRef.set({
                    id: notifRef.id,
                    tipo: "nuevo_usuario_zona",
                    emisor: String(newUser.uid || newUserId),
                    receptor: String(userData.uid),
                    timestamp: Date.now(),
                    leida: false,
                    data: { title, text: body, uid: String(newUser.uid || newUserId) }
                  });
                }
                return null;
              })
              .catch((e) => console.error("Error guardando notificación in-app:", e));
          }

          return admin.messaging().send(message);
        }
        return null;
      });

      await Promise.all(notificationPromises);
    } catch (error) {
      console.error("Error notifyMatchingUsers:", error);
    }
  }
);

exports.cleanupTrashScheduler = onSchedule(
  {
    schedule: "every 12 hours",
    timeZone: "America/Santiago",
    // Cloud Scheduler no está disponible en southamerica-west1; se usa east1.
    region: "southamerica-east1"
  },
  async () => {
    const now = Date.now();
    const snap = await db.collectionGroup("basura").get();
    const batch = admin.firestore().batch();
    let changes = 0;
    snap.docs.forEach((doc) => {
      const data = doc.data() || {};
      const moved = data.movidaA;
      const ts = moved && moved.toMillis ? moved.toMillis() : 0;
      if (!ts || now - ts > TRASH_TTL_MS) {
        batch.delete(doc.ref);
        changes++;
      }
    });
    if (changes > 0) await batch.commit();
  }
);

// Módulo de billetera/pagos. Se reexportan TODAS las funciones para que un
// despliegue global (firebase deploy --only functions) no elimine las que ya
// están activas en la nube (webhook, retorno, recargas y cobros).
const {
  iniciarCobroTurno,
  webhookMercadoPago,
  retornoPago,
  crearRecarga,
  iniciarCobro,
  responderCambiosCobro,
  pagarCobro,
  registrarCuentaDestino,
  webhookMercadoPagoPayout,
  iniciarOauthMp,
  canjearOauthMp,
  desvincularMp,
  cancelarRecarga,
  sincronizarRecargasPendientes
} = require("./pagos");

exports.iniciarCobroTurno = iniciarCobroTurno;
exports.webhookMercadoPago = webhookMercadoPago;
exports.retornoPago = retornoPago;
exports.crearRecarga = crearRecarga;
exports.iniciarCobro = iniciarCobro;
exports.responderCambiosCobro = responderCambiosCobro;
exports.pagarCobro = pagarCobro;
exports.registrarCuentaDestino = registrarCuentaDestino;
exports.webhookMercadoPagoPayout = webhookMercadoPagoPayout;
exports.iniciarOauthMp = iniciarOauthMp;
exports.canjearOauthMp = canjearOauthMp;
exports.desvincularMp = desvincularMp;
exports.cancelarRecarga = cancelarRecarga;
exports.sincronizarRecargasPendientes = sincronizarRecargasPendientes;
