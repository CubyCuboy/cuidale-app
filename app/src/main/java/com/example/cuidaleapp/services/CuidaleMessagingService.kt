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
import com.example.cuidaleapp.utils.InAppNotification
import com.example.cuidaleapp.utils.InAppNotificationCenter
import com.example.cuidaleapp.utils.NotificationTemplates
import com.example.cuidaleapp.activities.MenuActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.math.abs

class CuidaleMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            saveTokenToFirestore(token)
        } catch (e: Exception) {
            android.util.Log.e("CuidaleMessagingService", "Error guardando token: ${e.message}")
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userSession = com.example.cuidaleapp.data.UserSession(applicationContext)
        val rut = userSession.getUser().rut
        if (rut.isNotBlank()) {
            val hashedRut = com.example.cuidaleapp.utils.HashUtils.hashRut(rut)
            db.collection("usuarios").document(hashedRut).update("fcmToken", token)
        } else {
            db.collection("usuarios").whereEqualTo("uid", uid).get().addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    query.documents[0].reference.update("fcmToken", token)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        try {
            val data = remoteMessage.data
            val type = data["tipo"] ?: "generica"
            val chatId = data["chatId"]

            val currentScreen = AppNavigationState.currentScreen.value
            val activeChat = AppNavigationState.activeChatId.value

            if (currentScreen == "NOTIFICATIONS" && (type == "solicitud_vinculo" || type == "vinculo_aceptado" || type == "postulacion_recibida" || type == "configuracion_cuenta")) {
                return
            }

            if (currentScreen == "CHAT" && activeChat == chatId && type == "nuevo_mensaje") {
                return
            }

            val content = NotificationTemplates.resolve(
                context = this,
                tipo = type,
                data = data,
                notificationTitle = remoteMessage.notification?.title,
                notificationBody = remoteMessage.notification?.body
            )

            if (AppNavigationState.isAppVisible.value) {
                InAppNotificationCenter.show(
                    InAppNotification(
                        tipo = type,
                        title = content.title,
                        body = content.body,
                        targetScreen = content.targetScreen,
                        payload = data
                    )
                )
            } else {
                showNotification(content.title, content.body, data)
            }
        } catch (e: Exception) {
            android.util.Log.e("CuidaleMessagingService", "Error procesando notificación: ${e.message}")
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chatChannelId = "cuidale_chats"
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
                    description = "Alertas de solicitudes, tareas, cuenta y actualizaciones importantes"
                    enableLights(true)
                    lightColor = android.graphics.Color.GREEN
                    enableVibration(true)
                }

                notificationManager.createNotificationChannel(chatChannel)
                notificationManager.createNotificationChannel(alertChannel)
            }

            val type = data["tipo"] ?: "generica"
            val selectedChannel = if (type == "nuevo_mensaje") chatChannelId else alertChannelId
            val resolved = NotificationTemplates.resolve(this, type, data)

            val intent = Intent(this, MenuActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                data.forEach { (key, value) -> putExtra(key, value) }
                putExtra("TARGET_SCREEN", resolved.targetScreen)
                putExtra("FROM_PUSH", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stableId = abs((type.hashCode() * 31 + title.hashCode()) * 31 + body.hashCode()) % Int.MAX_VALUE

            val notificationBuilder = NotificationCompat.Builder(this, selectedChannel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(if (type == "nuevo_mensaje") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            notificationManager.notify(stableId, notificationBuilder.build())
        } catch (e: Exception) {
            android.util.Log.e("CuidaleMessagingService", "Error mostrando notificación: ${e.message}")
        }
    }
}
