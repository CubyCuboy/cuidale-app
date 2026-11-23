package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.EstadoCobro
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.RecargaDialog
import com.example.cuidaleapp.ui.components.RedirigiendoMercadoPagoDialog
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.CheckoutLauncher
import com.example.cuidaleapp.utils.MontoUtils
import com.example.cuidaleapp.utils.AppNavigationState
import com.example.cuidaleapp.viewmodel.BilleteraUiState
import com.example.cuidaleapp.viewmodel.BilleteraViewModel
import com.example.cuidaleapp.viewmodel.OperacionEstado

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilleteraScreen(
    viewModel: BilleteraViewModel,
    onVerCobro: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.billeteraState.collectAsState()
    val movimientos by viewModel.movimientos.collectAsState()
    val cobros by viewModel.cobros.collectAsState()
    val esRepresentante by viewModel.esRepresentante.collectAsState()
    val operacion by viewModel.operacion.collectAsState()
    val recargaUrl by viewModel.recargaUrl.collectAsState()
    var showRecarga by remember { mutableStateOf(false) }
    var redirigiendo by remember { mutableStateOf(false) }
    var recargaCancelada by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pagoEstado by AppNavigationState.pagoEstado.collectAsState()

    LaunchedEffect(recargaUrl) {
        recargaUrl?.let {
            redirigiendo = false
            if (!recargaCancelada) {
                CheckoutLauncher.abrir(context, it)
            }
            recargaCancelada = false
            viewModel.consumirRecargaUrl()
        }
    }

    LaunchedEffect(operacion) {
        when (val op = operacion) {
            is OperacionEstado.Error -> {
                redirigiendo = false
                recargaCancelada = false
                android.widget.Toast.makeText(context, op.message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.limpiarOperacion()
            }
            OperacionEstado.Exito -> {
                redirigiendo = false
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
        ) {
            when (val s = state) {
                is BilleteraUiState.Cargando -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is BilleteraUiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is BilleteraUiState.Contenido -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                    ) {
                        // Saldo
                        HardShadowPanel(
                            backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
                            shadowColor = Color.Black.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    stringResource(R.string.billetera_saldo),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    MontoUtils.formatClp(s.billetera.saldo),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(16.dp))
                                HardShadowButton(
                                    text = stringResource(R.string.billetera_recargar),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                    shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    icon = Icons.Default.Add,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    onClick = { showRecarga = true }
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            stringResource(R.string.billetera_cobros_activos),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))

                        val cobrosVisibles = cobros.filter {
                            it.estadoCobro != EstadoCobro.PAGADO && it.estadoCobro != EstadoCobro.RECHAZADO
                        }
                        if (cobrosVisibles.isEmpty()) {
                            Text(
                                stringResource(R.string.billetera_sin_cobros),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        } else {
                            cobrosVisibles.forEach { cobro ->
                                HardShadowPanel(
                                    backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
                                    shadowColor = Color.Black.copy(alpha = 0.08f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onVerCobro(cobro.id) }
                                ) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                cobro.nombrePaciente.ifEmpty { "Servicio" },
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                MontoUtils.formatClp(cobro.monto),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            textoEstadoCobro(cobro.estado),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            stringResource(R.string.billetera_movimientos),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))

                        if (movimientos.isEmpty()) {
                            Text(
                                stringResource(R.string.billetera_sin_movimientos),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        } else {
                            movimientos.forEach { mov ->
                                HardShadowPanel(
                                    backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
                                    shadowColor = Color.Black.copy(alpha = 0.05f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                mov.descripcion.ifEmpty { mov.tipo },
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                mov.timestamp?.toDate()?.let { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(it) } ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            MontoUtils.formatClp(mov.monto),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            color = if (mov.monto < 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }
    }

    LaunchedEffect(pagoEstado) {
        when (pagoEstado) {
            "aprobado" -> snackbarHostState.showSnackbar("¡Pago aprobado! Tu saldo se actualizará en unos momentos.")
            "rechazado" -> snackbarHostState.showSnackbar("El pago fue rechazado. Inténtalo nuevamente.")
            "pendiente" -> snackbarHostState.showSnackbar("Tu pago está en proceso. El saldo se acreditará cuando Mercado Pago confirme.")
            else -> Unit
        }
        if (pagoEstado != null) AppNavigationState.setPagoEstado(null)
    }

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
            onConfirm = { monto ->
                recargaCancelada = false
                redirigiendo = true
                viewModel.crearRecarga(monto)
                showRecarga = false
            },
            onDismiss = { showRecarga = false }
        )
    }

    if (redirigiendo) {
        RedirigiendoMercadoPagoDialog(
            onCancel = {
                redirigiendo = false
                recargaCancelada = true
            }
        )
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
