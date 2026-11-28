package com.example.cuidaleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cuidaleapp.models.Billetera
import com.example.cuidaleapp.models.Cobro
import com.example.cuidaleapp.models.CuentaDestino
import com.example.cuidaleapp.models.MovimientoBilletera
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.utils.FirebaseFunctionsApi
import com.example.cuidaleapp.utils.MontoUtils
import com.example.cuidaleapp.utils.PagoErrorMessages
import com.example.cuidaleapp.utils.RecargaUi
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

/** Estado del flujo de retiro (creación del payout). */
sealed class RetiroUi {
    object Procesando : RetiroUi()
    data class Final(val estado: String, val mensaje: String? = null) : RetiroUi()
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

    private val _recargaActivaId = MutableStateFlow<String?>(null)
    val recargaActivaId: StateFlow<String?> = _recargaActivaId

    private val _cuentasDestino = MutableStateFlow<List<CuentaDestino>>(emptyList())
    val cuentasDestino: StateFlow<List<CuentaDestino>> = _cuentasDestino

    private val _retiroUi = MutableStateFlow<RetiroUi?>(null)
    val retiroUi: StateFlow<RetiroUi?> = _retiroUi

    private val _oauthMpEnProceso = MutableStateFlow(false)
    val oauthMpEnProceso: StateFlow<Boolean> = _oauthMpEnProceso

    // Mensaje transitorio del flujo de Mercado Pago (éxito/error). Se muestra
    // una vez en la pantalla de cuentas y luego se limpia.
    private val _oauthMpFeedback = MutableStateFlow<String?>(null)
    val oauthMpFeedback: StateFlow<String?> = _oauthMpFeedback

    // URL de autorización OAuth (Mercado Pago Connect) para abrir en navegador.
    private val _oauthMpAuthUrl = MutableStateFlow<String?>(null)
    val oauthMpAuthUrl: StateFlow<String?> = _oauthMpAuthUrl

    // Señal para abrir el formulario manual cuando no hay OAuth configurado.
    private val _abrirFormularioMpManual = MutableStateFlow(false)
    val abrirFormularioMpManual: StateFlow<Boolean> = _abrirFormularioMpManual

    // Mensaje transitorio de las cuentas destino (guardar/eliminar/cargar).
    private val _cuentasFeedback = MutableStateFlow<String?>(null)
    val cuentasFeedback: StateFlow<String?> = _cuentasFeedback

    // Estado del overlay de pago/transferencia (reutiliza RecargaUi).
    private val _pagoUi = MutableStateFlow<RecargaUi?>(null)
    val pagoUi: StateFlow<RecargaUi?> = _pagoUi

    private var _retiroPendienteMovimientoId: String? = null

    private val _cargandoLegacy = MutableStateFlow(false)

    private var billeteraListener: ListenerRegistration? = null
    private var cobrosListener: ListenerRegistration? = null
    private var cobroDetalleListener: ListenerRegistration? = null
    private var cuentasListener: ListenerRegistration? = null

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
            escucharCuentasDestino(uid)
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
                    val idPendiente = _retiroPendienteMovimientoId
                    if (idPendiente != null) {
                        val mov = b.movimientos.find { it.id == idPendiente }
                        if (mov != null && mov.estado != "procesando") {
                            _retiroUi.value = RetiroUi.Final(mov.estado)
                            _retiroPendienteMovimientoId = null
                        }
                    }
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

