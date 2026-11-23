package com.example.cuidaleapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cuidaleapp.data.ChileData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.cuidaleapp.R
import com.example.cuidaleapp.models.*
import com.example.cuidaleapp.ui.components.*
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.viewmodel.FichaState
import com.example.cuidaleapp.viewmodel.FichaViewModel
import com.example.cuidaleapp.viewmodel.ProfileViewModel
import java.io.File

import com.example.cuidaleapp.utils.ConnectivityObserver
import com.example.cuidaleapp.utils.ErrorCodes
import com.example.cuidaleapp.utils.ErrorMessages
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaScreen(
    viewModel: FichaViewModel,
    profileViewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTasks: (() -> Unit)? = null,
    startInEditMode: Boolean = false
) {
    val isOnline by ConnectivityObserver.isOnline.collectAsState()
    val state by viewModel.state.collectAsState()
    val role by viewModel.role.collectAsState()
    val availableCommunes by viewModel.availableCommunes.collectAsState()
    val userData by profileViewModel.userData.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isPartialLoading by viewModel.isPartialLoading.collectAsState()
    val scrollState = rememberScrollState()

    val customColors = CuidaleThemeExtras.customColors
    val accentColor = if (role == "cuidador") MaterialTheme.colorScheme.primary else customColors.successStrong
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val panelBg = customColors.panelBackground

    LaunchedEffect(startInEditMode, isOnline) {
        if (startInEditMode && isOnline) {
            viewModel.startEditing()
        }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = { if (isOnline) viewModel.checkProfile() },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {


            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isOnline) accentColor else Color.Gray)
                        .statusBarsPadding()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (state is FichaState.Editing) {
                                        viewModel.cancelEditing()
                                    } else {
                                        onNavigateBack()
                                    }
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (role == "cuidador") stringResource(R.string.ficha_title_caregiver) else stringResource(R.string.ficha_title_patient),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = if (!isOnline) stringResource(R.string.ficha_offline_mode) else if (state is FichaState.Editing || (state is FichaState.NotFound)) stringResource(R.string.ficha_completing_info) else stringResource(R.string.ficha_info_saved),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                if (!isOnline) {
                    Surface(
                        color = Color.Red.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WifiOff, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ficha_offline_msg), color = Color.Red, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp)
                ) {
                    if (role == "representante" && userData.uid.isNotEmpty() && state !is FichaState.Loading) {
                        ModernPersonalDataCard(
                            nombre = "${userData.nombre} ${userData.apellido_pa}",
                            rut = userData.rut,
                            telefono = userData.telefono,
                            fotoUrl = userData.fotoUrl,
                            cardColor = panelBg,
                            primaryText = textColor,
                            secondaryText = secondaryTextColor,
                            accentColor = accentColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    AnimatedContent(
                        targetState = state,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                        },
                        label = "fichaContent"
                    ) { targetState ->
                        when (targetState) {
                            is FichaState.Loading -> {
                                if (isRefreshing) {
                                    null
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = accentColor)
                                    }
                                }
                            }
                            is FichaState.View -> {
                                val tareasPlantilla by viewModel.tareasPlantilla.collectAsState()
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (role == "cuidador") {
                                        CaregiverFichaView(
                                            data = targetState.data,
                                            tareasPlantilla = tareasPlantilla,
                                            accentColor = accentColor,
                                            onEdit = { viewModel.startEditing() }
                                        )
                                    } else {
                                        ProfileDetailView(
                                            role = role,
                                            data = targetState.data,
                                            tareasPlantilla = tareasPlantilla,
                                            showEditButton = isOnline,
                                            onEdit = { viewModel.startEditing() },
                                            onEditTasks = if (role == "representante" && isOnline) {
                                                { onNavigateToTasks?.invoke() }
                                            } else null,
                                            isEditMode = false
                                        )
                                    }
                                    SearchPreferencesCard(
                                        role = role,
                                        data = targetState.data,
                                        availableCommunes = availableCommunes,
                                        onUpdate = { c, h -> if (isOnline) viewModel.updateSearchPreferences(c, h) },
                                        accentColor = accentColor,
                                        panelBg = panelBg,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor,
                                        isLoading = isPartialLoading,
                                        isOnline = isOnline
                                    )
                                }
                            }
                            is FichaState.NotFound -> {
                                FichaPlaceholder(
                                    role = role,
                                    accentColor = accentColor,
                                    panelBg = panelBg,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onCreateClick = { viewModel.startEditing() }
                                )
                            }
                            is FichaState.Editing -> {
                                val editingData = (targetState as FichaState.Editing).data
                                val tareasTemporales by viewModel.tareasTemporales.collectAsState()
                                
                                if (role == "cuidador") {
                                    CaregiverEditFlow(
                                        viewModel = viewModel,
                                        availableCommunes = availableCommunes,
                                        initialData = editingData,
                                        cardColor = panelBg,
                                        accentColor = accentColor,
                                        onFinishSuccess = { if (startInEditMode) onNavigateBack() }
                                    )
                                } else {
                                    FamilyEditFlow(
                                        viewModel = viewModel,
                                        profileViewModel = profileViewModel,
                                        availableCommunes = availableCommunes,
                                        initialData = editingData,
                                        tareasTemporales = tareasTemporales,
                                        cardColor = panelBg,
                                        accentColor = accentColor,
                                        onFinishSuccess = { if (startInEditMode) onNavigateBack() }
                                    )
                                }
                            }
                            is FichaState.Error -> {
                                ModernErrorState(targetState.message, panelBg, accentColor) { viewModel.checkProfile() }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
fun ModernPersonalDataCard(
    nombre: String, rut: String, telefono: String, fotoUrl: String,
    cardColor: Color, primaryText: Color, secondaryText: Color,
    accentColor: Color
) {
    val shadowPalette = CuidaleThemeExtras.shadows
    HardShadowPanel(
        backgroundColor = cardColor,
        shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Color.Gray.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, primaryText.copy(alpha = 0.1f))
            ) {
                if (fotoUrl.isNotEmpty()) {
                    coil3.compose.SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fotoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize().background(shimmerBrush()),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = accentColor
                                )
                            }
                        }
                    )
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.padding(16.dp), tint = secondaryText)
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nombre, 
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold, 
                        color = primaryText,
                        fontSize = if (nombre.length > 25) 18.sp else 22.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (nombre.length > 25) 22.sp else 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Badge, null, modifier = Modifier.size(12.dp), tint = secondaryText.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text(text = stringResource(R.string.ficha_rut, rut), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(12.dp), tint = secondaryText.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (telefono.isBlank()) stringResource(R.string.ficha_sin_telefono)
                        else stringResource(R.string.ficha_tel, telefono),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (telefono.isBlank()) secondaryText.copy(alpha = 0.5f) else secondaryText
                    )
                }
            }
        }
    }
}

