package com.example.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.dao.MemberWithUser
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskDialog(
    viewModel: WeHabitaViewModel,
    onDismiss: () -> Unit
) {
    val members by viewModel.homeMembers.collectAsState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayStr = dateFormat.format(Date())

    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("fija") } // 'unica' | 'fija'
    var fechaInicio by remember { mutableStateOf(todayStr) }
    var fechaFin by remember { mutableStateOf("") }

    // Schedules list: pairs of (hora_inicio, hora_fin)
    val schedules = remember { mutableStateListOf<Pair<String, String?>>(Pair("08:00", "08:30"), Pair("14:00", "14:30"), Pair("20:00", "20:30")) }
    var newTimeInput by remember { mutableStateOf("12:00") }

    // Selected assigned users
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
                        text = "Nueva Tarea del Hogar",
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
                    label = { Text("Título de la tarea *") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_task_title"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción o detalles (opcional)") },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_task_description"),
                    maxLines = 3
                )
            }

            item {
                Text(
                    text = "Tipo de Tarea:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = tipo == "fija",
                        onClick = { tipo = "fija" },
                        label = { Text("Fija / Recurrente") },
                        leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = tipo == "unica",
                        onClick = { tipo = "unica" },
                        label = { Text("Única / Temporal") },
                        leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = fechaInicio,
                        onValueChange = { fechaInicio = it },
                        label = { Text("Fecha Inicio (AAAA-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    if (tipo == "unica") {
                        OutlinedTextField(
                            value = fechaFin,
                            onValueChange = { fechaFin = it },
                            label = { Text("Fecha Fin") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // Horarios al día (Multiple daily times, e.g., 08:00, 14:00, 20:00)
            item {
                Text(
                    text = "Horarios de ejecución al día (${schedules.size}):",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Se generará una ocurrencia diaria por cada horario configurado.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    schedules.forEachIndexed { index, (start, end) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IndigoPrimaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.AccessTime,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$start ${if (end != null) "- $end" else ""}",
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary
                                    )
                                }
                                IconButton(
                                    onClick = { schedules.removeAt(index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Eliminar",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Add new time row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = newTimeInput,
                            onValueChange = { newTimeInput = it },
                            label = { Text("Hora (HH:MM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newTimeInput.isNotBlank()) {
                                    schedules.add(Pair(newTimeInput.trim(), null))
                                    newTimeInput = "18:00"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Añadir Horario")
                        }
                    }
                }
            }

            // Assign to Members (Multi-user assignment)
            item {
                Text(
                    text = "Asignar a miembros responsables:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    members.forEach { memberWithUser ->
                        val isSelected = selectedUserIds.contains(memberWithUser.user.id)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IndigoPrimaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                                    colors = CheckboxDefaults.colors(checkedColor = IndigoPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                MemberAvatar(name = memberWithUser.user.nombre, size = 32)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = memberWithUser.user.nombre,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (memberWithUser.member.rol == "admin") "Admin" else "Miembro",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (titulo.isNotBlank() && schedules.isNotEmpty()) {
                            val assignees = if (selectedUserIds.isEmpty()) {
                                listOfNotNull(viewModel.currentUser.value?.id)
                            } else {
                                selectedUserIds.toList()
                            }
                            viewModel.createTask(
                                titulo = titulo,
                                descripcion = descripcion.ifBlank { null },
                                tipo = tipo,
                                fechaInicio = fechaInicio,
                                fechaFin = if (fechaFin.isNotBlank()) fechaFin else null,
                                horarios = schedules.toList(),
                                assignedUserIds = assignees,
                                onDone = onDismiss
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_create_task_button"),
                    enabled = titulo.isNotBlank() && schedules.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreenPrimary)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear Tarea y Generar Ocurrencias")
                }
            }
        }
    }
}
