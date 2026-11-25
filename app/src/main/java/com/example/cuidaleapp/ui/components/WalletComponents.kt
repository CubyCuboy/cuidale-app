package com.example.cuidaleapp.ui.components

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.Cobro
import com.example.cuidaleapp.models.EstadoCobro
import com.example.cuidaleapp.models.EstadoMovimiento
import com.example.cuidaleapp.models.MovimientoBilletera
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.MontoUtils
import com.example.cuidaleapp.utils.RecargaUi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
 * Pantalla completa de recarga de billetera (Checkout Pro de Mercado Pago).
 * Sin contenedores: la información se distribuye directamente sobre el fondo
 * oscuro con jerarquía visual (título, explicación, monto, sugerencias,
 * resumen y acciones al pie).
 */
@Composable
fun RecargaDialog(
    montoInicial: Long? = null,
    deudas: List<Long> = emptyList(),
    saldoActual: Long = 0L,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var montoStr by remember(montoInicial) {
        mutableStateOf(montoInicial?.let { MontoUtils.formatClp(it) } ?: "")
    }
    val monto = MontoUtils.parseMonto(montoStr)
    val valido = monto?.let { MontoUtils.validarMontoRecarga(it) } == true
    val montosRapidos = listOf(10_000L, 20_000L, 30_000L, 50_000L, 100_000L)
    val vivid = MaterialTheme.colorScheme.primary
    val blanco = Color.White

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
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1226), Color(0xFF141D36))
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(vivid.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.billetera_recargar_titulo),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = blanco
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.billetera_pago_cerrar),
                            tint = blanco.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.billetera_recargar_explicacion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = blanco.copy(alpha = 0.88f),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.billetera_recargar_cuanto),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = blanco.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = montoStr,
                    onValueChange = { montoStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(R.string.billetera_monto), color = blanco.copy(alpha = 0.85f)) },
                    placeholder = {
                        Text(
                            text = "Ej: 20.000",
                            color = blanco.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = {
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = vivid.copy(alpha = 0.9f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    },
                    isError = montoStr.isNotEmpty() && !valido,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(18.dp),
                    textStyle = LocalTextStyle.current.copy(
                        color = blanco,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = blanco,
                        unfocusedTextColor = blanco,
                        cursorColor = vivid,
                        focusedBorderColor = vivid,
                        unfocusedBorderColor = blanco.copy(alpha = 0.5f),
                        focusedLabelColor = vivid,
                        unfocusedLabelColor = blanco.copy(alpha = 0.8f),
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        errorLabelColor = MaterialTheme.colorScheme.error,
                        errorTextColor = blanco,
                        focusedLeadingIconColor = vivid.copy(alpha = 0.9f),
                        unfocusedLeadingIconColor = blanco.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (montoStr.isNotEmpty() && !valido) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.billetera_monto_invalido),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.billetera_montos_sugeridos),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = blanco.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(10.dp))
                montosRapidos.chunked(3).forEach { fila ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fila.forEach { m ->
                            MontoRapidoChip(
                                monto = m,
                                isSelected = monto == m,
                                onClick = { montoStr = MontoUtils.formatClp(m) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                deudas.filter { it > 0L }.forEach { deuda ->
                    OutlinedButton(
                        onClick = { montoStr = MontoUtils.formatClp(deuda) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = blanco),
                        border = BorderStroke(1.dp, blanco.copy(alpha = 0.4f))
                    ) {
                        Text(
                            stringResource(R.string.billetera_pagar_deuda, MontoUtils.formatClp(deuda)),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                HorizontalDivider(color = blanco.copy(alpha = 0.14f))
                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(
                        R.string.billetera_recargar_resumen,
                        if (valido) MontoUtils.formatClp(monto!!) else MontoUtils.formatClp(0L),
                        MontoUtils.formatClp(saldoActual)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = blanco.copy(alpha = 0.92f)
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = vivid,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.billetera_aviso_mp),
                        style = MaterialTheme.typography.bodySmall,
                        color = blanco.copy(alpha = 0.82f)
                    )
                }

                Spacer(Modifier.weight(1f))

                HardShadowButton(
                    text = stringResource(R.string.billetera_confirmar_pagar),
                    containerColor = vivid,
                    contentColor = Color.White,
                    shadowColor = vivid.copy(alpha = 0.3f),
                    icon = Icons.Default.AddCircle,
                    enabled = valido,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    onClick = { monto?.let(onConfirm) }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = blanco),
                    border = BorderStroke(1.dp, blanco.copy(alpha = 0.5f))
                ) {
                    Text(stringResource(R.string.action_back), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Pantalla completa para "Pagar/Transferir": lista los cobros pendientes del
 * representante (turnos a pagar) con el detalle del cuidado y el monto.
 */
@Composable
fun PantallaPagarCobros(
    cobros: List<Cobro>,
    saldoActual: Long,
    onPagar: (String) -> Unit,
    onVerCobro: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pendientes = cobros.filter { it.estadoCobro == EstadoCobro.PENDIENTE }
    val vivid = MaterialTheme.colorScheme.primary

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
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1226), Color(0xFF141D36))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.billetera_pagar_transferir),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.billetera_pago_cerrar),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Text(
                    text = stringResource(
                        R.string.billetera_pagar_explicacion,
                        MontoUtils.formatClp(saldoActual)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                if (pendientes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                modifier = Modifier.size(72.dp),
                                tint = Color.White.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.billetera_pagar_sin_pendientes),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pendientes, key = { it.id }) { cobro ->
                            TarjetaCobroPendiente(
                                cobro = cobro,
                                onClick = { onVerCobro(cobro.id) },
                                onPagar = { onPagar(cobro.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta de un cobro pendiente de pago (turno). */
@Composable
private fun TarjetaCobroPendiente(
    cobro: Cobro,
    onClick: () -> Unit,
    onPagar: () -> Unit
) {
    val vivid = MaterialTheme.colorScheme.primary
    HardShadowPanel(
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowColor = Color.Black.copy(alpha = 0.10f),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(vivid.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cobro.cuidadorNombre.firstOrNull()?.toString() ?: "?",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = cobro.cuidadorNombre.ifEmpty { "Cuidador" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = cobro.nombrePaciente.ifEmpty { "Servicio" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Turno: " + (cobro.createdAt?.toDate()?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                    } ?: "") + " • " + MontoUtils.formatHoras(cobro.horas) + "h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MontoUtils.formatClp(cobro.monto),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                HardShadowButton(
                    text = stringResource(R.string.billetera_pagar),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.height(38.dp),
                    onClick = onPagar
                )
            }
        }
    }
}

@Composable
private fun MontoRapidoChip(
    monto: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vivid = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = vivid.copy(alpha = 0.28f),
            labelColor = Color.White.copy(alpha = 0.85f),
            selectedLabelColor = Color.White,
            iconColor = Color.White.copy(alpha = 0.8f),
            selectedLeadingIconColor = Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) vivid else Color.White.copy(alpha = 0.35f)
        ),
        label = { Text(MontoUtils.formatClp(monto), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
    )
}

/**
 * Cortina de pantalla completa para el flujo de recarga (Mercado Pago).
 * No usa contenedores: la información se reparte directamente sobre el fondo
 * opaco, ocupando toda la pantalla con jerarquía visual (etiqueta arriba,
 * progreso, contenido central y acciones al pie).
 */
@Composable
fun RecargaOverlay(
    ui: RecargaUi,
    onCancel: () -> Unit,
    onReintentar: () -> Unit,
    onCerrar: () -> Unit
) {
    val vivid = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = {
            when (ui) {
                is RecargaUi.Redirigiendo -> onCancel()
                is RecargaUi.Final -> onCerrar()
                else -> Unit
            }
        },
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
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF080D1A), Color(0xFF141D36))
                    )
                )
        ) {
            // Brillo superior decorativo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(vivid.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
            )

            val indicePaso = when (ui) {
                is RecargaUi.Redirigiendo -> 0
                is RecargaUi.Procesando -> 1
                is RecargaUi.Final -> 2
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.05f))

                Text(
                    text = stringResource(R.string.billetera_pago_overlay_etiqueta),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = vivid.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(26.dp))

                PasosRecarga(indicePaso)

                Spacer(Modifier.weight(0.10f))

                // Cuerpo central: transición animada entre fases (reintento
                // incluido: error -> redirigiendo -> procesando -> resultado).
                AnimatedContent(
                    targetState = ui,
                    transitionSpec = {
                        (fadeIn(tween(300)) + scaleIn(initialScale = 0.94f, animationSpec = tween(300))) togetherWith
                            (fadeOut(tween(200)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)))
                    },
                    contentKey = { it::class },
                    label = "recargaFases"
                ) { estado ->
                    val info = infoParaEstado(estado, vivid)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(vivid.copy(alpha = 0.22f), Color.Transparent)
                                        )
                                    )
                            )
                            if (info.cargando) {
                                MpLoader()
                            } else {
                                info.icono?.let { EstadoIcon(it, info.colorIcono) }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = info.titulo,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = info.descripcion,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )
                        if (!info.ayuda.isNullOrBlank()) {
                            Spacer(Modifier.height(18.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = info.ayuda,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(0.12f))

                // Acciones al pie con fade de transición.
                AnimatedContent(
                    targetState = ui,
                    transitionSpec = {
                        fadeIn(tween(320)) togetherWith fadeOut(tween(200))
                    },
                    contentKey = { it::class },
                    label = "recargaFooter"
                ) { estado ->
                    val mostrarFooter = when (estado) {
                        is RecargaUi.Redirigiendo -> true
                        is RecargaUi.Final -> estado.estado != "aprobado"
                        else -> false
                    }
                    if (mostrarFooter) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (estado) {
                                is RecargaUi.Redirigiendo -> {
                                    OutlinedButton(
                                        onClick = onCancel,
                                        modifier = Modifier.fillMaxWidth().height(54.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(stringResource(R.string.solicitudes_cancelar), fontWeight = FontWeight.Bold)
                                    }
                                }
                                is RecargaUi.Final -> {
                                    if (estado.estado == "rechazado" || estado.estado == "error") {
                                        HardShadowButton(
                                            text = stringResource(R.string.billetera_pago_reintentar),
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White,
                                            shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            modifier = Modifier.fillMaxWidth().height(54.dp),
                                            onClick = onReintentar
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onCerrar,
                                        modifier = Modifier.fillMaxWidth().height(54.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(stringResource(R.string.billetera_pago_cerrar), fontWeight = FontWeight.Bold)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(0.06f))
            }
        }
    }
}

@Composable
private fun infoParaEstado(estado: RecargaUi, vivid: Color): InfoEstadoRecarga {
    return when (estado) {
        is RecargaUi.Redirigiendo -> InfoEstadoRecarga(
            icono = null,
            colorIcono = vivid,
            titulo = stringResource(R.string.billetera_recarga_overlay_titulo),
            descripcion = stringResource(R.string.billetera_recarga_overlay_desc),
            ayuda = null
        )
        is RecargaUi.Procesando -> InfoEstadoRecarga(
            icono = null,
            colorIcono = vivid,
            titulo = stringResource(R.string.billetera_procesando_titulo),
            descripcion = stringResource(R.string.billetera_procesando_desc),
            ayuda = null
        )
        is RecargaUi.Final -> {
            val custom = CuidaleThemeExtras.customColors
            val (icono, color) = when (estado.estado) {
                "aprobado" -> Icons.Default.Check to custom.success
                "rechazado" -> Icons.Default.Close to MaterialTheme.colorScheme.error
                "cancelado" -> Icons.Default.Close to custom.warning
                "pendiente" -> Icons.Default.Schedule to custom.warning
                else -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
            }
            val titulo = when (estado.estado) {
                "aprobado" -> stringResource(R.string.billetera_pago_aprobado_titulo)
                "rechazado" -> stringResource(R.string.billetera_pago_rechazado_titulo)
                "cancelado" -> stringResource(R.string.billetera_pago_cancelado_titulo)
                "pendiente" -> stringResource(R.string.billetera_pago_pendiente_titulo)
                else -> stringResource(R.string.billetera_pago_error_titulo)
            }
            val descripcion = when (estado.estado) {
                "aprobado" -> stringResource(R.string.billetera_pago_aprobado_desc)
                "rechazado" -> stringResource(R.string.billetera_pago_rechazado_desc)
                "cancelado" -> stringResource(R.string.billetera_pago_cancelado_desc)
                "pendiente" -> stringResource(R.string.billetera_pago_pendiente_desc)
                else -> estado.mensaje ?: stringResource(R.string.billetera_pago_error_desc_gen)
            }
            val ayuda = when (estado.estado) {
                "rechazado" -> stringResource(R.string.billetera_pago_rechazado_ayuda)
                "cancelado" -> stringResource(R.string.billetera_pago_cancelado_ayuda)
                "pendiente" -> stringResource(R.string.billetera_pago_pendiente_ayuda)
                else -> if (estado.estado == "error") {
                    stringResource(R.string.billetera_pago_error_ayuda)
                } else {
                    null
                }
            }
            InfoEstadoRecarga(icono, color, titulo, descripcion, ayuda)
        }
    }
}

private data class InfoEstadoRecarga(
    val icono: ImageVector?,
    val colorIcono: Color,
    val titulo: String,
    val descripcion: String,
    val ayuda: String?,
    val cargando: Boolean = icono == null
)

@Composable
private fun PasosRecarga(indiceActual: Int) {
    data class Paso(val nombre: String, val icono: ImageVector)
    val pasos = listOf(
        Paso(stringResource(R.string.billetera_paso_solicitar), Icons.Default.Create),
        Paso(stringResource(R.string.billetera_paso_pagar), Icons.Default.CreditCard),
        Paso(stringResource(R.string.billetera_paso_resultado), Icons.Default.Check)
    )
    val colorActivo = MaterialTheme.colorScheme.primary
    val exito = CuidaleThemeExtras.customColors.success
    val inactivo = Color.White.copy(alpha = 0.25f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        pasos.forEachIndexed { indice, paso ->
            val activo = indice == indiceActual
            val completado = indice < indiceActual

            if (indice > 0) {
                // Línea conectora entre pasos.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .align(Alignment.CenterVertically)
                        .background(
                            if (completado) exito.copy(alpha = 0.8f) else inactivo,
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (activo) {
                    // Paso activo: halo + círculo con pulso.
                    val transition = rememberInfiniteTransition(label = "pasoActivo")
                    val pulse by transition.animateFloat(
                        initialValue = 0.92f,
                        targetValue = 1.18f,
                        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                        label = "pulse"
                    )
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.55f,
                        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                        label = "glow"
                    )
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colorActivo.copy(alpha = alpha))
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colorActivo)
                                .graphicsLayer { scaleX = pulse; scaleY = pulse },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                paso.icono,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (completado) exito else inactivo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (completado) Icons.Default.Check else paso.icono,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = paso.nombre,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        activo -> colorActivo
                        completado -> Color.White.copy(alpha = 0.9f)
                        else -> Color.White.copy(alpha = 0.55f)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EstadoIcon(icono: ImageVector, color: Color) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "estadoIconScale"
    )
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        )
    }
}

@Composable
private fun MpLoader() {
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "mpLoader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotate"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = color, startAngle = 0f, sweepAngle = 110f, useCenter = false, style = stroke)
            drawArc(color = color.copy(alpha = 0.22f), startAngle = 135f, sweepAngle = 80f, useCenter = false, style = stroke)
        }
        Icon(
            Icons.Default.AccountBalanceWallet,
            contentDescription = null,
            tint = color.copy(alpha = alpha),
            modifier = Modifier.size(32.dp)
        )
    }
}

/** Tarjeta de una transferencia/movimiento de la billetera con su estado. */
@Composable
fun TransferenciaCard(mov: MovimientoBilletera, modifier: Modifier = Modifier) {
    val custom = CuidaleThemeExtras.customColors
    val estadoMov = EstadoMovimiento.fromString(mov.estado.ifBlank { estadoPorDefecto(mov.tipoMovimiento) })
    val esIngreso = mov.monto >= 0L

    val icono = when (mov.tipoMovimiento) {
        "recarga" -> Icons.Default.AddCircle
        "pago" -> if (esIngreso) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward
        "retiro" -> Icons.Default.ArrowUpward
        else -> if (esIngreso) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward
    }
    val color = when (estadoMov) {
        EstadoMovimiento.APROBADO, EstadoMovimiento.COMPLETADO ->
            if (esIngreso) custom.success else MaterialTheme.colorScheme.primary
        EstadoMovimiento.PENDIENTE, EstadoMovimiento.REVISION -> custom.warning
        EstadoMovimiento.RECHAZADO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val estadoTexto = when (estadoMov) {
        EstadoMovimiento.APROBADO -> stringResource(R.string.billetera_mov_estado_aprobado)
        EstadoMovimiento.COMPLETADO -> stringResource(R.string.billetera_mov_estado_completado)
        EstadoMovimiento.PENDIENTE -> stringResource(R.string.billetera_mov_estado_pendiente)
        EstadoMovimiento.RECHAZADO -> stringResource(R.string.billetera_mov_estado_rechazado)
        EstadoMovimiento.REVISION -> stringResource(R.string.billetera_mov_estado_en_revision)
        else -> stringResource(R.string.billetera_mov_estado_desconocido)
    }
    val titulo = when (mov.tipoMovimiento) {
        "recarga" -> stringResource(R.string.transferencias_titulo_recarga)
        "pago" -> if (esIngreso) {
            stringResource(R.string.transferencias_titulo_pago_recibido)
        } else {
            stringResource(R.string.transferencias_titulo_pago)
        }
        "retiro" -> stringResource(R.string.transferencias_titulo_retiro)
        else -> mov.descripcion.ifEmpty { mov.tipoMovimiento.ifEmpty { mov.tipo } }
    }

    HardShadowPanel(
        backgroundColor = CuidaleThemeExtras.customColors.panelBackground,
        shadowColor = Color.Black.copy(alpha = 0.05f),
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (mov.descripcion.isNotBlank() && mov.tipoMovimiento != "recarga") {
                    Text(
                        text = mov.descripcion,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = fechaAmigable(mov.timestamp?.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MontoUtils.formatClp(mov.monto),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (esIngreso) custom.success else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(2.dp))
                Surface(
                    color = color.copy(alpha = 0.15f),
                    contentColor = color,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = estadoTexto,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun fechaAmigable(fecha: Date?): String {
    if (fecha == null) return ""
    val hoy = Calendar.getInstance()
    val dia = Calendar.getInstance().apply { time = fecha }
    val mismoDia = hoy.get(Calendar.YEAR) == dia.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) == dia.get(Calendar.DAY_OF_YEAR)
    val ayer = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val esAyer = ayer.get(Calendar.YEAR) == dia.get(Calendar.YEAR) &&
        ayer.get(Calendar.DAY_OF_YEAR) == dia.get(Calendar.DAY_OF_YEAR)
    val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(fecha)
    return when {
        mismoDia -> "Hoy a las $hora"
        esAyer -> "Ayer a las $hora"
        else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(fecha)
    }
}

private fun estadoPorDefecto(tipo: String): String = when (tipo) {
    "recarga" -> "aprobado"
    "pago" -> "aprobado"
    "retiro" -> "pendiente"
    else -> ""
}
