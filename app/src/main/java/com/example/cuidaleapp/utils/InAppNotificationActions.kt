package com.example.cuidaleapp.utils

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object InAppNotificationActions {

    /**
     * Marca la notificación como leída en Firestore (subcolección notificaciones)
     * y además marca como leídos los mensajes del chat en Realtime Database,
     * para que el contador de no-leídos desaparezca también de la lista de chats.
     */
    fun markAsRead(context: Context, notification: InAppNotification) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val chatId = notification.payload["chatId"].orEmpty()
        val notifId = notification.payload["notifId"].orEmpty()

        db.collection("usuarios").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) return@addOnSuccessListener
                val docs = query.documents
                val canonical = docs.firstOrNull { it.getBoolean("completado") == true || !it.getString("rut").isNullOrBlank() }
                    ?: docs.first()
                val notifRef = canonical.reference.collection("notificaciones")

                // Marcar la notificación específica si tenemos su id.
                if (notifId.isNotBlank()) {
                    notifRef.document(notifId).update("leida", true)
                }

                // Si hay chat, marcar todas las notificaciones pendientes de ese chat.
                if (chatId.isNotBlank()) {
                    notifRef
                        .whereEqualTo("tipo", "nuevo_mensaje")
                        .whereEqualTo("data.chatId", chatId)
                        .get()
                        .addOnSuccessListener { snapshot ->
                            val batch = db.batch()
                            snapshot.documents.forEach { batch.update(it.reference, "leida", true) }
                            batch.commit()
                        }
                }
            }

        // Marcar mensajes como leídos en Realtime Database.
        if (chatId.isNotBlank()) {
            com.google.firebase.database.FirebaseDatabase.getInstance().reference
                .child("messages").child(chatId)
                .get()
                .addOnSuccessListener { snapshot ->
                    snapshot.children.forEach { child ->
                        val msg = child.getValue(com.example.cuidaleapp.models.ChatMessage::class.java)
                        if (msg != null && msg.senderId != uid && !msg.readBy.containsKey(uid)) {
                            child.ref.child("readBy").child(uid).setValue(true)
                        }
                    }
                }
        }
    }

    /**
     * Abre el chat correspondiente (navega a MenuActivity con TARGET_SCREEN=CHAT_DETAIL).
     */
    fun openChat(context: Context, notification: InAppNotification) {
        val chatId = notification.payload["chatId"].orEmpty()
        val otherUid = notification.payload["emisor"].orEmpty()
        val intent = Intent(context, com.example.cuidaleapp.activities.MenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_SCREEN", "CHAT_DETAIL")
            if (chatId.isNotBlank()) putExtra("chatId", chatId)
            if (otherUid.isNotBlank()) putExtra("USER_UID", otherUid)
            putExtra("FROM_LOCAL_PUSH", true)
        }
        context.startActivity(intent)
    }
}
