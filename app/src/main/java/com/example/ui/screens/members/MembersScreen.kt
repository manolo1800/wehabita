package com.example.ui.screens.members

import androidx.compose.foundation.background
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
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.entities.HomeMemberEntity
import com.example.data.local.entities.PrivilegeEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.components.PrivilegeBadge
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    viewModel: WeHabitaViewModel
) {
    val members by viewModel.homeMembers.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val allPrivileges by viewModel.allPrivileges.collectAsState()

    var showInviteDialog by remember { mutableStateOf(false) }
    var selectedMemberForPrivileges by remember { mutableStateOf<MemberWithUser?>(null) }

    val isAdmin = viewModel.isHomeAdmin()
    val clipboardManager = LocalClipboardManager.current
    var copiedCode by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (isAdmin || viewModel.hasPrivilege("gestionar_usuarios")) {
                FloatingActionButton(
                    onClick = { showInviteDialog = true },
                    containerColor = CoralSecondary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_invite_member")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Invitar Miembro")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Miembros & Privilegios",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Administración de roles y permisos granulares del hogar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Home Invitation Code Card
            if (currentHome != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = IndigoPrimaryContainer.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Código de Invitación del Hogar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = currentHome!!.codigo_invitacion,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndigoPrimaryDark
                            )
                        }
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(currentHome!!.codigo_invitacion))
                                copiedCode = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedCode) "¡Copiado!" else "Copiar Código")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Integrantes (${members.size})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(members) { memberWithUser ->
                    val member = memberWithUser.member
                    val user = memberWithUser.user
                    val isMemberAdmin = member.rol == "admin"

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_card_${user.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MemberAvatar(name = user.nombre, size = 44, isAdmin = isMemberAdmin)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = user.nombre,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${user.email} • Código: ${user.codigo_usuario}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isMemberAdmin) WarningContainer else IndigoPrimaryContainer
                                ) {
                                    Text(
                                        text = if (isMemberAdmin) "Admin ⭐" else "Miembro",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isMemberAdmin) WarningOrange else IndigoPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (isAdmin) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { selectedMemberForPrivileges = memberWithUser },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Gestionar Permisos", fontSize = 12.sp)
                                    }

                                    if (user.id != currentUser?.id) {
                                        OutlinedButton(
                                            onClick = {
                                                val newRole = if (isMemberAdmin) "miembro" else "admin"
                                                viewModel.updateMemberRole(member, newRole)
                                            }
                                        ) {
                                            Text(if (isMemberAdmin) "Quitar Admin" else "Hacer Admin", fontSize = 12.sp)
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

    // Member Privileges Editor Dialog
    if (selectedMemberForPrivileges != null) {
        val mw = selectedMemberForPrivileges!!
        MemberPrivilegesDialog(
            memberWithUser = mw,
            allPrivileges = allPrivileges,
            viewModel = viewModel,
            onDismiss = { selectedMemberForPrivileges = null }
        )
    }

    // Invite Member Dialog
    if (showInviteDialog) {
        var inviteEmail by remember { mutableStateOf("") }
        var inviteUserCode by remember { mutableStateOf("") }
        var statusMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = CoralSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invitar a un Miembro")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Puedes invitar directamente por Código de Usuario (ej. USR-5182) o por Correo:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inviteUserCode,
                        onValueChange = { inviteUserCode = it.uppercase() },
                        label = { Text("Código de Usuario (opcional)") },
                        leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("Correo Electrónico (opcional)") },
                        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (statusMsg.isNotBlank()) {
                        Text(
                            text = statusMsg,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteEmail.isNotBlank() || inviteUserCode.isNotBlank()) {
                            viewModel.createInvitation(inviteEmail.ifBlank { null }, inviteUserCode.ifBlank { null }) { msg ->
                                statusMsg = msg
                                showInviteDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralSecondary)
                ) {
                    Text("Enviar Invitación")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberPrivilegesDialog(
    memberWithUser: MemberWithUser,
    allPrivileges: List<PrivilegeEntity>,
    viewModel: WeHabitaViewModel,
    onDismiss: () -> Unit
) {
    val memberPrivsFlow = remember(memberWithUser.member.id) {
        viewModel.getPrivilegesForMember(memberWithUser.member.id)
    }
    val currentMemberPrivs by memberPrivsFlow.collectAsState(initial = emptyList())

    val selectedPrivilegeIds = remember { mutableStateListOf<Int>() }

    LaunchedEffect(currentMemberPrivs) {
        selectedPrivilegeIds.clear()
        selectedPrivilegeIds.addAll(currentMemberPrivs.map { it.id })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Permisos Granulares")
                Text(
                    text = "Para ${memberWithUser.user.nombre}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(allPrivileges) { priv ->
                    val isChecked = selectedPrivilegeIds.contains(priv.id)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isChecked) IndigoPrimaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isChecked) selectedPrivilegeIds.remove(priv.id)
                                else selectedPrivilegeIds.add(priv.id)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedPrivilegeIds.add(priv.id)
                                    else selectedPrivilegeIds.remove(priv.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (priv.nombre) {
                                    "crear_tareas" -> "Crear Tareas"
                                    "asignar_tareas" -> "Asignar Tareas a otros"
                                    "marcar_tareas" -> "Marcar Tareas Hechas"
                                    "crear_problemas" -> "Reportar Problemas"
                                    "crear_necesidades" -> "Crear Listas/Compras"
                                    "crear_objetivos" -> "Crear Objetivos"
                                    "gestionar_usuarios" -> "Gestionar Usuarios/Invitaciones"
                                    "aplicar_penalizaciones" -> "Aplicar Penalizaciones"
                                    "ver_reportes" -> "Ver Reportes y Recuentos"
                                    else -> priv.nombre
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateMemberPrivileges(memberWithUser.member.id, selectedPrivilegeIds.toList())
                    onDismiss()
                }
            ) {
                Text("Guardar Permisos")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
