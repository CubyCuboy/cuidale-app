package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.PostulacionCuidado
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.models.HistorialServicio
import com.example.cuidaleapp.models.UserData
import com.example.cuidaleapp.models.Notification
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SolicitudViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val currentUid get() = auth.currentUser?.uid

    private val _misSolicitudes = MutableStateFlow<List<SolicitudCuidado>>(emptyList())
    val misSolicitudes: StateFlow<List<SolicitudCuidado>> = _misSolicitudes

    private val _postulacionesRecibidas = MutableStateFlow<Map<String, List<PostulacionCuidado>>>(emptyMap())
    val postulacionesRecibidas: StateFlow<Map<String, List<PostulacionCuidado>>> = _postulacionesRecibidas

    private val _solicitudesAceptadas = MutableStateFlow<List<SolicitudCuidado>>(emptyList())
    val solicitudesAceptadas: StateFlow<List<SolicitudCuidado>> = _solicitudesAceptadas

    // Solicitudes dirigidas (el representante eligió a este cuidador) que aún
    // están en negociación de precio (estado abierta/negociando).
    private val _solicitudesDirigidas = MutableStateFlow<List<SolicitudCuidado>>(emptyList())
    val solicitudesDirigidas: StateFlow<List<SolicitudCuidado>> = _solicitudesDirigidas

    private val _misPostulaciones = MutableStateFlow<List<Pair<SolicitudCuidado, PostulacionCuidado>>>(emptyList())
    val misPostulaciones: StateFlow<List<Pair<SolicitudCuidado, PostulacionCuidado>>> = _misPostulaciones

    private val _historial = MutableStateFlow<List<HistorialServicio>>(emptyList())
    val historial: StateFlow<List<HistorialServicio>> = _historial

    private val _representantesFotos = MutableStateFlow<Map<String, String>>(emptyMap())
    val representantesFotos: StateFlow<Map<String, String>> = _representantesFotos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var solicitudesListener: ListenerRegistration? = null
    private var postulacionesListener: ListenerRegistration? = null
    private var cuidadorSolicitudesListener: ListenerRegistration? = null
    private var misPostulacionesListener: ListenerRegistration? = null
    private var historialListener: ListenerRegistration? = null

    init {
        initListeners()
    }

    private fun initListeners() {
        val uid = currentUid ?: return
        
        viewModelScope.launch {
            val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
            val role = if (!query.isEmpty) query.documents[0].getString("rol") ?: "" else ""

            if (role == "representante") {
                listenAsRepresentante(uid)
            } else if (role == "cuidador") {
                listenAsCuidador(uid)
            }
            listenHistorial(uid, role)
        }
    }

    private fun listenHistorial(uid: String, role: String) {
        historialListener?.remove()
        val query = if (role == "representante") {
            db.collection("historial_servicios")
                .whereEqualTo("representanteUid", uid)
                .whereEqualTo("eliminadoPorRepresentante", false)
        } else {
            db.collection("historial_servicios")
                .whereEqualTo("cuidadorUid", uid)
                .whereEqualTo("eliminadoPorCuidador", false)
        }
        
        historialListener = query.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                _historial.value = snapshot.toObjects(HistorialServicio::class.java)
                    .sortedByDescending { it.fechaTermino }
            }
        }
    }

    private fun listenAsRepresentante(uid: String) {
        solicitudesListener?.remove()
        solicitudesListener = db.collection("solicitudes")
            .whereEqualTo("representanteUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.toObjects(SolicitudCuidado::class.java)
                        .sortedByDescending { it.createdAt }
                    _misSolicitudes.value = list
                    cargarFotosRepresentantes(list.map { it.representanteUid })

                    listenToPostulaciones(list.map { it.id })
                }
            }
    }

    fun cargarFotosRepresentantes(uids: List<String>) {
        val missing = uids.filter { it.isNotBlank() && !_representantesFotos.value.containsKey(it) }.distinct()
        if (missing.isEmpty()) return
        viewModelScope.launch {
            try {
                var fotos = emptyMap<String, String>()
                for (chunk in missing.chunked(30)) {
                    val snap = db.collection("usuarios").whereIn("uid", chunk).get().await()
                    fotos += snap.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: return@mapNotNull null
                        uid to (doc.getString("fotoUrl") ?: "")
                    }.toMap()
                }
                _representantesFotos.value = _representantesFotos.value + fotos
            } catch (e: Exception) { }
        }
    }

    fun refreshData() {
        val uid = currentUid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = db.collection("solicitudes")
                    .whereEqualTo("representanteUid", uid)
                    .get().await()
                val list = snapshot.toObjects(SolicitudCuidado::class.java)
                    .sortedByDescending { it.createdAt }
                _misSolicitudes.value = list
                
                val solIds = list.map { it.id }
                if (solIds.isNotEmpty()) {
                    val postSnapshot = db.collection("postulaciones")
                        .whereIn("solicitudId", solIds.take(10))
                        .get().await()
                    _postulacionesRecibidas.value = postSnapshot.toObjects(PostulacionCuidado::class.java).groupBy { it.solicitudId }
                }
            } catch (e: Exception) { } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelarPostulacion(solicitudId: String) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("postulaciones")
                    .whereEqualTo("solicitudId", solicitudId)
                    .whereEqualTo("cuidadorUid", uid)
                    .get().await()
                
                for (doc in snapshot.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) { }
        }
    }

    fun eliminarPostulacionPorId(postulacionId: String) {
        viewModelScope.launch {
            try {
                db.collection("postulaciones").document(postulacionId).delete().await()
            } catch (e: Exception) { }
        }
    }

    private fun listenToPostulaciones(solicitudIds: List<String>) {
        if (solicitudIds.isEmpty()) {
            _postulacionesRecibidas.value = emptyMap()
            return
        }

        postulacionesListener?.remove()
        postulacionesListener = db.collection("postulaciones")
            .whereIn("solicitudId", solicitudIds.take(10))
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.toObjects(PostulacionCuidado::class.java)
                    _postulacionesRecibidas.value = list.groupBy { it.solicitudId }
                }
            }
    }

    private fun listenAsCuidador(uid: String) {
        cuidadorSolicitudesListener?.remove()
        cuidadorSolicitudesListener = db.collection("solicitudes")
            .whereEqualTo("cuidadorUid", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val todas = snapshot.toObjects(SolicitudCuidado::class.java)
                    val aceptadas = todas.filter { it.estado == "cerrada" }
                    val dirigidas = todas.filter { it.estado != "cerrada" }
                    _solicitudesAceptadas.value = aceptadas
                    _solicitudesDirigidas.value = dirigidas
                    cargarFotosRepresentantes(todas.map { it.representanteUid })
                }
            }

        misPostulacionesListener?.remove()
        misPostulacionesListener = db.collection("postulaciones")
            .whereEqualTo("cuidadorUid", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val postulaciones = snapshot.toObjects(PostulacionCuidado::class.java)
                    viewModelScope.launch {
                        val result = mutableListOf<Pair<SolicitudCuidado, PostulacionCuidado>>()
                        postulaciones.forEach { post ->
                            if (post.estado == "pendiente" || post.estado == "rechazada") {
                                val solDoc = db.collection("solicitudes").document(post.solicitudId).get().await()
                                val sol = solDoc.toObject(SolicitudCuidado::class.java)
                                
                                if (sol != null) {
                                    if (post.estado == "pendiente" && sol.estado == "abierta") {
                                        result.add(sol to post)
                                    } 
                                    else if (post.estado == "rechazada") {
                                        result.add(sol to post)
                                    }
                                }
                            }
                        }
                        _misPostulaciones.value = result
                        cargarFotosRepresentantes(result.map { it.first.representanteUid })
                    }
                }
            }
    }

    fun crearSolicitud(
        pacienteFichaId: String,
        nombrePaciente: String,
        fotoPaciente: String,
        fechaInicio: Timestamp?,
        fechaFin: Timestamp?,
        esIndefinido: Boolean,
        horarioStr: String,
        periodos: List<String>,
        exigirFotoTareas: Boolean = false,
        tarifaHora: Long = 0L,
        cuidadorUid: String? = null,
        cuidadorNombre: String = "",
        cuidadorFoto: String = "",
        tarifaHoraReferencia: Long? = null,
        estadoAcuerdo: String = "acordado",
        propuestaPrecio: Long? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = currentUid ?: run {
            onError("No hay sesión activa")
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                val userDoc = if (!query.isEmpty) query.documents[0] else null
                val repNombre = if (userDoc != null) "${userDoc.getString("nombre")} ${userDoc.getString("apellido_pa")}" else ""
                val repRut = userDoc?.getString("rut") ?: ""
                val repTel = userDoc?.getString("telefono") ?: ""
                val repFoto = userDoc?.getString("fotoUrl") ?: ""

                val id = UUID.randomUUID().toString()
                val solicitud = SolicitudCuidado(
                    id = id,
                    representanteUid = uid,
                    representanteNombre = repNombre,
                    representanteRut = repRut,
                    representanteTelefono = repTel,
                    representanteFoto = repFoto,
                    pacienteFichaId = pacienteFichaId,
                    nombrePaciente = nombrePaciente,
                    fotoPaciente = fotoPaciente,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    esIndefinido = esIndefinido,
                    horarioStr = horarioStr,
                    periodos = periodos,
                    exigirFotoTareas = exigirFotoTareas,
                    tarifaHora = tarifaHora,
                    tarifaHoraReferencia = tarifaHoraReferencia,
                    estadoAcuerdo = estadoAcuerdo,
                    propuestaPrecio = propuestaPrecio,
                    acuerdoAceptadoRepresentante = (estadoAcuerdo == "acordado"),
                    cuidadorUid = cuidadorUid,
                    cuidadorNombre = cuidadorNombre,
                    cuidadorFoto = cuidadorFoto,
                    estado = if (cuidadorUid != null && estadoAcuerdo == "acordado") "cerrada" else "abierta",
                    aceptadoAt = if (cuidadorUid != null && estadoAcuerdo == "acordado") Timestamp.now() else null,
                    createdAt = Timestamp.now()
                )

                db.collection("solicitudes").document(id).set(solicitud).await()

                // Si es solicitud dirigida en negociación, enviar propuesta al chat.
                if (cuidadorUid != null && estadoAcuerdo == "negociando" && propuestaPrecio != null) {
                    enviarPropuestaPrecioAlChat(id, cuidadorUid, cuidadorNombre, cuidadorFoto, propuestaPrecio, uid, repNombre, repFoto)
                }

                // Notificar al cuidador dirigido.
                if (cuidadorUid != null && cuidadorUid != uid) {
                    db.collection("usuarios").whereEqualTo("uid", cuidadorUid).get().addOnSuccessListener { targetQuery ->
                        if (!targetQuery.isEmpty) {
                            val notifRef = targetQuery.documents[0].reference.collection("notificaciones").document()
                            val tipo = if (estadoAcuerdo == "negociando") "solicitud_dirigida_negociacion" else "solicitud_dirigida"
                            val notif = Notification(
                                id = notifRef.id,
                                tipo = tipo,
                                emisor = uid,
                                receptor = cuidadorUid,
                                timestamp = System.currentTimeMillis(),
                                data = mapOf("solicitudId" to id)
                            )
                            notifRef.set(notif)
                        }
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error desconocido al crear solicitud")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Envía un mensaje de tipo "negociacion_precio" al chat entre el representante
     * y el cuidador con la propuesta de valor hora.
     */
    private suspend fun enviarPropuestaPrecioAlChat(
        solicitudId: String,
        cuidadorUid: String,
        cuidadorNombre: String,
        cuidadorFoto: String,
        monto: Long,
        representanteUid: String,
        representanteNombre: String,
        representanteFoto: String
    ) {
        try {
            val chatId = if (representanteUid < cuidadorUid) "${representanteUid}_$cuidadorUid" else "${cuidadorUid}_$representanteUid"
            val chatMsgRef = com.google.firebase.database.FirebaseDatabase.getInstance().reference
                .child("messages").child(chatId).push()
            val chatMsg = mapOf(
                "id" to (chatMsgRef.key ?: UUID.randomUUID().toString()),
                "senderId" to representanteUid,
                "text" to "Propuesta de valor por hora: $monto",
                "type" to "negociacion_precio",
                "metadata" to mapOf(
                    "solicitudId" to solicitudId,
                    "monto" to monto.toString(),
                    "emisorNombre" to representanteNombre,
                    "emisorFoto" to representanteFoto
                ),
                "timestamp" to System.currentTimeMillis(),
                "readBy" to mapOf(representanteUid to true)
            )
            chatMsgRef.setValue(chatMsg).await()
        } catch (e: Exception) { }
    }

    /**
     * El cuidador o representante acepta el precio propuesto. Si ambos aceptan,
     * la solicitud queda acordada y lista para iniciar el cuidado.
     */
    fun aceptarPrecio(solicitudId: String, rol: String, onReady: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val docRef = db.collection("solicitudes").document(solicitudId)
                val snap = docRef.get().await()
                val sol = snap.toObject(SolicitudCuidado::class.java) ?: return@launch
                val esRepresentante = rol == "representante"
                val esCuidador = rol == "cuidador"
                val updates = hashMapOf<String, Any>()
                if (esRepresentante) updates["acuerdoAceptadoRepresentante"] = true
                if (esCuidador) updates["acuerdoAceptadoCuidador"] = true
                val ambosAceptan = (esRepresentante || sol.acuerdoAceptadoCuidador) && (esCuidador || sol.acuerdoAceptadoRepresentante)
                if (ambosAceptan) {
                    updates["estadoAcuerdo"] = "acordado"
                    updates["propuestaPrecio"] = sol.tarifaHora
                    // Solicitud dirigida: al acordar el precio queda activa (cerrada) y
                    // lista para iniciar el cuidado.
                    if (!sol.cuidadorUid.isNullOrBlank()) {
                        updates["estado"] = "cerrada"
                        updates["aceptadoAt"] = Timestamp.now()
                    }
                }
                docRef.update(updates).await()
                onReady()
            } catch (e: Exception) { }
        }
    }

    /**
     * El cuidador o representante propone un nuevo valor. Se actualiza la
     * solicitud y se envía un mensaje al chat con la nueva propuesta.
     */
    fun proponerPrecio(
        solicitudId: String,
        nuevoMonto: Long,
        rol: String,
        emisorNombre: String,
        emisorFoto: String,
        onReady: () -> Unit = {}
    ) {
        if (nuevoMonto <= 0L) return
        viewModelScope.launch {
            try {
                val docRef = db.collection("solicitudes").document(solicitudId)
                val snap = docRef.get().await()
                val sol = snap.toObject(SolicitudCuidado::class.java) ?: return@launch
                val esRepresentante = rol == "representante"
                val updates = hashMapOf<String, Any>(
                    "propuestaPrecio" to nuevoMonto,
                    "tarifaHora" to nuevoMonto,
                    "estadoAcuerdo" to "negociando"
                )
                updates["acuerdoAceptadoRepresentante"] = esRepresentante
                updates["acuerdoAceptadoCuidador"] = !esRepresentante
                docRef.update(updates).await()

                val otroUid = if (esRepresentante) (sol.cuidadorUid ?: "") else sol.representanteUid
                if (otroUid.isNotBlank()) {
                    val emisorUid = if (esRepresentante) sol.representanteUid else (sol.cuidadorUid ?: "")
                    enviarPropuestaPrecioAlChat(
                        solicitudId = solicitudId,
                        cuidadorUid = if (esRepresentante) otroUid else emisorUid,
                        cuidadorNombre = sol.cuidadorNombre,
                        cuidadorFoto = sol.cuidadorFoto,
                        monto = nuevoMonto,
                        representanteUid = if (esRepresentante) emisorUid else otroUid,
                        representanteNombre = emisorNombre,
                        representanteFoto = emisorFoto
                    )
                }
                onReady()
            } catch (e: Exception) { }
        }
    }

    fun actualizarSolicitud(
        requestId: String,
        horarioStr: String,
        periodos: List<String>,
        esIndefinido: Boolean,
        fechaInicio: Timestamp?,
        fechaFin: Timestamp?,
        exigirFotoTareas: Boolean = false,
        tarifaHora: Long = 0L,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updates = hashMapOf<String, Any>(
                    "horarioStr" to horarioStr,
                    "periodos" to periodos,
                    "esIndefinido" to esIndefinido,
                    "exigirFotoTareas" to exigirFotoTareas,
                    "tarifaHora" to tarifaHora
                )
                if (fechaInicio != null) updates["fechaInicio"] = fechaInicio
                if (fechaFin != null) updates["fechaFin"] = fechaFin
                db.collection("solicitudes").document(requestId).update(updates).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al actualizar solicitud")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun eliminarSolicitud(id: String) {
        viewModelScope.launch {
            try {
                db.collection("solicitudes").document(id).delete().await()
                val postulaciones = db.collection("postulaciones").whereEqualTo("solicitudId", id).get().await()
                for (doc in postulaciones.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) { }
        }
    }

    fun postularASolicitud(solicitud: SolicitudCuidado, onReady: () -> Unit) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val query = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                val userData = if (!query.isEmpty) query.documents[0].toObject(UserData::class.java) else null
                
                val id = UUID.randomUUID().toString()
                val postulacion = PostulacionCuidado(
                    id = id,
                    solicitudId = solicitud.id,
                    cuidadorUid = uid,
                    cuidadorNombre = "${userData?.nombre} ${userData?.apellido_pa}",
                    cuidadorFoto = userData?.fotoUrl ?: "",
                    timestamp = Timestamp.now()
                )
                
                db.collection("postulaciones").document(id).set(postulacion).await()
                
                db.collection("usuarios").whereEqualTo("uid", solicitud.representanteUid).get().addOnSuccessListener { targetQuery ->
                    if (!targetQuery.isEmpty) {
                        val notifRef = targetQuery.documents[0].reference.collection("notificaciones").document()
                        val notif = Notification(
                            id = notifRef.id,
                            tipo = "postulacion_recibida",
                            emisor = uid,
                            receptor = solicitud.representanteUid,
                            timestamp = System.currentTimeMillis(),
                            data = mapOf("solicitudId" to solicitud.id, "postulacionId" to id)
                        )
                        notifRef.set(notif)
                    }
                }

                try {
                    val chatId = if (uid < solicitud.representanteUid) "${uid}_${solicitud.representanteUid}" else "${solicitud.representanteUid}_$uid"
                    val chatMsgRef = com.google.firebase.database.FirebaseDatabase.getInstance().reference
                        .child("messages").child(chatId).push()
                    
                    val chatMsg = mapOf(
                        "id" to (chatMsgRef.key ?: UUID.randomUUID().toString()),
                        "senderId" to uid,
                        "text" to "Se realizó una postulación de servicio para ${solicitud.nombrePaciente}",
                        "type" to "aviso_postulacion",
                        "metadata" to mapOf(
                            "solicitudId" to solicitud.id,
                            "postulacionId" to id
                        ),
                        "timestamp" to System.currentTimeMillis(),
                        "readBy" to mapOf(uid to true)
                    )
                    chatMsgRef.setValue(chatMsg).await()
                } catch (e: Exception) { }
                
            } catch (e: Exception) { 
            } finally {
                _isLoading.value = false
                onReady()
            }
        }
    }

    fun rechazarPostulacion(postulacion: PostulacionCuidado) {
        viewModelScope.launch {
            try {
                db.collection("postulaciones").document(postulacion.id).update("estado", "rechazada").await()
            } catch (e: Exception) { }
        }
    }

    fun aceptarCuidador(solicitud: SolicitudCuidado, postulacion: PostulacionCuidado, onReady: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val myUid = solicitud.representanteUid
                val targetUid = postulacion.cuidadorUid
                val vinculoId = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
                
                val vinculo = mapOf(
                    "id" to vinculoId,
                    "uid1" to (if (myUid < targetUid) myUid else targetUid),
                    "uid2" to (if (myUid < targetUid) targetUid else myUid),
                    "uids" to listOf(myUid, targetUid),
                    "solicitante" to myUid,
                    "estado" to "aceptado",
                    "fecha" to System.currentTimeMillis()
                )
                db.collection("vinculos").document(vinculoId).set(vinculo).await()

                db.collection("solicitudes").document(solicitud.id).update(
                    mapOf(
                        "cuidadorUid" to targetUid,
                        "cuidadorNombre" to postulacion.cuidadorNombre,
                        "cuidadorFoto" to postulacion.cuidadorFoto,
                        "estado" to "cerrada",
                        "aceptadoAt" to Timestamp.now()
                    )
                ).await()

                db.collection("postulaciones").document(postulacion.id).update("estado", "aceptada").await()

                val otherPostulaciones = db.collection("postulaciones")
                    .whereEqualTo("solicitudId", solicitud.id)
                    .get().await()
                
                for (doc in otherPostulaciones.documents) {
                    if (doc.id != postulacion.id) {
                        doc.reference.update("estado", "rechazada").await()
                    }
                }

                db.collection("usuarios").whereEqualTo("uid", targetUid).get().addOnSuccessListener { targetQuery ->
                    if (!targetQuery.isEmpty) {
                        val notifRef = targetQuery.documents[0].reference.collection("notificaciones").document()
                        val notif = Notification(
                            id = notifRef.id,
                            tipo = "postulacion_aceptada",
                            emisor = myUid,
                            receptor = targetUid,
                            timestamp = System.currentTimeMillis(),
                            data = mapOf("solicitudId" to solicitud.id, "vinculoId" to vinculoId)
                        )
                        notifRef.set(notif)
                    }
                }

                onReady()
            } catch (e: Exception) { } finally {
                _isLoading.value = false
            }
        }
    }

    fun eliminarDelHistorial(histId: String, role: String) {
        val field = if (role == "representante") "eliminadoPorRepresentante" else "eliminadoPorCuidador"
        db.collection("historial_servicios").document(histId).update(field, true)
    }

    fun borrarTodoElHistorial(role: String) {
        val uid = currentUid ?: return
        val field = if (role == "representante") "eliminadoPorRepresentante" else "eliminadoPorCuidador"
        val queryField = if (role == "representante") "representanteUid" else "cuidadorUid"
        
        viewModelScope.launch {
            try {
                val snapshot = db.collection("historial_servicios")
                    .whereEqualTo(queryField, uid)
                    .get().await()
                for (doc in snapshot.documents) {
                    doc.reference.update(field, true).await()
                }
            } catch (e: Exception) { }
        }
    }

    fun calificarServicio(historialId: String, calificacion: Double, observacion: String) {
        viewModelScope.launch {
            try {
                val histDoc = db.collection("historial_servicios").document(historialId).get().await()
                if (!histDoc.exists()) return@launch

                val cuidadorUid = histDoc.getString("cuidadorUid") ?: ""
                val autor = histDoc.getString("representanteNombre") ?: "Representante"

                histDoc.reference.update(
                    mapOf(
                        "calificacion" to calificacion,
                        "observacionCalificacion" to observacion
                    )
                ).await()

                if (cuidadorUid.isNotBlank()) {
                    val resenaId = UUID.randomUUID().toString()
                    db.collection("fichas").document(cuidadorUid).collection("resenas").document(resenaId).set(
                        mapOf(
                            "autorNombre" to autor,
                            "calificacion" to calificacion,
                            "comentario" to observacion,
                            "fecha" to Timestamp.now(),
                            "historialId" to historialId
                        )
                    ).await()

                    val resenasSnapshot = db.collection("fichas").document(cuidadorUid)
                        .collection("resenas").get().await()
                    val notas = resenasSnapshot.documents.mapNotNull { it.getDouble("calificacion") }
                    val promedio = if (notas.isNotEmpty()) notas.average().toFloat() else 0.0f
                    val total = resenasSnapshot.size()

                    val fichaRef = db.collection("fichas").document(cuidadorUid)
                    try {
                        fichaRef.update(
                            mapOf(
                                "rating_promedio" to promedio,
                                "total_resenas" to total
                            )
                        ).await()
                    } catch (e: Exception) {
                        fichaRef.set(
                            mapOf(
                                "rating_promedio" to promedio,
                                "total_resenas" to total
                            ),
                            SetOptions.merge()
                        ).await()
                    }

                    // Notificar al cuidador sobre la nueva calificación
                    val emisorUid = auth.currentUser?.uid ?: ""
                    val data = mutableMapOf(
                        "titulo" to "Nueva calificación",
                        "body" to "$autor te calificó con ${calificacion} ★",
                        "calificacion" to calificacion.toString()
                    )
                    val notif = Notification(
                        id = UUID.randomUUID().toString(),
                        tipo = "nueva_calificacion",
                        emisor = emisorUid,
                        receptor = cuidadorUid,
                        timestamp = System.currentTimeMillis(),
                        data = data
                    )
                    db.collection("usuarios").whereEqualTo("uid", cuidadorUid).get().addOnSuccessListener { query ->
                        if (!query.isEmpty) {
                            query.documents[0].reference.collection("notificaciones").document(notif.id).set(notif)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignorar errores para mantener el comportamiento seguro
            }
        }
    }

    fun migrarCalificacionesHistoricas() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("historial_servicios")
                    .whereEqualTo("representanteUid", uid)
                    .get().await()
                val fichasActualizar = mutableMapOf<String, MutableList<Pair<Double, String>>>()
                for (doc in snapshot.documents) {
                    val calif = doc.getDouble("calificacion") ?: continue
                    val cuidadorUid = doc.getString("cuidadorUid") ?: continue
                    val historialId = doc.id
                    val autor = doc.getString("representanteNombre") ?: "Representante"
                    val comentario = doc.getString("observacionCalificacion") ?: ""
                    // idempotence: skip if review already exists for this historialId
                    val existente = db.collection("fichas").document(cuidadorUid)
                        .collection("resenas")
                        .whereEqualTo("historialId", historialId)
                        .get().await()
                    if (!existente.isEmpty) continue
                    val fecha = doc.getTimestamp("fechaTermino") ?: doc.getTimestamp("fechaInicio") ?: Timestamp.now()
                    db.collection("fichas").document(cuidadorUid)
                        .collection("resenas").document(UUID.randomUUID().toString())
                        .set(mapOf(
                            "autorNombre" to autor,
                            "calificacion" to calif,
                            "comentario" to comentario,
                            "fecha" to fecha,
                            "historialId" to historialId
                        )).await()
                    val list = fichasActualizar.getOrPut(cuidadorUid) { mutableListOf() }
                    list.add(calif to comentario)
                }
                // recompute aggregates
                for ((cuidadorUid, _) in fichasActualizar) {
                    val resenas = db.collection("fichas").document(cuidadorUid)
                        .collection("resenas").get().await()
                    val notas = resenas.documents.mapNotNull { it.getDouble("calificacion") }
                    val promedio = if (notas.isNotEmpty()) notas.average().toFloat() else 0f
                    val total = notas.size
                    try {
                        db.collection("fichas").document(cuidadorUid)
                            .update(mapOf("rating_promedio" to promedio, "total_resenas" to total)).await()
                    } catch (e: Exception) {
                        db.collection("fichas").document(cuidadorUid)
                            .set(mapOf("rating_promedio" to promedio, "total_resenas" to total), SetOptions.merge()).await()
                    }
                }
            } catch (e: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        solicitudesListener?.remove()
        postulacionesListener?.remove()
        cuidadorSolicitudesListener?.remove()
        misPostulacionesListener?.remove()
        historialListener?.remove()
    }
}
