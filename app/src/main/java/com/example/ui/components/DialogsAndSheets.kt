package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.HomeEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSwitchBottomSheet(
    currentUser: UserEntity?,
    allUsers: List<UserEntity>,
    onSelectUser: (UserEntity) -> Unit,
    onRegisterUser: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var showRegisterForm by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current
    var copyNotice by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showRegisterForm) "Registrar Nuevo Usuario" else "Cambiar de Usuario",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!showRegisterForm) {
                Text(
                    text = "Selecciona un usuario para simular la experiencia y permisos en el hogar:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(allUsers) { user ->
                        val isSelected = user.id == currentUser?.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectUser(user)
                                    onDismiss()
                                }
                                .testTag("user_item_${user.id}"),
                            color = if (isSelected) IndigoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, IndigoPrimary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberAvatar(name = user.nombre, size = 44, isAdmin = user.rol_global == "admin")
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.nombre,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${user.email} • Código: ${user.codigo_usuario}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Activo",
                                        tint = IndigoPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showRegisterForm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("show_register_form_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralSecondary)
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear Nuevo Perfil / Usuario")
                }
            } else {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre Completo") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reg_nombre"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo Electrónico") },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reg_email"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de Usuario (@username)") },
                    leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reg_username"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reg_password"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showRegisterForm = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Atrás")
                    }
                    Button(
                        onClick = {
                            if (nombre.isNotBlank() && email.isNotBlank() && username.isNotBlank()) {
                                onRegisterUser(nombre, email, username, password.ifBlank { "pass123" })
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_register_button"),
                        enabled = nombre.isNotBlank() && email.isNotBlank()
                    ) {
                        Text("Registrar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSwitchBottomSheet(
    currentHome: HomeEntity?,
    userHomes: List<HomeEntity>,
    onSelectHome: (HomeEntity) -> Unit,
    onCreateHome: (String, String) -> Unit,
    onJoinHome: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("list") }
    var homeName by remember { mutableStateOf("") }
    var homeType by remember { mutableStateOf("familia") }
    var inviteCodeInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (mode) {
                        "create" -> "Crear Nuevo Hogar"
                        "join" -> "Unirse a un Hogar"
                        else -> "Mis Hogares"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (mode) {
                "list" -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 260.dp)
                    ) {
                        items(userHomes) { home ->
                            val isSelected = home.id == currentHome?.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectHome(home)
                                        onDismiss()
                                    }
                                    .testTag("home_item_${home.id}"),
                                color = if (isSelected) IndigoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, IndigoPrimary) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (home.tipo_hogar) {
                                                    "soltero" -> TealTertiaryLight
                                                    "pareja" -> CoralSecondaryLight
                                                    else -> IndigoPrimaryLight
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (home.tipo_hogar) {
                                                "soltero" -> Icons.Filled.Person
                                                "pareja" -> Icons.Filled.Favorite
                                                else -> Icons.Filled.Home
                                            },
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = home.nombre,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tipo: ${home.tipo_hogar.replaceFirstChar { it.uppercase() }} • Código: ${home.codigo_invitacion}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Activo",
                                            tint = IndigoPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { mode = "create" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_go_create_home"),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Icon(Icons.Filled.AddHome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crear Hogar")
                        }
                        OutlinedButton(
                            onClick = { mode = "join" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_go_join_home")
                        ) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unirme")
                        }
                    }
                }
                "create" -> {
                    OutlinedTextField(
                        value = homeName,
                        onValueChange = { homeName = it },
                        label = { Text("Nombre del Hogar (ej. Casa Jardín)") },
                        leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_home_name"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tipo de Hogar:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("familia" to "Familia", "pareja" to "Pareja", "soltero" to "Soltero").forEach { (type, label) ->
                            FilterChip(
                                selected = homeType == type,
                                onClick = { homeType = type },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { mode = "list" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Atrás")
                        }
                        Button(
                            onClick = {
                                if (homeName.isNotBlank()) {
                                    onCreateHome(homeName, homeType)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_create_home_button"),
                            enabled = homeName.isNotBlank()
                        ) {
                            Text("Crear")
                        }
                    }
                }
                "join" -> {
                    Text(
                        text = "Ingresa el código de invitación (ej. HOG-9481):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase() },
                        label = { Text("Código de Hogar") },
                        leadingIcon = { Icon(Icons.Outlined.QrCode, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_join_code"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { mode = "list" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Atrás")
                        }
                        Button(
                            onClick = {
                                if (inviteCodeInput.isNotBlank()) {
                                    onJoinHome(inviteCodeInput.trim())
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_join_home_button"),
                            enabled = inviteCodeInput.isNotBlank()
                        ) {
                            Text("Unirse")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
