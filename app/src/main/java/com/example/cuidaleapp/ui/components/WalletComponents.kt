package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.utils.MontoUtils

/**
 * Diálogo del cuidador para finalizar y cobrar un turno (C20-51).
 * Muestra el desglose (horas × tarifa = monto), permite ajustar horas y
 * agregar un comentario/observaciones. Al confirmar, el ViewModel llama a
 * `iniciarCobroTurno` (el backend crea el turno y el cobro borrador).
 */
@Composable
fun IniciarCobroTurnoDialog(
    solicitud: SolicitudCuidado,
    onConfirm: (horas: Double, comentario: String) -> Unit,
    onDismiss: () -> Unit
) {
    val horasIniciales = remember(solicitud.id) {
        MontoUtils.horasDesdeHorario(solicitud.horarioStr) ?: 8.0
    }
    var horasStr by remember(solicitud.id) { mutableStateOf(MontoUtils.formatHoras(horasIniciales)) }
    var comentario by remember { mutableStateOf("") }

    val horas = MontoUtils.parseHoras(horasStr)
    val tarifa = solicitud.tarifaHora
    val monto = horas?.let { MontoUtils.calcularMonto(tarifa, it) } ?: 0L
    val horasValidas = horas?.let { MontoUtils.validarHoras(it) } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cobro_finalizar_y_cobrar), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.cobro_resumen_turno),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = horasStr,
                    onValueChange = { horasStr = it.filter { c -> c.isDigit() || (c == ',' || c == '.') } },
                    label = { Text(stringResource(R.string.cobro_horas)) },
                    placeholder = { Text(stringResource(R.string.cobro_horas_placeholder)) },
                    isError = horasStr.isNotEmpty() && !horasValidas,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text(stringResource(R.string.cobro_tarifa), style = MaterialTheme.typography.labelSmall)
                        Text(MontoUtils.formatClp(tarifa), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(stringResource(R.string.cobro_monto), style = MaterialTheme.typography.labelSmall)
                        Text(MontoUtils.formatClp(monto), fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = stringResource(R.string.cobro_aviso_cambios),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = comentario,
                    onValueChange = { comentario = it },
                    label = { Text(stringResource(R.string.cobro_comentario_opcional)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (horasValidas) onConfirm(horas, comentario.trim()) },
                enabled = horasValidas && tarifa > 0L
            ) {
                Text(stringResource(R.string.cobro_confirmar))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        }
    )
}

/**
 * Diálogo de recarga de billetera (Checkout Pro de Mercado Pago).
 * Validación de monto entre 1.000 y 1.000.000 CLP.
 */
@Composable
fun RecargaDialog(
    montoInicial: Long? = null,
    deudas: List<Long> = emptyList(),
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var montoStr by remember(montoInicial) {
        mutableStateOf(montoInicial?.let { MontoUtils.formatClp(it) } ?: "")
    }
    val monto = MontoUtils.parseMonto(montoStr)
    val valido = monto?.let { MontoUtils.validarMontoRecarga(it) } == true

    val montosRapidos = listOf(10_000L, 20_000L, 30_000L, 50_000L, 100_000L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.billetera_recargar), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = montoStr,
                    onValueChange = { montoStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(R.string.billetera_monto)) },
                    placeholder = { Text("Ej: 20.000") },
                    isError = montoStr.isNotEmpty() && !valido,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (montoStr.isNotEmpty() && !valido) {
                    Text(
                        text = stringResource(R.string.billetera_monto_invalido),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Montos rápidos: de 10.000 a 100.000.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    montosRapidos.take(3).forEach { m ->
                        MontoRapidoChip(m, isSelected = monto == m) { montoStr = MontoUtils.formatClp(m) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    montosRapidos.drop(3).forEach { m ->
                        MontoRapidoChip(m, isSelected = monto == m) { montoStr = MontoUtils.formatClp(m) }
                    }
                }

                // Si hay deuda pendiente, botón para cargar ese monto directamente.
                deudas.filter { it > 0L }.forEach { deuda ->
                    OutlinedButton(
                        onClick = { montoStr = MontoUtils.formatClp(deuda) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.billetera_pagar_deuda, MontoUtils.formatClp(deuda)),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.billetera_aviso_mp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valido) onConfirm(monto) },
                enabled = valido
            ) { Text(stringResource(R.string.billetera_confirmar_pagar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        }
    )
}

@Composable
private fun MontoRapidoChip(monto: Long, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(MontoUtils.formatClp(monto)) }
    )
}

/**
 * Overlay bloqueante mientras se crea la preferencia de pago y se redirige a
 * Mercado Pago. Solo permite cancelar (botón) o volver (back del sistema).
 */
@Composable
fun RedirigiendoMercadoPagoDialog(onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.billetera_redirigiendo_mp),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.billetera_redirigiendo_mp_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.solicitudes_cancelar))
                }
            }
        }
    }
}
