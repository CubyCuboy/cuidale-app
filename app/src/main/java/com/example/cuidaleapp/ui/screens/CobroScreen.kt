package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.MontoUtils
import com.example.cuidaleapp.viewmodel.BilleteraViewModel

/**
 * Detalle de un cobro/turno. En esta fase es solo lectura (desglose y estado);
 * las acciones (confirmar/pagar) llegan en la Fase 6 con el backend de acuerdos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CobroScreen(
    cobroId: String,
    viewModel: BilleteraViewModel,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(cobroId) { viewModel.observarCobro(cobroId) }
    DisposableEffect(Unit) {
        onDispose { viewModel.detenerObservacionCobro() }
    }

    val cobro by viewModel.cobroDetalle.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cobro_titulo), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
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
            val actual = cobro
            if (actual == null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cobro_no_encontrado), color = Color.White)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    HardShadowPanel(
                        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
                        shadowColor = Color.Black.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                MontoUtils.formatClp(actual.monto),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(
                                textoEstadoCobro(actual.estado),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            FilaCobro(stringResource(R.string.cobro_paciente), actual.nombrePaciente)
                            FilaCobro(stringResource(R.string.cobro_cuidador), actual.cuidadorNombre)
                            FilaCobro(stringResource(R.string.cobro_horas), MontoUtils.formatHoras(actual.horas))
                            FilaCobro(stringResource(R.string.cobro_tarifa), MontoUtils.formatClp(actual.tarifaHora))
                            if (actual.comentario.isNotBlank()) {
                                FilaCobro(stringResource(R.string.cobro_comentario_opcional), actual.comentario)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = avisoEstadoCobro(actual.estado),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun FilaCobro(label: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun avisoEstadoCobro(estado: String): String = when (estado) {
    "pendiente_confirmacion_rep" -> "El representante debe confirmar este cobro."
    "pendiente_confirmacion_cui" -> "El cuidador debe confirmar este cobro."
    "acordado" -> "Cobro acordado. El pago se habilitará cuando esté disponible el backend de pagos."
    "en_disputa", "en_conflicto" -> "Este cobro está en disputa/conflicto. Resuélvelo con la otra parte."
    "pagado" -> "Este turno ya fue pagado."
    "rechazado" -> "Este cobro fue rechazado."
    else -> "Estado desconocido."
}
