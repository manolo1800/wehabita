package com.example.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.goals.GoalsScreen
import com.example.ui.screens.members.MembersScreen
import com.example.ui.screens.penalties.PenaltiesScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.*
import com.example.viewmodel.WeHabitaViewModel

enum class MoreSubScreen {
    HUB,
    GOALS,
    PENALTIES,
    MEMBERS,
    SETTINGS
}

@Composable
fun MoreHubScreen(
    viewModel: WeHabitaViewModel,
    onOpenCreateGoal: () -> Unit,
    onOpenCreatePenalty: () -> Unit
) {
    var currentSubScreen by remember { mutableStateOf(MoreSubScreen.HUB) }

    when (currentSubScreen) {
        MoreSubScreen.HUB -> {
            MoreMainList(
                viewModel = viewModel,
                onNavigateSub = { currentSubScreen = it }
            )
        }
        MoreSubScreen.GOALS -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubScreenHeader(title = "Objetivos del Hogar", onBack = { currentSubScreen = MoreSubScreen.HUB })
                GoalsScreen(viewModel = viewModel, onOpenCreateGoal = onOpenCreateGoal)
            }
        }
        MoreSubScreen.PENALTIES -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubScreenHeader(title = "Penalizaciones", onBack = { currentSubScreen = MoreSubScreen.HUB })
                PenaltiesScreen(viewModel = viewModel, onOpenCreatePenalty = onOpenCreatePenalty)
            }
        }
        MoreSubScreen.MEMBERS -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubScreenHeader(title = "Miembros & Privilegios", onBack = { currentSubScreen = MoreSubScreen.HUB })
                MembersScreen(viewModel = viewModel)
            }
        }
        MoreSubScreen.SETTINGS -> {
            SettingsScreen(viewModel = viewModel, onBack = { currentSubScreen = MoreSubScreen.HUB })
        }
    }
}

@Composable
fun SubScreenHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MoreMainList(
    viewModel: WeHabitaViewModel,
    onNavigateSub: (MoreSubScreen) -> Unit
) {
    val goals by viewModel.goals.collectAsState()
    val penalties by viewModel.penalties.collectAsState()
    val members by viewModel.homeMembers.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "Módulos & Herramientas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Metas conjuntas, sanciones, administración de usuarios y configuración",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            HubCard(
                title = "Objetivos & Metas",
                subtitle = "${goals.size} objetivos registrados (${goals.count { it.completado }} completados)",
                icon = Icons.Filled.Flag,
                iconBg = PurpleAccent,
                onClick = { onNavigateSub(MoreSubScreen.GOALS) },
                testTag = "hub_goals_card"
            )
        }

        item {
            HubCard(
                title = "Penalizaciones",
                subtitle = "${penalties.count { it.estado == "activa" }} activas • Consecuencias por faltas",
                icon = Icons.Filled.Gavel,
                iconBg = ErrorRed,
                onClick = { onNavigateSub(MoreSubScreen.PENALTIES) },
                testTag = "hub_penalties_card"
            )
        }

        item {
            HubCard(
                title = "Miembros & Privilegios Granulares",
                subtitle = "${members.size} integrantes • Permisos y códigos de invitación",
                icon = Icons.Filled.Group,
                iconBg = CoralSecondary,
                onClick = { onNavigateSub(MoreSubScreen.MEMBERS) },
                testTag = "hub_members_card"
            )
        }

        item {
            HubCard(
                title = "Ajustes, Perfil & Base de Datos",
                subtitle = "Código de usuario, PostgreSQL de respaldo y SQLite local",
                icon = Icons.Filled.Settings,
                iconBg = IndigoPrimary,
                onClick = { onNavigateSub(MoreSubScreen.SETTINGS) },
                testTag = "hub_settings_card"
            )
        }
    }
}

@Composable
fun HubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBg.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBg,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
