const { setGlobalOptions } = require("firebase-functions/v2");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
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

const RECOVERY_TTL_MS = 5 * 60 * 1000;
const VERIFICATION_TTL_MS = 30 * 60 * 1000;
const MAX_ATTEMPTS = 5;

const hash = (value) =>
  crypto.createHash("sha256").update(String(value)).digest("hex");

const transporter = (email, pass) =>
  nodemailer.createTransport({
    host: "smtp.gmail.com",
    port: 465,
    secure: true,
    auth: {
      user: email,
      pass: pass,
    },
  });

async function sendEmailCode(email, type) {
  const isRecovery = type === "recovery";
  const ttl = isRecovery ? RECOVERY_TTL_MS : VERIFICATION_TTL_MS;
  const prefix = isRecovery ? "recovery" : "verification";
  const subject = isRecovery ? "Código de recuperación de contraseña" : "Código de verificación de cuenta";
  const title = isRecovery ? "recuperación" : "verificación";

  const userQuery = await db.collection("usuarios").where("correo", "==", email).limit(1).get();
  if (userQuery.empty) {
    throw new HttpsError("not-found", "El usuario no está registrado en la base de datos");
  }
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
    text: `Tu código de ${title} es: ${code}. Expira en ${ttl / 60000} minutos.`,
    html: `<p>Tu código de ${title} es: <b>${code}</b></p><p>Este código expira en ${ttl / 60000} minutos.</p>`
  });

  return {
    success: true,
    expiresAt,
    maxAttempts: MAX_ATTEMPTS
  };
}

async function verifyEmailCode(email, code, type) {
  const prefix = type === "recovery" ? "recovery" : "verification";

  const userQuery = await db.collection("usuarios").where("correo", "==", email).limit(1).get();
  if (userQuery.empty) throw new HttpsError("not-found", "Usuario no encontrado");

  const userDoc = userQuery.docs[0];
  const data = userDoc.data();

  if (!data[`${prefix}_code_hash`]) {
    throw new HttpsError("failed-precondition", `No se ha solicitado ${type === "recovery" ? "recuperación" : "verificación"}`);
  }

  const now = Date.now();
  if (now > data[`${prefix}_expires_at`]) {
    throw new HttpsError("failed-precondition", "El código ha expirado");
  }

  const attempts = (data[`${prefix}_attempts`] || 0) + 1;

  if (hash(code) !== data[`${prefix}_code_hash`]) {
    const updateError = {};
    updateError[`${prefix}_attempts`] = attempts;
    await userDoc.ref.update(updateError);

    if (attempts >= MAX_ATTEMPTS) {
      const resetData = {};
      resetData[`${prefix}_code_hash`] = admin.firestore.FieldValue.delete();
      resetData[`${prefix}_emission_time`] = admin.firestore.FieldValue.delete();
      resetData[`${prefix}_expires_at`] = admin.firestore.FieldValue.delete();
      resetData[`${prefix}_attempts`] = admin.firestore.FieldValue.delete();
      resetData[`${prefix}_verified`] = admin.firestore.FieldValue.delete();
      await userDoc.ref.update(resetData);
      throw new HttpsError("resource-exhausted", "Máximo de intentos alcanzado. Solicita un nuevo código.");
    }

    throw new HttpsError("permission-denied", "Código incorrecto", {
      remaining: MAX_ATTEMPTS - attempts
    });
  }

  const updateSuccess = {};
  if (type === "verification") {
    updateSuccess["verificado"] = true;
    // Limpieza total para registro exitoso
    updateSuccess[`${prefix}_code_hash`] = admin.firestore.FieldValue.delete();
    updateSuccess[`${prefix}_emission_time`] = admin.firestore.FieldValue.delete();
    updateSuccess[`${prefix}_expires_at`] = admin.firestore.FieldValue.delete();
    updateSuccess[`${prefix}_attempts`] = admin.firestore.FieldValue.delete();
    updateSuccess[`${prefix}_verified`] = admin.firestore.FieldValue.delete();
  } else {
    // Para recuperación, marcamos como verificado pero limpiamos los datos sensibles del código
    updateSuccess[`${prefix}_verified`] = true;
    updateSuccess[`${prefix}_code_hash`] = admin.firestore.FieldValue.delete();
    updateSuccess[`${prefix}_emission_time`] = admin.firestore.FieldValue.delete();
    updateSuccess[`${prefix}_expires_at`] = admin.firestore.FieldValue.delete();
    updateSuccess[`${prefix}_attempts`] = admin.firestore.FieldValue.delete();
  }

  await userDoc.ref.update(updateSuccess);
  return { success: true };
}

