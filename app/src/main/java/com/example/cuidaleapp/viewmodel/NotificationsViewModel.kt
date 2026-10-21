package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.cuidaleapp.models.Notification
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.utils.InAppNotification
import com.example.cuidaleapp.utils.InAppNotificationCenter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
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

    init {
        listenNotifications()
    }

    private fun listenNotifications() {
        val uid = auth.currentUser?.uid ?: return
        listenerRegistration?.remove()

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
                    emitNewAsInApp(previous, list)
                }
        }
    }

    private fun emitNewAsInApp(previous: List<Notification>, current: List<Notification>) {
        val previousIds = previous.map { it.id }.toSet()
        val inChat = com.example.cuidaleapp.utils.AppNavigationState.currentScreen.value == "CHAT"
        val activeChatId = com.example.cuidaleapp.utils.AppNavigationState.activeChatId.value
        current
            .filter { !it.leida && it.id !in previousIds }
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
                InAppNotificationCenter.show(
                    InAppNotification(
                        tipo = notification.tipo,
                        title = content.first,
                        body = content.second,
                        targetScreen = targetFor(notification.tipo),
                        payload = notification.data
                    )
                )
            }
    }

    private fun contentFor(notification: Notification): Pair<String, String> {
        val resolved = com.example.cuidaleapp.utils.NotificationTemplates.resolve(app, notification.tipo, notification.data)
        return resolved.title to resolved.body
    }

    private fun targetFor(tipo: String): String {
        return com.example.cuidaleapp.utils.NotificationTemplates.resolve(app, tipo).targetScreen
    }

    private fun fetchUsers(notifications: List<Notification>) {
        val uids = notifications.map { it.emisor }.distinct().filter { !_users.value.containsKey(it) }
        if (uids.isEmpty()) return

        uids.forEach { targetUid ->
            db.collection("usuarios").whereEqualTo("uid", targetUid).get().addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val user = query.documents[0].toObject(UserData::class.java)
                    if (user != null) {
                        _users.value = _users.value + (targetUid to user)
                    }
                }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").whereEqualTo("uid", uid).get().addOnSuccessListener { query ->
            if (!query.isEmpty) {
                query.documents[0].reference.collection("notificaciones").document(notificationId)
                    .update("leida", true)
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").whereEqualTo("uid", uid).get().addOnSuccessListener { query ->
            if (!query.isEmpty) {
                query.documents[0].reference.collection("notificaciones").document(notificationId)
                    .delete()
            }
        }
    }

    fun markChatGroupAsRead(chatId: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("usuarios").whereEqualTo("uid", uid).get().addOnSuccessListener { query ->
            if (query.isEmpty) return@addOnSuccessListener
            query.documents[0].reference.collection("notificaciones")
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
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
