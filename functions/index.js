const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const crypto = require("crypto");

admin.initializeApp();

const GMAIL_EMAIL = defineSecret("GMAIL_EMAIL");
const GMAIL_PASS = defineSecret("GMAIL_APP_PASSWORD");
const db = admin.firestore();

const hash = (value) =>
    crypto.createHash("sha256").update(String(value)).digest("hex");

const verificationRef = (email) =>
    db.collection("email_verifications").doc(email);

exports.sendVerificationCode = onCall(
    {secrets: [GMAIL_EMAIL, GMAIL_PASS]},
    async (req) => {
        const email = req.data?.email;

        if (!email) {
            throw new HttpsError("invalid-argument", "Email requerido");
        }

        const code = Math.floor(100000 + Math.random() * 900000).toString();

        await verificationRef(email).set({
            hash: hash(code),
            expiresAt: Date.now() + 300000,
            attempts: 0,
            verified: false,
        });

        const transporter = nodemailer.createTransport({
            host: "smtp.gmail.com",
            port: 465,
            secure: true,
            auth: {
                user: GMAIL_EMAIL.value(),
                pass: GMAIL_PASS.value(),
            },
        });

        await transporter.sendMail({
            from: `"Cuidale" <${GMAIL_EMAIL.value()}>`,
            to: email,
            subject: "Código de recuperación",
            text: `Tu código es ${code}. Expira en 5 min.`,
        });

        return {ok: true};
    },
);

exports.verifyCode = onCall(async (req) => {
    const email = req.data?.email;
    const code = req.data?.code;

    if (!email || !code) {
        throw new HttpsError("invalid-argument", "Email y código requeridos");
    }

    const ref = verificationRef(email);
    const doc = await ref.get();

    if (!doc.exists) {
        throw new HttpsError("not-found", "No hay código");
    }

    const data = doc.data();

    if (Date.now() > data.expiresAt) {
        await ref.delete();
        throw new HttpsError("failed-precondition", "Código expirado");
    }

    if (data.attempts >= 3) {
        await ref.delete();
        throw new HttpsError("failed-precondition", "Demasiados intentos");
    }

    if (hash(code) !== data.hash) {
        await ref.update({
            attempts: admin.firestore.FieldValue.increment(1),
        });
        throw new HttpsError("permission-denied", "Código incorrecto");
    }

    await ref.update({verified: true});
    return {ok: true};
});

exports.updatePassword = onCall(async (req) => {
    const email = req.data?.email;
    const code = req.data?.code;
    const newPassword = req.data?.newPassword;

    if (!email || !code || !newPassword) {
        throw new HttpsError(
            "invalid-argument",
            "Email, código y nueva contraseña requeridos",
        );
    }

    const ref = verificationRef(email);
    const doc = await ref.get();

    if (!doc.exists) {
        throw new HttpsError("permission-denied", "No autorizado");
    }

    const data = doc.data();

    if (!data.verified || hash(code) !== data.hash) {
        throw new HttpsError("permission-denied", "No autorizado");
    }

    const user = await admin.auth().getUserByEmail(email);
    await admin.auth().updateUser(user.uid, {password: newPassword});

    await ref.delete();
    return {ok: true};
});
