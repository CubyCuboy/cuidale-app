package com.example.cuidaleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.cuidaleapp.R
import com.example.cuidaleapp.utils.DateUtils
import com.example.cuidaleapp.models.SolicitudCuidado
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.ErrorCodes
import com.example.cuidaleapp.utils.ErrorMessages
import com.example.cuidaleapp.viewmodel.SolicitudViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSolicitudScreen(
    solicitud: SolicitudCuidado,
    viewModel: SolicitudViewModel,
    isAccepted: Boolean = false,
    onNavigateBack: () -> Unit,
    onViewFullFicha: (String) -> Unit,
    onRedirectToRequests: () -> Unit,
    onNavigateToChat: ((String, String) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var patientFicha by remember { mutableStateOf<Map<String, Any>?>(null) }
    var patientTareas by remember { mutableStateOf<List<com.example.cuidaleapp.models.Tarea>>(emptyList()) }
    var isLoadingFicha by remember { mutableStateOf(true) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showCancelPostulationConfirm by remember { mutableStateOf(false) }

    val customColors = CuidaleThemeExtras.customColors
    val isDark = customColors.isDark
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    var hasAlreadyApplied by remember { mutableStateOf(false) }
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(solicitud.id, currentUid) {
        if (currentUid != null) {
            try {
                val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("postulaciones")
                    .whereEqualTo("solicitudId", solicitud.id)
                    .whereEqualTo("cuidadorUid", currentUid)
                    .whereEqualTo("estado", "pendiente")
                    .get().await()
                hasAlreadyApplied = !snapshot.isEmpty
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(ErrorMessages.resolve(context, ErrorCodes.Service.POSTULACION_FAILED))
            }
        }
    }

    LaunchedEffect(solicitud.pacienteFichaId) {
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("fichas")
                .document(solicitud.pacienteFichaId).get().await()
            patientFicha = doc.data
            
            val tasksSnapshot = db.collection("fichas")
                .document(solicitud.pacienteFichaId)
                .collection("tareas_plantilla")
                .whereEqualTo("activa", true)
                .get().await()
            patientTareas = tasksSnapshot.toObjects(com.example.cuidaleapp.models.Tarea::class.java)
        } catch (_: Exception) {
            snackbarHostState.showSnackbar(ErrorMessages.resolve(context, ErrorCodes.Database.GENERIC))
        } finally {
            isLoadingFicha = false
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text(stringResource(R.string.solicitudes_finalizar_servicio_titulo)) },
            text = { Text(stringResource(R.string.solicitudes_finalizar_servicio_mensaje)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.finalizarServicio(solicitud) { 
                        showFinishConfirm = false
                        onRedirectToRequests()
                    }
                }) { Text(stringResource(R.string.solicitudes_si_finalizar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text(stringResource(R.string.solicitudes_no)) }
            }
        )
    }

    if (showCancelPostulationConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelPostulationConfirm = false },
            title = { Text(stringResource(R.string.review_solicitud_cancelar_postulacion_titulo)) },
            text = { Text(stringResource(R.string.review_solicitud_cancelar_postulacion_mensaje)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelarPostulacion(solicitud.id)
                    showCancelPostulationConfirm = false
                    onNavigateBack()
                }) { Text(stringResource(R.string.review_solicitud_si_cancelar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelPostulationConfirm = false }) { Text(stringResource(R.string.solicitudes_no)) }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                if (isAccepted) {
                    HardShadowButton(
                        text = stringResource(R.string.solicitudes_finalizar_servicio),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                        shadowColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                        icon = Icons.Default.CheckCircle,
                        onClick = { showFinishConfirm = true }
                    )
                } else if (currentUid == solicitud.representanteUid) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.review_solicitud_creada_por_ti),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    HardShadowButton(
                        text = if (hasAlreadyApplied) stringResource(R.string.review_solicitud_cancelar_postulacion) else stringResource(R.string.review_solicitud_postular_a_servicio),
                        containerColor = if (hasAlreadyApplied) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        shadowColor = if (hasAlreadyApplied) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        enabled = true,
                        icon = if (hasAlreadyApplied) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                        onClick = {
                            if (hasAlreadyApplied) {
                                showCancelPostulationConfirm = true
                            } else {
                                viewModel.postularASolicitud(solicitud) { 
                                    onRedirectToRequests() 
                                }
                            }
                        }
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(if (isAccepted) stringResource(R.string.review_solicitud_servicio_activo) else stringResource(R.string.review_solicitud_detalles_de_solicitud), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        if (!isAccepted) {
                            Text(stringResource(R.string.review_solicitud_revisa_requerimientos), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoadingFicha) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (patientFicha != null) {
                
                Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 8.dp,
                            border = BorderStroke(4.dp, primaryColor.copy(alpha = 0.5f))
                        ) {
                            if (solicitud.fotoPaciente.isNotEmpty()) {
                                AsyncImage(
                                    model = solicitud.fotoPaciente, 
                                    contentDescription = null, 
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .blur(if (isAccepted) 0.dp else 25.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp), tint = Color.LightGray)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        val displayName = if (solicitud.nombrePaciente.isBlank() || solicitud.nombrePaciente == "n/a") stringResource(R.string.historial_paciente) else solicitud.nombrePaciente
                        Text(displayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = if (isDark) Color.White else Color.Black)
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           Surface(
                               color = primaryColor.copy(alpha = 0.1f),
                               shape = RoundedCornerShape(16.dp),
                               modifier = Modifier.padding(top = 4.dp)
                           ) {
                               Text(
                                   text = stringResource(R.string.review_solicitud_paciente_badge),
                                   modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                   style = MaterialTheme.typography.labelSmall,
                                   fontWeight = FontWeight.Bold,
                                   color = primaryColor
                               )
                           }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onViewFullFicha(solicitud.pacienteFichaId) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.1f), contentColor = primaryColor)
                    ) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.review_solicitud_ver_ficha), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    
                    if (isAccepted && onNavigateToChat != null) {
                        Button(
                            onClick = { 
                                val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                val otherUid = solicitud.representanteUid
                                val chatId = if (myUid < otherUid) "${myUid}_$otherUid" else "${otherUid}_$myUid"
                                onNavigateToChat(chatId, otherUid) 
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.caregiver_profile_ir_al_chat), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupervisorAccount, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.review_solicitud_representante), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isAccepted) {
                            CuidaleDetailRow(Icons.Default.Person, stringResource(R.string.account_management_name), solicitud.representanteNombre)
                            CuidaleDetailRow(Icons.Default.Phone, stringResource(R.string.historial_telefono), solicitud.representanteTelefono)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.05f)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.review_solicitud_contacto_protegido), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.review_solicitud_contacto_protegido_desc), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HardShadowPanel(
                    backgroundColor = customColors.panelBackground,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MedicalServices, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.review_solicitud_condicion_medica), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primaryColor)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val enfermedades = (patientFicha!!["enfermedades"] as? List<*>)?.joinToString(", ") ?: stringResource(R.string.review_solicitud_ninguna)
                        val movilidad = patientFicha!!["movilidad"]?.toString() ?: stringResource(R.string.caregiver_profile_no_especificada)
                        val genero = patientFicha!!["genero"]?.toString() ?: stringResource(R.string.common_not_specified)
                        val observaciones = patientFicha!!["condiciones"]?.toString() ?: stringResource(R.string.review_solicitud_sin_observaciones)

                        CuidaleDetailRow(Icons.Default.HealthAndSafety, stringResource(R.string.review_solicitud_enfermedades), enfermedades)
                        CuidaleDetailRow(Icons.Default.Accessibility, stringResource(R.string.review_solicitud_movilidad), movilidad)
                        CuidaleDetailRow(Icons.Default.Wc, stringResource(R.string.profile_label_gender), genero)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(stringResource(R.string.review_solicitud_observaciones_generales), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(observaciones, style = MaterialTheme.typography.bodySmall, color = if (isDark) Color.LightGray else Color.DarkGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                HardShadowPanel(
                    backgroundColor = if (isDark) customColors.panelBackground else Color.White,
                    shadowColor = Color.Black.copy(alpha = 0.05f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.EventNote, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.review_solicitud_requerimientos_turno), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primaryColor)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isAccepted) {
                            val calle = patientFicha!!["calle"]?.toString() ?: ""
                            val numero = patientFicha!!["numero"]?.toString() ?: ""
                            val depto = patientFicha!!["depto"]?.toString() ?: ""
                            val comuna = patientFicha!!["comuna"]?.toString() ?: ""
                            val direccionStr = if (calle.isNotEmpty()) {
                                if (depto.isNotEmpty()) {
                                    stringResource(R.string.review_solicitud_direccion_con_depto, calle, numero, depto, comuna)
                                } else {
                                    stringResource(R.string.review_solicitud_direccion_sin_depto, calle, numero, comuna)
                                }
                            } else {
                                stringResource(R.string.caregiver_profile_no_especificada)
                            }

                            CuidaleDetailRow(Icons.Default.Place, stringResource(R.string.review_solicitud_direccion), direccionStr)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.05f)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.review_solicitud_ubicacion_protegida), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    val comuna = patientFicha!!["comuna"]?.toString() ?: stringResource(R.string.caregiver_profile_no_especificada)
                                    Text(stringResource(R.string.review_solicitud_referencia_comuna, comuna), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        CuidaleDetailRow(Icons.Default.Schedule, stringResource(R.string.crear_solicitud_campo_horario), solicitud.horarioStr)
                        CuidaleDetailRow(Icons.Default.DateRange, stringResource(R.string.review_solicitud_duracion), if (solicitud.esIndefinido) stringResource(R.string.review_solicitud_contrato_indefinido) else stringResource(R.string.review_solicitud_servicio_temporal))
                        
                        if (!solicitud.esIndefinido) {
                            Row(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                Column {
                                    Text(stringResource(R.string.review_solicitud_inicio, DateUtils.formatDate(solicitud.fechaInicio?.toDate()?.time)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.review_solicitud_termino, DateUtils.formatDate(solicitud.fechaFin?.toDate()?.time)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (isAccepted && patientTareas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.ListAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.caregiver_view_regimen_tareas), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        patientTareas.forEach { tarea ->
                            TaskItemCardInView(tarea, MaterialTheme.colorScheme.primary, textColor, secondaryTextColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.review_solicitud_error_cargar_info), color = Color.Gray)
                    }
                }
            }
        }
    }
}
