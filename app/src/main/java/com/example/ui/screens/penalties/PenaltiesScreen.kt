package com.example.ui.screens.penalties

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.PenaltyEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenaltiesScreen(
    viewModel: WeHabitaViewModel,
    onOpenCreatePenalty: () -> Unit
) {
    val penalties by viewModel.penalties.collectAsState()
    val members by viewModel.homeMembers.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()
    val userMap = members.associate { it.user.id to it.user }

    LaunchedEffect(currentHome?.id) {
        viewModel.refreshPenalties()
    }

    var filterStatus by remember { mutableStateOf("all") }
    val canApply = viewModel.hasPrivilege("aplicar_penalizaciones")

    val filteredPenalties = penalties.filter {
        when (filterStatus) {
            "activa" -> it.estado == "activa"
            "cumplida" -> it.estado == "cumplida"
            else -> true
        }
    }

    Scaffold(
        floatingActionButton = {
            if (canApply) {
                FloatingActionButton(
                    onClick = onOpenCreatePenalty,
                    containerColor = ErrorRed,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_create_penalty")
                ) {
                    Icon(Icons.Filled.Gavel, contentDescription = "Nueva Penalización")
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
                text = "Penalizaciones del Hogar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Consecuencias pactadas por tareas no realizadas o faltas a los acuerdos",
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
                    selected = filterStatus == "all",
                    onClick = { filterStatus = "all" },
                    label = { Text("Todas (${penalties.size})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterStatus == "activa",
                    onClick = { filterStatus = "activa" },
                    label = { Text("Activas (${penalties.count { it.estado == "activa" }})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterStatus == "cumplida",
                    onClick = { filterStatus = "cumplida" },
                    label = { Text("Cumplidas (${penalties.count { it.estado == "cumplida" }})", maxLines = 1, softWrap = false) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredPenalties.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No hay penalizaciones en este estado",
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
                    items(filteredPenalties) { penalty ->
                        PenaltyCard(
                            penalty = penalty,
                            viewModel = viewModel,
                            userMap = userMap,
                            canManage = canApply
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PenaltyCard(
    penalty: PenaltyEntity,
    viewModel: WeHabitaViewModel,
    userMap: Map<Int, com.example.data.local.entities.UserEntity>,
    canManage: Boolean
) {
    val assignedUserIdsFlow = remember(penalty.id) { viewModel.getAssignedUsersForPenalty(penalty.id) }
    val assignedUserIds by assignedUserIdsFlow.collectAsState(initial = emptyList())
    val isPending = penalty.estado == "activa"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) ErrorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPending) ErrorRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("penalty_card_${penalty.id}")
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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isPending) ErrorRed else SuccessGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPending) Icons.Filled.Gavel else Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = penalty.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPending) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = penalty.estado.replaceFirstChar { it.uppercase() },
                        color = if (isPending) ErrorRed else SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Motivo
            Text(
                text = "Motivo: ${penalty.descripcion_motivo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Consecuencia / Penalización
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isPending) ErrorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.SportsHandball,
                        contentDescription = null,
                        tint = if (isPending) ErrorRed else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Penalización a cumplir:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isPending) ErrorRed else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = penalty.penalizacion,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Assigned Members
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Penalizado(s):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    assignedUserIds.forEach { uid ->
                        userMap[uid]?.let { u ->
                            MemberAvatar(name = u.nombre, size = 22)
                            Text(u.nombre.split(" ").first(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "Vigencia: ${penalty.fecha_inicio}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions for Admin
            if (canManage) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPending) {
                        OutlinedButton(
                            onClick = { viewModel.updatePenaltyStatus(penalty, "cancelada") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.updatePenaltyStatus(penalty, "cumplida") },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cumplida ✓", fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { viewModel.deletePenalty(penalty.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
