package com.example.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.TaskEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: WeHabitaViewModel,
    onOpenCreateTask: () -> Unit
) {
    val occurrences by viewModel.todayOccurrences.collectAsState()
    val allOccurrences by viewModel.allOccurrences.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val members by viewModel.homeMembers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var viewMode by remember { mutableStateOf("date") } // "date" or "all"
    var filterStatus by remember { mutableStateOf("all") }
    var showStatsDialog by remember { mutableStateOf(false) }
    var taskNoteDialogOccurrence by remember { mutableStateOf<OccurrenceWithDetails?>(null) }
    var noteText by remember { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<com.example.data.local.entities.TaskEntity?>(null) }

    val canCreate = viewModel.hasPrivilege("crear_tareas")
    val canMark = viewModel.hasPrivilege("marcar_tareas")

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayDate = dateFormat.format(Date())

    val displayOccurrences = if (viewMode == "date") occurrences else allOccurrences
    val completedCount = displayOccurrences.count { it.occurrence.completada }
    val pendingCount = displayOccurrences.size - completedCount

    val filteredOccurrences = displayOccurrences.filter {
        when (filterStatus) {
            "completed" -> it.occurrence.completada
            "pending" -> !it.occurrence.completada
            else -> true
        }
    }

    Scaffold(
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = onOpenCreateTask,
                    containerColor = SageGreenPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_create_task")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nueva Tarea")
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

            // Header & Recuento button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Gestión de Tareas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (viewMode == "date") "Fecha: $selectedDate ${if (selectedDate == todayDate) "(Hoy)" else ""}" else "Mostrando todo el historial",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = { showStatsDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = IndigoPrimaryContainer)
                ) {
                    Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recuento", fontWeight = FontWeight.Bold, color = IndigoPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode Selector: "Por Fecha" vs "Todas las Tareas"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = viewMode == "date",
                    onClick = { viewMode = "date" },
                    label = { Text("Día Seleccionado (${occurrences.size})") },
                    leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = viewMode == "all",
                    onClick = { viewMode = "all" },
                    label = { Text("Todas (${allOccurrences.size})") },
                    leadingIcon = { Icon(Icons.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Date Navigation (if in date view)
            if (viewMode == "date") {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    val cal = Calendar.getInstance()
                                    cal.time = dateFormat.parse(selectedDate) ?: Date()
                                    cal.add(Calendar.DAY_OF_YEAR, -1)
                                    viewModel.setSelectedDate(dateFormat.format(cal.time))
                                } catch (_: Exception) {}
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior")
                        }

                        TextButton(
                            onClick = { viewModel.setSelectedDate(todayDate) }
                        ) {
                            Text(
                                text = if (selectedDate == todayDate) "Hoy ($selectedDate)" else selectedDate,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }

                        IconButton(
                            onClick = {
                                try {
                                    val cal = Calendar.getInstance()
                                    cal.time = dateFormat.parse(selectedDate) ?: Date()
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                    viewModel.setSelectedDate(dateFormat.format(cal.time))
                                } catch (_: Exception) {}
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día siguiente")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Stats Pill Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatPill(count = displayOccurrences.size, label = "Total", color = ElegantPurplePrimary)
                    VerticalDivider(modifier = Modifier.height(28.dp), color = ElegantDarkOutline)
                    StatPill(count = completedCount, label = "Hechas", color = ElegantSuccessGreen)
                    VerticalDivider(modifier = Modifier.height(28.dp), color = ElegantDarkOutline)
                    StatPill(count = pendingCount, label = "Pendientes", color = ElegantPenaltiesRed)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterStatus == "all",
                    onClick = { filterStatus = "all" },
                    label = { Text("Todas (${displayOccurrences.size})") }
                )
                FilterChip(
                    selected = filterStatus == "pending",
                    onClick = { filterStatus = "pending" },
                    label = { Text("Pendientes ($pendingCount)") }
                )
                FilterChip(
                    selected = filterStatus == "completed",
                    onClick = { filterStatus = "completed" },
                    label = { Text("Hechas ($completedCount)") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredOccurrences.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (filterStatus == "completed") "Aún no hay tareas completadas" else "No hay tareas registradas para esta vista",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (viewMode == "date" && allOccurrences.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(onClick = { viewMode = "all" }) {
                                Text("Ver todas las tareas del hogar (${allOccurrences.size})")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredOccurrences) { occWithDetails ->
                        val occ = occWithDetails.occurrence
                        val task = occWithDetails.task
                        val completedByUser = occWithDetails.completedByUser

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (occ.completada) SuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (occ.completada) SuccessGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("occurrence_card_${occ.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = occ.completada,
                                        onCheckedChange = { isChecked ->
                                            if (canMark) {
                                                viewModel.markOccurrence(occ.id, isChecked)
                                            }
                                        },
                                        enabled = canMark,
                                        colors = CheckboxDefaults.colors(checkedColor = SuccessGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task?.titulo ?: "Tarea",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (occ.completada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!task?.descripcion.isNullOrBlank()) {
                                            Text(
                                                text = task?.descripcion ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            taskNoteDialogOccurrence = occWithDetails
                                            noteText = occ.notas ?: ""
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (!occ.notas.isNullOrBlank()) Icons.Filled.Note else Icons.Outlined.NoteAdd,
                                            contentDescription = "Notas",
                                            tint = if (!occ.notas.isNullOrBlank()) IndigoPrimary else MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    if (task != null) {
                                        IconButton(
                                            onClick = {
                                                editingTask = task
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Editar",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteTask(task.id)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "Eliminar",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = IndigoPrimaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.Schedule,
                                                    contentDescription = null,
                                                    tint = IndigoPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${occ.fecha} ${occ.hora_inicio} ${if (occ.hora_fin != null) "a " + occ.hora_fin else ""}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndigoPrimary
                                                )
                                            }
                                        }

                                        Surface(
                                            color = if (task?.tipo == "fija") TealTertiaryContainer else PurpleAccentContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (task?.tipo == "fija") "Recurrente" else "Única",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (task?.tipo == "fija") TealTertiary else PurpleAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    if (occ.completada && completedByUser != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            MemberAvatar(name = completedByUser.nombre, size = 20)
                                            Text(
                                                text = completedByUser.nombre.split(" ").first(),
                                                fontSize = 11.sp,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (!occ.notas.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Nota: ${occ.notas}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

    // Add Note Dialog
    if (taskNoteDialogOccurrence != null) {
        val occ = taskNoteDialogOccurrence!!.occurrence
        AlertDialog(
            onDismissRequest = { taskNoteDialogOccurrence = null },
            title = { Text("Nota de la Tarea") },
            text = {
                Column {
                    Text(
                        text = taskNoteDialogOccurrence?.task?.titulo ?: "Tarea",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Detalles o comentarios...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markOccurrence(
                            occurrenceId = occ.id,
                            completed = occ.completada,
                            notes = noteText.ifBlank { null }
                        )
                        taskNoteDialogOccurrence = null
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskNoteDialogOccurrence = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Task Stats / Recuento Final Dialog
    if (showStatsDialog) {
        val allTotal = allOccurrences.size
        val allCompleted = allOccurrences.count { it.occurrence.completada }
        val allPending = allTotal - allCompleted
        val overallRate = if (allTotal > 0) (allCompleted.toFloat() / allTotal) * 100 else 0f

        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Assessment, contentDescription = null, tint = IndigoPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recuento Final de Tareas")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Balance global acumulado de cumplimiento en el hogar:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = IndigoPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Tasa de Cumplimiento Global",
                                style = MaterialTheme.typography.labelMedium,
                                color = IndigoPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${overallRate.toInt()}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimaryDark
                            )
                            LinearProgressIndicator(
                                progress = { if (allTotal > 0) allCompleted.toFloat() / allTotal else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = SuccessGreen,
                                trackColor = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Ocurrencias:", fontWeight = FontWeight.SemiBold)
                        Text("$allTotal", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tareas Realizadas:", fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                        Text("$allCompleted", fontWeight = FontWeight.Bold, color = SuccessGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tareas No Realizadas:", fontWeight = FontWeight.SemiBold, color = ErrorRed)
                        Text("$allPending", fontWeight = FontWeight.Bold, color = ErrorRed)
                    }

                    HorizontalDivider(color = ElegantDarkOutline)

                    Text(
                        text = "Cumplimiento por Miembro:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    members.forEach { m ->
                        val memberDone = allOccurrences.count { it.occurrence.completada && it.occurrence.completada_por == m.user.id }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(name = m.user.nombre, size = 24)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(m.user.nombre, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "$memberDone completadas",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = IndigoPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showStatsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    editingTask?.let { currentEditingTask ->
        EditTaskDialog(
            task = currentEditingTask,
            viewModel = viewModel,
            onDismiss = { editingTask = null }
        )
    }
}

@Composable
fun StatPill(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
