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
import com.google.firebase.firestore.Query
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

    private val _misPostulaciones = MutableStateFlow<List<Pair<SolicitudCuidado, PostulacionCuidado>>>(emptyList())
    val misPostulaciones: StateFlow<List<Pair<SolicitudCuidado, PostulacionCuidado>>> = _misPostulaciones

    private val _historial = MutableStateFlow<List<HistorialServicio>>(emptyList())
    val historial: StateFlow<List<HistorialServicio>> = _historial

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val finalizingIds = mutableSetOf<String>()

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
            val userDoc = db.collection("usuarios").document(uid).get().await()
            val role = userDoc.getString("rol") ?: ""

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
                        .filter { it.id !in finalizingIds }
                        .sortedByDescending { it.createdAt }
                    _misSolicitudes.value = list
                    
                    // Always refresh applications listening when requests change
                    listenToPostulaciones(list.map { it.id })
                }
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
                
                // Refresh applications for these requests
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

    fun eliminarPostulacionEspecifica(solicitudId: String) {
        cancelarPostulacion(solicitudId)
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
            .whereEqualTo("estado", "cerrada")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _solicitudesAceptadas.value = snapshot.toObjects(SolicitudCuidado::class.java)
                        .filter { it.id !in finalizingIds }
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
                            // Mostramos postulaciones pendientes o rechazadas. 
                            // Las aceptadas ya aparecen en la sección de "Servicios Activos"
                            if (post.estado == "pendiente" || post.estado == "rechazada") {
                                val solDoc = db.collection("solicitudes").document(post.solicitudId).get().await()
                                val sol = solDoc.toObject(SolicitudCuidado::class.java)
                                
                                // Si la solicitud fue eliminada, no la mostramos
                                if (sol != null) {
                                    // Para las pendientes, solo las mostramos si la solicitud sigue abierta
                                    if (post.estado == "pendiente" && sol.estado == "abierta") {
                                        result.add(sol to post)
                                    } 
                                    // Para las rechazadas, las mostramos siempre para que el usuario sepa
                                    else if (post.estado == "rechazada") {
                                        result.add(sol to post)
                                    }
                                }
                            }
                        }
                        _misPostulaciones.value = result
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
                val userDoc = db.collection("usuarios").document(uid).get().await()
                val repNombre = "${userDoc.getString("nombre")} ${userDoc.getString("apellido_pa")}"
                val repRut = userDoc.getString("rut") ?: ""
                val repTel = userDoc.getString("telefono") ?: ""

                val id = UUID.randomUUID().toString()
                val solicitud = SolicitudCuidado(
                    id = id,
                    representanteUid = uid,
                    representanteNombre = repNombre,
                    representanteRut = repRut,
                    representanteTelefono = repTel,
                    pacienteFichaId = pacienteFichaId,
                    nombrePaciente = nombrePaciente,
                    fotoPaciente = fotoPaciente,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    esIndefinido = esIndefinido,
                    horarioStr = horarioStr,
                    periodos = periodos,
                    estado = "abierta",
                    createdAt = Timestamp.now()
                )

                db.collection("solicitudes").document(id).set(solicitud).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error desconocido al crear solicitud")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun eliminarSolicitud(id: String) {
        viewModelScope.launch {
            try {
                db.collection("solicitudes").document(id).delete().await()
                // Also delete applications? User didn't specify but good practice
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
                val userDoc = db.collection("usuarios").document(uid).get().await()
                val userData = userDoc.toObject(UserData::class.java)
                
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
                
                // 1. Notify representative (Notification collection)
                val notifRef = db.collection("usuarios").document(solicitud.representanteUid).collection("notificaciones").document()
                val notif = Notification(
                    id = notifRef.id,
                    tipo = "postulacion_recibida",
                    emisor = uid,
                    receptor = solicitud.representanteUid,
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("solicitudId" to solicitud.id, "postulacionId" to id)
                )
                notifRef.set(notif).await()

                // 2. Send Message to Chat
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
                // Log application error
            } finally {
                _isLoading.value = false
                onReady() // Always call onReady to allow redirection even if partial fail
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
                // 1. Establish vínculo (Bidirectional connection)
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

                // 2. Assign caregiver to request and close it
                db.collection("solicitudes").document(solicitud.id).update(
                    mapOf(
                        "cuidadorUid" to targetUid,
                        "cuidadorNombre" to postulacion.cuidadorNombre,
                        "cuidadorFoto" to postulacion.cuidadorFoto,
                        "estado" to "cerrada",
                        "aceptadoAt" to Timestamp.now()
                    )
                ).await()

                // 3. Mark application as accepted
                db.collection("postulaciones").document(postulacion.id).update("estado", "aceptada").await()

                // 4. Reject other candidates for this specific request
                val otherPostulaciones = db.collection("postulaciones")
                    .whereEqualTo("solicitudId", solicitud.id)
                    .get().await()
                
                for (doc in otherPostulaciones.documents) {
                    if (doc.id != postulacion.id) {
                        doc.reference.update("estado", "rechazada").await()
                    }
                }

                // 5. Notify caregiver
                val notifRef = db.collection("usuarios").document(targetUid).collection("notificaciones").document()
                val notif = Notification(
                    id = notifRef.id,
                    tipo = "postulacion_aceptada",
                    emisor = myUid,
                    receptor = targetUid,
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("solicitudId" to solicitud.id, "vinculoId" to vinculoId)
                )
                notifRef.set(notif).await()

                onReady()
            } catch (e: Exception) { } finally {
                _isLoading.value = false
            }
        }
    }

    fun finalizarServicio(solicitud: SolicitudCuidado, onReady: () -> Unit) {
        if (finalizingIds.contains(solicitud.id)) return
        finalizingIds.add(solicitud.id)
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                // Check if already deleted/finalized by other party
                val check = db.collection("solicitudes").document(solicitud.id).get().await()
                if (!check.exists()) {
                    finalizingIds.remove(solicitud.id)
                    onReady()
                    return@launch
                }

                // 1. Create history record before deleting
                val histId = UUID.randomUUID().toString()
                
                // Get caregiver specialty for history
                val cuidadorDoc = db.collection("fichas").document(solicitud.cuidadorUid ?: "").get().await()
                val especialidad = cuidadorDoc.getString("especializacion") ?: "No especificada"
                val pacienteDoc = db.collection("fichas").document(solicitud.pacienteFichaId).get().await()
                val pacienteEdad = pacienteDoc.get("edad")?.toString() ?: ""

                val historial = HistorialServicio(
                    id = histId,
                    solicitudId = solicitud.id,
                    representanteUid = solicitud.representanteUid,
                    representanteNombre = solicitud.representanteNombre,
                    representanteTelefono = solicitud.representanteTelefono,
                    pacienteNombre = solicitud.nombrePaciente,
                    pacienteEdad = pacienteEdad,
                    cuidadorUid = solicitud.cuidadorUid ?: "",
                    cuidadorNombre = solicitud.cuidadorNombre,
                    cuidadorEspecialidad = especialidad,
                    fechaInicio = solicitud.fechaInicio,
                    fechaTermino = Timestamp.now(),
                    aceptadoAt = solicitud.aceptadoAt
                )
                db.collection("historial_servicios").document(histId).set(historial).await()

                // 2. Delete request and applications
                db.collection("solicitudes").document(solicitud.id).delete().await()
                
                // Cleanup applications for this request
                try {
                    val postSnapshot = db.collection("postulaciones").whereEqualTo("solicitudId", solicitud.id).get().await()
                    for (doc in postSnapshot.documents) {
                        doc.reference.delete().await()
                    }
                } catch (e: Exception) {
                    // Non-critical cleanup error
                }

                // 3. Notify parties
                val receptor = if (currentUid == solicitud.representanteUid) solicitud.cuidadorUid else solicitud.representanteUid
                if (receptor != null) {
                    val notifRef = db.collection("usuarios").document(receptor).collection("notificaciones").document()
                    val notif = Notification(
                        id = notifRef.id,
                        tipo = "servicio_finalizado",
                        emisor = currentUid ?: "",
                        receptor = receptor,
                        timestamp = System.currentTimeMillis(),
                        data = mapOf("solicitudId" to solicitud.id)
                    )
                    notifRef.set(notif).await()
                }
                
                onReady()
            } catch (e: Exception) { 
                finalizingIds.remove(solicitud.id)
            } finally {
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
        db.collection("historial_servicios").document(historialId).update(
            mapOf(
                "calificacion" to calificacion,
                "observacionCalificacion" to observacion
            )
        )
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
