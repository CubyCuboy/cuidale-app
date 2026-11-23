package com.example.cuidaleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.cuidaleapp.utils.NotificationHelper
import android.content.Context
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class TareasViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    private val _tareasPlantilla = MutableStateFlow<List<Tarea>>(emptyList())
    val tareasPlantilla: StateFlow<List<Tarea>> = _tareasPlantilla

    private val _tareasHoy = MutableStateFlow<List<RegistroTarea>>(emptyList())
    val tareasHoy: StateFlow<List<RegistroTarea>> = _tareasHoy

    private val _tareasAtrasadas = MutableStateFlow<List<RegistroTarea>>(emptyList())
    val tareasAtrasadas: StateFlow<List<RegistroTarea>> = _tareasAtrasadas

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var monitoringJob: kotlinx.coroutines.Job? = null

    fun iniciarMonitoreoTareas(context: Context) {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            while (true) {
                verificarRecordatorios(context)
                delay(60000)
            }
        }
    }

    private fun verificarRecordatorios(context: Context) {
        val ahora = Calendar.getInstance()
        val horaActual = ahora.get(Calendar.HOUR_OF_DAY)
        val minutoActual = ahora.get(Calendar.MINUTE)
        
        _tareasHoy.value.forEach { tarea ->
            if (tarea.completada) return@forEach
            
            val partes = tarea.horarioPrevisto.split(":")
            if (partes.size != 2) return@forEach
            
            val horaTarea = partes[0].toInt()
            val minutoTarea = partes[1].toInt()
            
            val calendarTarea = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, horaTarea)
                set(Calendar.MINUTE, minutoTarea)
                set(Calendar.SECOND, 0)
            }
            
            val diffMillis = calendarTarea.timeInMillis - ahora.timeInMillis
            val diffMinutes = diffMillis / 60000
            
            if (diffMinutes in 14..15) {
                val title = "Tarea Próxima: ${tarea.titulo}"
                val body = "Inicia en 15 minutos (${tarea.horarioPrevisto})"
                NotificationHelper.showNotification(context, title, body)
                
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                enviarNotificacionApp(uid, "recordatorio_tarea", title, body, mapOf("tareaId" to tarea.id, "solicitudId" to tarea.solicitudId))
            }
            
            if (diffMinutes < 0) {
                val delayMinutes = -diffMinutes
                if (delayMinutes > 0 && delayMinutes % 30 == 0L) {
                    val title = "Tarea Pendiente: ${tarea.titulo}"
                    val body = "Debía realizarse a las ${tarea.horarioPrevisto}. Sigue pendiente."
                    NotificationHelper.showNotification(context, title, body)
                    
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    enviarNotificacionApp(uid, "tarea_atrasada", title, body, mapOf("tareaId" to tarea.id, "solicitudId" to tarea.solicitudId))
                }
            }
        }
    }

    private fun enviarNotificacionApp(emisor: String, tipo: String, titulo: String, body: String, extraData: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            try {
                val data = extraData.toMutableMap()
                data["titulo"] = titulo
                data["body"] = body
                
                val notif = Notification(
                    id = UUID.randomUUID().toString(),
                    tipo = tipo,
                    emisor = emisor,
                    timestamp = System.currentTimeMillis(),
                    data = data
                )
                
                db.collection("usuarios").whereEqualTo("uid", emisor).get().addOnSuccessListener { query ->
                    if (!query.isEmpty) {
                        query.documents[0].reference.collection("notificaciones").document(notif.id).set(notif)
                    }
                }
            } catch (e: Exception) {}
        }
    }


    private var tareasListener: ListenerRegistration? = null
    private var registrosListener: ListenerRegistration? = null

    private var atrasadasListener: ListenerRegistration? = null

    fun cargarTareasAtrasadas(solicitudId: String) {
        atrasadasListener?.remove()
        val today = getTodayDate()
        atrasadasListener = db.collection("solicitudes").document(solicitudId)
            .collection("registros_tareas")
            .whereLessThan("fecha", today)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasAtrasadas.value = snapshot.toObjects(RegistroTarea::class.java)
                        .filter { !it.completada && !it.postergada }
                        .sortedByDescending { it.fecha }
                }
            }
    }

    fun cargarTareasFicha(pacienteFichaId: String) {
        tareasListener?.remove()
        tareasListener = db.collection("fichas").document(pacienteFichaId)
            .collection("tareas_plantilla")
            .whereEqualTo("activa", true)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasPlantilla.value = snapshot.toObjects(Tarea::class.java)
                }
            }
    }

    fun cargarTareasDelDia(solicitudId: String, fecha: String = getTodayDate()) {
        registrosListener?.remove()
        registrosListener = db.collection("solicitudes").document(solicitudId)
            .collection("registros_tareas")
            .whereEqualTo("fecha", fecha)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasHoy.value = snapshot.toObjects(RegistroTarea::class.java)
                        .sortedBy { it.horarioPrevisto }
                }
            }
    }

    fun agregarTareaAPlantilla(pacienteFichaId: String, tarea: Tarea) {
        if (pacienteFichaId.isEmpty()) {
            _error.value = "ID de ficha no válido"
            return
        }
        viewModelScope.launch {
            try {
                db.collection("fichas").document(pacienteFichaId)
                    .collection("tareas_plantilla").document(tarea.id)
                    .set(tarea).await()
            } catch (e: Exception) {
                _error.value = "Error al guardar en ficha: ${e.localizedMessage}"
            }
        }
    }

    fun agregarTareaARegistroHoy(solicitudId: String, tarea: Tarea) {
        viewModelScope.launch {
            try {
                val today = getTodayDate()
                
                val existing = db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas")
                    .whereEqualTo("tareaId", tarea.id)
                    .whereEqualTo("fecha", today)
                    .get().await()
                
                if (!existing.isEmpty) return@launch

                val regId = UUID.randomUUID().toString()
                val registro = RegistroTarea(
                    id = regId,
                    tareaId = tarea.id,
                    solicitudId = solicitudId,
                    fecha = today,
                    horarioPrevisto = tarea.horarioInicio,
                    titulo = tarea.titulo,
                    tipo = tarea.tipo,
                    descripcion = tarea.descripcion,
                    frecuencia = tarea.frecuencia,
                    dosis = tarea.dosis,
                    unidades = tarea.unidades,
                    viaAdministracion = tarea.viaAdministracion,
                    exigirFoto = tarea.exigirFoto
                )
                db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas").document(regId)
                    .set(registro).await()
            } catch (e: Exception) {
                _error.value = "Error al guardar tarea de hoy: ${e.localizedMessage}"
            }
        }
    }

    fun marcarTareaCompletada(
        solicitudId: String,
        registroId: String,
        completada: Boolean,
        userId: String,
        notas: String = "",
        titulo: String = "",
        otherUid: String = "",
        fotoUrl: String = "",
        fotoStoragePath: String = "",
        exigirFoto: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val ahora = Timestamp.now()
                val sinEvidencia = completada && exigirFoto && fotoUrl.isBlank()
                val updates = mutableMapOf<String, Any?>(
                    "completada" to completada,
                    "completadaPor" to if (completada) userId else "",
                    "fechaCompletado" to (if (completada) ahora else null),
                    "notas" to notas,
                    "sinEvidenciaFotografica" to sinEvidencia
                )
                if (fotoUrl.isNotEmpty()) updates["fotoUrl"] = fotoUrl
                if (fotoStoragePath.isNotEmpty()) updates["fotoStoragePath"] = fotoStoragePath

                db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas").document(registroId)
                    .update(updates).await()

                if (completada && otherUid.isNotEmpty()) {
                    val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora.toDate())
                    if (sinEvidencia) {
                        val texto = "⚠️ La tarea \"$titulo\" se completó sin evidencia fotográfica ($hora)."
                        enviarMensajeSistemaChat(userId, otherUid, texto)
                        enviarNotificacionAUsuario(otherUid, userId, "tarea_sin_evidencia", "Tarea sin evidencia", texto, mapOf("solicitudId" to solicitudId, "registroId" to registroId))
                    } else {
                        if (fotoUrl.isNotEmpty()) {
                            enviarMensajeTareaFinalizada(userId, otherUid, titulo, hora, notas, fotoUrl, solicitudId, registroId)
                        } else {
                            val texto = "✅ Tarea finalizada ($hora): $titulo"
                            enviarMensajeSistemaChat(userId, otherUid, texto)
                        }
                        enviarNotificacionAUsuario(otherUid, userId, "tarea_finalizada", "Tarea Finalizada", "✅ Tarea finalizada ($hora): $titulo", mapOf("solicitudId" to solicitudId, "registroId" to registroId))
                    }
                }
            } catch (e: Exception) {
                _error.value = "Error al marcar tarea: ${e.localizedMessage}"
            }
        }
    }

    suspend fun subirFotoEvidencia(context: Context, uri: android.net.Uri): Pair<String, String>? {
        return try {
            val bytes = com.example.cuidaleapp.utils.MediaCompressor.compressImage(context, uri) ?: return null
            val ext = if (com.example.cuidaleapp.utils.MediaCompressor.isImage(context, uri)) ".jpg" else ".dat"
            val path = "chat_media/tarea_evidencia/${System.currentTimeMillis()}-${(0 until 100000).random()}$ext"
            val ref = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child(path)
            ref.putBytes(bytes).await()
            val url = ref.downloadUrl.await().toString()
            url to path
        } catch (e: Exception) {
            null
        }
    }

    fun limpiarEvidenciasViejas(solicitudId: String) {
        viewModelScope.launch {
            try {
                val dayMs = 24L * 60 * 60 * 1000
                val ahora = System.currentTimeMillis()
                val snapshot = db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas")
                    .get().await()
                val viejos = snapshot.documents.mapNotNull { doc ->
                    val fecha = doc.getTimestamp("fechaCompletado")?.toDate()?.time
                    val path = doc.getString("fotoStoragePath")
                    if (path != null && fecha != null && (ahora - fecha) > 3 * dayMs) doc to path else null
                }
                for ((doc, path) in viejos) {
                    runCatching { com.google.firebase.storage.FirebaseStorage.getInstance().getReference(path).delete().await() }
                    doc.reference.update(mapOf("fotoUrl" to "", "fotoStoragePath" to "")).await()
                }
            } catch (e: Exception) { }
        }
    }

    private fun enviarMensajeTareaFinalizada(myUid: String, otherUid: String, titulo: String, hora: String, notas: String, fotoUrl: String, solicitudId: String, registroId: String) {
        val chatId = if (myUid < otherUid) "${myUid}_$otherUid" else "${otherUid}_$myUid"
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance().reference
            .child("messages").child(chatId).push()
        val msg = mapOf(
            "id" to (ref.key ?: UUID.randomUUID().toString()),
            "senderId" to myUid,
            "text" to "✅ Tarea finalizada ($hora): $titulo" +
                "\n\n📝 DESCRIPCIÓN DE LO REALIZADO:\n${notas.ifBlank { "Sin descripción adicional." }}",
            "type" to "text",
            "timestamp" to System.currentTimeMillis(),
            "imageUrl" to fotoUrl.ifBlank { null },
            "metadata" to mapOf(
                "solicitudId" to solicitudId,
                "registroId" to registroId,
                "titulo" to titulo,
                "hora" to hora
            ),
            "readBy" to mapOf(myUid to true)
        )
        ref.setValue(msg)
    }

    fun enviarConsultaTarea(myUid: String, otherUid: String, titulo: String, horario: String, descripcion: String, solicitudId: String, registroId: String) {
        val chatId = if (myUid < otherUid) "${myUid}_$otherUid" else "${otherUid}_$myUid"
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance().reference
            .child("messages").child(chatId).push()
        val msg = mapOf(
            "id" to (ref.key ?: UUID.randomUUID().toString()),
            "senderId" to myUid,
            "text" to "🔎 Pregunta sobre la tarea:\n\n" +
                "*$titulo* ($horario)\n\n" +
                "📝 Indicaciones: ${descripcion.ifBlank { "Sin indicaciones específicas" }}\n\n" +
                "¿Podrías confirmarme cómo proceder?",
            "type" to "text",
            "timestamp" to System.currentTimeMillis(),
            "metadata" to mapOf("solicitudId" to solicitudId, "registroId" to registroId),
            "readBy" to mapOf(myUid to true)
        )
        ref.setValue(msg)
    }

    fun postergarTarea(solicitudId: String, registroId: String, motivo: String, userId: String, titulo: String, otherUid: String) {
        viewModelScope.launch {
            try {
                val ahora = Timestamp.now()
                val updates = mapOf(
                    "postergada" to true,
                    "motivoPostergacion" to motivo,
                    "fechaPostergacion" to ahora,
                    "completada" to false,
                    "notas" to "Postergada: $motivo"
                )
                db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas").document(registroId)
                    .update(updates).await()

                if (otherUid.isNotEmpty()) {
                    val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora.toDate())
                    val texto = "⏳ Tarea postergada ($hora): $titulo\nMotivo: $motivo"
                    enviarMensajeSistemaChat(userId, otherUid, texto)
                    
                    enviarNotificacionAUsuario(otherUid, userId, "tarea_postergada", "Tarea Postergada", texto, mapOf("solicitudId" to solicitudId, "registroId" to registroId))
                }
            } catch (e: Exception) {
                _error.value = "Error al postergar: ${e.localizedMessage}"
            }
        }
    }

    private fun enviarNotificacionAUsuario(receptor: String, emisor: String, tipo: String, titulo: String, body: String, extraData: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            try {
                val data = extraData.toMutableMap()
                data["titulo"] = titulo
                data["body"] = body
                
                val notif = Notification(
                    id = UUID.randomUUID().toString(),
                    tipo = tipo,
                    emisor = emisor,
                    receptor = receptor,
                    timestamp = System.currentTimeMillis(),
                    data = data
                )
                db.collection("usuarios").whereEqualTo("uid", receptor).get().addOnSuccessListener { query ->
                    if (!query.isEmpty) {
                        query.documents[0].reference.collection("notificaciones").document(notif.id).set(notif)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun enviarMensajeSistemaChat(myUid: String, otherUid: String, texto: String) {
        val chatId = if (myUid < otherUid) "${myUid}_$otherUid" else "${otherUid}_$myUid"
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference
        val msgRef = database.child("messages").child(chatId).push()
        val msgId = msgRef.key ?: return
        
        val message = ChatMessage(
            id = msgId,
            senderId = "system",
            text = texto,
            timestamp = System.currentTimeMillis(),
            readBy = mapOf(myUid to true)
        )
        msgRef.setValue(message)
    }

    fun generarInstanciasParaHoy(solicitudId: String, pacienteFichaId: String) {
        val today = getTodayDate()
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

        viewModelScope.launch {
            try {
                val solRef = db.collection("solicitudes").document(solicitudId)
                val solDoc = solRef.get().await()
                if (solDoc.getString("ultimaGeneracion") == today) return@launch

                var plantillas = solRef.collection("tareas_servicio")
                    .get().await().toObjects(Tarea::class.java)

                if (solDoc.getString("ultimaGeneracion") == null && plantillas.isEmpty() && pacienteFichaId.isNotEmpty()) {
                    val fichaTareas = db.collection("fichas").document(pacienteFichaId)
                        .collection("rutina_data").document("actual").get().await()
                        .let { doc ->
                            val list = doc.get("tareas") as? List<Map<String, Any>> ?: emptyList()
                            list.map { map ->
                                Tarea(
                                    id = map["id"] as? String ?: "",
                                    titulo = map["titulo"] as? String ?: "",
                                    tipo = TipoTarea.valueOf(map["tipo"] as? String ?: "OTRO"),
                                    horarioInicio = map["horarioInicio"] as? String ?: "",
                                    frecuencia = FrecuenciaTarea.valueOf(map["frecuencia"] as? String ?: "DIARIA"),
                                    descripcion = map["descripcion"] as? String ?: ""
                                )
                            }.filter { it.id.isNotEmpty() }
                        }
                    
                    if (fichaTareas.isNotEmpty()) {
                        val batchImport = db.batch()
                        fichaTareas.forEach { t ->
                            val ref = solRef.collection("tareas_servicio").document(t.id)
                            batchImport.set(ref, t)
                        }
                        batchImport.commit().await()
                        plantillas = fichaTareas
                    }
                }

                val monthOfYear = calendar.get(Calendar.MONTH) + 1
                val tareasParaHoy = plantillas.filter { tarea ->
                    when (tarea.frecuencia) {
                        FrecuenciaTarea.DIARIA -> true
                        FrecuenciaTarea.SEMANAL -> {
                            val adjustDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
                            tarea.diasSeleccionados.contains(adjustDay)
                        }
                        FrecuenciaTarea.MENSUAL -> {
                            val monthApplies = tarea.mesesSeleccionados.isEmpty() || tarea.mesesSeleccionados.contains(monthOfYear)
                            monthApplies && tarea.diasSeleccionados.contains(dayOfMonth)
                        }
                        FrecuenciaTarea.UNA_VEZ -> tarea.fechaEspecifica == today
                    }
                }

                val existingRegistros = solRef.collection("registros_tareas")
                    .whereEqualTo("fecha", today)
                    .get().await().toObjects(RegistroTarea::class.java)

                val batch = db.batch()
                
                tareasParaHoy.forEach { tarea ->
                    val existing = existingRegistros.find { it.tareaId == tarea.id }
                    
                    if (existing == null) {
                        val regId = UUID.randomUUID().toString()
                        val registro = RegistroTarea(
                            id = regId,
                            tareaId = tarea.id,
                            solicitudId = solicitudId,
                            fecha = today,
                            horarioPrevisto = tarea.horarioInicio,
                            titulo = tarea.titulo,
                            tipo = tarea.tipo,
                            descripcion = tarea.descripcion,
                            frecuencia = tarea.frecuencia,
                            exigirFoto = tarea.exigirFoto
                        )
                        val ref = solRef.collection("registros_tareas").document(regId)
                        batch.set(ref, registro)
                    } else if (!existing.completada) {
                        val ref = solRef.collection("registros_tareas").document(existing.id)
                        batch.update(ref, mapOf(
                            "titulo" to tarea.titulo,
                            "horarioPrevisto" to tarea.horarioInicio,
                            "descripcion" to tarea.descripcion,
                            "tipo" to tarea.tipo.name,
                            "exigirFoto" to tarea.exigirFoto
                        ))
                    }
                }
                
                val plantillasIds = plantillas.map { it.id }
                existingRegistros.forEach { reg ->
                    if (!plantillasIds.contains(reg.tareaId) && !reg.completada) {
                        val ref = solRef.collection("registros_tareas").document(reg.id)
                        batch.delete(ref)
                    }
                }

                batch.update(solRef, "ultimaGeneracion", today)

                batch.commit().await()
            } catch (e: Exception) {
                _error.value = "Error al generar tareas diarias: ${e.localizedMessage}"
            }
        }
    }

    fun cargarTareasServicio(solicitudId: String) {
        tareasListener?.remove()
        tareasListener = db.collection("solicitudes").document(solicitudId)
            .collection("tareas_servicio")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _tareasPlantilla.value = snapshot.toObjects(Tarea::class.java)
                }
            }
    }

    fun agregarTareaAServicio(solicitudId: String, tarea: Tarea) {
        viewModelScope.launch {
            try {
                val tareaActiva = tarea.copy(activa = true)
                db.collection("solicitudes").document(solicitudId)
                    .collection("tareas_servicio").document(tareaActiva.id)
                    .set(tareaActiva).await()
                
                val currentPlantillas = _tareasPlantilla.value.toMutableList()
                currentPlantillas.removeAll { it.id == tareaActiva.id }
                currentPlantillas.add(tareaActiva)
                _tareasPlantilla.value = currentPlantillas

                if (debeEjecutarseHoy(tareaActiva)) {
                    agregarTareaARegistroHoy(solicitudId, tareaActiva)
                }
            } catch (e: Exception) {
                _error.value = "Error al guardar en servicio: ${e.localizedMessage}"
            }
        }
    }

    fun eliminarTareasSeleccionadas(solicitudId: String, tareaIds: List<String>) {
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val today = getTodayDate()
                
                tareaIds.forEach { tId ->
                    val serviceRef = db.collection("solicitudes").document(solicitudId)
                        .collection("tareas_servicio").document(tId)
                    batch.delete(serviceRef)
                }
                
                val registrosSnapshot = db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas")
                    .whereEqualTo("fecha", today)
                    .get().await()
                
                registrosSnapshot.documents.forEach { doc ->
                    val reg = doc.toObject(RegistroTarea::class.java)
                    if (reg != null && tareaIds.contains(reg.tareaId) && !reg.completada) {
                        batch.delete(doc.reference)
                    }
                }

                batch.commit().await()
                
                _tareasHoy.value = _tareasHoy.value.filter { reg -> 
                    !(tareaIds.contains(reg.tareaId) && !reg.completada)
                }

            } catch (e: Exception) {
                _error.value = "Error al eliminar múltiples tareas: ${e.localizedMessage}"
            }
        }
    }

    fun eliminarRegistroHoy(solicitudId: String, registroId: String) {
        viewModelScope.launch {
            try {
                db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas").document(registroId)
                    .delete().await()
            } catch (e: Exception) {
                _error.value = "Error al eliminar registro diario: ${e.localizedMessage}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun debeEjecutarseHoy(tarea: Tarea): Boolean {
        val today = getTodayDate()
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val monthOfYear = calendar.get(Calendar.MONTH) + 1
        
        return when (tarea.frecuencia) {
            FrecuenciaTarea.DIARIA -> true
            FrecuenciaTarea.SEMANAL -> {
                val adjustDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
                tarea.diasSeleccionados.contains(adjustDay)
            }
            FrecuenciaTarea.MENSUAL -> {
                val monthApplies = tarea.mesesSeleccionados.isEmpty() || tarea.mesesSeleccionados.contains(monthOfYear)
                monthApplies && tarea.diasSeleccionados.contains(dayOfMonth)
            }
            FrecuenciaTarea.UNA_VEZ -> tarea.fechaEspecifica == today
        }
    }

    fun completarMultiplesTareas(solicitudId: String, registroIds: List<String>, userId: String, otherUid: String) {
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val ahora = Timestamp.now()
                val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora.toDate())
                
                registroIds.forEach { id ->
                    val ref = db.collection("solicitudes").document(solicitudId)
                        .collection("registros_tareas").document(id)
                    batch.update(ref, mapOf(
                        "completada" to true,
                        "completadaPor" to userId,
                        "fechaCompletado" to ahora
                    ))
                }
                batch.commit().await()
                
                if (otherUid.isNotEmpty()) {
                    enviarMensajeSistemaChat(userId, otherUid, "✅ Se han finalizado ${registroIds.size} tareas masivamente a las $hora")
                }
            } catch (e: Exception) {
                _error.value = "Error al completar múltiples tareas: ${e.localizedMessage}"
            }
        }
    }

    fun postergarMultiplesTareas(solicitudId: String, registroIds: List<String>, motivo: String, userId: String, otherUid: String) {
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val ahora = Timestamp.now()
                val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora.toDate())
                
                registroIds.forEach { id ->
                    val ref = db.collection("solicitudes").document(solicitudId)
                        .collection("registros_tareas").document(id)
                    batch.update(ref, mapOf(
                        "postergada" to true,
                        "motivoPostergacion" to motivo,
                        "fechaPostergacion" to ahora,
                        "completada" to false,
                        "notas" to "Postergación masiva: $motivo"
                    ))
                }
                batch.commit().await()
                
                if (otherUid.isNotEmpty()) {
                    enviarMensajeSistemaChat(userId, otherUid, "⏳ Se han postergado ${registroIds.size} tareas masivamente a las $hora\nMotivo: $motivo")
                }
            } catch (e: Exception) {
                _error.value = "Error al postergar múltiples tareas: ${e.localizedMessage}"
            }
        }
    }

    fun generarInformeFinal(solicitudId: String, cuidadorUid: String, observaciones: String, otherUid: String = "", onComplete: (InformeTurno) -> Unit) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("solicitudes").document(solicitudId)
                    .collection("registros_tareas")
                    .get().await()
                
                val registros = snapshot.toObjects(RegistroTarea::class.java)
                val informe = InformeTurno(
                    solicitudId = solicitudId,
                    fecha = getTodayDate(),
                    cuidadorUid = cuidadorUid,
                    resumenTareas = registros,
                    observacionesGenerales = observaciones
                )
                
                db.collection("informes_servicios").document(informe.id).set(informe).await()
                
                if (otherUid.isNotEmpty()) {
                    val texto = "🏁 El cuidador ha finalizado su turno.\nObservaciones: ${if(observaciones.isNotBlank()) observaciones else "Sin observaciones."}"
                    enviarMensajeSistemaChat(cuidadorUid, otherUid, texto)
                    enviarNotificacionAUsuario(otherUid, cuidadorUid, "turno_finalizado", "Turno Finalizado", "El cuidador ha completado el turno de hoy.", mapOf("solicitudId" to solicitudId, "informeId" to informe.id))
                }

                onComplete(informe)
            } catch (e: Exception) {}
        }
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        tareasListener?.remove()
        registrosListener?.remove()
        atrasadasListener?.remove()
    }
}
