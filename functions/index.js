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

const VERIFICATION_TTL_MS = 30 * 60 * 1000;
const RECOVERY_TTL_MS = 5 * 60 * 1000;
const MAX_ATTEMPTS = 5;
const MIN_PASSWORD_LENGTH = 8;

const CODE_TYPES = {
  VERIFICATION: "verification",
  RECOVERY: "recovery",
};

const hash = (value) =>
  crypto.createHash("sha256").update(String(value)).digest("hex");

const normalizeEmail = (email) => String(email || "").trim().toLowerCase();

const getRef = (email) => db.collection("email_verifications").doc(email);

const generateCode = () =>
  Math.floor(100000 + Math.random() * 900000).toString();

const validatePassword = (password) => {
  const value = String(password || "");
  if (value.length < MIN_PASSWORD_LENGTH) return false;
  if (!/[A-Z]/.test(value)) return false;
  if (!/[^A-Za-z0-9]/.test(value)) return false;
  return true;
};

const getCodeConfig = (type) => {
  if (type === CODE_TYPES.VERIFICATION) {
    return {
      ttlMs: VERIFICATION_TTL_MS,
      expiresInSeconds: 1800,
      subject: "Código de verificación de cuenta",
      textBuilder: (code) => `Tu código de verificación es ${code}. Expira en 30 minutos.`,
    };
  }

  if (type === CODE_TYPES.RECOVERY) {
    return {
      ttlMs: RECOVERY_TTL_MS,
      expiresInSeconds: 300,
      subject: "Código de recuperación",
      textBuilder: (code) => `Tu código de recuperación es ${code}. Expira en 5 minutos.`,
    };
  }

  throw new HttpsError("invalid-argument", "Tipo de código inválido");
};

const transporter = () =>
  nodemailer.createTransport({
    host: "smtp.gmail.com",
    port: 465,
    secure: true,
    auth: {
      user: GMAIL_EMAIL.value(),
      pass: GMAIL_PASS.value(),
    },
  });

exports.sendVerificationCode = onCall(
  {
    secrets: [GMAIL_EMAIL, GMAIL_PASS],
    enforceAppCheck: false,
  },
  async (req) => {
    const email = normalizeEmail(req.data?.email);
    const type = String(req.data?.type || CODE_TYPES.RECOVERY).trim().toLowerCase();

    if (!email) {
      throw new HttpsError("invalid-argument", "Email requerido");
    }

    const config = getCodeConfig(type);
    const code = generateCode();
    const ref = getRef(email);

    await ref.set({
      email,
      type,
      hash: hash(code),
      expiresAt: Date.now() + config.ttlMs,
      attempts: 0,
      verified: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    try {
      await transporter().sendMail({
        from: `"Cuidale" <${GMAIL_EMAIL.value()}>`,
        to: email,
        subject: config.subject,
        text: config.textBuilder(code),
      });
    } catch (error) {
      await ref.delete().catch(() => null);
      console.error("Error sending email:", error);
      throw new HttpsError("internal", "No se pudo enviar el correo");
    }

    return {
      ok: true,
      type,
      expiresInSeconds: config.expiresInSeconds,
      maxAttempts: MAX_ATTEMPTS,
    };
  }
);

exports.verifyCode = onCall(
  {
    enforceAppCheck: false,
  },
  async (req) => {
    const email = normalizeEmail(req.data?.email);
    const code = String(req.data?.code || "").trim();

    if (!email || !code) {
      throw new HttpsError("invalid-argument", "Email y código requeridos");
    }

    if (!/^\d{6}$/.test(code)) {
      throw new HttpsError("invalid-argument", "El código debe tener 6 dígitos");
    }

    const ref = getRef(email);

    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);

      if (!snap.exists) {
        throw new HttpsError("not-found", "No hay código activo");
      }

      const data = snap.data();
      const now = Date.now();

      if (!data || typeof data.expiresAt !== "number") {
        tx.delete(ref);
        throw new HttpsError("failed-precondition", "Código inválido");
      }

      if (now > data.expiresAt) {
        tx.delete(ref);
        throw new HttpsError("failed-precondition", "Código expirado");
      }

      const attempts = Number(data.attempts || 0);

      if (attempts >= MAX_ATTEMPTS) {
        tx.delete(ref);
        throw new HttpsError("failed-precondition", "Máximos intentos alcanzados");
      }

      if (hash(code) !== data.hash) {
        const newAttempts = attempts + 1;

        if (newAttempts >= MAX_ATTEMPTS) {
          tx.delete(ref);
          throw new HttpsError(
            "failed-precondition",
            "Máximos intentos alcanzados",
            { attempts: newAttempts, remaining: 0 }
          );
        }

        tx.update(ref, {
          attempts: newAttempts,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        throw new HttpsError(
          "permission-denied",
          "Código incorrecto",
          { attempts: newAttempts, remaining: MAX_ATTEMPTS - newAttempts }
        );
      }

      tx.update(ref, {
        verified: true,
        verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return {
        ok: true,
        type: data.type || CODE_TYPES.RECOVERY,
      };
    });

    return result;
  }
);

exports.cancelVerification = onCall(
  {
    enforceAppCheck: false,
  },
  async (req) => {
    const email = normalizeEmail(req.data?.email);

    if (!email) {
      return { ok: true };
    }

    await getRef(email).delete().catch(() => null);

    return { ok: true };
  }
);

exports.updatePassword = onCall(
  {
    enforceAppCheck: false,
  },
  async (req) => {
    const email = normalizeEmail(req.data?.email);
    const newPassword = String(req.data?.newPassword || "");

    if (!email || !newPassword) {
      throw new HttpsError("invalid-argument", "Datos incompletos");
    }

    if (!validatePassword(newPassword)) {
      throw new HttpsError(
        "invalid-argument",
        "La contraseña no cumple los requisitos mínimos"
      );
    }

    const ref = getRef(email);
    const snap = await ref.get();

    if (!snap.exists) {
      throw new HttpsError("permission-denied", "No autorizado");
    }

    const data = snap.data();
    const now = Date.now();

    if (!data || !data.verified) {
      throw new HttpsError("permission-denied", "No autorizado");
    }

    if (data.type !== CODE_TYPES.RECOVERY) {
      throw new HttpsError(
        "permission-denied",
        "Este código no autoriza cambio de contraseña"
      );
    }

    if (typeof data.expiresAt !== "number" || now > data.expiresAt) {
      await ref.delete().catch(() => null);
      throw new HttpsError("failed-precondition", "Código expirado");
    }

    try {
      const user = await auth.getUserByEmail(email);
      await auth.updateUser(user.uid, { password: newPassword });

      const userQuery = await db.collection("usuarios").where("correo", "==", email).get();
      if (!userQuery.empty) {
        const batch = db.batch();
        userQuery.forEach((doc) => {
          batch.update(doc.ref, {
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        });
        await batch.commit();
      }

      await ref.delete().catch(() => null);
      return { ok: true };
    } catch (error) {
      console.error("Error updating password:", error);
      throw new HttpsError("internal", "No se pudo actualizar la contraseña");
    }
  }
);