package com.example.ui.screens.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.GoalEntity
import com.example.data.local.entities.GoalPointEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: WeHabitaViewModel,
    onOpenCreateGoal: () -> Unit
) {
    val goals by viewModel.goals.collectAsState()
    val members by viewModel.homeMembers.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()
    val userMap = members.associate { it.user.id to it.user }

    LaunchedEffect(currentHome?.id) {
        viewModel.refreshGoals()
    }

    var filterType by remember { mutableStateOf("all") }
    var postponeGoalTarget by remember { mutableStateOf<GoalEntity?>(null) }
    var newDeadlineToSet by remember { mutableStateOf("") }

    val canCreateGoals = viewModel.hasPrivilege("crear_objetivos")

    val filteredGoals = goals.filter {
        when (filterType) {
            "simple" -> it.tipo == "simple" && !it.completado
            "compound" -> it.tipo == "compuesto" && !it.completado
            "completed" -> it.completado
            else -> true
        }
    }

    Scaffold(
        floatingActionButton = {
            if (canCreateGoals) {
                FloatingActionButton(
                    onClick = onOpenCreateGoal,
                    containerColor = PurpleAccent,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_create_goal")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuevo Objetivo")
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
                text = "Objetivos del Hogar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Metas simples y proyectos compuestos con misiones asignadas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips (Responsive Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == "all",
                    onClick = { filterType = "all" },
                    label = { Text("Todos (${goals.size})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterType == "simple",
                    onClick = { filterType = "simple" },
                    label = { Text("Simples", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterType == "compound",
                    onClick = { filterType = "compound" },
                    label = { Text("Compuestos", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterType == "completed",
                    onClick = { filterType = "completed" },
                    label = { Text("Completados", maxLines = 1, softWrap = false) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredGoals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No hay objetivos en esta categoría",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredGoals) { goal ->
                        GoalCard(
                            goal = goal,
                            viewModel = viewModel,
                            userMap = userMap,
                            onPostpone = {
                                postponeGoalTarget = goal
                                newDeadlineToSet = goal.fecha_limite_actual
                            }
                        )
                    }
                }
            }
        }
    }

    // Postpone Goal Dialog (Preserves original deadline!)
    if (postponeGoalTarget != null) {
        val goal = postponeGoalTarget!!
        AlertDialog(
            onDismissRequest = { postponeGoalTarget = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Update, contentDescription = null, tint = CoralSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aplazar Objetivo")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = goal.titulo,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Fecha Límite Original: ${goal.fecha_limite_original}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "(Se conservará registrada en el historial)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newDeadlineToSet,
                        onValueChange = { newDeadlineToSet = it },
                        label = { Text("Nueva Fecha Límite (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDeadlineToSet.isNotBlank()) {
                            viewModel.postponeGoal(goal, newDeadlineToSet.trim())
                            postponeGoalTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralSecondary)
                ) {
                    Text("Guardar Nueva Fecha")
                }
            },
            dismissButton = {
                TextButton(onClick = { postponeGoalTarget = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun GoalCard(
    goal: GoalEntity,
    viewModel: WeHabitaViewModel,
    userMap: Map<Int, com.example.data.local.entities.UserEntity>,
    onPostpone: () -> Unit
) {
    val pointsFlow = remember(goal.id) { viewModel.getPointsForGoal(goal.id) }
    val points by pointsFlow.collectAsState(initial = emptyList())

    val isPostponed = goal.fecha_limite_original != goal.fecha_limite_actual
    val isCompound = goal.tipo == "compuesto"

    val completedPoints = points.count { it.completado }
    val totalPoints = points.size
    val progress = if (totalPoints > 0) completedPoints.toFloat() / totalPoints else if (goal.completado) 1f else 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.completado) SuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (goal.completado) SuccessGreen.copy(alpha = 0.4f) else PurpleAccent.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("goal_card_${goal.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!isCompound) {
                        Checkbox(
                            checked = goal.completado,
                            onCheckedChange = { isChecked ->
                                viewModel.setGoalCompleted(goal, isChecked)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = PurpleAccent)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PurpleAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AccountTree, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Text(
                            text = goal.titulo,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (goal.completado) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Text(
                            text = if (isCompound) "Objetivo Compuesto (${completedPoints}/${totalPoints} misiones)" else "Objetivo Simple",
                            fontSize = 11.sp,
                            color = PurpleAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPostpone) {
                        Icon(
                            Icons.Outlined.EventRepeat,
                            contentDescription = "Aplazar",
                            tint = CoralSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.deleteGoal(goal.id) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (!goal.descripcion.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = goal.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Deadline indicators (Original vs Actual if postponed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndigoPrimaryContainer
                ) {
                    Text(
                        text = "Vence: ${goal.fecha_limite_actual}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (isPostponed) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CoralSecondaryContainer
                    ) {
                        Text(
                            text = "Aplazado (Orig: ${goal.fecha_limite_original})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnCoralSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Compound Missions / Points
            if (isCompound && points.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SuccessGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    points.forEach { point ->
                        val assignedUser = point.asignado_a?.let { userMap[it] }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (point.completado) SuccessContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = point.completado,
                                    onCheckedChange = { isChecked ->
                                        viewModel.toggleGoalPoint(point, isChecked)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = point.titulo,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (point.completado) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    if (assignedUser != null) {
                                        Text(
                                            text = "Asignado: ${assignedUser.nombre.split(" ").first()}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (assignedUser != null) {
                                    MemberAvatar(name = assignedUser.nombre, size = 18)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
