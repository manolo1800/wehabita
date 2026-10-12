package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MemberAvatar
import com.example.ui.components.AppMessagePopupDialog
import com.example.ui.components.PopupMessage
import com.example.ui.components.PopupMessageType
import com.example.ui.components.UserFriendlyErrorHelper
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WeHabitaViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var copiedText by remember { mutableStateOf<String?>(null) }
    var popupMessage by remember { mutableStateOf<PopupMessage?>(null) }

    AppMessagePopupDialog(
        popupMessage = popupMessage,
        onDismiss = { popupMessage = null }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración & Perfil", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // User Profile Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = IndigoPrimaryContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(
                            name = currentUser?.nombre ?: "Usuario",
                            size = 54,
                            isAdmin = currentUser?.rol_global == "admin"
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.nombre ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentUser?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IndigoPrimary
                                ) {
                                    Text(
                                        text = "Código: ${currentUser?.codigo_usuario}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        currentUser?.codigo_usuario?.let {
                                            clipboardManager.setText(AnnotatedString(it))
                                            copiedText = "Código de usuario copiado"
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Current Home Info
            if (currentHome != null) {
                item {
                    Text(
                        text = "Hogar Activo",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentHome!!.nombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = TealTertiaryContainer
                                ) {
                                    Text(
                                        text = currentHome!!.tipo_hogar.replaceFirstChar { it.uppercase() },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = TealTertiary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Código de invitación: ${currentHome!!.codigo_invitacion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tu rol en este hogar: ${if (currentMember?.rol == "admin") "Administrador" else "Miembro"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoPrimary
                            )
                        }
                    }
                }
            }

            // REST Services Server Connection & Offline Sync Card
            item {
                val currentRestUrl by viewModel.restServerUrl.collectAsState()
                var serverUrlInput by remember(currentRestUrl) { mutableStateOf(currentRestUrl) }
                var showUrlConfig by remember { mutableStateOf(false) }
                val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
                val isSyncing by viewModel.isSyncing.collectAsState()
                val pendingCount by viewModel.pendingSyncCount.collectAsState()
                val scope = rememberCoroutineScope()

                Text(
                    text = "Servidor de Servicios REST & Sincronización",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (pendingCount > 0) ElegantShoppingOrange else ElegantSuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (pendingCount > 0) "Modo Offline ($pendingCount pendientes)" else "REST API Conectado",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (pendingCount > 0) ElegantShoppingOrange else ElegantSuccessGreen
                                )
                            }

                            TextButton(onClick = { showUrlConfig = !showUrlConfig }) {
                                Text(if (showUrlConfig) "Ocultar URL" else "Cambiar URL", fontSize = 12.sp)
                            }
                        }

                        // Connection Info Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElegantDarkBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Endpoint: $currentRestUrl",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = ElegantPurplePrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Estado: $lastSyncStatus",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (pendingCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "⏳ $pendingCount operación(es) local(es) encoladas en SQLite esperando envío al servidor",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElegantShoppingOrange,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (showUrlConfig) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = serverUrlInput,
                                    onValueChange = { 
                                        serverUrlInput = it
                                        viewModel.updateRestServerUrl(it)
                                    },
                                    label = { Text("URL Base del Servidor REST") },
                                    placeholder = { Text("https://wehabita.mayorealo.com/api/") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.updateRestServerUrl(serverUrlInput)
                                    viewModel.syncWithRestServer { success, msg ->
                                        popupMessage = if (success) {
                                            PopupMessage(
                                                title = "Sincronización Completada",
                                                description = "Los datos del hogar se han sincronizado correctamente con el servidor.",
                                                type = PopupMessageType.SUCCESS,
                                                icon = Icons.Outlined.CheckCircle,
                                                buttonText = "Aceptar"
                                            )
                                        } else {
                                            UserFriendlyErrorHelper.formatMessage(msg, defaultTitle = "Error de Sincronización")
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = ElegantPurplePrimary, contentColor = ElegantPurpleDark),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("button_sync_rest_server")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ElegantPurpleDark)
                                } else {
                                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sincronizar Servicios", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.updateRestServerUrl(serverUrlInput)
                                    viewModel.testRestApiConnection(serverUrlInput) { success, msg ->
                                        popupMessage = if (success) {
                                            PopupMessage(
                                                title = "Conexión Exitosa",
                                                description = "El servidor REST responde correctamente y está listo para operar.",
                                                type = PopupMessageType.SUCCESS,
                                                icon = Icons.Outlined.CheckCircle,
                                                buttonText = "Excelente"
                                            )
                                        } else {
                                            UserFriendlyErrorHelper.formatMessage(msg, defaultTitle = "Fallo de Conexión")
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("button_test_rest_connection")
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Probar")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElegantDarkBackground,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💡 Servicios REST & Base de Datos Compartida:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ElegantPurplePrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• La aplicación sincroniza directamente con los servicios del hogar en tiempo real.\n• El token de autenticación y la información activa se guardan de forma segura.\n• Todas las creaciones, ediciones y penalizaciones se reflejan de inmediato para todos los integrantes.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Diagnostic Logs Section
                        val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()
                        var showLogsSection by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElegantDarkSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElegantPurplePrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.BugReport,
                                            contentDescription = null,
                                            tint = ElegantPurplePrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Registro de Diagnóstico (${diagnosticLogs.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ElegantPurplePrimary
                                        )
                                    }
                                    TextButton(onClick = { showLogsSection = !showLogsSection }) {
                                        Text(if (showLogsSection) "Ocultar" else "Ver Logs", fontSize = 12.sp)
                                    }
                                }

                                if (showLogsSection) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val text = viewModel.getDiagnosticLogsAsText()
                                                clipboardManager.setText(AnnotatedString(text))
                                                popupMessage = PopupMessage(
                                                    title = "Logs Copiados",
                                                    description = "Los registros de diagnóstico han sido copiados al portapapeles.",
                                                    type = PopupMessageType.INFO,
                                                    icon = Icons.Outlined.ContentCopy,
                                                    buttonText = "Entendido"
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copiar Logs", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.clearDiagnosticLogs() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Limpiar", fontSize = 12.sp)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = androidx.compose.ui.graphics.Color(0xFF0F0B18),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF2E2442)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                    ) {
                                        if (diagnosticLogs.isEmpty()) {
                                            Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    "No hay logs de diagnóstico aún. Realiza una prueba o sincronización para ver el registro en tiempo real.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                items(diagnosticLogs.reversed()) { log ->
                                                    val color = when (log.severity) {
                                                        com.example.data.repository.LogSeverity.ERROR -> androidx.compose.ui.graphics.Color(0xFFFF5252)
                                                        com.example.data.repository.LogSeverity.WARN -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
                                                        com.example.data.repository.LogSeverity.SUCCESS -> androidx.compose.ui.graphics.Color(0xFF69F0AE)
                                                        com.example.data.repository.LogSeverity.INFO -> androidx.compose.ui.graphics.Color(0xFF80D8FF)
                                                        com.example.data.repository.LogSeverity.DEBUG -> androidx.compose.ui.graphics.Color(0xFFB0BEC5)
                                                    }
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Text(
                                                            text = "[${log.formattedTime}] [${log.severity.name}] [${log.tag}] ${log.message}",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 10.sp,
                                                            color = color,
                                                            fontWeight = if (log.severity == com.example.data.repository.LogSeverity.ERROR) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (!log.details.isNullOrBlank()) {
                                                            Text(
                                                                text = log.details,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 9.sp,
                                                                color = color.copy(alpha = 0.85f),
                                                                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
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
                    }
                }
            }

            // Logout & Session Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElegantPenaltiesRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Sesión Activa",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Conectado actualmente como ${currentUser?.nombre ?: "Usuario"} (@${currentUser?.username ?: ""})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                viewModel.logout()
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElegantPenaltiesRed.copy(alpha = 0.2f),
                                contentColor = ElegantPenaltiesRed
                            ),
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("button_logout")
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // About WeHabita
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface.copy(alpha = 0.5f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "WeHabita v1.0",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = ElegantPurplePrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gestión inteligente y colaborativa del hogar y la convivencia familiar o de pareja.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
