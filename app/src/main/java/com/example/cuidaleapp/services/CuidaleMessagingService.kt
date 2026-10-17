package com.example.cuidaleapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.cuidaleapp.R
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.activities.MenuActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CuidaleMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("usuarios").whereEqualTo("uid", uid).get().addOnSuccessListener { query ->
            if (!query.isEmpty) {
                query.documents[0].reference.update("fcmToken", token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val type = remoteMessage.data["tipo"] ?: "mensaje"
        val chatId = remoteMessage.data["chatId"]
        
        // Supresión de notificaciones redundantes
        val currentScreen = AppNavigationState.currentScreen.value
        val activeChat = AppNavigationState.activeChatId.value

        if (currentScreen == "NOTIFICATIONS" && (type == "solicitud_vinculo" || type == "vinculo_aceptado")) {
            return // Estamos viendo las notificaciones, no hacer ruido
        }
        
        if (currentScreen == "CHAT" && activeChat == chatId && type == "nuevo_mensaje") {
            return // Estamos en el mismo chat, no hacer ruido
        }

        if (remoteMessage.notification != null) {
            val title = remoteMessage.notification?.title ?: "CuidaleApp"
            val body = remoteMessage.notification?.body ?: ""
            showNotification(title, body, remoteMessage.data)
        } else if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "CuidaleApp"
            val body = remoteMessage.data["body"] ?: "Tienes una nueva actualización"
            showNotification(title, body, remoteMessage.data)
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Canal para Mensajes de Chat (Importancia Alta + Sonido)
        val chatChannelId = "cuidale_chats"
        // Canal para Solicitudes y Alertas (Importancia Alta)
        val alertChannelId = "cuidale_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chatChannel = NotificationChannel(chatChannelId, "Mensajes de Chat", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificaciones de nuevos mensajes recibidos"
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
                setShowBadge(true)
            }
            
            val alertChannel = NotificationChannel(alertChannelId, "Solicitudes y Alertas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alertas de solicitudes de vínculo y actualizaciones importantes"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannel(chatChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }

        val type = data["tipo"] ?: "mensaje"
        val selectedChannel = if (type == "nuevo_mensaje") chatChannelId else alertChannelId

        val intent = Intent(this, MenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data.forEach { (key, value) -> putExtra(key, value) }
            
            // Lógica de redirección basada en el tipo de notificación
            when (type) {
                "nuevo_mensaje" -> {
                    putExtra("TARGET_SCREEN", "CHAT_DETAIL")
                    putExtra("USER_UID", data["emisor"])
                    putExtra("chatId", data["chatId"])
                }
                "solicitud_vinculo" -> {
                    putExtra("TARGET_SCREEN", "NOTIFICATIONS")
                }
                "vinculo_aceptado" -> {
                    putExtra("TARGET_SCREEN", "CHATS")
                }
                else -> {
                    putExtra("TARGET_SCREEN", "MAIN")
                }
            }
            putExtra("FROM_PUSH", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 
            System.currentTimeMillis().toInt(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, selectedChannel)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Asegúrate de tener un icono adecuado
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (type == "nuevo_mensaje") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setNumber(1)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
