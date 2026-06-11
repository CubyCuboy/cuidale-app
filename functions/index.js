const { setGlobalOptions } = require("firebase-functions/v2");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const crypto = require("crypto");

setGlobalOptions({ region: "southamerica-west1" });
admin.initializeApp();

const GMAIL_EMAIL = defineSecret("GMAIL_EMAIL");
const GMAIL_PASS = defineSecret("GMAIL_APP_PASSWORD");
const db = admin.firestore();
const auth = admin.auth();

// ... (Funciones de recuperación de contraseña se mantienen igual) ...
const hash = (value) => crypto.createHash("sha256").update(String(value)).digest("hex");
const transporter = (email, pass) => nodemailer.createTransport({ host: "smtp.gmail.com", port: 465, secure: true, auth: { user: email, pass: pass } });

async function sendEmailCode(email, type) {
  const isRecovery = type === "recovery";
  const ttl = isRecovery ? 5 * 60 * 1000 : 30 * 60 * 1000;
  const prefix = isRecovery ? "recovery" : "verification";
  const subject = isRecovery ? "Código de recuperación" : "Código de verificación";
  const userQuery = await db.collection("usuarios").where("correo", "==", email).limit(1).get();
  if (userQuery.empty) throw new HttpsError("not-found", "Usuario no registrado");
  const userDoc = userQuery.docs[0];
  const code = Math.floor(100000 + Math.random() * 900000).toString();
  const now = Date.now();
  const expiresAt = now + ttl;
  const updateData = {};
  updateData[`${prefix}_code_hash`] = hash(code);
  updateData[`${prefix}_emission_time`] = now;
  updateData[`${prefix}_expires_at`] = expiresAt;
  updateData[`${prefix}_attempts`] = 0;
  updateData[`${prefix}_verified`] = false;
  await userDoc.ref.update(updateData);
  await transporter(GMAIL_EMAIL.value(), GMAIL_PASS.value()).sendMail({
    from: `"Cuidale App" <${GMAIL_EMAIL.value()}>`,
    to: email,
    subject: subject,
    text: `Tu código es: ${code}`,
    html: `<p>Tu código es: <b>${code}</b></p>`
  });
  return { success: true, expiresAt };
}

async function verifyEmailCode(email, code, type) {
  const prefix = type === "recovery" ? "recovery" : "verification";
  const userQuery = await db.collection("usuarios").where("correo", "==", email).limit(1).get();
  if (userQuery.empty) throw new HttpsError("not-found", "Usuario no encontrado");
  const userDoc = userQuery.docs[0];
  const data = userDoc.data();
  if (!data[`${prefix}_code_hash`]) throw new HttpsError("failed-precondition", "No solicitado");
  if (Date.now() > data[`${prefix}_expires_at`]) throw new HttpsError("failed-precondition", "Expirado");
  const attempts = (data[`${prefix}_attempts`] || 0) + 1;
  if (hash(code) !== data[`${prefix}_code_hash`]) {
    await userDoc.ref.update({ [`${prefix}_attempts`]: attempts });
    if (attempts >= 5) throw new HttpsError("resource-exhausted", "Máximo de intentos");
    throw new HttpsError("permission-denied", "Código incorrecto");
  }
  const updateSuccess = {};
  if (type === "verification") {
    updateSuccess["verificado"] = true;
  } else {
    updateSuccess[`${prefix}_verified`] = true;
  }
  await userDoc.ref.update(updateSuccess);
  return { success: true };
}

exports.sendRecoveryCode = onCall({ secrets: [GMAIL_EMAIL, GMAIL_PASS] }, async (req) => { return await sendEmailCode(String(req.data?.email || ""), "recovery"); });
exports.verifyRecoveryCode = onCall(async (req) => { return await verifyEmailCode(req.data.email, req.data.code, "recovery"); });
exports.sendVerificationCode = onCall({ secrets: [GMAIL_EMAIL, GMAIL_PASS] }, async (req) => { return await sendEmailCode(String(req.data?.email || ""), "verification"); });
exports.verifyVerificationCode = onCall(async (req) => { return await verifyEmailCode(req.data.email, req.data.code, "verification"); });