    private fun escucharCuentasDestino(uid: String) {
        cuentasListener?.remove()
        cuentasListener = db.collection("cuentas_bancarias")
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _cuentasFeedback.value = "No se pudieron cargar tus cuentas."
                    return@addSnapshotListener
                }
                _cuentasDestino.value = snapshot?.toObjects(CuentaDestino::class.java) ?: emptyList()
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
                .onSuccess { iniciada ->
                    _recargaActivaId.value = iniciada.recargaId
                    _recargaUrl.value = iniciada.url
                    _operacion.value = OperacionEstado.Exito
                }
                .onFailure { e ->
                    val codigo = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-')
                    val server = (e as? FirebaseFunctionsException)?.message
                    _operacion.value = OperacionEstado.Error(PagoErrorMessages.mensaje(codigo, server))
                }
        }
    }

    /** Registra un destino de retiro (banco o Mercado Pago). */
    fun guardarCuentaDestino(
        tipoDestino: String,
        banco: String? = null,
        tipoCuenta: String? = null,
        numeroCuenta: String? = null,
        titular: String? = null,
        rut: String? = null,
        mpIdentificador: String? = null
    ) {
        viewModelScope.launch {
            FirebaseFunctionsApi.registrarCuentaDestino(
                tipoDestino = tipoDestino,
                banco = banco,
                tipoCuenta = tipoCuenta,
                numeroCuenta = numeroCuenta,
                titular = titular,
                rut = rut,
                mpIdentificador = mpIdentificador
            )
                .onSuccess { _cuentasFeedback.value = "Cuenta guardada correctamente" }
                .onFailure { e ->
                    val codigo = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-')
                    val server = (e as? FirebaseFunctionsException)?.message
                    _cuentasFeedback.value = PagoErrorMessages.mensaje(codigo, server)
                }
        }
    }

    /** Elimina un destino de retiro (solo si es del usuario; regla de Firestore). */
    fun eliminarCuentaDestino(id: String) {
        if (currentUid == null) return
        viewModelScope.launch {
            runCatching {
                db.collection("cuentas_bancarias").document(id).delete().await()
            }.onSuccess {
                _cuentasFeedback.value = "Cuenta eliminada"
            }.onFailure {
                _cuentasFeedback.value = "No se pudo eliminar la cuenta."
            }
        }
    }

    /** Solicita un retiro de billetera hacia una cuenta destino (mínimo 1.000 CLP). */
    fun retirarSaldo(monto: Long, cuentaId: String) {
        if (monto < MontoUtils.MONTO_MIN_RETIRO) {
            _operacion.value = OperacionEstado.Error(
                "El retiro mínimo es ${MontoUtils.formatClp(MontoUtils.MONTO_MIN_RETIRO)}.",
            )
            return
        }
        if (_retiroUi.value != null) return
        _retiroUi.value = RetiroUi.Procesando
        _operacion.value = OperacionEstado.EnCurso
        viewModelScope.launch {
            FirebaseFunctionsApi.retirarSaldo(monto, cuentaId)
                .onSuccess { iniciado ->
                    _retiroPendienteMovimientoId = iniciado.movimientoId
                    when (iniciado.estado) {
                        "aprobada" -> _retiroUi.value = RetiroUi.Final("aprobada")
                        "rechazada" -> _retiroUi.value = RetiroUi.Final(
                            "rechazada",
                            "El retiro fue rechazado por Mercado Pago. Se devolvió el saldo."
                        )
                        else -> Unit // procesando: el webhook lo resolverá
                    }
                    _operacion.value = OperacionEstado.Exito
                }
                .onFailure { e ->
                    val codigo = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-')
                    val server = (e as? FirebaseFunctionsException)?.message
                    _retiroUi.value = RetiroUi.Final("error", PagoErrorMessages.mensaje(codigo, server))
                    _operacion.value = OperacionEstado.Idle
                }
        }
    }

    fun limpiarRetiroUi() {
        _retiroUi.value = null
        _retiroPendienteMovimientoId = null
    }

    /** Inicia la vinculación de Mercado Pago (OAuth Connect externo). */
    fun iniciarVincularMp() {
        if (_oauthMpEnProceso.value) return
        _oauthMpEnProceso.value = true
        viewModelScope.launch {
            FirebaseFunctionsApi.mpOauthIniciar()
                .onSuccess { data ->
                    _oauthMpEnProceso.value = false
                    val authUrl = data["authUrl"].orEmpty()
                    if (authUrl.isNotBlank()) {
                        _oauthMpAuthUrl.value = authUrl
                    } else {
                        // Sin client_id configurado: no hay OAuth; se abre el
                        // formulario manual para ingresar el User ID numérico.
                        _oauthMpFeedback.value = "Vincula tu cuenta ingresando tu User ID numérico."
                        _abrirFormularioMpManual.value = true
                    }
                }
                .onFailure { e ->
                    _oauthMpEnProceso.value = false
                    _oauthMpFeedback.value =
                        if (e is FirebaseFunctionsException) e.message ?: "Error al vincular Mercado Pago."
                        else "Error de conexión al vincular Mercado Pago."
                }
        }
    }

    fun consumirOauthMpAuthUrl() {
        _oauthMpAuthUrl.value = null
    }

    fun consumirAbrirFormularioMpManual() {
        _abrirFormularioMpManual.value = false
    }

    /** Canjea el código OAuth devuelto por el deep link y vincula la cuenta MP. */
    fun finalizarConexionMp(code: String) {
        if (_oauthMpEnProceso.value) return
        _oauthMpEnProceso.value = true
        viewModelScope.launch {
            FirebaseFunctionsApi.mpOauthCanjear(code)
                .onSuccess {
                    _oauthMpEnProceso.value = false
                    _oauthMpFeedback.value = "Mercado Pago conectado correctamente"
                }
                .onFailure { e ->
                    _oauthMpEnProceso.value = false
                    _oauthMpFeedback.value =
                        if (e is FirebaseFunctionsException) e.message ?: "Error al vincular Mercado Pago."
                        else "Error de conexión al vincular Mercado Pago."
                }
        }
    }

    /**
     * Muestra el resultado del flujo OAuth de Mercado Pago al volver del
     * navegador (el canje ya ocurrió en el backend). Con estado "ok" refresca
     * las cuentas destino para reflejar la cuenta MP recién vinculada.
     */
    fun mostrarResultadoOauthMp(estado: String) {
        _oauthMpEnProceso.value = false
        when (estado.lowercase()) {
            "ok" -> {
                _oauthMpFeedback.value = "Cuenta de Mercado Pago vinculada"
                recargarCuentasDestino()
            }
            "cancelado" -> _oauthMpFeedback.value = "Vinculación cancelada"
            else -> _oauthMpFeedback.value = "No se pudo vincular tu cuenta de Mercado Pago. Inténtalo de nuevo."
        }
    }

    /** Recarga las cuentas destino desde Firestore (tras una vinculación OAuth). */
    fun recargarCuentasDestino() {
        val uid = currentUid ?: return
        escucharCuentasDestino(uid)
    }

    fun consumirOauthMpFeedback() {
        _oauthMpFeedback.value = null
    }

    fun consumirCuentasFeedback() {
        _cuentasFeedback.value = null
    }

    /** Desvincula la cuenta de Mercado Pago (elimina destino y limpia conexión). */
    fun desvincularConexionMp() {
        if (_oauthMpEnProceso.value) return
        _oauthMpEnProceso.value = true
        viewModelScope.launch {
            FirebaseFunctionsApi.mpOauthDesvincular()
                .onSuccess {
                    _oauthMpEnProceso.value = false
                    _oauthMpFeedback.value = "Mercado Pago desconectado"
                }
                .onFailure { e ->
                    _oauthMpEnProceso.value = false
                    _oauthMpFeedback.value =
                        if (e is FirebaseFunctionsException) e.message ?: "Error al desconectar Mercado Pago."
                        else "Error de conexión al desconectar Mercado Pago."
                }
        }
    }

    /** Cancela la recarga pendiente (marca como rechazada en Firestore). */
    fun cancelarRecarga(recargaId: String?) {
        if (recargaId.isNullOrBlank()) return
        viewModelScope.launch {
            FirebaseFunctionsApi.cancelarRecarga(recargaId)
        }
    }

    /** Paga un cobro pendiente desde la billetera (Cloud Function `pagarCobro`). */
    fun pagarCobro(cobroId: String) {
        if (_pagoUi.value != null) return
        _pagoUi.value = RecargaUi.Procesando
        viewModelScope.launch {
            FirebaseFunctionsApi.pagarCobro(cobroId)
                .onSuccess {
                    _pagoUi.value = RecargaUi.Final("aprobado")
                }
                .onFailure { e ->
                    val codigo = (e as? FirebaseFunctionsException)?.code?.name?.lowercase()?.replace('_', '-')
                    val server = (e as? FirebaseFunctionsException)?.message
                    _pagoUi.value = RecargaUi.Final("error", PagoErrorMessages.mensaje(codigo, server))
                }
        }
    }

    fun limpiarPagoUi() {
        _pagoUi.value = null
    }

    fun consumirRecargaUrl() {
        _recargaUrl.value = null
    }

    fun limpiarRecargaActiva() {
        _recargaActivaId.value = null
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
        cuentasListener?.remove()
    }
}
