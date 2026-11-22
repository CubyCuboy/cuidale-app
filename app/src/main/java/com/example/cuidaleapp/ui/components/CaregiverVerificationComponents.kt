package com.example.cuidaleapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.models.*

val CERTIFICADO_FORMATOS_VALIDOS = setOf("pdf", "doc", "docx", "png", "jpg", "jpeg")

fun formatoCertificadoValido(nombreArchivo: String): Boolean {
    val ext = nombreArchivo.substringAfterLast('.', "").lowercase()
    return ext in CERTIFICADO_FORMATOS_VALIDOS
}

fun formatFechaVisual(valor: String): String {
    if (valor.isBlank()) return ""
    val parts = valor.split("-")
    return if (parts.size == 3 && parts[0].length == 4) "${parts[2]}/${parts[1]}/${parts[0]}" else valor
}

fun calcularDuracion(inicio: String, fin: String): String {
    if (inicio.isBlank()) return ""
    val parse = { s: String -> runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).parse(s) }.getOrNull() }
    val start = parse(inicio) ?: return ""
    val end = if (fin.isBlank()) java.util.Date() else (parse(fin) ?: return "")
    val calS = java.util.Calendar.getInstance().apply { time = start }
    val calE = java.util.Calendar.getInstance().apply { time = end }
    var years = calE.get(java.util.Calendar.YEAR) - calS.get(java.util.Calendar.YEAR)
    var months = calE.get(java.util.Calendar.MONTH) - calS.get(java.util.Calendar.MONTH)
    if (months < 0) { years--; months += 12 }
    return when {
        years <= 0 && months <= 0 -> "menos de un mes"
        years <= 0 -> "$months ${if (months == 1) "mes" else "meses"}"
        months <= 0 -> "$years ${if (years == 1) "año" else "años"}"
        else -> "$years ${if (years == 1) "año" else "años"} $months ${if (months == 1) "mes" else "meses"}"
    }
}

fun calcularEdad(fechaNacimiento: String): String {
    if (fechaNacimiento.isBlank()) return "--"
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val birth = fmt.parse(fechaNacimiento) ?: return "--"
        val birthCal = java.util.Calendar.getInstance().apply { time = birth }
        val nowCal = java.util.Calendar.getInstance()
        var age = nowCal.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR)
        if (nowCal.get(java.util.Calendar.DAY_OF_YEAR) < birthCal.get(java.util.Calendar.DAY_OF_YEAR)) age--
        "$age años"
    } catch (e: Exception) {
        "--"
    }
}

@Composable
fun VerificationBadge(verificacionOficial: String) {
    val verified = verificacionOficial.equals("verificado", ignoreCase = true)
    Surface(
        color = if (verified) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (verified) Icons.Default.VerifiedUser else Icons.Default.Warning,
                null,
                tint = if (verified) Color(0xFF2E7D32) else Color(0xFFE65100),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (verified) "Verificado oficialmente" else "Sin verificación oficial",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (verified) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
        }
    }
}

@Composable
fun ConsejoFotoPerfil(onUploadPhoto: () -> Unit = {}) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Consejo: agrega una foto de perfil", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("Las fichas con foto generan más confianza en los representantes.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            TextButton(onClick = onUploadPhoto) { Text("SUBIR") }
        }
    }
}