exports.confirmPasswordReset = onCall(async (req) => {
    const { email, newPassword } = req.data;
    const userQuery = await db.collection("usuarios").where("correo", "==", email).limit(1).get();
    const userDoc = userQuery.docs[0];
    if (!userDoc.data().recovery_verified) throw new HttpsError("permission-denied", "No verificado");
    const userAuth = await auth.getUserByEmail(email);
    await auth.updateUser(userAuth.uid, { password: newPassword });
    await userDoc.ref.update({ recovery_verified: admin.firestore.FieldValue.delete(), updatedAt: admin.firestore.FieldValue.serverTimestamp() });
    return { success: true };
});

// --- NOTIFICACIONES PUSH MEJORADAS ---

exports.sendPushNotification = onDocumentCreated(
  "usuarios/{userId}/notificaciones/{notifId}",
  async (event) => {
    const notification = event.data.data();
    const userId = event.params.userId;
    if (!notification) return;

    try {
      const userDoc = await db.collection("usuarios").doc(userId).get();
      const fcmToken = userDoc.data()?.fcmToken;
      if (!fcmToken) return;

      const type = notification.tipo || "mensaje";
      let title = "CuidaleApp";
      let body = "Tienes una nueva notificación";

      if (type === "nuevo_mensaje") {
        title = "Nuevo Mensaje";
        body = notification.data?.text || "Has recibido un mensaje";
      } else if (type === "solicitud_vinculo") {
        title = "Nueva Solicitud";
        body = "Alguien quiere contactar contigo.";
      } else if (type === "vinculo_aceptado") {
        title = "¡Solicitud Aceptada!";
        body = "Ahora puedes chatear con el usuario.";
      }

      const message = {
        token: fcmToken,
        notification: { title, body },
        data: {
          tipo: type,
          emisor: notification.emisor || "",
          chatId: notification.data?.chatId || "",
          click_action: "FLUTTER_NOTIFICATION_CLICK" // Compatibilidad
        },
        android: {
          priority: "high",
          notification: {
            channelId: type === "nuevo_mensaje" ? "cuidale_chats" : "cuidale_alerts",
            sound: "default"
          }
        }
      };

      await admin.messaging().send(message);
    } catch (error) {
      console.error("Error sending push notification:", error);
    }
  }
);

// --- NOTIFICAR NUEVOS USUARIOS POR ZONA ---

exports.notifyMatchingUsers = onDocumentCreated(
  "usuarios/{newUserId}",
  async (event) => {
    const newUser = event.data.data();
    const newUserId = event.params.newUserId;
    if (!newUser || !newUser.rol || !newUser.comuna) return;

    const targetRol = newUser.rol === "cuidador" ? "representante" : "cuidador";

    try {
      // Buscar usuarios del rol opuesto en la misma comuna
      const matches = await db.collection("usuarios")
        .where("rol", "==", targetRol)
        .where("comuna", "==", newUser.comuna)
        .limit(50)
        .get();

      const notificationPromises = matches.docs.map(async (doc) => {
        const userData = doc.data();
        if (userData.fcmToken && doc.id !== newUserId) {
          const title = targetRol === "cuidador" ? "¡Nuevo Cuidador en tu zona!" : "¡Nueva Familia busca ayuda!";
          const body = `${newUser.nombre} se ha unido cerca de ti. Toca para ver su perfil.`;

          return admin.messaging().send({
            token: userData.fcmToken,
            notification: { title, body },
            data: {
              tipo: "nuevo_usuario_zona",
              uid: newUserId,
              TARGET_SCREEN: "OTHER_FICHA"
            },
            android: {
              priority: "high",
              notification: { channelId: "cuidale_alerts" }
            }
          });
        }
      });

      await Promise.all(notificationPromises);
    } catch (error) {
      console.error("Error notifyMatchingUsers:", error);
    }
  }
);
