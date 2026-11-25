package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.Billetera
import com.example.cuidaleapp.models.Cobro
import com.example.cuidaleapp.models.EstadoCobro
import com.example.cuidaleapp.models.MovimientoBilletera
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.PantallaPagarCobros
import com.example.cuidaleapp.ui.components.RecargaDialog
import com.example.cuidaleapp.ui.components.RecargaOverlay
import com.example.cuidaleapp.ui.components.TransferenciaCard
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.utils.CheckoutLauncher
import com.example.cuidaleapp.utils.MontoUtils
import com.example.cuidaleapp.utils.RecargaUi
import com.example.cuidaleapp.viewmodel.BilleteraUiState
import com.example.cuidaleapp.viewmodel.BilleteraViewModel
import com.example.cuidaleapp.viewmodel.OperacionEstado
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilleteraScreen(
    viewModel: BilleteraViewModel,
    onVerCobro: (String) -> Unit,
    onNavigateBack: () -> Unit,
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
    var mostrarExitoRetiro by remember { mutableStateOf(false) }
    var mostrarExitoPago by remember { mutableStateOf(false) }
    var recargaCancelada by remember { mutableStateOf(false) }
    var tabAbierta by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pagoEstado by AppNavigationState.pagoEstado.collectAsState()
    val recargaUi by AppNavigationState.recargaUi.collectAsState()

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
                    AppNavigationState.finalizarRecarga("cancelado")
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
                when {
                    mostrarExitoRetiro -> {
                        snackbarHostState.showSnackbar("Solicitud de retiro registrada.")
                        mostrarExitoRetiro = false
                    }
                    mostrarExitoPago -> {
                        snackbarHostState.showSnackbar("Pago realizado correctamente.")
                        mostrarExitoPago = false
                        showPagar = false
                    }
                }
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
                        onVerTransferencias = onVerTransferencias
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

    // Estado final aprobado: se autociella tras mostrar el check animado.
    LaunchedEffect(recargaUi) {
        val ui = recargaUi
        if (ui is RecargaUi.Final && ui.estado == "aprobado") {
            delay(2200)
            AppNavigationState.limpiarRecarga()
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
        RetiroDialog(
            onConfirm = { monto ->
                mostrarExitoRetiro = true
                viewModel.retirarSaldo(monto)
                showRetiro = false
            },
            onDismiss = { showRetiro = false }
        )
    }

    if (showPagar) {
        PantallaPagarCobros(
            cobros = cobros,
            saldoActual = saldoActual,
            onPagar = { cobroId ->
                mostrarExitoPago = true
                viewModel.pagarCobro(cobroId)
            },
            onVerCobro = { cobroId ->
                showPagar = false
                onVerCobro(cobroId)
            },
            onDismiss = { showPagar = false }
        )
    }

    recargaUi?.let { ui ->
        RecargaOverlay(
            ui = ui,
            onCancel = {
                recargaCancelada = true
                AppNavigationState.limpiarRecarga()
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
            onCerrar = { AppNavigationState.limpiarRecarga() }
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
                OutlinedButton(
                    onClick = onRetirar,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.billetera_retirar), fontWeight = FontWeight.Bold)
                }
            }
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

/**
 * Diálogo de retiro de billetera. Mínimo $1.000 CLP con montos rápidos.
 */
@Composable
private fun RetiroDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var montoStr by remember { mutableStateOf("") }
    val monto = MontoUtils.parseMonto(montoStr)
    val valido = monto != null && monto >= MontoUtils.MONTO_MIN_RETIRO
    val presets = listOf(1_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.billetera_retirar_titulo), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = montoStr,
                    onValueChange = { montoStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(R.string.billetera_monto)) },
                    placeholder = {
                        Text(
                            text = "Ej: 10.000",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        )
                    },
                    leadingIcon = {
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    },
                    isError = montoStr.isNotEmpty() && !valido,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (montoStr.isNotEmpty() && !valido) {
                    Text(
                        text = stringResource(R.string.billetera_retiro_invalido),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { monto?.let(onConfirm) },
                enabled = valido
            ) { Text(stringResource(R.string.billetera_confirmar_retiro)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        }
    )
}
