package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.*
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.ui.components.getIconForTaskType
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformeServicioScreen(
    informe: InformeTurno,
    onNavigateBack: () -> Unit
) {
    val gradient = CuidaleThemeExtras.gradient

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.informe_titulo), fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    HardShadowPanel(backgroundColor = Color.White) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(stringResource(R.string.informe_resumen_general), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.informe_fecha, informe.fecha), style = MaterialTheme.typography.bodySmall)
                            Text(
                                stringResource(R.string.informe_generado_el, java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(informe.generadoAt.toDate())),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (informe.observacionesGenerales.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(stringResource(R.string.informe_observaciones), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(informe.observacionesGenerales, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    Text(stringResource(R.string.informe_detalle_tareas), style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }

                items(informe.resumenTareas) { registro ->
                    TaskReportItem(registro)
                }
            }
        }
    }
}

@Composable
fun TaskReportItem(registro: RegistroTarea) {
    val color = if (registro.completada) Color(0xFF4CAF50) else Color(0xFFF44336)

    HardShadowPanel(
        backgroundColor = Color.White,
        shadowColor = Color.Black.copy(alpha = 0.05f),
        cornerRadius = 12.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        getIconForTaskType(registro.tipo),
                        null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(registro.titulo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.informe_previsto, registro.horarioPrevisto), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (registro.completada) stringResource(R.string.tareas_estado_completada) else stringResource(R.string.informe_no_realizada),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                    if (registro.completada && registro.fechaCompletado != null) {
                        Text(
                            text = java.text.SimpleDateFormat("HH:mm").format(registro.fechaCompletado.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            if (registro.fotoUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                coil3.compose.AsyncImage(
                    model = registro.fotoUrl,
                    contentDescription = stringResource(R.string.informe_cd_evidencia_tarea),
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            if (registro.sinEvidenciaFotografica) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.informe_sin_evidencia_fotografica),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
