package com.example.ui.screens.conflicts

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.ProblemEntity
import com.example.data.local.entities.ProblemPointOfViewEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    viewModel: WeHabitaViewModel,
    onOpenCreateProblem: () -> Unit
) {
    val problems by viewModel.problems.collectAsState()
    val members by viewModel.homeMembers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()

    LaunchedEffect(currentHome?.id) {
        viewModel.refreshProblems()
    }

    var filterStatus by remember { mutableStateOf("all") }
    var selectedProblemForDetail by remember { mutableStateOf<ProblemEntity?>(null) }

    val canCreate = viewModel.hasPrivilege("crear_problemas")

    val filteredProblems = problems.filter {
        when (filterStatus) {
            "open" -> !it.solucionado
            "solved" -> it.solucionado
            else -> true
        }
    }

    Scaffold(
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = onOpenCreateProblem,
                    containerColor = WarningOrange,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_create_problem")
                ) {
                    Icon(Icons.Filled.AddComment, contentDescription = "Reportar Problema")
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
                text = "Convivencia & Resolución de Conflictos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Espacio de diálogo constructivo, puntos de vista y acuerdos del hogar",
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
                    label = { Text("Todos (${problems.size})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterStatus == "open",
                    onClick = { filterStatus = "open" },
                    label = { Text("En Proceso (${problems.count { !it.solucionado }})", maxLines = 1, softWrap = false) }
                )
                FilterChip(
                    selected = filterStatus == "solved",
                    onClick = { filterStatus = "solved" },
                    label = { Text("Solucionados (${problems.count { it.solucionado }})", maxLines = 1, softWrap = false) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredProblems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Mood,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "¡Excelente convivencia! No hay asuntos pendientes.",
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
                    items(filteredProblems) { problem ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (problem.solucionado) SuccessContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (problem.solucionado) SuccessGreen.copy(alpha = 0.4f) else WarningOrange.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProblemForDetail = problem }
                                .testTag("problem_card_${problem.id}")
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
                                                .background(if (problem.solucionado) SuccessGreen else WarningOrange),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (problem.solucionado) Icons.Filled.Check else Icons.Filled.Forum,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = problem.titulo,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (problem.solucionado) SuccessGreen.copy(alpha = 0.15f)
                                        else if (problem.hablado) InfoBlue.copy(alpha = 0.15f)
                                        else WarningOrange.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (problem.solucionado) "Solucionado" else if (problem.hablado) "Hablado" else "Abierto",
                                            color = if (problem.solucionado) SuccessGreen else if (problem.hablado) InfoBlue else WarningOrange,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = problem.descripcion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!problem.acuerdo.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SuccessContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.Handshake,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Acuerdo consensuado:",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = SuccessGreen
                                                )
                                                Text(
                                                    text = problem.acuerdo,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Toca para ver puntos de vista y acuerdos →",
                                        fontSize = 12.sp,
                                        color = IndigoPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Problem Detail / Discussion BottomSheet
    if (selectedProblemForDetail != null) {
        val problem = selectedProblemForDetail!!
        ProblemDetailModal(
            problem = problem,
            viewModel = viewModel,
            onDismiss = { selectedProblemForDetail = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemDetailModal(
    problem: ProblemEntity,
    viewModel: WeHabitaViewModel,
    onDismiss: () -> Unit
) {
    val povList by viewModel.getPointsOfView(problem.id).collectAsState(initial = emptyList())
    val involvedUserIds by viewModel.getInvolvedUsers(problem.id).collectAsState(initial = emptyList())
    val members by viewModel.homeMembers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var newPovText by remember { mutableStateOf("") }
    var showAddPovField by remember { mutableStateOf(false) }
    var agreementText by remember { mutableStateOf(problem.acuerdo ?: "") }
    var isEditingAgreement by remember { mutableStateOf(false) }

    val userMap = members.associate { it.user.id to it.user }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detalle del Asunto",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            }

            item {
                Text(
                    text = problem.titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = IndigoPrimaryDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = problem.descripcion,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Involved Users
            item {
                Text(
                    text = "Personas Involucradas:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    involvedUserIds.forEach { uid ->
                        val u = userMap[uid]
                        if (u != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IndigoPrimaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberAvatar(name = u.nombre, size = 20)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = u.nombre,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IndigoPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Status Toggles (Hablado / Solucionado)
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = problem.hablado,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateProblemDetails(
                                        problem = problem,
                                        hablado = isChecked,
                                        solucionado = problem.solucionado,
                                        acuerdo = problem.acuerdo
                                    )
                                }
                            )
                            Text("Asunto Hablado / Conversado", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Consensus Agreement Section
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessContainer.copy(alpha = 0.4f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Handshake, contentDescription = null, tint = SuccessGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Acuerdo de Convivencia",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                            IconButton(onClick = { isEditingAgreement = !isEditingAgreement }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Editar Acuerdo", tint = SuccessGreen)
                            }
                        }

                        if (isEditingAgreement) {
                            OutlinedTextField(
                                value = agreementText,
                                onValueChange = { agreementText = it },
                                label = { Text("Escribe el acuerdo alcanzado...") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.updateProblemDetails(
                                        problem = problem,
                                        hablado = true,
                                        solucionado = problem.solucionado,
                                        acuerdo = agreementText
                                    )
                                    isEditingAgreement = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                            ) {
                                Text("Guardar Acuerdo")
                            }
                        } else {
                            Text(
                                text = if (!problem.acuerdo.isNullOrBlank()) problem.acuerdo else "Aún no se ha registrado un acuerdo. Toca el lápiz para escribirlo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (!problem.acuerdo.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Points of View Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Puntos de Vista (${povList.size}):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { showAddPovField = !showAddPovField }) {
                        Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir mi postura")
                    }
                }
            }

            if (showAddPovField) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IndigoPrimaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Mi punto de vista (${currentUser?.nombre}):",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = newPovText,
                                onValueChange = { newPovText = it },
                                label = { Text("Explica tu perspectiva con respeto...") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (newPovText.isNotBlank()) {
                                        viewModel.addPointOfView(problem.id, newPovText) {
                                            newPovText = ""
                                            showAddPovField = false
                                        }
                                    }
                                },
                                enabled = newPovText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                            ) {
                                Text("Publicar Punto de Vista")
                            }
                        }
                    }
                }
            }

            items(povList) { pov ->
                val author = userMap[pov.usuario_id]
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(name = author?.nombre ?: "Usuario", size = 26)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = author?.nombre ?: "Miembro",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (currentUser?.id == pov.usuario_id || viewModel.hasPrivilege("resolver_problemas")) {
                                IconButton(
                                    onClick = { viewModel.deletePointOfView(problem.id, pov.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Eliminar punto de vista",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pov.contenido,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Mark Solved Action Button & Delete Problem
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.updateProblemDetails(
                                problem = problem,
                                hablado = true,
                                solucionado = !problem.solucionado,
                                acuerdo = problem.acuerdo
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (problem.solucionado) MaterialTheme.colorScheme.outline else SuccessGreen
                        )
                    ) {
                        Icon(
                            imageVector = if (problem.solucionado) Icons.Filled.RestartAlt else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (problem.solucionado) "Reabrir" else "Solucionado ✓")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.deleteProblem(problem.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar")
                    }
                }
            }
        }
    }
}
