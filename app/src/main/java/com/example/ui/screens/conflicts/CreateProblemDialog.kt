package com.example.ui.screens.conflicts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.WarningOrange
import com.example.viewmodel.WeHabitaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProblemDialog(
    viewModel: WeHabitaViewModel,
    onDismiss: () -> Unit
) {
    val members by viewModel.homeMembers.collectAsState()
    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
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
                        text = "Reportar Asunto / Problema",
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
                    label = { Text("Título del problema *") },
                    leadingIcon = { Icon(Icons.Outlined.Title, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_problem_title"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción de la situación *") },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_problem_description"),
                    maxLines = 4
                )
            }

            item {
                Text(
                    text = "Seleccionar miembros involucrados:",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    members.forEach { memberWithUser ->
                        val isSelected = selectedUserIds.contains(memberWithUser.user.id)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) WarningOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                                    colors = CheckboxDefaults.colors(checkedColor = WarningOrange)
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
                        if (titulo.isNotBlank() && descripcion.isNotBlank()) {
                            viewModel.createProblem(
                                titulo = titulo,
                                descripcion = descripcion,
                                involvedUserIds = selectedUserIds.toList(),
                                onDone = onDismiss
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_create_problem_button"),
                    enabled = titulo.isNotBlank() && descripcion.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                ) {
                    Icon(Icons.Filled.ReportProblem, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registrar Problema para Diálogo")
                }
            }
        }
    }
}