exports.sendRecoveryCode = onCall(
  { secrets: [GMAIL_EMAIL, GMAIL_PASS], enforceAppCheck: false },
  async (req) => {
    const email = String(req.data?.email || "").trim().toLowerCase();
    if (!email) throw new HttpsError("invalid-argument", "El correo es obligatorio");
    try {
      await auth.getUserByEmail(email);
      return await sendEmailCode(email, "recovery");
    } catch (error) {
      console.error("Error en sendRecoveryCode:", error);
      if (error.code === "auth/user-not-found") throw new HttpsError("not-found", "No existe cuenta asociada");
      throw error;
    }
  }
);

exports.verifyRecoveryCode = onCall(
  { enforceAppCheck: false },
  async (req) => {
    const email = String(req.data?.email || "").trim().toLowerCase();
    const code = String(req.data?.code || "").trim();
    if (!email || !code) throw new HttpsError("invalid-argument", "Datos incompletos");
    return await verifyEmailCode(email, code, "recovery");
  }
);

exports.sendVerificationCode = onCall(
  { secrets: [GMAIL_EMAIL, GMAIL_PASS], enforceAppCheck: false },
  async (req) => {
    const email = String(req.data?.email || "").trim().toLowerCase();
    if (!email) throw new HttpsError("invalid-argument", "El correo es obligatorio");
    return await sendEmailCode(email, "verification");
  }
);

exports.verifyVerificationCode = onCall(
  { enforceAppCheck: false },
  async (req) => {
    const email = String(req.data?.email || "").trim().toLowerCase();
    const code = String(req.data?.code || "").trim();
    if (!email || !code) throw new HttpsError("invalid-argument", "Datos incompletos");
    return await verifyEmailCode(email, code, "verification");
  }
);


exports.confirmPasswordReset = onCall(
  { enforceAppCheck: false },
  async (req) => {
    const email = String(req.data?.email || "").trim().toLowerCase();
    const newPassword = String(req.data?.newPassword || "");

    if (!email || !newPassword) throw new HttpsError("invalid-argument", "Datos incompletos");

    const userQuery = await db.collection("usuarios").where("correo", "==", email).limit(1).get();
    if (userQuery.empty) throw new HttpsError("not-found", "Usuario no encontrado");

    const userDoc = userQuery.docs[0];
    const data = userDoc.data();

    if (!data.recovery_verified) {
      throw new HttpsError("permission-denied", "El código no ha sido verificado");
    }

    try {
      const userAuth = await auth.getUserByEmail(email);
      await auth.updateUser(userAuth.uid, { password: newPassword });

      await userDoc.ref.update({
        recovery_code_hash: admin.firestore.FieldValue.delete(),
        recovery_emission_time: admin.firestore.FieldValue.delete(),
        recovery_expires_at: admin.firestore.FieldValue.delete(),
        recovery_attempts: admin.firestore.FieldValue.delete(),
        recovery_verified: admin.firestore.FieldValue.delete(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      return { success: true };
    } catch (error) {
      console.error("Error al actualizar contraseña:", error);
      throw new HttpsError("internal", "No se pudo actualizar la contraseña");
    }
  }
);