@Composable
fun CalificacionesSection(
    calificaciones: List<CalificacionPublica>,
    modifier: Modifier = Modifier
) {
    var sortOrder by remember { mutableStateOf("recientes") }
    val sorted = when (sortOrder) {
        "asc" -> calificaciones.sortedBy { it.calificacion }
        "desc" -> calificaciones.sortedByDescending { it.calificacion }
        else -> calificaciones.sortedByDescending { it.fecha?.toDate()?.time ?: 0L }
    }
    val promedio = if (calificaciones.isNotEmpty()) calificaciones.map { it.calificacion }.average() else 0.0

    SectionCard(
        title = "Calificaciones",
        subtitle = if (calificaciones.isEmpty()) null else "${calificaciones.size} ${if (calificaciones.size == 1) "reseña" else "reseñas"}",
        modifier = modifier,
        icon = Icons.Default.Star
    ) {
        if (calificaciones.isEmpty()) {
            PlaceholderText("Aún no hay calificaciones para este cuidador.")
        } else {
            Surface(
                color = Color(0xFFFFF8E1),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(String.format("%.1f", promedio), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(6.dp))
                    Text("de 5.0", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.weight(1f))
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) { Text("Ordenar", fontWeight = FontWeight.Bold) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Recientes") }, onClick = { sortOrder = "recientes"; expanded = false })
                            DropdownMenuItem(text = { Text("Ascendente") }, onClick = { sortOrder = "asc"; expanded = false })
                            DropdownMenuItem(text = { Text("Descendente") }, onClick = { sortOrder = "desc"; expanded = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            sorted.forEach { c ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(c.autorNombre.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.autorNombre.ifBlank { "Anónimo" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                c.fecha?.let {
                                    Text(java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT).format(it.toDate()), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                            Surface(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(50)) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFB300))
                                    Spacer(Modifier.width(4.dp))
                                    Text(String.format("%.1f", c.calificacion), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        if (c.observacion.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(c.observacion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormacionAcademicaSection(list: List<FormacionAcademica>, modifier: Modifier = Modifier) {
    SectionCard(
        title = "Formación académica",
        subtitle = if (list.isEmpty()) null else "${list.size} ${if (list.size == 1) "registro" else "registros"}",
        modifier = modifier,
        icon = Icons.Default.School
    ) {
        if (list.isEmpty()) {
            PlaceholderText("Sin formación registrada.")
        } else {
            list.forEach { f ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.School, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.titulo.ifBlank { "Sin título" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            if (f.institucion.isNotBlank()) {
                                Text(f.institucion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (f.anioInicio.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(50)) {
                                    Text(
                                        "${formatFechaVisual(f.anioInicio)} - ${if (f.anioFin.isBlank()) "Actualidad" else formatFechaVisual(f.anioFin)} · ${calcularDuracion(f.anioInicio, f.anioFin)}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExperienciasLaboralesSection(list: List<ExperienciaLaboral>, modifier: Modifier = Modifier) {
    SectionCard(
        title = "Experiencia laboral",
        subtitle = if (list.isEmpty()) null else "${list.size} ${if (list.size == 1) "experiencia" else "experiencias"}",
        modifier = modifier,
        icon = Icons.Default.Work
    ) {
        if (list.isEmpty()) {
            PlaceholderText("Sin experiencia laboral registrada.")
        } else {
            list.forEach { e ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Work, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.cargo.ifBlank { "Cargo" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            if (e.empresa.isNotBlank()) {
                                Text(e.empresa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (e.desde.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(50)) {
                                    Text(
                                        "${formatFechaVisual(e.desde)} - ${if (e.hasta.isBlank()) "Actualidad" else formatFechaVisual(e.hasta)} · ${calcularDuracion(e.desde, e.hasta)} ${if (e.tiempoExperiencia.isNotBlank()) "(${e.tiempoExperiencia})" else ""}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (e.descripcion.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(e.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CertificadosSection(list: List<Certificado>, modifier: Modifier = Modifier) {
    SectionCard(
        title = "Certificados y documentos",
        subtitle = if (list.isEmpty()) null else "${list.size} ${if (list.size == 1) "documento" else "documentos"}",
        modifier = modifier,
        icon = Icons.Default.Description
    ) {
        if (list.isEmpty()) {
            PlaceholderText("Sin certificados subidos.")
        } else {
            list.forEach { c ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nombre.ifBlank { "Documento" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (c.tipoArchivo.isNotBlank()) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
                                        Text(
                                            c.tipoArchivo.uppercase(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }
                                Surface(
                                    color = if (c.estadoValidacion.equals("verificado", true)) Color(0xFFE5F8E8) else Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        c.estadoValidacion.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (c.estadoValidacion.equals("verificado", true)) Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReferenciasLaboralesSection(list: List<ReferenciaLaboral>, modifier: Modifier = Modifier) {
    SectionCard(
        title = "Referencias laborales",
        subtitle = if (list.isEmpty()) null else "${list.size} ${if (list.size == 1) "referencia" else "referencias"}",
        modifier = modifier,
        icon = Icons.Default.Group
    ) {
        if (list.isEmpty()) {
            PlaceholderText("Sin referencias registradas.")
        } else {
            list.forEach { r ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.nombre.ifBlank { "Referencia" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                if (r.empresa.isNotBlank()) {
                                    Text(r.empresa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (r.cargo.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(50)) {
                                Text(
                                    r.cargo,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (r.detalle.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(r.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ValorHoraSection(valor: Int, modifier: Modifier = Modifier) {
    if (valor > 0) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachMoney, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Valor hora referencial", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$$valor/hora", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PlaceholderText(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}
