package com.example.cuidaleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cuidaleapp.models.BancosChile
import com.example.cuidaleapp.models.CuentaDestino
import com.example.cuidaleapp.ui.components.HardShadowButton
import com.example.cuidaleapp.ui.components.LogoBanco
import com.example.cuidaleapp.ui.components.LogoCuenta
import com.example.cuidaleapp.ui.theme.CuidaleThemeExtras
import com.example.cuidaleapp.utils.CheckoutLauncher

/**
 * Pantalla propia de cuentas de destino (bancarias y Mercado Pago).
 * Accesible desde la Billetera y desde el Perfil/Configuración.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentasScreen(
    cuentas: List<CuentaDestino>,
    oauthEnProceso: Boolean = false,
    onGuardar: (
        tipoDestino: String,
        banco: String?,
        tipoCuenta: String?,
        numeroCuenta: String?,
        titular: String?,
        rut: String?,
        mpIdentificador: String?
    ) -> Unit,
    onEliminar: (String) -> Unit,
    onDesconectarMp: () -> Unit = {},
    oauthMpFeedback: String? = null,
    onConsumirOauthMpFeedback: () -> Unit = {},
    cuentasFeedback: String? = null,
    onConsumirCuentasFeedback: () -> Unit = {},
    oauthMpAuthUrl: String? = null,
    onConsumirOauthMpAuthUrl: () -> Unit = {},
    abrirFormularioMpManual: Boolean = false,
    onConsumirAbrirFormularioMpManual: () -> Unit = {},
    onVincularMp: () -> Unit = {},
    onBack: () -> Unit
) {
    var mostrandoFormulario by remember { mutableStateOf(false) }
    var tipoDestinoInicial by remember { mutableStateOf(CuentaDestino.TIPO_BANCO) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(oauthMpFeedback) {
        oauthMpFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onConsumirOauthMpFeedback()
        }
    }

    LaunchedEffect(cuentasFeedback) {
        cuentasFeedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onConsumirCuentasFeedback()
        }
    }

    val context = LocalContext.current
    LaunchedEffect(oauthMpAuthUrl) {
        val url = oauthMpAuthUrl ?: return@LaunchedEffect
        if (!CheckoutLauncher.abrir(context, url)) {
            snackbarHostState.showSnackbar("No se pudo abrir la ventana de Mercado Pago.")
        }
        onConsumirOauthMpAuthUrl()
    }

    LaunchedEffect(abrirFormularioMpManual) {
        if (abrirFormularioMpManual) {
            tipoDestinoInicial = CuentaDestino.TIPO_MP
            mostrandoFormulario = true
            onConsumirAbrirFormularioMpManual()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mis cuentas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    if (mostrandoFormulario) {
                        SolidColor(MaterialTheme.colorScheme.surface)
                    } else {
                        Brush.verticalGradient(
                            listOf(CuidaleThemeExtras.gradient.top, CuidaleThemeExtras.gradient.bottom)
                        )
                    }
                )
        ) {
            if (!mostrandoFormulario) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        TarjetaMercadoPago(
                            conectada = cuentas.any { it.tipoDestino == CuentaDestino.TIPO_MP },
                            oauthEnProceso = oauthEnProceso,
                            onConectar = onVincularMp,
                            onDesconectar = onDesconectarMp
                        )
                    }

                    item {
                        Text(
                            "Cuentas bancarias",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (cuentas.none { it.tipoDestino == CuentaDestino.TIPO_BANCO }) {
                        item {
                            Text(
                                "Aún no tienes cuentas bancarias. Agrega una para retirar tu saldo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        items(cuentas.filter { it.tipoDestino == CuentaDestino.TIPO_BANCO }, key = { it.id }) { cuenta ->
                            FilaCuenta(cuenta = cuenta, onEliminar = { onEliminar(cuenta.id) })
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { mostrandoFormulario = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Agregar cuenta bancaria", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                FormularioCuenta(
                    tipoDestinoInicial = tipoDestinoInicial,
                    onCancel = { mostrandoFormulario = false },
                    onGuardar = { tipo, banco, tipoCuenta, numero, titular, rut, mp ->
                        onGuardar(tipo, banco, tipoCuenta, numero, titular, rut, mp)
                        mostrandoFormulario = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TarjetaMercadoPago(
    conectada: Boolean,
    oauthEnProceso: Boolean,
    onConectar: () -> Unit,
    onDesconectar: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF00A1E9).copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoCuenta(CuentaDestino(tipoDestino = CuentaDestino.TIPO_MP))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Mercado Pago",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        if (conectada) "Conectado — retiro rápido" else "La forma más rápida y recomendada de retirar",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                if (conectada) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (!conectada) {
                Spacer(Modifier.height(12.dp))
                HardShadowButton(
                    text = if (oauthEnProceso) "Conectando..." else "Vincular cuenta de Mercado Pago",
                    containerColor = Color(0xFF00A1E9),
                    contentColor = Color.White,
                    shadowColor = Color(0xFF00A1E9).copy(alpha = 0.3f),
                    enabled = !oauthEnProceso,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    onClick = onConectar
                )
            } else {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDesconectar,
                    enabled = !oauthEnProceso,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (oauthEnProceso) "Desconectando..." else "Desconectar Mercado Pago", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilaCuenta(cuenta: CuentaDestino, onEliminar: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoCuenta(cuenta, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cuenta.bancoNombre.ifEmpty { "Cuenta bancaria" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${cuenta.mascara} • ${cuenta.tipoCuenta.ifEmpty { "—" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEliminar) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FormularioCuenta(
    tipoDestinoInicial: String = CuentaDestino.TIPO_BANCO,
    onCancel: () -> Unit,
    onGuardar: (
        tipoDestino: String,
        banco: String?,
        tipoCuenta: String?,
        numeroCuenta: String?,
        titular: String?,
        rut: String?,
        mpIdentificador: String?
    ) -> Unit
) {
    var tipoDestino by remember { mutableStateOf(tipoDestinoInicial) }
    var bancoSel by remember { mutableStateOf(BancosChile.mapa.keys.firstOrNull() ?: "") }
    var tipoCuenta by remember { mutableStateOf("corriente") }
    var numeroCuenta by remember { mutableStateOf("") }
    var titular by remember { mutableStateOf("") }
    var rut by remember { mutableStateOf("") }
    var mpIdentificador by remember { mutableStateOf("") }

    val esBanco = tipoDestino == CuentaDestino.TIPO_BANCO
    val numeroValido = Regex("^\\d{6,12}$").matches(numeroCuenta)
    val rutValido = !esBanco || CuentaDestino.validarRut(rut)
    val mpIdValido = Regex("^\\d{4,}$").matches(mpIdentificador.trim())
    val formValido = when (tipoDestino) {
        CuentaDestino.TIPO_BANCO -> numeroValido && rutValido && titular.isNotBlank()
        else -> mpIdValido
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (esBanco) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color(0xFF00A1E9).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (esBanco) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        LogoCuenta(CuentaDestino(tipoDestino = CuentaDestino.TIPO_MP), size = 28.dp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Agregar cuenta de destino",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (esBanco) "Cuenta bancaria chilena" else "Cuenta de Mercado Pago para retiros rápidos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = esBanco,
                onClick = { tipoDestino = CuentaDestino.TIPO_BANCO },
                label = { Text("Cuenta bancaria") },
                leadingIcon = {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = !esBanco,
                onClick = { tipoDestino = CuentaDestino.TIPO_MP },
                label = { Text("Mercado Pago") },
                leadingIcon = {
                    LogoCuenta(CuentaDestino(tipoDestino = CuentaDestino.TIPO_MP), size = 20.dp)
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (esBanco) {
            Text("Banco", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BancosChile.lista) { (codigo, nombre) ->
                    val seleccionado = bancoSel == codigo
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (seleccionado) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }
                        ),
                        border = if (seleccionado) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { bancoSel = codigo }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LogoBanco(banco = codigo, bancoNombre = nombre, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = nombre,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (seleccionado) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = tipoCuenta == "corriente",
                    onClick = { tipoCuenta = "corriente" },
                    label = { Text("Corriente") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = tipoCuenta == "vista",
                    onClick = { tipoCuenta = "vista" },
                    label = { Text("Vista") },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = numeroCuenta,
                onValueChange = { numeroCuenta = it.filter(Char::isDigit).take(12) },
                label = { Text("N° de cuenta") },
                isError = numeroCuenta.isNotEmpty() && !numeroValido,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = titular,
                onValueChange = { titular = it },
                label = { Text("Titular") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = rut,
                onValueChange = { rut = it },
                label = { Text("RUT") },
                isError = rut.isNotEmpty() && !rutValido,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = mpIdentificador,
                onValueChange = { mpIdentificador = it.filter(Char::isDigit).take(20) },
                label = { Text("ID de usuario de Mercado Pago (número)") },
                isError = mpIdentificador.isNotEmpty() && !mpIdValido,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Ingresa tu User ID numérico de Mercado Pago (aparece en tu perfil de MP como \"User ID\"). Ejemplo: 3877352752. Los retiros solo se envían a IDs numéricos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mpIdentificador.isNotEmpty() && !mpIdValido) {
                Text(
                    text = "Ingresa solo números (tu User ID de Mercado Pago).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        HardShadowButton(
            text = "Guardar",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            enabled = formValido,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            onClick = {
                onGuardar(
                    tipoDestino,
                    if (esBanco) bancoSel else null,
                    if (esBanco) tipoCuenta else null,
                    if (esBanco) numeroCuenta else null,
                    if (esBanco) titular else null,
                    if (esBanco) rut else null,
                    if (!esBanco) mpIdentificador else null
                )
            }
        )
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Cancelar", fontWeight = FontWeight.Bold)
        }
        }
    }
}
