package com.example.cuidaleapp.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import com.example.cuidaleapp.R
import com.example.cuidaleapp.activities.MenuActivity
import com.example.cuidaleapp.models.Notification
import com.example.cuidaleapp.models.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import com.google.firebase.database.FirebaseDatabase
import com.example.cuidaleapp.models.ChatMessage

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val context = application.applicationContext

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
                
                snapshot?.documentChanges?.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        // Eliminamos showLocalNotification(notif) para evitar duplicados con FCM
                    }
                }

                val list = snapshot?.toObjects(Notification::class.java) ?: emptyList()
                _notifications.value = list
                _unreadCount.value = list.count { !it.leida }
                fetchUsers(list)
            }
    }

    private fun showLocalNotification(notification: Notification) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "cuidale_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Solicitudes y Alertas", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val title = when (notification.tipo) {
            "solicitud_vinculo" -> "Nueva Solicitud"
            "vinculo_aceptado" -> "¡Solicitud Aceptada!"
            else -> "CuidaleApp"
        }

        val body = when (notification.tipo) {
            "solicitud_vinculo" -> "Alguien quiere contactar contigo para cuidar o ser cuidado."
            "vinculo_aceptado" -> "Ahora puedes chatear con este usuario."
            else -> "Tienes una nueva notificación"
        }

        val intent = Intent(context, MenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_SCREEN", if (notification.tipo == "solicitud_vinculo") "NOTIFICATIONS" else "CHATS")
            putExtra("FROM_LOCAL_PUSH", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 
            notification.id.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notification.id.hashCode(), builder.build())
    }

    private fun fetchUsers(notifications: List<Notification>) {
        val uids = notifications.map { it.emisor }.distinct().filter { !_users.value.containsKey(it) }
        if (uids.isEmpty()) return

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

    fun markChatGroupAsRead(chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        
        // 1. Borrar documentos de notificación en Firestore
        db.collection("usuarios").document(uid).collection("notificaciones")
            .whereEqualTo("tipo", "nuevo_mensaje")
            .whereEqualTo("data.chatId", chatId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
            }
            
        // 2. Marcar mensajes como leídos en Realtime Database
        rtdb.child("messages").child(chatId).get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { child ->
                val msg = child.getValue(ChatMessage::class.java)
                if (msg != null && msg.senderId != uid && !msg.readBy.containsKey(uid)) {
                    child.ref.child("readBy").child(uid).setValue(true)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
