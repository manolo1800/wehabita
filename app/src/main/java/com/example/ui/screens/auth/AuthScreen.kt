package com.example.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.ui.components.WebHabitaLogo
import com.example.ui.components.MemberAvatar
import com.example.ui.components.AppMessagePopupDialog
import com.example.ui.components.PopupMessage
import com.example.ui.components.PopupMessageType
import com.example.ui.components.UserFriendlyErrorHelper
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: WeHabitaViewModel
) {
    val allUsers by viewModel.allUsers.collectAsState()
    var selectedTab by remember(allUsers.isEmpty()) { mutableIntStateOf(if (allUsers.isEmpty()) 1 else 0) } // 0: Login, 1: Register

    // Login Form State
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Register Form State
    var regNombre by remember { mutableStateOf("") }
    var regUsername by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }

    // Popup Message State for clear, beautiful feedback
    var popupMessage by remember { mutableStateOf<PopupMessage?>(null) }

    // Popup Dialog
    AppMessagePopupDialog(
        popupMessage = popupMessage,
        onDismiss = { popupMessage = null }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Branding Hero
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WebHabitaLogo(
                        size = 110.dp,
                        showCardContainer = true,
                        showText = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Convivencia, tareas y acuerdos compartidos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EarthyMediumGray
                    )
                }
            }

            // Tab Selector
            item {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = ElegantDarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (selectedTab == 0) ElegantPurplePrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedTab = 0
                                }
                                .testTag("tab_login")
                        ) {
                            Text(
                                text = "Iniciar Sesión",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) ElegantPurpleDark else ElegantOnSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (selectedTab == 1) ElegantPurplePrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedTab = 1
                                }
                                .testTag("tab_register")
                        ) {
                            Text(
                                text = "Crear Cuenta",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) ElegantPurpleDark else ElegantOnSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // --- LOGIN FORM ---
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Accede a tu Hogar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            OutlinedTextField(
                                value = loginIdentifier,
                                onValueChange = {
                                    loginIdentifier = it
                                },
                                label = { Text("Usuario o Correo Electrónico") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Person, contentDescription = null, tint = ElegantPurplePrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_input_identifier")
                            )

                            OutlinedTextField(
                                value = loginPassword,
                                onValueChange = {
                                    loginPassword = it
                                },
                                label = { Text("Contraseña") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = ElegantPurplePrimary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                        Icon(
                                            if (showLoginPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Mostrar/Ocultar"
                                        )
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (loginIdentifier.isBlank()) {
                                        popupMessage = PopupMessage(
                                            title = "Campo Requerido",
                                            description = "Por favor ingresa tu nombre de usuario o correo electrónico.",
                                            type = PopupMessageType.WARNING,
                                            icon = Icons.Outlined.Person
                                        )
                                        return@KeyboardActions
                                    }
                                    isSubmitting = true
                                    viewModel.login(loginIdentifier.trim(), loginPassword) { success, msg ->
                                        isSubmitting = false
                                        if (!success) {
                                            popupMessage = UserFriendlyErrorHelper.formatMessage(msg, defaultTitle = "No se pudo Iniciar Sesión")
                                        }
                                    }
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_input_password")
                            )

                            Button(
                                onClick = {
                                    if (loginIdentifier.isBlank()) {
                                        popupMessage = PopupMessage(
                                            title = "Campo Requerido",
                                            description = "Por favor ingresa tu nombre de usuario o correo electrónico.",
                                            type = PopupMessageType.WARNING,
                                            icon = Icons.Outlined.Person
                                        )
                                        return@Button
                                    }
                                    isSubmitting = true
                                    viewModel.login(loginIdentifier.trim(), loginPassword) { success, msg ->
                                        isSubmitting = false
                                        if (!success) {
                                            popupMessage = UserFriendlyErrorHelper.formatMessage(msg, defaultTitle = "No se pudo Iniciar Sesión")
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElegantPurplePrimary,
                                    contentColor = ElegantPurpleDark
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("login_submit_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = ElegantPurpleDark,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Iniciar Sesión", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }

                // Quick Demo Profiles (Multi-User Switch on Device)
                if (allUsers.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface.copy(alpha = 0.6f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Acceso Rápido (Integrantes Registrados):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ElegantPurpleContainer
                                )
                                Text(
                                    text = "Toca un perfil para iniciar sesión inmediatamente sin escribir credenciales:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    allUsers.forEach { user ->
                                        Surface(
                                            color = ElegantDarkBackground,
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, ElegantDarkOutline),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.switchUser(user)
                                                }
                                                .testTag("demo_user_${user.username}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                MemberAvatar(
                                                    name = user.nombre,
                                                    size = 36,
                                                    isAdmin = user.rol_global == "admin"
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = user.nombre,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                    Text(
                                                        text = "@${user.username} • Cód: ${user.codigo_usuario}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    Icons.Filled.ArrowForwardIos,
                                                    contentDescription = null,
                                                    tint = ElegantPurplePrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface.copy(alpha = 0.6f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElegantPurplePrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PersonAdd,
                                    contentDescription = null,
                                    tint = ElegantPurplePrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "¡Bienvenido a WeHabita!",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "La aplicación inicia totalmente limpia sin datos preexistentes. Crea tu primera cuenta de usuario para comenzar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { selectedTab = 1 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElegantPurplePrimary,
                                        contentColor = ElegantPurpleDark
                                    ),
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Crear Mi Cuenta Ahora", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // --- REGISTER FORM ---
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Crear Nuevo Usuario",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            OutlinedTextField(
                                value = regNombre,
                                onValueChange = {
                                    regNombre = it
                                },
                                label = { Text("Nombre Completo *") },
                                placeholder = { Text("Ej: Elena García") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Badge, contentDescription = null, tint = ElegantPurplePrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_input_nombre")
                            )

                            OutlinedTextField(
                                value = regUsername,
                                onValueChange = {
                                    regUsername = it
                                },
                                label = { Text("Nombre de Usuario (@username) *") },
                                placeholder = { Text("Ej: elena_g") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AlternateEmail, contentDescription = null, tint = ElegantPurplePrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_input_username")
                            )

                            OutlinedTextField(
                                value = regEmail,
                                onValueChange = {
                                    regEmail = it
                                },
                                label = { Text("Correo Electrónico *") },
                                placeholder = { Text("Ej: elena@wehabita.app") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Email, contentDescription = null, tint = ElegantPurplePrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_input_email")
                            )

                            OutlinedTextField(
                                value = regPassword,
                                onValueChange = {
                                    regPassword = it
                                },
                                label = { Text("Contraseña *") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = ElegantPurplePrimary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showRegPassword = !showRegPassword }) {
                                        Icon(
                                            if (showRegPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Mostrar/Ocultar"
                                        )
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_input_password")
                            )

                            OutlinedTextField(
                                value = regConfirmPassword,
                                onValueChange = {
                                    regConfirmPassword = it
                                },
                                label = { Text("Confirmar Contraseña *") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.LockClock, contentDescription = null, tint = ElegantPurplePrimary)
                                },
                                singleLine = true,
                                visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_input_confirm_password")
                            )

                            Button(
                                onClick = {
                                    if (regNombre.isBlank()) {
                                        popupMessage = PopupMessage(
                                            title = "Nombre Requerido",
                                            description = "Por favor ingresa tu nombre completo.",
                                            type = PopupMessageType.WARNING,
                                            icon = Icons.Outlined.Badge
                                        )
                                        return@Button
                                    }
                                    if (regUsername.isBlank()) {
                                        popupMessage = PopupMessage(
                                            title = "Usuario Requerido",
                                            description = "Por favor ingresa un nombre de usuario.",
                                            type = PopupMessageType.WARNING,
                                            icon = Icons.Outlined.AlternateEmail
                                        )
                                        return@Button
                                    }
                                    if (regEmail.isBlank() || !regEmail.contains("@")) {
                                        popupMessage = PopupMessage(
                                            title = "Correo Inválido",
                                            description = "Por favor ingresa una dirección de correo electrónico válida.",
                                            type = PopupMessageType.WARNING,
                                            icon = Icons.Outlined.Email
                                        )
                                        return@Button
                                    }
                                    if (regPassword.length < 4) {
                                        popupMessage = PopupMessage(
                                            title = "Contraseña Muy Corta",
                                            description = "La contraseña debe tener al menos 4 caracteres.",
                                            type = PopupMessageType.WARNING,
                                            icon = Icons.Outlined.Lock
                                        )
                                        return@Button
                                    }
                                    if (regPassword != regConfirmPassword) {
                                        popupMessage = PopupMessage(
                                            title = "Contraseñas No Coinciden",
                                            description = "Las contraseñas ingresadas no son iguales. Por favor revísalas.",
                                            type = PopupMessageType.WARNING,
                                            icon = Icons.Outlined.LockReset
                                        )
                                        return@Button
                                    }

                                    isSubmitting = true
                                    viewModel.registerAndLogin(
                                        nombre = regNombre.trim(),
                                        email = regEmail.trim(),
                                        username = regUsername.trim().lowercase(),
                                        pass = regPassword
                                    ) { success, msg ->
                                        isSubmitting = false
                                        if (!success) {
                                            popupMessage = UserFriendlyErrorHelper.formatMessage(msg, defaultTitle = "No se pudo Crear la Cuenta")
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElegantPurplePrimary,
                                    contentColor = ElegantPurpleDark
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("reg_submit_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = ElegantPurpleDark,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Crear Cuenta y Entrar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
