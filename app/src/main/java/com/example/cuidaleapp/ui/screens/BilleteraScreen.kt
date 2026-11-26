package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.Billetera
import com.example.cuidaleapp.models.Cobro
import com.example.cuidaleapp.models.CuentaDestino
import com.example.cuidaleapp.models.EstadoCobro
import com.example.cuidaleapp.models.MovimientoBilletera
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.LogoCuenta
import com.example.cuidaleapp.ui.components.PantallaPagarCobros
import com.example.cuidaleapp.ui.components.PagoOverlay
import com.example.cuidaleapp.ui.components.RecargaDialog
import com.example.cuidaleapp.ui.components.RecargaOverlay
import com.example.cuidaleapp.ui.components.RetiroOverlay
import com.example.cuidaleapp.ui.components.TransferenciaCard
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.utils.CheckoutLauncher
import com.example.cuidaleapp.utils.MontoUtils
import com.example.cuidaleapp.utils.RecargaUi
import com.example.cuidaleapp.viewmodel.BilleteraUiState
import com.example.cuidaleapp.viewmodel.BilleteraViewModel
import com.example.cuidaleapp.viewmodel.OperacionEstado
import com.example.cuidaleapp.viewmodel.RetiroUi
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilleteraScreen(
    viewModel: BilleteraViewModel,
    onVerCobro: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onGestionarCuentas: () -> Unit = {},
    onVerTransferencias: () -> Unit
) {
    val state by viewModel.billeteraState.collectAsState()
    val transferencias by viewModel.transferencias.collectAsState()
    val cobros by viewModel.cobros.collectAsState()
    val esRepresentante by viewModel.esRepresentante.collectAsState()
    val operacion by viewModel.operacion.collectAsState()
    val recargaUrl by viewModel.recargaUrl.collectAsState()

    var showRecarga by remember { mutableStateOf(false) }
    var showRetiro by remember { mutableStateOf(false) }
    var showPagar by remember { mutableStateOf(false) }
    var saldoVisible by remember { mutableStateOf(true) }
    var ultimoMontoRecarga by remember { mutableStateOf<Long?>(null) }
    var recargaCancelada by remember { mutableStateOf(false) }
    var tabAbierta by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pagoEstado by AppNavigationState.pagoEstado.collectAsState()
    val recargaUi by AppNavigationState.recargaUi.collectAsState()
    val recargaActivaId by viewModel.recargaActivaId.collectAsState()
    val cuentasDestino by viewModel.cuentasDestino.collectAsState()
    val retiroUi by viewModel.retiroUi.collectAsState()
    val pagoUi by viewModel.pagoUi.collectAsState()
    val oauthMpEnProceso by viewModel.oauthMpEnProceso.collectAsState()

    val currentTabAbierta by rememberUpdatedState(tabAbierta)

    // Si la app vuelve a primer plano después de abrir el checkout y no hubo
    // retorno del deep link, el usuario salió del proceso: se cancela.
    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentTabAbierta) {
                tabAbierta = false
                val uiActual = AppNavigationState.recargaUi.value
                val pagoActual = AppNavigationState.pagoEstado.value
                if (uiActual is RecargaUi.Procesando && pagoActual == null) {
                    // Antes de marcar como cancelado, verificamos si el webhook ya
                    // acreditó el pago (el saldo refleja la recarga aprobada).
                    val activaId = viewModel.recargaActivaId.value
                    val bs = viewModel.billeteraState.value
                    val aprobada = activaId != null && bs is BilleteraUiState.Contenido &&
                        bs.billetera.movimientos.any {
                            it.id == activaId && it.estado == "aprobada"
                        }
                    if (!aprobada) viewModel.cancelarRecarga(activaId)
                    AppNavigationState.finalizarRecarga(if (aprobada) "aprobado" else "cancelado")
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(recargaUrl) {
        recargaUrl?.let {
            if (!recargaCancelada) {
                tabAbierta = true
                val abierto = CheckoutLauncher.abrir(context, it)
                if (abierto) {
                    AppNavigationState.marcarProcesando()
                } else {
                    AppNavigationState.finalizarRecarga(
                        "error",
                        "No se pudo abrir el portal de pago."
                    )
                }
            }
            recargaCancelada = false
            viewModel.consumirRecargaUrl()
        }
    }

    LaunchedEffect(operacion) {
        when (val op = operacion) {
            is OperacionEstado.Error -> {
                if (recargaUi != null) {
                    AppNavigationState.finalizarRecarga("error", op.message)
                } else {
                    snackbarHostState.showSnackbar(op.message)
                }
                viewModel.limpiarOperacion()
            }
            OperacionEstado.Exito -> {
                viewModel.limpiarOperacion()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.billetera_titulo), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .background(
                    Brush.verticalGradient(
                        listOf(CuidaleThemeExtras.gradient.top, CuidaleThemeExtras.gradient.bottom)
                    )
                )
        ) {
            when (val s = state) {
                is BilleteraUiState.Cargando -> PantallaCargando()
                is BilleteraUiState.Error -> PantallaErrorBilletera(
                    mensaje = s.message,
                    onReintentar = { viewModel.recargar() }
                )
                is BilleteraUiState.Contenido -> {
                    ContenidoBilletera(
                        billetera = s.billetera,
                        transferencias = transferencias,
                        cobros = cobros,
                        esRepresentante = esRepresentante,
                        operacion = operacion,
                        saldoVisible = saldoVisible,
                        onToggleSaldo = { saldoVisible = !saldoVisible },
                        onRecargar = { showRecarga = true },
                        onRetirar = { showRetiro = true },
                        onPagar = { showPagar = true },
                        onVerCobro = onVerCobro,
                        onVerTransferencias = onVerTransferencias,
                        onGestionarCuentas = onGestionarCuentas
                    )
                }
            }
        }
    }

    LaunchedEffect(pagoEstado) {
        val est = pagoEstado ?: return@LaunchedEffect
        if (recargaUi != null) {
            AppNavigationState.finalizarRecarga(est)
        } else {
            when (est) {
                "aprobado" -> snackbarHostState.showSnackbar("¡Pago aprobado! Tu saldo se actualizará en unos momentos.")
                "rechazado" -> snackbarHostState.showSnackbar("El pago fue rechazado. Inténtalo nuevamente.")
                "cancelado" -> snackbarHostState.showSnackbar("El pago fue cancelado.")
                "pendiente" -> snackbarHostState.showSnackbar("Tu pago está en proceso. El saldo se acreditará cuando Mercado Pago confirme.")
            }
        }
        AppNavigationState.setPagoEstado(null)
    }

    // Respaldo: aunque el navegador no regrese solo al checkout, si el webhook
    // ya acreditó el pago (movimiento aprobada) la pantalla pasa a éxito.
    LaunchedEffect(state, recargaUi, recargaActivaId) {
        val ui = recargaUi
        if (ui is RecargaUi.Procesando) {
            val activaId = recargaActivaId
            val cont = state as? BilleteraUiState.Contenido
            val aprobada = activaId != null && cont != null &&
                cont.billetera.movimientos.any {
                    it.id == activaId && it.estado == "aprobada"
                }
            if (aprobada) {
                AppNavigationState.finalizarRecarga("aprobado")
            }
        }
    }

    // Estado final aprobado: se autociella tras mostrar el check animado.
    LaunchedEffect(recargaUi) {
        val ui = recargaUi
        if (ui is RecargaUi.Final && ui.estado == "aprobado") {
            delay(2200)
            AppNavigationState.limpiarRecarga()
            viewModel.limpiarRecargaActiva()
        }
    }

    val saldoActual = (state as? BilleteraUiState.Contenido)?.billetera?.saldo ?: 0L

    if (showRecarga) {
        val deudas = if (esRepresentante) {
            cobros.filter {
                it.estadoCobro != EstadoCobro.PAGADO && it.estadoCobro != EstadoCobro.RECHAZADO
            }.map { it.monto }
        } else {
            emptyList()
        }
        RecargaDialog(
            deudas = deudas,
            saldoActual = saldoActual,
            onConfirm = { monto ->
                ultimoMontoRecarga = monto
                recargaCancelada = false
                AppNavigationState.iniciarRecarga()
                viewModel.crearRecarga(monto)
                showRecarga = false
            },
            onDismiss = { showRecarga = false }
        )
    }

    if (showRetiro) {
        PantallaRetiro(
            cuentas = cuentasDestino,
            saldoActual = saldoActual,
            esRepresentante = esRepresentante,
            onConfirm = { monto, cuentaId ->
                showRetiro = false
                viewModel.retirarSaldo(monto, cuentaId)
            },
            onGestionarCuentas = {
                showRetiro = false
                onGestionarCuentas()
            },
            onDismiss = { showRetiro = false }
        )
    }

    retiroUi?.let { ui ->
        RetiroOverlay(
            ui = ui.aRecargaUi() ?: return@let,
            onReintentar = { viewModel.limpiarRetiroUi() },
            onCerrar = { viewModel.limpiarRetiroUi() }
        )
    }

    if (showPagar) {
        PantallaPagarCobros(
            cobros = cobros,
            saldoActual = saldoActual,
            onPagar = { cobroId ->
                showPagar = false
                viewModel.pagarCobro(cobroId)
            },
            onVerCobro = { cobroId ->
                showPagar = false
                onVerCobro(cobroId)
            },
            onDismiss = { showPagar = false }
        )
    }

    pagoUi?.let { ui ->
        PagoOverlay(
            ui = ui,
            onReintentar = {
                viewModel.limpiarPagoUi()
                showPagar = true
            },
            onCerrar = { viewModel.limpiarPagoUi() }
        )
    }

    recargaUi?.let { ui ->
        RecargaOverlay(
            ui = ui,
            onCancel = {
                viewModel.cancelarRecarga(viewModel.recargaActivaId.value)
                recargaCancelada = true
                AppNavigationState.limpiarRecarga()
                viewModel.limpiarRecargaActiva()
            },
            onReintentar = {
                val monto = ultimoMontoRecarga
                if (monto != null) {
                    // Reintentar con el mismo monto, sin volver al formulario.
                    AppNavigationState.limpiarRecarga()
                    recargaCancelada = false
                    AppNavigationState.iniciarRecarga()
                    viewModel.crearRecarga(monto)
                } else {
                    AppNavigationState.limpiarRecarga()
                    showRecarga = true
                }
            },
            onCerrar = {
                AppNavigationState.limpiarRecarga()
                viewModel.limpiarRecargaActiva()
            }
        )
    }
}

@Composable
private fun PantallaCargando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.billetera_cargando),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PantallaErrorBilletera(mensaje: String, onReintentar: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text(
                mensaje,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            HardShadowButton(
                text = stringResource(R.string.billetera_pago_reintentar),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                icon = Icons.Default.Refresh,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                onClick = onReintentar
            )
        }
    }
}

