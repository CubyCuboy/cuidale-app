package com.example.cuidaleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.Billetera
import com.example.cuidaleapp.models.Cobro
import com.example.cuidaleapp.models.MovimientoBilletera
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.utils.FirebaseFunctionsApi
import com.example.cuidaleapp.utils.MontoUtils
import com.example.cuidaleapp.utils.PagoErrorMessages
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Estado de una operación asíncrona del módulo de pagos. */
sealed class OperacionEstado {
    object Idle : OperacionEstado()
    object EnCurso : OperacionEstado()
    data class Error(val message: String) : OperacionEstado()
    object Exito : OperacionEstado()
}

/** Estado de la billetera (lectura de Firestore). */
sealed class BilleteraUiState {
    object Cargando : BilleteraUiState()
    data class Contenido(val billetera: Billetera) : BilleteraUiState()
    data class Error(val message: String) : BilleteraUiState()
}

/**
 * ViewModel de billetera y cobros.
 * Escucha la billetera, sus movimientos, los cobros del usuario y el detalle
 * de un cobro. Solo lectura; las escrituras las hace únicamente el backend.
 */
class BilleteraViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val currentUid get() = auth.currentUser?.uid

    private val _billeteraState = MutableStateFlow<BilleteraUiState>(BilleteraUiState.Cargando)
    val billeteraState: StateFlow<BilleteraUiState> = _billeteraState

    private val _movimientos = MutableStateFlow<List<MovimientoBilletera>>(emptyList())
    val movimientos: StateFlow<List<MovimientoBilletera>> = _movimientos

    private val _cobros = MutableStateFlow<List<Cobro>>(emptyList())
    val cobros: StateFlow<List<Cobro>> = _cobros

    private val _cobroDetalle = MutableStateFlow<Cobro?>(null)
    val cobroDetalle: StateFlow<Cobro?> = _cobroDetalle

    private val _esRepresentante = MutableStateFlow(false)
    val esRepresentante: StateFlow<Boolean> = _esRepresentante

    private val _operacion = MutableStateFlow<OperacionEstado>(OperacionEstado.Idle)
    val operacion: StateFlow<OperacionEstado> = _operacion

    private val _recargaUrl = MutableStateFlow<String?>(null)
    val recargaUrl: StateFlow<String?> = _recargaUrl

    private var billeteraListener: ListenerRegistration? = null
    private var movimientosListener: ListenerRegistration? = null
    private var cobrosListener: ListenerRegistration? = null
    private var cobroDetalleListener: ListenerRegistration? = null

    init {
        iniciarListeners()
    }

    private fun iniciarListeners() {
        val uid = currentUid ?: return
        viewModelScope.launch {
            val rol = try {
                val q = db.collection("usuarios").whereEqualTo("uid", uid).get().await()
                if (q.isEmpty) "" else q.documents[0].getString("rol") ?: ""
            } catch (_: Exception) {
                ""
            }
            _esRepresentante.value = rol == "representante"
            escucharBilletera(uid)
            escucharMovimientos(uid)
            escucharCobros(uid, rol)
        }
    }

    private fun escucharBilletera(uid: String) {
        billeteraListener?.remove()
        billeteraListener = db.collection("billeteras").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    _billeteraState.value = BilleteraUiState.Error("La billetera aún no está disponible.")
                    return@addSnapshotListener
                }
                val b = if ((snap != null) && snap.exists()) {
                    snap.toObject(Billetera::class.java) ?: Billetera(uid = uid)
                } else {
                    Billetera(uid = uid)
                }
                _billeteraState.value = BilleteraUiState.Contenido(b)
            }
    }

    private fun escucharMovimientos(uid: String) {
        movimientosListener?.remove()
        movimientosListener = db.collection("billeteras").document(uid).collection("movimientos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _movimientos.value = snapshot?.toObjects(MovimientoBilletera::class.java) ?: emptyList()
            }
    }

    private fun escucharCobros(uid: String, rol: String) {
        cobrosListener?.remove()
        val campo = if (rol == "cuidador") "cuidadorUid" else "representanteUid"
        cobrosListener = db.collection("cobros").whereEqualTo(campo, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _cobros.value = snapshot?.toObjects(Cobro::class.java) ?: emptyList()
            }
    }

    /** Observable de un cobro específico (por id = solicitudId/turnoId). */
    fun observarCobro(cobroId: String) {
        cobroDetalleListener?.remove()
        cobroDetalleListener = db.collection("cobros").document(cobroId)
            .addSnapshotListener { snap, _ ->
                _cobroDetalle.value =
                    if (snap != null && snap.exists()) snap.toObject(Cobro::class.java) else null
            }
    }

    fun detenerObservacionCobro() {
        cobroDetalleListener?.remove()
        cobroDetalleListener = null
        _cobroDetalle.value = null
    }

    /**
     * Inicia el cobro del turno de un contrato (C20-51).
     * Calcula las horas desde el horario del contrato si no se reciben explícitas.
     */
    fun iniciarCobroTurno(solicitud: SolicitudCuidado, horas: Double? = null, comentario: String = "") {
        if (_operacion.value is OperacionEstado.EnCurso) return
        _operacion.value = OperacionEstado.EnCurso

        val horasTurno = horas ?: MontoUtils.horasDesdeHorario(solicitud.horarioStr) ?: 0.0
        if (horasTurno <= 0.0) {
            _operacion.value = OperacionEstado.Error("No fue posible calcular las horas del turno.")
            return
        }
        if (!MontoUtils.validarTarifaHora(solicitud.tarifaHora)) {
            _operacion.value = OperacionEstado.Error("El contrato no tiene una tarifa horaria válida.")
            return
        }

        viewModelScope.launch {
            val resultado = FirebaseFunctionsApi.iniciarCobroTurno(solicitud.id, horasTurno, comentario)
            resultado.onSuccess {
                _operacion.value = OperacionEstado.Exito
            }.onFailure { e ->
                val codigo = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-')
                val server = (e as? FirebaseFunctionsException)?.message
                _operacion.value = OperacionEstado.Error(PagoErrorMessages.mensaje(codigo, server))
            }
        }
    }

    /** Solicita una recarga de billetera (Checkout Pro de Mercado Pago). */
    fun crearRecarga(monto: Long) {
        if (!MontoUtils.validarMontoRecarga(monto)) {
            _operacion.value = OperacionEstado.Error(
                "El monto debe estar entre ${MontoUtils.formatClp(MontoUtils.MONTO_MIN_RECARGA)} y ${MontoUtils.formatClp(MontoUtils.MONTO_MAX_RECARGA)}.",
            )
            return
        }
        if (_operacion.value is OperacionEstado.EnCurso) return
        _operacion.value = OperacionEstado.EnCurso
        viewModelScope.launch {
            FirebaseFunctionsApi.crearRecarga(monto)
                .onSuccess { url ->
                    _recargaUrl.value = url
                    _operacion.value = OperacionEstado.Exito
                }
                .onFailure { e ->
                    val codigo = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-')
                    val server = (e as? FirebaseFunctionsException)?.message
                    _operacion.value = OperacionEstado.Error(PagoErrorMessages.mensaje(codigo, server))
                }
        }
    }

    fun consumirRecargaUrl() {
        _recargaUrl.value = null
    }

    fun limpiarOperacion() {
        _operacion.value = OperacionEstado.Idle
    }

    override fun onCleared() {
        super.onCleared()
        billeteraListener?.remove()
        movimientosListener?.remove()
        cobrosListener?.remove()
        cobroDetalleListener?.remove()
    }
}
