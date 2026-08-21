package com.example.ui.screens.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PurpleAccent
import com.example.viewmodel.WeHabitaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalDialog(
    viewModel: WeHabitaViewModel,
    onDismiss: () -> Unit
) {
    val members by viewModel.homeMembers.collectAsState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayStr = dateFormat.format(Date())

    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("compuesto") } // 'simple' | 'compuesto'
    var fechaLimite by remember { mutableStateOf(todayStr) }

    // Mission points for compound goal: list of (title, assignedUserId)
    val missionPoints = remember {
        mutableStateListOf(
            Pair("Paso 1: Planificación y compra de insumos", members.firstOrNull()?.user?.id),
            Pair("Paso 2: Ejecución principal", members.getOrNull(1)?.user?.id)
        )
    }
    var newMissionTitle by remember { mutableStateOf("") }

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
                        text = "Nuevo Objetivo del Hogar",
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
                    label = { Text("Título del objetivo *") },
                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_goal_title"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción (opcional)") },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_goal_description"),
                    maxLines = 3
                )
            }

            item {
                Text("Tipo de Objetivo:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = tipo == "compuesto",
                        onClick = { tipo = "compuesto" },
                        label = { Text("Compuesto (Con misiones)") },
                        leadingIcon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = tipo == "simple",
                        onClick = { tipo = "simple" },
                        label = { Text("Simple (Meta directa)") },
                        leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = fechaLimite,
                    onValueChange = { fechaLimite = it },
                    label = { Text("Fecha Límite Original (AAAA-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (tipo == "compuesto") {
                item {
                    Text(
                        text = "Misiones / Puntos del Objetivo (${missionPoints.size}):",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Cada punto puede tener un responsable asignado y fecha de cumplimiento individual.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                itemsIndexed(missionPoints) { index, (pTitle, pAssigned) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pTitle,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val assignedMember = members.find { it.user.id == pAssigned }
                                Text(
                                    text = "Asignado a: ${assignedMember?.user?.nombre ?: "Sin asignar"}",
                                    fontSize = 11.sp,
                                    color = PurpleAccent
                                )
                            }
                            IconButton(onClick = { missionPoints.removeAt(index) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar", tint = Color.Gray)
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMissionTitle,
                            onValueChange = { newMissionTitle = it },
                            label = { Text("Nueva misión...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newMissionTitle.isNotBlank()) {
                                    missionPoints.add(Pair(newMissionTitle.trim(), members.firstOrNull()?.user?.id))
                                    newMissionTitle = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                        ) {
                            Text("Añadir")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (titulo.isNotBlank() && fechaLimite.isNotBlank()) {
                            viewModel.createGoal(
                                titulo = titulo.trim(),
                                descripcion = descripcion.ifBlank { null },
                                tipo = tipo,
                                fechaLimite = fechaLimite.trim(),
                                puntos = if (tipo == "compuesto") missionPoints.toList() else emptyList(),
                                onDone = onDismiss
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_create_goal_button"),
                    enabled = titulo.isNotBlank() && fechaLimite.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear Objetivo")
                }
            }
        }
    }
}
