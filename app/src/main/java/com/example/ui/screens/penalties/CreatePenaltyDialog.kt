package com.example.ui.screens.penalties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.ErrorRed
import com.example.viewmodel.WeHabitaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePenaltyDialog(
    viewModel: WeHabitaViewModel,
    onDismiss: () -> Unit
) {
    val members by viewModel.homeMembers.collectAsState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayStr = dateFormat.format(Date())

    var titulo by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var penalizacion by remember { mutableStateOf("") }
    var fechaInicio by remember { mutableStateOf(todayStr) }
    var fechaFin by remember { mutableStateOf(todayStr) }
    val selectedUserIds = remember { mutableStateListOf<Int>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nueva Penalización",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título de la penalización *") },
                    leadingIcon = { Icon(Icons.Outlined.Title, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_penalty_title"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Motivo / Falta cometida *") },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_penalty_motive"),
                    maxLines = 3
                )
            }

            item {
                OutlinedTextField(
                    value = penalizacion,
                    onValueChange = { penalizacion = it },
                    label = { Text("Descripción de la consecuencia o penalización *") },
                    leadingIcon = { Icon(Icons.Filled.Gavel, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_penalty_action"),
                    maxLines = 3
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = fechaInicio,
                        onValueChange = { fechaInicio = it },
                        label = { Text("Fecha Inicio") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fechaFin,
                        onValueChange = { fechaFin = it },
                        label = { Text("Fecha Fin (Opcional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            item {
                Text(
                    text = "Seleccionar miembros a penalizar:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    members.forEach { memberWithUser ->
                        val isSelected = selectedUserIds.contains(memberWithUser.user.id)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ErrorRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedUserIds.remove(memberWithUser.user.id)
                                    else selectedUserIds.add(memberWithUser.user.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedUserIds.add(memberWithUser.user.id)
                                        else selectedUserIds.remove(memberWithUser.user.id)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = ErrorRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                MemberAvatar(name = memberWithUser.user.nombre, size = 32)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = memberWithUser.user.nombre,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (titulo.isNotBlank() && motivo.isNotBlank() && penalizacion.isNotBlank() && selectedUserIds.isNotEmpty()) {
                            viewModel.createPenalty(
                                titulo = titulo,
                                motivo = motivo,
                                penalizacion = penalizacion,
                                fechaInicio = fechaInicio,
                                fechaFin = fechaFin.ifBlank { null },
                                assignedUserIds = selectedUserIds.toList(),
                                onDone = onDismiss
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_create_penalty_button"),
                    enabled = titulo.isNotBlank() && motivo.isNotBlank() && penalizacion.isNotBlank() && selectedUserIds.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Icon(Icons.Filled.Gavel, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aplicar Penalización")
                }
            }
        }
    }
}
