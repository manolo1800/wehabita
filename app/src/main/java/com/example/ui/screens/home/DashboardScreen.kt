package com.example.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import com.example.ui.components.MemberAvatar
import com.example.ui.components.NavigationDestination
import com.example.ui.components.PrivilegeBadge
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel

@Composable
fun DashboardScreen(
    viewModel: WeHabitaViewModel,
    onNavigate: (NavigationDestination) -> Unit,
    onOpenTaskCreate: () -> Unit,
    onOpenProblemCreate: () -> Unit,
    onOpenShoppingCreate: () -> Unit,
    onOpenGoalCreate: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState()
    val userPrivileges by viewModel.userPrivileges.collectAsState()
    val members by viewModel.homeMembers.collectAsState()
    val todayOccurrences by viewModel.todayOccurrences.collectAsState()
    val penalties by viewModel.penalties.collectAsState()
    val problems by viewModel.problems.collectAsState()
    val needLists by viewModel.needLists.collectAsState()
    val goals by viewModel.goals.collectAsState()

    val completedCount = todayOccurrences.count { it.occurrence.completada }
    val totalOccurrences = todayOccurrences.size
    val completionPercentage = if (totalOccurrences > 0) (completedCount.toFloat() / totalOccurrences) * 100 else 0f

    val activePenalties = penalties.filter { it.estado == "activa" }
    val activeProblems = problems.filter { !it.solucionado }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // Featured Next Task / Hero Card
        val nextPending = todayOccurrences.firstOrNull { !it.occurrence.completada }
        val canMark = viewModel.hasPrivilege("marcar_tareas")

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = ElegantPurpleDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_hero_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = ElegantPurpleContainer
                        ) {
                            Text(
                                text = if (nextPending != null) "PRÓXIMA TAREA" else "PROGRESO DE HOY",
                                color = ElegantOnPurpleContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = if (nextPending != null) "Hoy, ${nextPending.occurrence.hora_inicio}" else "${completionPercentage.toInt()}% completado",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElegantOnBackground.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = nextPending?.task?.titulo ?: if (todayOccurrences.isNotEmpty()) "¡Todas las tareas al día!" else "Sin tareas pendientes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (nextPending != null) {
                            if (!nextPending.task?.descripcion.isNullOrBlank()) nextPending.task?.descripcion ?: "" else "Tarea programada para hoy"
                        } else {
                            "Hogar: ${currentHome?.nombre ?: "Mi Casa"} • $completedCount de $totalOccurrences tareas hechas"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElegantOnBackground.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (nextPending != null) {
                        Button(
                            onClick = {
                                if (canMark) {
                                    viewModel.markOccurrence(nextPending.occurrence.id, true)
                                }
                            },
                            enabled = canMark,
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SageGreenPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("mark_next_task_button")
                        ) {
                            Text(
                                text = "Marcar realizada",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        LinearProgressIndicator(
                            progress = { if (totalOccurrences > 0) completedCount.toFloat() / totalOccurrences else 1f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(100.dp)),
                            color = ElegantPurplePrimary,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        // Two-column Metric / Status Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Penalties Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(NavigationDestination.MORE) }
                        .testTag("metric_penalties_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PENALIZACIÓN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElegantPenaltiesRed
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (activePenalties.isNotEmpty()) ElegantPenaltiesRed else ElegantDarkOutline)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = String.format("%02d", activePenalties.size),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Light,
                            color = ElegantOnBackground
                        )
                        Text(
                            text = "Activas esta semana",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElegantOnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Problems Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(NavigationDestination.CONFLICTS) }
                        .testTag("metric_problems_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PROBLEMAS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElegantPurplePrimary
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (activeProblems.isNotEmpty()) ElegantPurplePrimary else ElegantDarkOutline)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = String.format("%02d", activeProblems.size),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Light,
                            color = ElegantOnBackground
                        )
                        Text(
                            text = "Sin hablar todavía",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElegantOnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    title = "Tarea",
                    icon = Icons.Filled.AddCircleOutline,
                    color = ElegantPurplePrimary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTaskCreate,
                    enabled = viewModel.hasPrivilege("crear_tareas")
                )
                QuickActionButton(
                    title = "Compra",
                    icon = Icons.Filled.AddShoppingCart,
                    color = ElegantTertiaryTeal,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenShoppingCreate,
                    enabled = viewModel.hasPrivilege("crear_necesidades")
                )
                QuickActionButton(
                    title = "Problema",
                    icon = Icons.Filled.ReportProblem,
                    color = ElegantPenaltiesRed,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenProblemCreate,
                    enabled = viewModel.hasPrivilege("crear_problemas")
                )
                QuickActionButton(
                    title = "Objetivo",
                    icon = Icons.Filled.Flag,
                    color = ElegantPurplePrimary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenGoalCreate,
                    enabled = viewModel.hasPrivilege("crear_objetivos")
                )
            }
        }

        // Compound Goals / Goals Section in Elegant Dark Card Container
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElegantDarkOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OBJETIVOS Y PROYECTOS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElegantPurpleContainer,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${goals.count { it.completado }} de ${goals.size} completados",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElegantOnSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (goals.isEmpty()) {
                        Text(
                            text = "No hay objetivos registrados aún.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElegantOnSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            goals.take(3).forEach { goal ->
                                val progress = if (goal.completado) 1f else 0.4f
                                Surface(
                                    color = ElegantDarkBackground,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ElegantDarkOutline),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (goal.tipo == "compuesto") Icons.Filled.AccountTree else Icons.Filled.Flag,
                                                contentDescription = null,
                                                tint = ElegantPurplePrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = goal.titulo,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = ElegantOnBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (goal.completado) "100%" else "En curso",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (goal.completado) ElegantSuccessGreen else ElegantPurplePrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(100.dp)),
                                                color = if (goal.completado) ElegantSuccessGreen else ElegantPurplePrimary,
                                                trackColor = ElegantDarkOutline
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

        // Today's Tasks Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tareas de Hoy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { onNavigate(NavigationDestination.TASKS) },
                    modifier = Modifier.testTag("see_all_tasks_button")
                ) {
                    Text("Ver todas (${totalOccurrences})", color = IndigoPrimary)
                }
            }
        }

        if (todayOccurrences.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay tareas pendientes para hoy",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            items(todayOccurrences.take(4)) { occWithDetails ->
                val occ = occWithDetails.occurrence
                val task = occWithDetails.task
                val canMark = viewModel.hasPrivilege("marcar_tareas")

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (occ.completada) SuccessContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (occ.completada) SuccessGreen.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_task_${occ.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (occ.completada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = IndigoPrimaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${occ.hora_inicio} ${if (occ.hora_fin != null) "- " + occ.hora_fin else ""}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndigoPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (occ.completada && occWithDetails.completedByUser != null) {
                                    Text(
                                        text = "✓ Por ${occWithDetails.completedByUser.nombre.split(" ").first()}",
                                        fontSize = 11.sp,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Conflicts Section
        if (activeProblems.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Convivencia & Acuerdos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { onNavigate(NavigationDestination.CONFLICTS) }) {
                        Text("Ver todo", color = IndigoPrimary)
                    }
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningContainer.copy(alpha = 0.4f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(NavigationDestination.CONFLICTS) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = WarningOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeProblems.first().titulo,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeProblems.first().descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Home Members Row
        item {
            Text(
                text = "Miembros del Hogar (${members.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(members) { memberWithUser ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(72.dp)
                    ) {
                        MemberAvatar(
                            name = memberWithUser.user.nombre,
                            size = 50,
                            isAdmin = memberWithUser.member.rol == "admin"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = memberWithUser.user.nombre.split(" ").first(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (memberWithUser.member.rol == "admin") "Admin" else "Miembro",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) color.copy(alpha = 0.25f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) color else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) color else MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}