@Composable
fun ModernErrorState(message: String, cardColor: Color, accentColor: Color, onRetry: () -> Unit) {
    val shadowPalette = CuidaleThemeExtras.shadows
    val customColors = CuidaleThemeExtras.customColors
    val textColor = MaterialTheme.colorScheme.onSurface
    HardShadowPanel(backgroundColor = cardColor, shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f), cornerRadius = 16.dp) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, textAlign = TextAlign.Center, color = textColor)
            Spacer(modifier = Modifier.height(24.dp))
            HardShadowButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                containerColor = accentColor,
                contentColor = Color.White,
                shadowColor = shadowPalette.hardShadowPrimary,
                isDark = customColors.isDark
            )
        }
    }
}

private fun parseFormaciones(text: String): List<FormacionAcademica> =
    text.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split("|").map { it.trim() }
        FormacionAcademica(
            titulo = p.getOrNull(0) ?: "",
            institucion = p.getOrNull(1) ?: "",
            anioInicio = p.getOrNull(2) ?: "",
            anioFin = p.getOrNull(3) ?: ""
        )
    }

private fun parseExperiencias(text: String): List<ExperienciaLaboral> =
    text.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split("|").map { it.trim() }
        ExperienciaLaboral(
            cargo = p.getOrNull(0) ?: "",
            empresa = p.getOrNull(1) ?: "",
            desde = p.getOrNull(2) ?: "",
            hasta = p.getOrNull(3) ?: "",
            tiempoExperiencia = p.getOrNull(4) ?: "",
            descripcion = p.getOrNull(5) ?: ""
        )
    }

private fun parseReferencias(text: String): List<ReferenciaLaboral> =
    text.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split("|").map { it.trim() }
        ReferenciaLaboral(
            nombre = p.getOrNull(0) ?: "",
            empresa = p.getOrNull(1) ?: "",
            telefono = p.getOrNull(2) ?: "",
            cargo = p.getOrNull(3) ?: "",
            detalle = p.getOrNull(4) ?: ""
        )
    }

private fun parseCertificadosDeMapa(items: List<*>): List<Certificado> =
    items.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            Certificado(
                id = m["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                nombre = m["nombre"] as? String ?: "",
                tipoArchivo = m["tipoArchivo"] as? String ?: "",
                urlArchivo = m["urlArchivo"] as? String ?: "",
                storagePath = m["storagePath"] as? String ?: "",
                fechaSubida = m["fechaSubida"] as? com.google.firebase.Timestamp,
                estadoValidacion = m["estadoValidacion"] as? String ?: "pendiente"
            )
        }
    }

private fun parseFormacionesDeMapa(items: List<*>): List<FormacionAcademica> =
    items.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            FormacionAcademica(
                titulo = m["titulo"] as? String ?: "",
                institucion = m["institucion"] as? String ?: "",
                anioInicio = m["anioInicio"] as? String ?: "",
                anioFin = m["anioFin"] as? String ?: ""
            )
        }
    }

private fun parseExperienciasDeMapa(items: List<*>): List<ExperienciaLaboral> =
    items.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            ExperienciaLaboral(
                cargo = m["cargo"] as? String ?: "",
                empresa = m["empresa"] as? String ?: "",
                desde = m["desde"] as? String ?: "",
                hasta = m["hasta"] as? String ?: "",
                tiempoExperiencia = m["tiempoExperiencia"] as? String ?: "",
                descripcion = m["descripcion"] as? String ?: ""
            )
        }
    }

private fun parseReferenciasDeMapa(items: List<*>): List<ReferenciaLaboral> =
    items.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            ReferenciaLaboral(
                nombre = m["nombre"] as? String ?: "",
                empresa = m["empresa"] as? String ?: "",
                telefono = m["telefono"] as? String ?: "",
                cargo = m["cargo"] as? String ?: "",
                detalle = m["detalle"] as? String ?: ""
            )
        }
    }

fun formatoMiles(valor: Int): String {
    val s = valor.toString()
    return s.reversed().chunked(3).joinToString(".").reversed()
}

private fun parseFechaMillis(fecha: String): Long? {
    if (fecha.isBlank()) return null
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(fecha)?.time
    } catch (e: Exception) { null }
}

private fun millisAFecha(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return fmt.format(java.util.Date(millis))
}

fun formatFechaChilena(fecha: String): String {
    if (fecha.isBlank()) return ""
    val parts = fecha.split("-")
    return if (parts.size == 3 && parts[0].length == 4) "${parts[2]}/${parts[1]}/${parts[0]}" else fecha
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CuidaleDatePickerDialog(
    initialDate: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = parseFechaMillis(initialDate),
        yearRange = 1930..currentYear
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onDateSelected(millisAFecha(it)) }
                onDismiss()
            }) { Text(stringResource(R.string.action_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuscadorOpciones(
    label: String,
    opciones: List<String>,
    valor: String,
    onSeleccionar: (String) -> Unit,
    containerColor: Color,
    textColor: Color,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(valor) }
    val filtradas = opciones.filter { it.contains(query, ignoreCase = true) }.take(8)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                focusedBorderColor = accentColor,
                unfocusedTextColor = textColor,
                focusedTextColor = textColor
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (filtradas.isEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.ficha_sin_coincidencias)) }, onClick = { expanded = false })
            } else {
                filtradas.forEach { opcion ->
                    DropdownMenuItem(text = { Text(opcion) }, onClick = {
                        query = opcion
                        onSeleccionar(opcion)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun CampoFechaEdicion(
    label: String,
    valor: String,
    onSeleccionar: (String) -> Unit,
    accentColor: Color,
    containerColor: Color,
    textColor: Color
) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        InputFieldLabel(text = label, color = textColor)
        Box {
            CuidaleTextField(
                value = if (valor.isBlank()) stringResource(R.string.caregiver_edit_seleccionar_fecha) else formatFechaChilena(valor),
                onValueChange = {},
                placeholder = stringResource(R.string.caregiver_edit_fecha_formato_placeholder),
                singleLine = true,
                enabled = false,
                containerColor = containerColor,
                textColor = if (valor.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else textColor,
                trailingIcon = {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null, tint = accentColor)
                    }
                }
            )
        }
        if (showPicker) {
            CuidaleDatePickerDialog(
                initialDate = valor,
                onDateSelected = { onSeleccionar(it); showPicker = false },
                onDismiss = { showPicker = false }
            )
        }
    }
}

private fun formatoDesdeNombre(nombre: String): String {
    val ext = nombre.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "pdf"
        "doc" -> "doc"
        "docx" -> "docx"
        "png" -> "png"
        "jpg", "jpeg" -> "jpg"
        else -> ext.ifBlank { "archivo" }
    }
}

