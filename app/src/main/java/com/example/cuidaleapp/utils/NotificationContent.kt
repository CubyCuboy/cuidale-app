package com.example.cuidaleapp.utils

import android.content.Context
import com.example.cuidaleapp.R

data class NotificationContent(
    val title: String,
    val body: String,
    val targetScreen: String
)

object NotificationTemplates {

    fun resolve(
        context: Context,
        tipo: String,
        data: Map<String, String> = emptyMap(),
        notificationTitle: String? = null,
        notificationBody: String? = null
    ): NotificationContent {
        val explicitTitle = notificationTitle ?: data["title"] ?: data["titulo"]
        val explicitBody = notificationBody ?: data["text"] ?: data["body"] ?: data["texto"]

        val fallback = when (tipo) {
            "nuevo_mensaje" -> Triple(
                context.getString(R.string.notification_chat),
                context.getString(R.string.notification_chat_body),
                "CHAT_DETAIL"
            )
            "solicitud_vinculo" -> Triple(
                context.getString(R.string.notification_request),
                context.getString(R.string.notification_request_body),
                "NOTIFICATIONS"
            )
            "vinculo_aceptado" -> Triple(
                context.getString(R.string.notification_accept),
                context.getString(R.string.notification_accept_body),
                "CHATS"
            )
            "postulacion_recibida" -> Triple(
                "Nueva Postulación Recibida",
                "Un cuidador se ha postulado a tu solicitud de servicio.",
                "NOTIFICATIONS"
            )
            "postulacion_aceptada" -> Triple(
                "¡Postulación Aceptada!",
                "Tu postulación de cuidado fue aceptada.",
                "SOLICITUDES"
            )
            "servicio_finalizado" -> Triple(
                "Servicio Finalizado",
                "El servicio de cuidado ha sido finalizado.",
                "SOLICITUDES"
            )
            "recordatorio_tarea" -> Triple(
                "Recordatorio de Tarea",
                "Tienes una tarea próxima a realizar.",
                "TAREAS"
            )
            "tarea_atrasada" -> Triple(
                "Tarea Atrasada",
                "Una tarea del servicio está atrasada.",
                "SOLICITUDES"
            )
            "tarea_finalizada" -> Triple(
                "Tarea Finalizada",
                "Se ha completado una tarea del servicio.",
                "SOLICITUDES"
            )
            "tarea_postergada" -> Triple(
                "Tarea Postergada",
                "Se ha postergado una tarea del servicio.",
                "SOLICITUDES"
            )
            "turno_finalizado" -> Triple(
                "Turno Finalizado",
                "El cuidador ha completado el turno de hoy.",
                "SOLICITUDES"
            )
            "configuracion_cuenta" -> Triple(
                "Gestión de Cuenta",
                "Se realizó una actualización en la configuración de tu cuenta.",
                "NOTIFICATIONS"
            )
            "nuevo_usuario_zona" -> Triple(
                "Nuevo usuario en tu zona",
                "Se ha registrado un nuevo usuario cerca de ti.",
                "OTHER_FICHA"
            )
            "nueva_calificacion" -> Triple(
                "Nueva calificación",
                "Te han dejado una nueva calificación.",
                "PROFILE_CALIFICACIONES"
            )
            else -> Triple(
                context.getString(R.string.notification_generic),
                context.getString(R.string.notification_generic_body),
                "MAIN"
            )
        }

        return NotificationContent(
            title = explicitTitle ?: fallback.first,
            body = explicitBody ?: fallback.second,
            targetScreen = fallback.third
        )
    }

    fun isGeneric(title: String, body: String): Boolean {
        return title.contains("CuidaleApp") &&
            (body.contains("nueva actualización") || body.isBlank())
    }
}
