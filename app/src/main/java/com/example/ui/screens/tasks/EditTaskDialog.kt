package com.example.ui.screens.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.TaskEntity
import com.example.data.local.entities.TaskScheduleEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskEntity,
    viewModel: WeHabitaViewModel,
    onDismiss: () -> Unit
) {
    var titulo by remember { mutableStateOf(task.titulo) }
    var descripcion by remember { mutableStateOf(task.descripcion ?: "") }
    var tipo by remember { mutableStateOf(task.tipo) } // 'unica' | 'fija'
    var fechaInicio by remember { mutableStateOf(task.fecha_inicio) }
    var fechaFin by remember { mutableStateOf(task.fecha_fin ?: "") }
    var activa by remember { mutableStateOf(task.activa) }

    // Schedules from ViewModel/Repo
    val schedulesFromRepo by viewModel.getSchedulesForTask(task.id).collectAsState(initial = emptyList())
    val localSchedules = remember { mutableStateListOf<Pair<String, String?>>() }
    var schedulesInitialized by remember { mutableStateOf(false) }

    // Synchronize local schedules with repo when first loaded
    LaunchedEffect(schedulesFromRepo) {
        if (!schedulesInitialized && schedulesFromRepo.isNotEmpty()) {
            localSchedules.clear()
            schedulesFromRepo.forEach { s ->
                localSchedules.add(Pair(s.hora_inicio, s.hora_fin))
            }
            schedulesInitialized = true
        }
    }

    // New schedule time inputs
    var newStartTime by remember { mutableStateOf("") }
    var newEndTime by remember { mutableStateOf("") }

    // Members & assignments
    val members by viewModel.homeMembers.collectAsState()
    val assignmentsFromRepo by viewModel.getAssignmentsForTask(task.id).collectAsState(initial = emptyList())
    val selectedUserIds = remember { mutableStateListOf<Int>() }

    LaunchedEffect(assignmentsFromRepo) {
        if (assignmentsFromRepo.isNotEmpty() || selectedUserIds.isEmpty()) {
            selectedUserIds.clear()
            assignmentsFromRepo.forEach { a ->
                if (!selectedUserIds.contains(a.usuario_id)) {
                    selectedUserIds.add(a.usuario_id)
                }
            }
        }
    }

    var showDeleteTaskConfirm by remember { mutableStateOf(false) }

    if (showDeleteTaskConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteTaskConfirm = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Eliminar tarea completa?") },
            text = { Text("Esta acción eliminará la tarea '${task.titulo}', todos sus horarios y sus ocurrencias en el servidor.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteTaskConfirm = false
                        viewModel.deleteTask(task.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Tarea")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTaskConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

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
                        text = "Editar Tarea",
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
                        .testTag("input_edit_task_title"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción o detalles (opcional)") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            item {
                Text(
                    text = "Tipo de Tarea",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = tipo == "fija",
                        onClick = { tipo = "fija" },
                        label = { Text("Recurrente (Fija)") },
                        leadingIcon = { Icon(Icons.Filled.Repeat, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = tipo == "unica",
                        onClick = { tipo = "unica" },
                        label = { Text("Puntual (Única)") },
                        leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fechaInicio,
                        onValueChange = { fechaInicio = it },
                        label = { Text("Fecha Inicio (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    if (tipo == "unica") {
                        OutlinedTextField(
                            value = fechaFin,
                            onValueChange = { fechaFin = it },
                            label = { Text("Fecha Fin (Opcional)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tarea Activa",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = activa,
                        onCheckedChange = { activa = it }
                    )
                }
            }

            // --- SECCIÓN: HORARIOS ESPECÍFICOS DE LA TAREA ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            item {
                Column {
                    Text(
                        text = "Horarios de la Tarea (${localSchedules.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Puedes definir hora de inicio y hora de finalización para cada horario.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (localSchedules.isEmpty()) {
                item {
                    Text(
                        text = "No hay horarios registrados para esta tarea. Añade uno abajo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(localSchedules) { itemSchedule ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (!itemSchedule.second.isNullOrBlank()) {
                                        "${itemSchedule.first} - ${itemSchedule.second}"
                                    } else {
                                        itemSchedule.first
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    // Remove from local list
                                    localSchedules.remove(itemSchedule)
                                    // If exists in repo with ID, trigger delete
                                    val matchEntity = schedulesFromRepo.find {
                                        it.hora_inicio.trim() == itemSchedule.first.trim()
                                    }
                                    if (matchEntity != null) {
                                        viewModel.deleteTaskSchedule(task.id, matchEntity.id)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Eliminar este horario",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Input fields for adding a new schedule (Hora Inicio and Hora Fin)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newStartTime,
                            onValueChange = { newStartTime = it },
                            label = { Text("Hora Inicio (HH:MM)") },
                            placeholder = { Text("08:00") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newEndTime,
                            onValueChange = { newEndTime = it },
                            label = { Text("Hora Fin (Opcional)") },
                            placeholder = { Text("08:30") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = {
                            if (newStartTime.isNotBlank()) {
                                val sTime = newStartTime.trim()
                                val eTime = newEndTime.trim().ifBlank { null }
                                if (localSchedules.none { it.first == sTime && it.second == eTime }) {
                                    localSchedules.add(Pair(sTime, eTime))
                                }
                                viewModel.addTaskSchedule(task.id, sTime, eTime)
                                newStartTime = ""
                                newEndTime = ""
                                schedulesInitialized = true
                            }
                        },
                        enabled = newStartTime.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Añadir Horario")
                    }
                }
            }

            // --- SECCIÓN: ASIGNACIÓN DE MIEMBROS ---
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            item {
                Text(
                    text = "Miembros Asignados:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            items(members) { memberWithUser ->
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

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // --- ACCIONES: GUARDAR CAMBIOS / ELIMINAR TAREA COMPLETA ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showDeleteTaskConfirm = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar Tarea")
                    }

                    Button(
                        onClick = {
                            if (titulo.isNotBlank()) {
                                val finalSchedules = localSchedules.toMutableList()
                                if (newStartTime.isNotBlank()) {
                                    val sTime = newStartTime.trim()
                                    val eTime = newEndTime.trim().ifBlank { null }
                                    if (finalSchedules.none { it.first == sTime && it.second == eTime }) {
                                        finalSchedules.add(Pair(sTime, eTime))
                                    }
                                }
                                viewModel.updateTask(
                                    taskId = task.id,
                                    titulo = titulo.trim(),
                                    descripcion = descripcion.trim().ifBlank { null },
                                    tipo = tipo,
                                    fechaInicio = fechaInicio.trim(),
                                    fechaFin = fechaFin.trim().ifBlank { null },
                                    activa = activa,
                                    horarios = finalSchedules.toList(),
                                    asignadosIds = selectedUserIds.toList(),
                                    onDone = onDismiss
                                )
                            }
                        },
                        enabled = titulo.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_edit_task")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
