package com.example.cuidaleapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.PostulacionCuidado
import com.example.cuidaleapp.models.SolicitudCuidado
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

    private val _misPostulaciones = MutableStateFlow<List<Pair<SolicitudCuidado, String>>>(emptyList())
    val misPostulaciones: StateFlow<List<Pair<SolicitudCuidado, String>>> = _misPostulaciones

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var solicitudesListener: ListenerRegistration? = null
    private var postulacionesListener: ListenerRegistration? = null
    private var cuidadorSolicitudesListener: ListenerRegistration? = null
    private var misPostulacionesListener: ListenerRegistration? = null

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
                }
            }

        misPostulacionesListener?.remove()
        misPostulacionesListener = db.collection("postulaciones")
            .whereEqualTo("cuidadorUid", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val postulaciones = snapshot.toObjects(PostulacionCuidado::class.java)
                    viewModelScope.launch {
                        val result = mutableListOf<Pair<SolicitudCuidado, String>>()
                        postulaciones.forEach { post ->
                            val solDoc = db.collection("solicitudes").document(post.solicitudId).get().await()
                            val sol = solDoc.toObject(SolicitudCuidado::class.java)
                            if (sol != null && sol.estado == "abierta") {
                                result.add(sol to post.estado)
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
        val id = UUID.randomUUID().toString()
        val solicitud = SolicitudCuidado(
            id = id,
            representanteUid = uid,
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

        viewModelScope.launch {
            _isLoading.value = true
            try {
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
                
                // Notify representative
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
                
                onReady()
            } catch (e: Exception) { } finally {
                _isLoading.value = false
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
                        "estado" to "cerrada"
                    )
                ).await()

                // 3. Mark application as accepted
                db.collection("postulaciones").document(postulacion.id).update("estado", "aceptada").await()

                // 4. Notify caregiver
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

    fun finalizarServicio(solicitud: SolicitudCuidado) {
        viewModelScope.launch {
            try {
                db.collection("solicitudes").document(solicitud.id).update("estado", "finalizada").await()
                
                // Notify representative
                val notifRef = db.collection("usuarios").document(solicitud.representanteUid).collection("notificaciones").document()
                val notif = Notification(
                    id = notifRef.id,
                    tipo = "servicio_finalizado",
                    emisor = currentUid ?: "",
                    receptor = solicitud.representanteUid,
                    timestamp = System.currentTimeMillis(),
                    data = mapOf("solicitudId" to solicitud.id)
                )
                notifRef.set(notif).await()
            } catch (e: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        solicitudesListener?.remove()
        postulacionesListener?.remove()
        cuidadorSolicitudesListener?.remove()
    }
}
