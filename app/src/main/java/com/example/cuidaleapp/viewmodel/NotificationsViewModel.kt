package com.example.cuidaleapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.cuidaleapp.models.Notification
import com.example.cuidaleapp.models.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

class NotificationsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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

        listenerRegistration = db.collection("usuarios").document(uid).collection("notificaciones")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val list = snapshot?.toObjects(Notification::class.java) ?: emptyList()
                _notifications.value = list
                _unreadCount.value = list.count { !it.leida }
                fetchUsers(list)
            }
    }

    private fun fetchUsers(notifications: List<Notification>) {
        val uids = notifications.map { it.emisor }.distinct().filter { !_users.value.containsKey(it) }
        if (uids.isEmpty()) return

        // Simple fetch for now
        uids.forEach { targetUid ->
            db.collection("usuarios").document(targetUid).get().addOnSuccessListener { doc ->
                val user = doc.toObject(UserData::class.java)
                if (user != null) {
                    _users.value = _users.value + (targetUid to user)
                }
            }
        }
    }

    fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid).collection("notificaciones").document(notificationId)
            .update("leida", true)
    }

    fun deleteNotification(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid).collection("notificaciones").document(notificationId)
            .delete()
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
