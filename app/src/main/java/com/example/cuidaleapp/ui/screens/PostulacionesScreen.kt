package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.PostulacionCuidado
import com.example.cuidaleapp.ui.components.HardShadowPanel
import com.example.cuidaleapp.viewmodel.SolicitudViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostulacionesScreen(
    solicitudId: String,
    viewModel: SolicitudViewModel,
    onNavigateBack: () -> Unit,
    onViewCaregiver: (String) -> Unit
) {
    val postulacionesMap by viewModel.postulacionesRecibidas.collectAsState()
    val list = postulacionesMap[solicitudId] ?: emptyList()
    val misSolicitudes by viewModel.misSolicitudes.collectAsState()
    val solicitud = misSolicitudes.find { it.id == solicitudId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.postulaciones_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 24.dp)) {
            if (list.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.postulaciones_empty))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(list) { p ->
                        PostulacionItem(
                            postulacion = p,
                            onReject = { viewModel.rechazarPostulacion(p) },
                            onAccept = { 
                                if (solicitud != null) {
                                    viewModel.aceptarCuidador(solicitud, p, onNavigateBack)
                                }
                            },
                            onViewInfo = { onViewCaregiver(p.cuidadorUid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostulacionItem(
    postulacion: PostulacionCuidado,
    onReject: () -> Unit,
    onAccept: () -> Unit,
    onViewInfo: () -> Unit
) {
    HardShadowPanel(
        backgroundColor = MaterialTheme.colorScheme.surface,
        shadowColor = Color.Black.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f))) {
                    if (postulacion.cuidadorFoto.isNotEmpty()) {
                        AsyncImage(model = postulacion.cuidadorFoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, stringResource(R.string.postulaciones_avatar_cuidador), modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(postulacion.cuidadorNombre, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onViewInfo,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.postulaciones_ver_info), fontSize = 11.sp)
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.action_accept), fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.solicitudes_rechazar), fontSize = 11.sp)
                }
            }
        }
    }
}
