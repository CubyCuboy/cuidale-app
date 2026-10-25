package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.Notification
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.InAppNotification
import com.example.cuidaleapp.utils.InAppNotificationCenter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val app = application

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _users = MutableStateFlow<Map<String, UserData>>(emptyMap())
    val users: StateFlow<Map<String, UserData>> = _users

    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var initialLoadDone = false

    init {
        listenNotifications()
    }

    private fun listenNotifications() {
        val uid = auth.currentUser?.uid ?: return
        listenerRegistration?.remove()
        initialLoadDone = false

        db.collection("usuarios").whereEqualTo("uid", uid).get().addOnSuccessListener { query ->
            if (query.isEmpty) return@addOnSuccessListener
            val userDocRef = query.documents[0].reference
            listenerRegistration = userDocRef.collection("notificaciones")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    val list = snapshot?.toObjects(Notification::class.java) ?: emptyList()
                    val previous = _notifications.value
                    _notifications.value = list
                    _unreadCount.value = list.count { !it.leida }
                    fetchUsers(list)
                    // La primera carga no debe emitir in-app (son notificaciones previas),
                    // solo las que llegan en vivo después.
                    if (initialLoadDone) {
                        emitNewAsInApp(previous, list)
                    } else {
                        initialLoadDone = true
                    }
                }
        }
    }

    private fun emitNewAsInApp(previous: List<Notification>, current: List<Notification>) {
        val previousIds = previous.asSequence().map { it.id }.toSet()
        val inChat = com.example.cuidaleapp.utils.AppNavigationState.currentScreen.value == "CHAT"
        val activeChatId = com.example.cuidaleapp.utils.AppNavigationState.activeChatId.value
        current
            .asSequence()
            .filter { (it.tipo != "generica") && !it.leida && (it.id !in previousIds) }
            .filter { notification ->
                if (notification.tipo == "nuevo_mensaje" && inChat) {
                    val chatId = notification.data["chatId"]
                    chatId != activeChatId
                } else {
                    true
                }
            }
            .forEach { notification ->
                val content = contentFor(notification)
                val senderUser = _users.value[notification.emisor]
                val enrichedPayload = notification.data + mapOf(
                    "notifId" to notification.id,
                    "emisorFoto" to (notification.data["emisorFoto"] ?: (senderUser?.fotoUrl ?: ""))
                )
                InAppNotificationCenter.show(
                    InAppNotification(
                        tipo = notification.tipo,
                        title = content.first,
                        body = content.second,
                        targetScreen = targetFor(notification.tipo),
                        payload = enrichedPayload
                    )
                )
            }
    }

    private fun contentFor(notification: Notification): Pair<String, String> {
        // Para notificaciones de chat, enriquecer con el nombre del remitente,
        // el nombre del chat (contacto) y la hora.
        if (notification.tipo == "nuevo_mensaje") {
            val senderName = notification.data["emisorNombre"]
                ?: _users.value[notification.emisor]?.nombre
                ?: app.getString(R.string.notification_chat_default_sender)
            val chatName = notification.data["chatNombre"]
                ?: _users.value[notification.emisor]?.nombre
                ?: app.getString(R.string.notification_chat_room_fallback)
            val text = notification.data["text"]
                ?: notification.data["body"]
                ?: app.getString(R.string.notification_chat_new_message)
            val time = formatNotifHour(notification.timestamp)
            val title = app.getString(R.string.notif_chat_title, senderName)
            val body = app.getString(R.string.notif_chat_detail, chatName, text, time)
            return title to body
        }
        val resolved = com.example.cuidaleapp.utils.NotificationTemplates.resolve(app, notification.tipo, notification.data)
        return resolved.title to resolved.body
    }

    private fun targetFor(tipo: String): String {
        return com.example.cuidaleapp.utils.NotificationTemplates.resolve(app, tipo).targetScreen
    }

    private fun formatNotifHour(timestamp: Long): String {
        return try {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        } catch (_: Exception) {
            ""
        }
    }

    private fun fetchUsers(notifications: List<Notification>) {
        val uids = notifications.asSequence().map { it.emisor }.distinct().filter { !_users.value.containsKey(it) }.toList()
        if (uids.isEmpty()) return

        uids.forEach { targetUid ->
            db.collection("usuarios").whereEqualTo("uid", targetUid).get().addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val user = query.documents[0].toObject(UserData::class.java) ?: return@addOnSuccessListener
                    _users.value += targetUid to user
                }
            }
        }
    }

    /**
     * Ejecuta una acción sobre el documento canónico del usuario (evita el documento
     * fantasma temporal por uid, que puede no tener notificaciones).
     */
    private fun runOnCanonicalUserDoc(uid: String, action: (com.google.firebase.firestore.DocumentReference) -> Unit) {
        db.collection("usuarios").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) return@addOnSuccessListener
                val docs = query.documents
                val canonical = docs.firstOrNull { it.getBoolean("completado") == true || !it.getString("rut").isNullOrBlank() }
                    ?: docs.first()
                action(canonical.reference)
            }
    }

    fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        runOnCanonicalUserDoc(uid) { ref ->
            ref.collection("notificaciones").document(notificationId).update("leida", true)
        }
    }

    fun deleteNotification(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        runOnCanonicalUserDoc(uid) { ref ->
            ref.collection("notificaciones").document(notificationId).delete()
        }
    }

    fun markChatGroupAsRead(chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        runOnCanonicalUserDoc(uid) { ref ->
            ref.collection("notificaciones")
                .whereEqualTo("tipo", "nuevo_mensaje")
                .whereEqualTo("data.chatId", chatId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.isEmpty) return@addOnSuccessListener
                    val batch = db.batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.commit()
                }
        }
        // También marcar como leídos los mensajes en Realtime Database para que
        // el contador de no-leídos de la lista de chats desaparezca.
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

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