@Composable
private fun ContenidoBilletera(
    billetera: Billetera,
    transferencias: List<MovimientoBilletera>,
    cobros: List<Cobro>,
    esRepresentante: Boolean,
    operacion: OperacionEstado,
    saldoVisible: Boolean,
    onToggleSaldo: () -> Unit,
    onRecargar: () -> Unit,
    onRetirar: () -> Unit,
    onPagar: () -> Unit,
    onVerCobro: (String) -> Unit,
    onGestionarCuentas: () -> Unit,
    onVerTransferencias: () -> Unit
) {
    val opEnCurso = operacion is OperacionEstado.EnCurso
    val errorOperacion = operacion as? OperacionEstado.Error
    val cobrosVisibles = cobros.filter {
        it.estadoCobro != EstadoCobro.PAGADO && it.estadoCobro != EstadoCobro.RECHAZADO
    }
    val cobrosPendientesPago = cobros.filter { it.estadoCobro == EstadoCobro.PENDIENTE }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TarjetaSaldo(
                saldo = billetera.saldo,
                visible = saldoVisible,
                onToggle = onToggleSaldo
            )
        }

        item {
            // Acción principal: Pagar / Transferir (solo representante con pendientes).
            if (esRepresentante && cobrosPendientesPago.isNotEmpty()) {
                HardShadowButton(
                    text = stringResource(R.string.billetera_pagar_transferir),
                    containerColor = CuidaleThemeExtras.customColors.success,
                    contentColor = Color.White,
                    shadowColor = CuidaleThemeExtras.customColors.success.copy(alpha = 0.3f),
                    icon = Icons.AutoMirrored.Filled.Send,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = onPagar
                )
                Spacer(Modifier.height(10.dp))
            }
            // Regla de negocio: solo el representante carga saldo; el cuidador
            // únicamente puede retirar (el pago de turnos lo hace el representante).
            if (esRepresentante) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HardShadowButton(
                        text = stringResource(R.string.billetera_recargar),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        icon = Icons.Default.AddCircle,
                        modifier = Modifier.weight(1f).height(50.dp),
                        onClick = onRecargar
                    )
                    HardShadowButton(
                        text = stringResource(R.string.billetera_retirar),
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shadowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                        icon = Icons.Default.ArrowUpward,
                        modifier = Modifier.weight(1f).height(50.dp),
                        onClick = onRetirar
                    )
                }
            } else {
                HardShadowButton(
                    text = stringResource(R.string.billetera_retirar),
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shadowColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    icon = Icons.Default.ArrowUpward,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = onRetirar
                )
            }
        }

        item {
            HardShadowButton(
                text = "Mis cuentas",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shadowColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                icon = Icons.Default.AccountBalanceWallet,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                onClick = onGestionarCuentas
            )
        }

        if (opEnCurso) {
            item { TarjetaProcesando() }
        }

        if (errorOperacion != null) {
            item {
                TarjetaErrorOperacion(errorOperacion.message) {
                    // El ViewModel limpia la operación automáticamente.
                }
            }
        }

        item {
            Text(
                stringResource(R.string.billetera_cobros_activos),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (cobrosVisibles.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.billetera_sin_cobros),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        } else {
            items(cobrosVisibles, key = { it.id }) { cobro ->
                TarjetaCobro(cobro, onClick = { onVerCobro(cobro.id) })
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.billetera_ultimas_transacciones),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                TextButton(onClick = onVerTransferencias) {
                    Text(
                        stringResource(R.string.billetera_ver_transferencias),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (transferencias.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.transferencias_vacias),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        } else {
            items(transferencias.take(4), key = { it.id.ifBlank { it.hashCode().toString() } }) { mov ->
                TransferenciaCard(mov)
            }
            if (transferencias.size > 4) {
                item {
                    OutlinedButton(
                        onClick = onVerTransferencias,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.billetera_ver_transferencias),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun TarjetaSaldo(saldo: Long, visible: Boolean, onToggle: () -> Unit) {
    val custom = CuidaleThemeExtras.customColors
    HardShadowPanel(
        backgroundColor = custom.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.12f),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.billetera_saldo),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.billetera_actualizacion_tiempo_real),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (visible) MontoUtils.formatClp(saldo) else "$ ••••",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TarjetaProcesando() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CuidaleThemeExtras.customColors.panelBackground
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    stringResource(R.string.billetera_procesando_titulo),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    stringResource(R.string.billetera_procesando_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TarjetaErrorOperacion(mensaje: String, onCerrar: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                mensaje,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCerrar) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.billetera_pago_cerrar),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TarjetaCobro(cobro: Cobro, onClick: () -> Unit) {
    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.08f),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cobro.nombrePaciente.ifEmpty { "Servicio" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    MontoUtils.formatClp(cobro.monto),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    textoEstadoCobro(cobro.estado),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

fun textoEstadoCobro(estado: String): String = when (estado) {
    "pendiente_confirmacion_rep", "pendiente_confirmacion_cui" -> "EN CONFIRMACIÓN"
    "acordado" -> "LISTO PARA PAGAR"
    "en_disputa" -> "EN DISPUTA"
    "en_conflicto" -> "EN CONFLICTO"
    "pagado" -> "PAGADO"
    "rechazado" -> "RECHAZADO"
    else -> estado.uppercase()
}

private const val COMISION_RETIRO_PCT = 0.02
private const val COMISION_RETIRO_TOPE = 5000L

private fun calcularComisionLocal(monto: Long): Long =
    minOf(kotlin.math.round(monto * COMISION_RETIRO_PCT).toLong(), COMISION_RETIRO_TOPE)

private fun RetiroUi.aRecargaUi(): RecargaUi? = when (this) {
    is RetiroUi.Procesando -> RecargaUi.Procesando
    is RetiroUi.Final -> RecargaUi.Final(estado, mensaje)
}

@Composable
private fun PantallaRetiro(
    cuentas: List<CuentaDestino>,
    saldoActual: Long,
    esRepresentante: Boolean,
    onConfirm: (Long, String) -> Unit,
    onGestionarCuentas: () -> Unit,
    onDismiss: () -> Unit
) {
    var paso by remember { mutableStateOf(0) } // 0 = elegir cuenta, 1 = monto
    var cuentaSel by remember { mutableStateOf<CuentaDestino?>(cuentas.firstOrNull()) }
    var montoStr by remember { mutableStateOf("") }
    val monto = MontoUtils.parseMonto(montoStr)
    val valido = monto != null && monto >= MontoUtils.MONTO_MIN_RETIRO &&
        monto <= MontoUtils.MONTO_MAX_RETIRO_POR_CARGO && cuentaSel != null
    // La comisión por retiro aplica solo al cuidador; el representante retira sin comisión.
    val comision = if (esRepresentante) 0L else (monto?.let(::calcularComisionLocal) ?: 0L)
    val totalDebito = (monto ?: 0L) + comision

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (paso == 0) "Elegir cuenta para retirar" else "Monto a retirar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (paso == 0) {
                    if (cuentas.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "No tienes cuentas de destino.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onGestionarCuentas) {
                                Text("Ir a Mis cuentas")
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(cuentas, key = { it.id }) { c ->
                                val marcada = cuentaSel?.id == c.id
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (marcada) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth().clickable { cuentaSel = c }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LogoCuenta(c)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = if (c.tipoDestino == CuentaDestino.TIPO_MP) "Mercado Pago" else c.bancoNombre.ifEmpty { "Banco" },
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = c.mascara,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (marcada) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HardShadowButton(
                            text = "Continuar",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            enabled = cuentaSel != null,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            onClick = { paso = 1 }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        cuentaSel?.let { c ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LogoCuenta(c, size = 36.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = if (c.tipoDestino == CuentaDestino.TIPO_MP) "Mercado Pago" else c.bancoNombre.ifEmpty { "Banco" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = c.mascara,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = "Máximo por retiro: ${MontoUtils.formatClp(MontoUtils.MONTO_MAX_RETIRO_POR_CARGO)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Hasta ${MontoUtils.RETIROS_MAX_POR_DIA} retiros al día (incluye comisiones).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = montoStr,
                            onValueChange = { montoStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            label = { Text("Monto (CLP)") },
                            leadingIcon = {
                                Text(
                                    text = "$",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            },
                            isError = montoStr.isNotEmpty() && monto != null &&
                                (monto < MontoUtils.MONTO_MIN_RETIRO || monto > MontoUtils.MONTO_MAX_RETIRO_POR_CARGO),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (monto != null && monto > MontoUtils.MONTO_MAX_RETIRO_POR_CARGO) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "El monto supera el máximo permitido por retiro.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (monto != null) {
                            Spacer(Modifier.height(12.dp))
                            if (esRepresentante) {
                                Text(
                                    text = "Sin comisión para representantes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Comisión (2%, tope $5.000): ${MontoUtils.formatClp(comision)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Total a debitar: ${MontoUtils.formatClp(totalDebito)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (totalDebito > saldoActual) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.billetera_retiro_saldo_insuficiente),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val presets = listOf(1_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
                        presets.chunked(3).forEach { fila ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                fila.forEach { m ->
                                    FilterChip(
                                        selected = monto == m,
                                        onClick = { montoStr = MontoUtils.formatClp(m) },
                                        label = { Text(MontoUtils.formatClp(m)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        HardShadowButton(
                            text = "Confirmar retiro",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            enabled = valido && totalDebito <= saldoActual,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            onClick = { monto?.let { onConfirm(it, cuentaSel!!.id) } }
                        )
                        TextButton(
                            onClick = { paso = 0 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cambiar cuenta", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

