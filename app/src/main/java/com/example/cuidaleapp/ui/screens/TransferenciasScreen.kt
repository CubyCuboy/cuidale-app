package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.MovimientoBilletera
import com.example.cuidaleapp.ui.components.TransferenciaCard
import com.example.cuidaleapp.viewmodel.BilleteraViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class FiltroTransferencia(
    val clave: String,
    val etiquetaRes: Int,
    val icono: ImageVector
) {
    TODAS("todas", R.string.transferencias_filtro_todas, Icons.Default.History),
    RECARGAS("recarga", R.string.transferencias_filtro_recargas, Icons.Default.AddCircle),
    PAGOS("pago", R.string.transferencias_filtro_pagos, Icons.AutoMirrored.Filled.Send),
    RETIROS("retiro", R.string.transferencias_filtro_retiros, Icons.Default.ArrowUpward)
}

/**
 * Historial completo de transferencias con filtros por tipo (scroll horizontal
 * para no cortarse en pantallas angostas) y agrupación por fecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferenciasScreen(
    viewModel: BilleteraViewModel,
    onNavigateBack: () -> Unit
) {
    val transferencias by viewModel.transferencias.collectAsState()
    var filtro by remember { mutableStateOf(FiltroTransferencia.TODAS) }

    val filtradas = remember(transferencias, filtro) {
        if (filtro == FiltroTransferencia.TODAS) {
            transferencias
        } else {
            transferencias.filter { it.tipoMovimiento == filtro.clave }
        }
    }

    val agrupadas = remember(filtradas) {
        filtradas.groupBy { claveFecha(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transferencias_titulo), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Filtros por tipo: scroll horizontal, chips con ícono.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FiltroTransferencia.entries.forEach { f ->
                    FilterChip(
                        selected = filtro == f,
                        onClick = { filtro = f },
                        label = { Text(stringResource(f.etiquetaRes), fontWeight = if (filtro == f) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                f.icono,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            labelColor = Color.White.copy(alpha = 0.85f),
                            selectedLabelColor = Color.White,
                            iconColor = Color.White.copy(alpha = 0.8f),
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            if (agrupadas.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            null,
                            modifier = Modifier.size(90.dp),
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.transferencias_vacias),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    agrupadas.forEach { (titulo, lista) ->
                        item(key = "grupo_$titulo") {
                            Text(
                                text = titulo,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        items(lista, key = { it.id.ifBlank { it.hashCode().toString() } }) { mov ->
                            TransferenciaCard(mov)
                        }
                    }
                }
            }
        }
    }
}

private fun claveFecha(mov: MovimientoBilletera): String {
    val fecha = mov.timestamp?.toDate() ?: return "Sin fecha"
    val hoy = Calendar.getInstance()
    val dia = Calendar.getInstance().apply { time = fecha }
    if (hoy.get(Calendar.YEAR) == dia.get(Calendar.YEAR) &&
        hoy.get(Calendar.DAY_OF_YEAR) == dia.get(Calendar.DAY_OF_YEAR)
    ) {
        return "Hoy"
    }
    val ayer = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (ayer.get(Calendar.YEAR) == dia.get(Calendar.YEAR) &&
        ayer.get(Calendar.DAY_OF_YEAR) == dia.get(Calendar.DAY_OF_YEAR)
    ) {
        return "Ayer"
    }
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(fecha)
}
