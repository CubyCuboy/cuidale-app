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
 * La billetera vive en la colección `billetera/{uid}` con el saldo y un arreglo
 * de movimientos embebidos. Este ViewModel solo lee; las escrituras las hace el
 * backend (Cloud Functions).
 */
class BilleteraViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val currentUid get() = auth.currentUser?.uid

    private val _billeteraState = MutableStateFlow<BilleteraUiState>(BilleteraUiState.Cargando)
    val billeteraState: StateFlow<BilleteraUiState> = _billeteraState

    private val _transferencias = MutableStateFlow<List<MovimientoBilletera>>(emptyList())
    val transferencias: StateFlow<List<MovimientoBilletera>> = _transferencias

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

    private val _cargandoLegacy = MutableStateFlow(false)

    private var billeteraListener: ListenerRegistration? = null
    private var cobrosListener: ListenerRegistration? = null
    private var cobroDetalleListener: ListenerRegistration? = null

    init {
        iniciarListeners()
    }

    /** Reintenta cargar la billetera y sus datos (para la vista de error). */
    fun recargar() {
        _billeteraState.value = BilleteraUiState.Cargando
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
            escucharCobros(uid, rol)
        }
    }

    private fun escucharBilletera(uid: String) {
        billeteraListener?.remove()
        billeteraListener = db.collection("billetera").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    _billeteraState.value = BilleteraUiState.Error("La billetera aún no está disponible.")
                    return@addSnapshotListener
                }
                val existeNuevo = snap != null && snap.exists()
                val b = if (existeNuevo) {
                    snap.toObject(Billetera::class.java) ?: Billetera(uid = uid)
                } else {
                    Billetera(uid = uid)
                }
                val tieneDatos = b.saldo != 0L || b.movimientos.isNotEmpty()
                if (existeNuevo && tieneDatos) {
                    _billeteraState.value = BilleteraUiState.Contenido(b)
                    _transferencias.value = ordenarMovimientos(b.movimientos)
                } else {
                    // El documento nuevo (billetera/{uid}) aún no tiene datos: se
                    // intenta leer de las colecciones anteriores (billeteras / transferencias)
                    // para no mostrar la billetera vacía durante la migración.
                    cargarDatosLegacy(uid)
                }
            }
    }

    private fun cargarDatosLegacy(uid: String) {
        if (_cargandoLegacy.value) return
        _cargandoLegacy.value = true
        viewModelScope.launch {
            try {
                var saldo = 0L
                runCatching {
                    db.collection("billeteras").document(uid).get().await()
                }.onSuccess { doc ->
                    if (doc.exists()) saldo = doc.getLong("saldo") ?: 0L
                }

                val transSnap = runCatching {
                    db.collection("transferencias").whereEqualTo("uid", uid).limit(100).get().await()
                }.getOrNull()

                val items = transSnap?.documents?.mapNotNull { d ->
                    val mov = d.toObject(MovimientoBilletera::class.java) ?: return@mapNotNull null
                    mov.copy(
                        tipoMovimiento = "recarga",
                        timestamp = d.getTimestamp("createdAt") ?: mov.timestamp
                    )
                } ?: emptyList()

                val movs = ordenarMovimientos(items)
                _billeteraState.value = BilleteraUiState.Contenido(
                    Billetera(uid = uid, saldo = saldo, movimientos = movs)
                )
                _transferencias.value = movs
            } catch (_: Exception) {
            } finally {
                _cargandoLegacy.value = false
            }
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

    /** Solicita un retiro de billetera (mínimo 1.000 CLP). */
    fun retirarSaldo(monto: Long) {
        if (monto < MontoUtils.MONTO_MIN_RETIRO) {
            _operacion.value = OperacionEstado.Error(
                "El retiro mínimo es ${MontoUtils.formatClp(MontoUtils.MONTO_MIN_RETIRO)}.",
            )
            return
        }
        if (_operacion.value is OperacionEstado.EnCurso) return
        _operacion.value = OperacionEstado.EnCurso
        viewModelScope.launch {
            FirebaseFunctionsApi.retirarSaldo(monto)
                .onSuccess {
                    _operacion.value = OperacionEstado.Exito
                }
                .onFailure { e ->
                    val codigo = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-')
                    val server = (e as? FirebaseFunctionsException)?.message
                    _operacion.value = OperacionEstado.Error(PagoErrorMessages.mensaje(codigo, server))
                }
        }
    }

    /** Paga un cobro pendiente desde la billetera (Cloud Function `pagarCobro`). */
    fun pagarCobro(cobroId: String) {
        if (_operacion.value is OperacionEstado.EnCurso) return
        _operacion.value = OperacionEstado.EnCurso
        viewModelScope.launch {
            FirebaseFunctionsApi.pagarCobro(cobroId)
                .onSuccess {
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

    private fun ordenarMovimientos(items: List<MovimientoBilletera>): List<MovimientoBilletera> {
        return items.sortedWith(
            compareByDescending<MovimientoBilletera> { it.timestamp?.seconds ?: -1L }
                .thenByDescending { it.timestamp?.nanoseconds ?: 0L }
        )
    }

    override fun onCleared() {
        super.onCleared()
        billeteraListener?.remove()
        cobrosListener?.remove()
        cobroDetalleListener?.remove()
    }
}