@Composable
fun CaregiverEditFlow(
    viewModel: FichaViewModel,
    availableCommunes: List<String>,
    initialData: Map<String, Any>?,
    cardColor: Color,
    accentColor: Color,
    onFinishSuccess: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val isDark = customColors.isDark
    
    var step by remember { mutableIntStateOf(1) }
    var especializacion by remember { mutableStateOf(initialData?.get("especializacion") as? String ?: "") }
    var selectedCommunes by remember { mutableStateOf<List<String>>(initialData?.get("comunas") as? List<String> ?: emptyList()) }
    var selectedHorarios by remember { mutableStateOf<List<String>>(initialData?.get("horarios") as? List<String> ?: emptyList()) }
    var descripcion by remember { mutableStateOf(initialData?.get("descripcion") as? String ?: "") }

    var fechaNacimiento by remember { mutableStateOf(initialData?.get("fechaNacimiento") as? String ?: "") }
    var discapacidadBool by remember { mutableStateOf(initialData?.get("discapacidad") as? Boolean ?: false) }
    var tipoDiscapacidad by remember { mutableStateOf(initialData?.get("tipoDiscapacidad") as? String ?: "") }
    var tiempoDiscapacidad by remember { mutableStateOf(initialData?.get("tiempoDiscapacidad") as? String ?: "") }
    var valorHoraReferencial by remember { mutableStateOf((initialData?.get("valorHoraReferencial") as? Number)?.toInt() ?: 0) }
    var formacionesEditadas by remember { mutableStateOf(parseFormacionesDeMapa(initialData?.get("formacionAcademica") as? List<*> ?: emptyList<Any>())) }
    var experienciasEditadas by remember { mutableStateOf(parseExperienciasDeMapa(initialData?.get("experienciasLaborales") as? List<*> ?: emptyList<Any>())) }
    var referenciasTexto by remember { mutableStateOf(initialData?.get("referencias_texto") as? String ?: "") }
    var errorEdadMinima by remember { mutableStateOf(false) }


    var certificadosList by remember { mutableStateOf(parseCertificadosDeMapa(initialData?.get("certificados") as? List<*> ?: emptyList<Any>())) }
    var uploadingCertificado by remember { mutableStateOf(false) }
    var showFechaPicker by remember { mutableStateOf(false) }
    var subSeccion by remember { mutableIntStateOf(0) }
    var formacionesEnCurso by remember { mutableStateOf(formacionesEditadas.map { it.anioFin.isBlank() }) }
    var experienciasActivas by remember { mutableStateOf(experienciasEditadas.map { it.hasta.isBlank() }) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val certificadoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                uploadingCertificado = true
                try {
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                    val resolver = context.contentResolver
                    val originalName = runCatching {
                        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull() ?: "certificado_${System.currentTimeMillis()}"

                    val ext = formatoDesdeNombre(originalName)
                    val allowed = setOf("pdf", "doc", "docx", "png", "jpg")
                    if (ext !in allowed) {
                        android.widget.Toast.makeText(context, ErrorMessages.resolve(context, ErrorCodes.Storage.INVALID_FORMAT), android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                    if (size > 5 * 1024 * 1024) {
                        android.widget.Toast.makeText(context, ErrorMessages.resolve(context, ErrorCodes.Storage.FILE_TOO_LARGE), android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                    val safeName = "${System.currentTimeMillis()}_$originalName".replace(" ", "_")
                    val ref = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                        .child("certificados/$uid/$safeName")
                    ref.putBytes(bytes).await()
                    val url = ref.downloadUrl.await().toString()
                    certificadosList = certificadosList + Certificado(
                        id = java.util.UUID.randomUUID().toString(),
                        nombre = originalName,
                        tipoArchivo = ext,
                        urlArchivo = url,
                        storagePath = ref.path,
                        fechaSubida = com.google.firebase.Timestamp.now(),
                        estadoValidacion = "pendiente"
                    )
                    android.widget.Toast.makeText(context, context.getString(R.string.caregiver_edit_certificado_subido), android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, ErrorMessages.resolve(context, ErrorCodes.Storage.UPLOAD_FAILED), android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    uploadingCertificado = false
                }
            }
        }
    }

    fun eliminarCertificado(c: Certificado) {
        certificadosList = certificadosList.filter { it.id != c.id }
        if (c.storagePath.isNotBlank()) {
            scope.launch {
                runCatching { com.google.firebase.storage.FirebaseStorage.getInstance().getReference(c.storagePath).delete().await() }
            }
        }
    }

    fun verCertificado(c: Certificado) {
        val url = c.urlArchivo
        if (url.isNotBlank()) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    Column {
        com.example.cuidaleapp.ui.components.VerificationBadge(initialData?.get("verificacionOficial") as? String ?: "pendiente")
        Spacer(modifier = Modifier.height(12.dp))
        if (step == 1) {
            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.ficha_formation_area), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    CuidaleExposedDropdown(
                        label = stringResource(R.string.ficha_specialty),
                        options = listOf(
                            stringResource(R.string.ficha_specialty_nurse),
                            stringResource(R.string.ficha_specialty_technician),
                            stringResource(R.string.ficha_specialty_elderly),
                            stringResource(R.string.ficha_specialty_children),
                            stringResource(R.string.common_other)
                        ),
                        selectedOption = especializacion,
                        onOptionSelected = { especializacion = it }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Spacer(modifier = Modifier.height(32.dp))
                        HardShadowButton(
                            text = stringResource(R.string.action_next), onClick = { step = 2 },
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            isDark = isDark
                        )
                }
            }
        } else if (step == 2) {
            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EditNote, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.ficha_personal_presentation), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    InputFieldLabel(text = stringResource(R.string.ficha_about_you), color = textColor)
                    CuidaleTextField(
                        value = descripcion, onValueChange = { if(it.length <= 300) descripcion = it },
                        placeholder = stringResource(R.string.ficha_vocation_placeholder),
                        singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )
                    Text(stringResource(R.string.caregiver_edit_contador_caracteres, descripcion.length), modifier = Modifier.align(Alignment.End).padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = secondaryTextColor.copy(alpha = 0.8f))

                    Spacer(modifier = Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HardShadowButton(
                            text = stringResource(R.string.action_back),
                            onClick = { step = 1 },
                            modifier = Modifier.weight(1f).height(58.dp),
                            containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                            contentColor = if (isDark) Color.White else Color.DarkGray,
                            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = if (isDark) 0.15f else 0.1f),
                            isDark = isDark
                        )
                        HardShadowButton(
                            text = stringResource(R.string.action_next),
                            onClick = { step = 3 },
                            modifier = Modifier.weight(1.5f).height(58.dp),
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            isDark = isDark
                        )
                    }
                }
            }
        } else {
            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Badge, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.caregiver_edit_datos_adicionales), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { subSeccion = 0 },
                            color = if (subSeccion == 0) accentColor.copy(alpha = 0.18f) else customColors.inputBackground,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                stringResource(R.string.caregiver_edit_datos_personales),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (subSeccion == 0) accentColor else secondaryTextColor
                            )
                        }
                        Surface(
                            onClick = { subSeccion = 1 },
                            color = if (subSeccion == 1) accentColor.copy(alpha = 0.18f) else customColors.inputBackground,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                stringResource(R.string.caregiver_edit_trayectoria),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (subSeccion == 1) accentColor else secondaryTextColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    if (subSeccion == 0) {
                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_fecha_nacimiento_label), color = textColor)
                    Box {
                        CuidaleTextField(
                            value = if (fechaNacimiento.isBlank()) stringResource(R.string.caregiver_edit_selecciona_fecha_nacimiento) else formatFechaChilena(fechaNacimiento),
                            onValueChange = {},
                            placeholder = stringResource(R.string.caregiver_edit_fecha_formato_placeholder),
                            singleLine = true,
                            enabled = false,
                            containerColor = customColors.inputBackground,
                            textColor = if (fechaNacimiento.isBlank()) secondaryTextColor else textColor,
                            trailingIcon = {
                                IconButton(onClick = { showFechaPicker = true }) {
                                    Icon(Icons.Default.CalendarMonth, stringResource(R.string.caregiver_edit_abrir_calendario), tint = accentColor)
                                }
                            }
                        )
                    }
                    Text(stringResource(R.string.caregiver_edit_edad_visible, com.example.cuidaleapp.ui.components.calcularEdad(fechaNacimiento)), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                    if (errorEdadMinima) {
                        Text(stringResource(R.string.caregiver_edit_error_edad_minima), modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }

                    if (showFechaPicker) {
                        CuidaleDatePickerDialog(
                            initialDate = fechaNacimiento,
                            onDateSelected = { fecha ->
                                val edadTexto = com.example.cuidaleapp.ui.components.calcularEdad(fecha).removeSuffix(" años")
                                val edadNum = edadTexto.toIntOrNull()
                                if (edadNum != null && edadNum < 18) {
                                    errorEdadMinima = true
                                } else {
                                    errorEdadMinima = false
                                    fechaNacimiento = fecha
                                }
                            },
                            onDismiss = { showFechaPicker = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = discapacidadBool, onCheckedChange = { discapacidadBool = it })
                        Text(stringResource(R.string.caregiver_edit_discapacidad_pregunta), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    if (discapacidadBool) {
                        CuidaleTextField(
                            value = tipoDiscapacidad,
                            onValueChange = { tipoDiscapacidad = it },
                            placeholder = stringResource(R.string.caregiver_edit_tipo_discapacidad_ph),
                            singleLine = true,
                            containerColor = customColors.inputBackground,
                            textColor = textColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CuidaleTextField(
                            value = tiempoDiscapacidad,
                            onValueChange = { tiempoDiscapacidad = it },
                            placeholder = stringResource(R.string.caregiver_edit_tiempo_discapacidad_ph),
                            singleLine = true,
                            containerColor = customColors.inputBackground,
                            textColor = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_valor_hora), color = textColor)
                    CuidaleTextField(
                        value = if (valorHoraReferencial > 0) formatoMiles(valorHoraReferencial) else "",
                        onValueChange = { valorHoraReferencial = it.filter(Char::isDigit).take(6).toIntOrNull() ?: 0 },
                        placeholder = stringResource(R.string.caregiver_edit_valor_hora_ph_miles),
                        singleLine = true,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )

                    }
                    if (subSeccion == 1) {

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.caregiver_edit_formacion_titulo), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
                        TextButton(onClick = { formacionesEditadas = formacionesEditadas + FormacionAcademica() }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = accentColor)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.caregiver_edit_agregar), color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (formacionesEditadas.isEmpty()) {
                        Surface(color = customColors.inputBackground, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.caregiver_edit_sin_formacion), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = secondaryTextColor.copy(alpha = 0.7f))
                        }
                    } else {
                        formacionesEditadas.forEachIndexed { index, item ->
                            Surface(color = customColors.inputBackground, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.caregiver_edit_formacion_item, index + 1), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { formacionesEditadas = formacionesEditadas.filterIndexed { i, _ -> i != index } }) {
                                            Icon(Icons.Default.Delete, stringResource(R.string.caregiver_edit_eliminar_formacion_cd), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_titulo_carrera), color = textColor)
                                    CuidaleTextField(
                                        value = item.titulo,
                                        onValueChange = { nuevo -> formacionesEditadas = formacionesEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(titulo = nuevo) else elemento } },
                                        placeholder = stringResource(R.string.caregiver_edit_titulo_carrera_ph),
                                        singleLine = true,
                                        containerColor = customColors.panelBackground,
                                        textColor = textColor
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_institucion), color = textColor)
                                    BuscadorOpciones(
                                        label = stringResource(R.string.caregiver_edit_buscar_institucion),
                                        opciones = InstitucionesMedicas.lista,
                                        valor = item.institucion,
                                        onSeleccionar = { nuevo -> formacionesEditadas = formacionesEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(institucion = nuevo) else elemento } },
                                        containerColor = customColors.panelBackground,
                                        textColor = textColor,
                                        accentColor = accentColor
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CampoFechaEdicion(
                                            label = stringResource(R.string.caregiver_edit_fecha_inicio),
                                            valor = item.anioInicio,
                                            onSeleccionar = { nuevo -> formacionesEditadas = formacionesEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(anioInicio = nuevo) else elemento } },
                                            accentColor = accentColor,
                                            containerColor = customColors.panelBackground,
                                            textColor = textColor
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = formacionesEnCurso.getOrElse(index) { item.anioFin.isBlank() },
                                                onCheckedChange = { enCurso ->
                                                    val updatedFlags = formacionesEnCurso.toMutableList()
                                                    while (updatedFlags.size <= index) updatedFlags.add(false)
                                                    updatedFlags[index] = enCurso
                                                    formacionesEnCurso = updatedFlags
                                                    formacionesEditadas = formacionesEditadas.mapIndexed { i, elemento ->
                                                        if (i == index) elemento.copy(anioFin = if (enCurso) "" else elemento.anioFin) else elemento
                                                    }
                                                }
                                            )
                                            Text(stringResource(R.string.caregiver_edit_actualmente_cursando), style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                                        }
                                        if (!formacionesEnCurso.getOrElse(index) { item.anioFin.isBlank() }) {
                                            CampoFechaEdicion(
                                                label = stringResource(R.string.caregiver_edit_fecha_termino),
                                                valor = item.anioFin,
                                                onSeleccionar = { nuevo -> formacionesEditadas = formacionesEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(anioFin = nuevo) else elemento } },
                                                accentColor = accentColor,
                                                containerColor = customColors.panelBackground,
                                                textColor = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }


                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.caregiver_edit_experiencia_titulo), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
                        TextButton(onClick = { experienciasEditadas = experienciasEditadas + ExperienciaLaboral() }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = accentColor)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.caregiver_edit_agregar), color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (experienciasEditadas.isEmpty()) {
                        Surface(color = customColors.inputBackground, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.caregiver_edit_sin_experiencia), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = secondaryTextColor.copy(alpha = 0.7f))
                        }
                    } else {
                        experienciasEditadas.forEachIndexed { index, item ->
                            Surface(color = customColors.inputBackground, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.caregiver_edit_experiencia_item, index + 1), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { experienciasEditadas = experienciasEditadas.filterIndexed { i, _ -> i != index } }) {
                                            Icon(Icons.Default.Delete, stringResource(R.string.caregiver_edit_eliminar_experiencia_cd), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_cargo), color = textColor)
                                    CuidaleTextField(
                                        value = item.cargo,
                                        onValueChange = { nuevo -> experienciasEditadas = experienciasEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(cargo = nuevo) else elemento } },
                                        placeholder = stringResource(R.string.caregiver_edit_cargo_ph),
                                        singleLine = true,
                                        containerColor = customColors.panelBackground,
                                        textColor = textColor
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_empresa), color = textColor)
                                    CuidaleTextField(
                                        value = item.empresa,
                                        onValueChange = { nuevo -> experienciasEditadas = experienciasEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(empresa = nuevo) else elemento } },
                                        placeholder = stringResource(R.string.caregiver_edit_empresa_ph),
                                        singleLine = true,
                                        containerColor = customColors.panelBackground,
                                        textColor = textColor
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CampoFechaEdicion(
                                            label = stringResource(R.string.caregiver_edit_desde),
                                            valor = item.desde,
                                            onSeleccionar = { nuevo -> experienciasEditadas = experienciasEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(desde = nuevo) else elemento } },
                                            accentColor = accentColor,
                                            containerColor = customColors.panelBackground,
                                            textColor = textColor
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = experienciasActivas.getOrElse(index) { item.hasta.isBlank() },
                                                onCheckedChange = { actual ->
                                                    val updatedFlags = experienciasActivas.toMutableList()
                                                    while (updatedFlags.size <= index) updatedFlags.add(false)
                                                    updatedFlags[index] = actual
                                                    experienciasActivas = updatedFlags
                                                    experienciasEditadas = experienciasEditadas.mapIndexed { i, elemento ->
                                                        if (i == index) elemento.copy(hasta = if (actual) "" else elemento.hasta) else elemento
                                                    }
                                                }
                                            )
                                            Text(stringResource(R.string.caregiver_edit_sigo_trabajando), style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                                        }
                                        if (!experienciasActivas.getOrElse(index) { item.hasta.isBlank() }) {
                                            CampoFechaEdicion(
                                                label = stringResource(R.string.caregiver_edit_hasta),
                                                valor = item.hasta,
                                                onSeleccionar = { nuevo -> experienciasEditadas = experienciasEditadas.mapIndexed { i, elemento -> if (i == index) elemento.copy(hasta = nuevo) else elemento } },
                                                accentColor = accentColor,
                                                containerColor = customColors.panelBackground,
                                                textColor = textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_referencias), color = textColor)
                    CuidaleTextField(
                        value = referenciasTexto,
                        onValueChange = { referenciasTexto = it },
                        placeholder = stringResource(R.string.caregiver_edit_referencias_ph),
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )


                    Spacer(modifier = Modifier.height(20.dp))
                    InputFieldLabel(text = stringResource(R.string.caregiver_edit_certificados), color = textColor)
                    if (certificadosList.isEmpty()) {
                        Surface(
                            color = customColors.inputBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.caregiver_edit_sin_certificados),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        certificadosList.forEach { c ->
                            Surface(
                                color = customColors.inputBackground,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = accentColor.copy(alpha = 0.12f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Description, null, modifier = Modifier.size(19.dp), tint = accentColor)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.nombre.ifBlank { stringResource(R.string.caregiver_edit_documento) }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            stringResource(R.string.caregiver_edit_certificado_formato_estado, c.tipoArchivo.uppercase(), c.estadoValidacion.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = secondaryTextColor
                                        )
                                    }
                                    IconButton(onClick = { verCertificado(c) }, enabled = !uploadingCertificado) {
                                        Icon(Icons.Default.Visibility, stringResource(R.string.caregiver_edit_ver_certificado_cd), tint = accentColor, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { eliminarCertificado(c) }, enabled = !uploadingCertificado) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.caregiver_edit_eliminar_certificado_cd), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HardShadowButton(
                        text = if (uploadingCertificado) stringResource(R.string.caregiver_edit_subiendo) else stringResource(R.string.caregiver_edit_subir_certificado),
                        onClick = {
                            certificadoPicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/png", "image/jpeg"))
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        containerColor = accentColor,
                        contentColor = Color.White,
                        shadowColor = shadowPalette.hardShadowPrimary,
                        isDark = isDark,
                        icon = Icons.Default.Add,
                        enabled = !uploadingCertificado
                    )

                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HardShadowButton(
                            text = stringResource(R.string.action_back),
                            onClick = { if (subSeccion == 1) subSeccion = 0 else step = 2 },
                            modifier = Modifier.weight(1f).height(58.dp),
                            containerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                            contentColor = if (isDark) Color.White else Color.DarkGray,
                            shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = if (isDark) 0.15f else 0.1f),
                            isDark = isDark
                        )
                        HardShadowButton(
                            text = if (subSeccion == 0) stringResource(R.string.action_next) else stringResource(R.string.action_finish),
                            onClick = {
                                if (subSeccion == 0) {
                                    subSeccion = 1
                                } else {
                                    val edadTexto = com.example.cuidaleapp.ui.components.calcularEdad(fechaNacimiento).removeSuffix(" años")
                                    if (fechaNacimiento.isNotBlank() && (edadTexto.toIntOrNull() ?: 0) < 18) {
                                        errorEdadMinima = true
                                    } else {
                                        errorEdadMinima = false
                                        viewModel.createCaregiverFicha(
                                            especializacion,
                                            "",
                                            selectedCommunes,
                                            selectedHorarios,
                                            descripcion,
                                            fechaNacimiento = fechaNacimiento.trim(),
                                            discapacidad = discapacidadBool,
                                            tipoDiscapacidad = tipoDiscapacidad.trim(),
                                            tiempoDiscapacidad = tiempoDiscapacidad.trim(),
                                            valorHoraReferencial = valorHoraReferencial,
                                            formacionAcademica = formacionesEditadas,
                                            experienciasLaborales = experienciasEditadas,
                                            referenciasLaborales = parseReferencias(referenciasTexto),
                                            certificados = certificadosList,
                                            onSuccess = onFinishSuccess
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.5f).height(58.dp),
                            containerColor = accentColor,
                            contentColor = Color.White,
                            shadowColor = shadowPalette.hardShadowPrimary,
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun CaregiverFichaView(
    data: Map<String, Any>,
    tareasPlantilla: List<Tarea>,
    accentColor: Color,
    onEdit: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadows = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val fechaNacimiento = data["fechaNacimiento"] as? String ?: ""
    val valorHora = (data["valorHoraReferencial"] as? Number)?.toInt() ?: 0
    val discapBool = data["discapacidad"] as? Boolean ?: false
    val tipoDiscap = data["tipoDiscapacidad"] as? String ?: ""
    val tiempoDiscap = data["tiempoDiscapacidad"] as? String ?: ""
    val especialidad = data["especializacion"] as? String ?: ""
    val experiencia = data["experiencia"] as? String ?: ""
    val comunas = (data["comunas"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val horarios = (data["horarios"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val descripcion = data["descripcion"] as? String ?: ""
    val formaciones = parseFormacionesDeMapa(data["formacionAcademica"] as? List<*> ?: emptyList<Any>())
    val experiencias = parseExperienciasDeMapa(data["experienciasLaborales"] as? List<*> ?: emptyList<Any>())
    val referencias = parseReferenciasDeMapa(data["referenciasLaborales"] as? List<*> ?: emptyList<Any>())
    val certificados = parseCertificadosDeMapa(data["certificados"] as? List<*> ?: emptyList<Any>())

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.12f), cornerRadius = 24.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(10.dp), color = accentColor.copy(alpha = 0.12f)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Badge, null, tint = accentColor) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.caregiver_view_mi_ficha), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                        Text(stringResource(R.string.caregiver_view_perfil_profesional), style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                    }
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.caregiver_view_editar), fontWeight = FontWeight.Bold, color = accentColor) }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.06f))
                Spacer(Modifier.height(12.dp))
                DetailRow(stringResource(R.string.caregiver_view_especialidad), especialidad.ifBlank { stringResource(R.string.caregiver_profile_no_especificada) }, accentColor)
                DetailRow(
                    stringResource(R.string.caregiver_view_experiencia),
                    if (experiencias.isNotEmpty()) "${experiencias.size} ${if (experiencias.size == 1) stringResource(R.string.caregiver_view_experiencia_registrada) else stringResource(R.string.caregiver_view_experiencias_registradas)}" else experiencia.ifBlank { stringResource(R.string.caregiver_profile_no_especificada) },
                    accentColor
                )
                DetailRow(stringResource(R.string.caregiver_view_trabaja_en), comunas.joinToString(", ").ifEmpty { stringResource(R.string.common_not_specified) }, accentColor)
                DetailRow(stringResource(R.string.caregiver_view_disponibilidad), horarios.joinToString(", ").ifEmpty { stringResource(R.string.common_not_specified) }, accentColor)
                DetailRow(stringResource(R.string.caregiver_profile_edad), com.example.cuidaleapp.ui.components.calcularEdad(fechaNacimiento), accentColor)
                DetailRow(stringResource(R.string.caregiver_profile_valor_hora), if (valorHora > 0) stringResource(R.string.caregiver_view_valor_hora_formato, formatoMiles(valorHora)) else stringResource(R.string.caregiver_profile_a_convenir), accentColor)
                DetailRow(stringResource(R.string.caregiver_profile_discapacidad), if (discapBool) (if (tiempoDiscap.isNotBlank()) stringResource(R.string.caregiver_view_discapacidad_detalle, tipoDiscap, tiempoDiscap) else tipoDiscap) else stringResource(R.string.caregiver_profile_no_declara), accentColor)
            }
        }

        if (descripcion.isNotBlank()) {
            HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.caregiver_view_sobre_mi), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor)
                    Spacer(Modifier.height(8.dp))
                    Text(descripcion, style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
        }

        FormacionAcademicaSection(formaciones)
        ExperienciasLaboralesSection(experiencias)
        CertificadosSection(certificados)
        ReferenciasLaboralesSection(referencias)

        if (tareasPlantilla.isNotEmpty()) {
            HardShadowPanel(backgroundColor = customColors.panelBackground, shadowColor = shadows.hardShadowPrimary.copy(alpha = 0.1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.caregiver_view_regimen_tareas), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor)
                    Spacer(Modifier.height(10.dp))
                    tareasPlantilla.forEach { tarea ->
                        TaskItemCardInView(tarea, accentColor, textColor, secondaryTextColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, accentColor: Color) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(16.dp).background(accentColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = secondaryTextColor, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyEditFlow(
    viewModel: FichaViewModel,
    profileViewModel: ProfileViewModel,
    availableCommunes: List<String>,
    initialData: Map<String, Any>?,
    tareasTemporales: List<Tarea>,
    cardColor: Color,
    accentColor: Color,
    onFinishSuccess: () -> Unit
) {
    val customColors = CuidaleThemeExtras.customColors
    val shadowPalette = CuidaleThemeExtras.shadows
    val textColor = MaterialTheme.colorScheme.onSurface
    val isDark = customColors.isDark

    val currentUserData by profileViewModel.userData.collectAsState()
    
    var nombreCompleto by remember { mutableStateOf(initialData?.get("nombre_completo") as? String ?: "") }
    var edad by remember { mutableStateOf(initialData?.get("edad") as? String ?: "") }
    var genero by remember { mutableStateOf(initialData?.get("genero") as? String ?: "") }
    var vivesConFamiliar by remember { mutableStateOf(initialData?.get("vives_con_familiar") as? Boolean ?: false) }
    var region by remember { mutableStateOf(initialData?.get("region") as? String ?: "") }
    var comuna by remember { mutableStateOf(initialData?.get("comuna") as? String ?: "") }
    var calle by remember { mutableStateOf(initialData?.get("calle") as? String ?: "") }
    var numero by remember { mutableStateOf(initialData?.get("numero") as? String ?: "") }
    var depto by remember { mutableStateOf(initialData?.get("depto") as? String ?: "") }
    var nivelDependencia by remember { mutableStateOf(initialData?.get("nivel_dependencia") as? String ?: "Independiente") }
    var movilidad by remember { mutableStateOf(initialData?.get("movilidad") as? String ?: "Normal") }
    var comunicacion by remember { mutableStateOf(initialData?.get("comunicacion") as? String ?: "Normal") }
    var discapacidades by remember { mutableStateOf(initialData?.get("discapacidades") as? List<String> ?: emptyList()) }
    var discapacidadOtro by remember { mutableStateOf(initialData?.get("discapacidad_otro") as? String ?: "") }
    var enfermedades by remember { mutableStateOf(initialData?.get("enfermedades") as? List<String> ?: emptyList()) }
    var enfermedadOtro by remember { mutableStateOf(initialData?.get("enfermedad_otro") as? String ?: "") }
    
    var condicionesExtra by remember { mutableStateOf(initialData?.get("condiciones") as? String ?: "") }
    var selectedCommunesPref by remember { mutableStateOf(initialData?.get("comunas_preferencia") as? List<String> ?: emptyList()) }
    var selectedHorariosPref by remember { mutableStateOf(initialData?.get("horarios_preferencia") as? List<String> ?: emptyList()) }
    
    val pendingPhoto by viewModel.pendingPatientPhotoUri.collectAsState()
    
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf(false) }
    var showConfirmPhotoDialog by remember { mutableStateOf(false) }
    var photoToConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> 
        uri?.let {
            photoToConfirm = it
            showConfirmPhotoDialog = true
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempPhotoUri?.let {
                photoToConfirm = it
                showConfirmPhotoDialog = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "temp_patient_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    if (showConfirmPhotoDialog && photoToConfirm != null) {
        AlertDialog(
            onDismissRequest = { 
                showConfirmPhotoDialog = false
                photoToConfirm = null
            },
            title = { Text(stringResource(R.string.edit_profile_confirm_photo), fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = photoToConfirm,
                        contentDescription = null,
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ficha_confirm_photo_question), textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPendingPatientPhoto(photoToConfirm)
                    pendingDeletePhoto = false
                    showConfirmPhotoDialog = false
                    photoToConfirm = null
                }) {
                    Text(stringResource(R.string.edit_profile_use_this))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showConfirmPhotoDialog = false
                    photoToConfirm = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showImageSourceDialog) {
        val currentPhoto = initialData?.get("fotoUrl")?.toString() ?: ""
        PhotoSourcePickerDialog(
            title = stringResource(R.string.ficha_patient_photo),
            hasCurrentPhoto = currentPhoto.isNotEmpty() || pendingPhoto != null,
            onCamera = {
                val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                    val file = File(context.cacheDir, "temp_patient_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    tempPhotoUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onGallery = { galleryLauncher.launch("image/*") },
            onRemove = {
                pendingDeletePhoto = true
                viewModel.setPendingPatientPhoto(null)
            },
            onDismiss = { showImageSourceDialog = false }
        )
    }

    LaunchedEffect(vivesConFamiliar, currentUserData.region, currentUserData.comuna) {
        if (vivesConFamiliar) {
            region = currentUserData.region
            comuna = currentUserData.comuna
        }
    }

    val regionsList = ChileData.regions.map { it.name }
    val communesForRegion = ChileData.regions.find { it.name == region }?.comunas?.sorted() ?: emptyList()

    val isStep1Valid = nombreCompleto.isNotBlank() && edad.isNotBlank() && genero.isNotBlank() &&
        region.isNotBlank() && comuna.isNotBlank() && calle.isNotBlank() && numero.isNotBlank()

    Column {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.ficha_patient_data), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    InputFieldLabel(text = stringResource(R.string.ficha_patient_name), color = textColor)
                    CuidaleTextField(
                        value = nombreCompleto,
                        onValueChange = { input -> 
                            nombreCompleto = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
                        },
                        placeholder = stringResource(R.string.ficha_patient_name_placeholder),
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    var showFechaPickerPaciente by remember { mutableStateOf(false) }
                    var pacienteFechaNacimiento by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            InputFieldLabel(text = stringResource(R.string.ficha_age), color = textColor)
                            CuidaleTextField(
                                value = edad,
                                onValueChange = { if ((it.isEmpty() || it.all { char -> char.isDigit() }) && it.length <= 3) edad = it },
                                placeholder = stringResource(R.string.ficha_age_placeholder),
                                containerColor = customColors.inputBackground,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                textColor = textColor,
                                textAlign = TextAlign.Center
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { showFechaPickerPaciente = true }) {
                                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp), tint = accentColor)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.family_edit_usar_calendario), style = MaterialTheme.typography.labelSmall, color = accentColor)
                                }
                                if (pacienteFechaNacimiento.isNotBlank()) {
                                    Text(formatFechaChilena(pacienteFechaNacimiento), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (showFechaPickerPaciente) {
                                CuidaleDatePickerDialog(
                                    initialDate = pacienteFechaNacimiento,
                                    onDateSelected = { fecha ->
                                        pacienteFechaNacimiento = fecha
                                        val age = com.example.cuidaleapp.ui.components.calcularEdad(fecha).removeSuffix(" años")
                                        edad = age.takeIf { it != "--" } ?: edad
                                    },
                                    onDismiss = { showFechaPickerPaciente = false }
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1.5f)) {
                            CuidaleExposedDropdown(label = stringResource(R.string.ficha_gender), options = listOf(stringResource(R.string.ficha_gender_male), stringResource(R.string.ficha_gender_female), stringResource(R.string.common_other)), selectedOption = genero, onOptionSelected = { genero = it })
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = textColor.copy(alpha = if (isDark) 0.15f else 0.1f))
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.ficha_location), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.ficha_lives_with_you), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = vivesConFamiliar, 
                            onCheckedChange = { vivesConFamiliar = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                        )
                    }
                    
                    if (!vivesConFamiliar) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CuidaleExposedDropdown(label = stringResource(R.string.ficha_region), options = regionsList, selectedOption = region, onOptionSelected = { region = it })
                        Spacer(modifier = Modifier.height(12.dp))
                        CuidaleExposedDropdown(label = stringResource(R.string.ficha_commune), options = communesForRegion, selectedOption = comuna, onOptionSelected = { comuna = it }, enabled = region.isNotBlank())
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = accentColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.ficha_uses_current_address, currentUserData.comuna, currentUserData.region),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    InputFieldLabel(text = stringResource(R.string.ficha_patient_address), color = textColor)
                    CuidaleTextField(
                        value = calle,
                        onValueChange = { input -> 
                            calle = input.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                        },
                        placeholder = stringResource(R.string.ficha_street_placeholder),
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CuidaleTextField(
                            value = numero,
                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 6) numero = it },
                            placeholder = stringResource(R.string.ficha_number_placeholder),
                            modifier = Modifier.weight(1f),
                            containerColor = customColors.inputBackground,
                            textColor = textColor,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                        CuidaleTextField(
                            value = depto,
                            onValueChange = { input ->
                                depto = input.uppercase()
                            },
                            placeholder = stringResource(R.string.ficha_dept_placeholder),
                            modifier = Modifier.weight(1f),
                            containerColor = customColors.inputBackground,
                            textColor = textColor
                        )
                    }
                }
            }

            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.ficha_health_status), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    CuidaleExposedDropdown(label = stringResource(R.string.ficha_dependency), options = listOf(stringResource(R.string.ficha_dependency_independent), stringResource(R.string.ficha_dependency_mild), stringResource(R.string.ficha_dependency_moderate), stringResource(R.string.ficha_dependency_severe)), selectedOption = nivelDependencia, onOptionSelected = { nivelDependencia = it })
                    Spacer(modifier = Modifier.height(16.dp))
                    CuidaleExposedDropdown(label = stringResource(R.string.ficha_mobility), options = listOf(stringResource(R.string.ficha_mobility_normal), stringResource(R.string.ficha_mobility_reduced), stringResource(R.string.ficha_mobility_wheelchair), stringResource(R.string.ficha_mobility_bedridden)), selectedOption = movilidad, onOptionSelected = { movilidad = it })
                    Spacer(modifier = Modifier.height(20.dp))

                    CuidaleMultiSelectDropdown(
                        label = stringResource(R.string.ficha_disabilities),
                        options = listOf(stringResource(R.string.ficha_disability_visual), stringResource(R.string.ficha_disability_hearing), stringResource(R.string.ficha_disability_motor), stringResource(R.string.ficha_disability_intellectual), stringResource(R.string.ficha_disability_psychic), stringResource(R.string.common_other)),
                        selectedOptions = discapacidades,
                        onOptionToggled = {
                            if (discapacidades.contains(it)) discapacidades -= it
                            else discapacidades += it
                        },
                        placeholder = stringResource(R.string.ficha_disabilities_placeholder)
                    )
                    if (discapacidades.contains("Otro")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CuidaleTextField(value = discapacidadOtro, onValueChange = { discapacidadOtro = it }, placeholder = stringResource(R.string.ficha_disability_other_placeholder), containerColor = customColors.inputBackground, textColor = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    CuidaleMultiSelectDropdown(
                        label = stringResource(R.string.ficha_chronic_diseases),
                        options = listOf(stringResource(R.string.ficha_disease_diabetes), stringResource(R.string.ficha_disease_hypertension), stringResource(R.string.ficha_disease_alzheimer), stringResource(R.string.ficha_disease_parkinson), stringResource(R.string.ficha_disease_arthrosis), stringResource(R.string.common_other)),
                        selectedOptions = enfermedades,
                        onOptionToggled = {
                            if (enfermedades.contains(it)) enfermedades -= it
                            else enfermedades += it
                        },
                        placeholder = stringResource(R.string.ficha_diseases_placeholder)
                    )
                    if (enfermedades.contains("Otro")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CuidaleTextField(value = enfermedadOtro, onValueChange = { enfermedadOtro = it }, placeholder = stringResource(R.string.ficha_disease_other_placeholder), containerColor = customColors.inputBackground, textColor = textColor)
                    }
                }
            }

            HardShadowPanel(
                backgroundColor = cardColor,
                shadowColor = shadowPalette.hardShadowPrimary.copy(alpha = 0.2f),
                cornerRadius = 24.dp,
                contentPadding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = accentColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.ficha_observations_photo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    InputFieldLabel(text = stringResource(R.string.ficha_extra_observations), color = textColor)
                    CuidaleTextField(
                        value = condicionesExtra,
                        onValueChange = { condicionesExtra = it },
                        placeholder = stringResource(R.string.ficha_conditions_placeholder),
                        singleLine = false,
                        modifier = Modifier.height(100.dp),
                        containerColor = customColors.inputBackground,
                        textColor = textColor
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    InputFieldLabel(text = stringResource(R.string.ficha_patient_photo_label), color = textColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(customColors.inputBackground)
                                .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape)
                                .clickable { showImageSourceDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            val currentUrl = initialData?.get("fotoUrl")?.toString() ?: ""
                            val photoToShow = when {
                                pendingDeletePhoto -> null
                                pendingPhoto != null -> pendingPhoto
                                currentUrl.isNotEmpty() -> currentUrl
                                else -> null
                            }

                            if (photoToShow != null) {
                                AsyncImage(
                                    model = photoToShow,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.AddAPhoto, null, tint = textColor.copy(alpha = 0.3f))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (pendingDeletePhoto) stringResource(R.string.ficha_photo_will_delete) else if (pendingPhoto != null || (initialData?.get("fotoUrl")?.toString()?.isNotEmpty() == true)) stringResource(R.string.ficha_patient_photo) else stringResource(R.string.ficha_no_photo),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            TextButton(
                                onClick = { showImageSourceDialog = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(stringResource(R.string.ficha_manage_photo), color = accentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            HardShadowButton(
                text = stringResource(R.string.action_finish),
                onClick = {
                    viewModel.createFamilyFicha(
                        nombreCompleto, edad, genero, vivesConFamiliar, 
                        region, comuna, calle, numero, depto, 
                        nivelDependencia, movilidad, comunicacion, 
                        discapacidades, discapacidadOtro, enfermedades, enfermedadOtro, 
                        condicionesExtra,
                        selectedCommunesPref, selectedHorariosPref,
                        shouldDeletePhoto = pendingDeletePhoto,
                        onSuccess = onFinishSuccess
                    )
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                enabled = isStep1Valid,
                containerColor = accentColor,
                contentColor = Color.White,
                shadowColor = shadowPalette.hardShadowPrimary,
                isDark = isDark
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                content()
            }
        }
    )
}
