package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.HomeEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.theme.*

@Composable
fun MemberAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 36,
    isAdmin: Boolean = false,
    backgroundColor: Color? = null
) {
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()

    val colors = listOf(
        Color(0xFF3F51B5), Color(0xFF00897B), Color(0xFFE65100),
        Color(0xFF7B1FA2), Color(0xFFC2185B), Color(0xFF0288D1),
        Color(0xFF689F38), Color(0xFF5D4037)
    )
    val color = backgroundColor ?: colors[kotlin.math.abs(name.hashCode()) % colors.size]

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (initials.isNotEmpty()) initials else "U",
                color = Color.White,
                fontSize = (size * 0.4).sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (isAdmin) {
            Box(
                modifier = Modifier
                    .size((size * 0.45).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(WarningOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Admin",
                    tint = Color.White,
                    modifier = Modifier.size((size * 0.3).dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeHabitaTopAppBar(
    currentHome: HomeEntity?,
    currentUser: UserEntity?,
    onSwitchHomeClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebHabitaLogo(
                        size = 36.dp,
                        showCardContainer = true,
                        showText = false
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "webhabita",
                            color = SageGreenPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSwitchHomeClick() }
                                .padding(vertical = 1.dp)
                                .testTag("home_selector_button")
                        ) {
                            Text(
                                text = currentHome?.nombre ?: "Seleccionar Hogar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EarthyDarkText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Cambiar hogar",
                                tint = TerracottaSecondary
                            )
                        }
                    }
                }
            },
            actions = {
                // User switcher avatar button
                if (currentUser != null) {
                    IconButton(
                        onClick = onSwitchUserClick,
                        modifier = Modifier.testTag("user_switch_button")
                    ) {
                        MemberAvatar(
                            name = currentUser.nombre,
                            size = 36,
                            isAdmin = currentUser.rol_global == "admin"
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Ajustes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

enum class NavigationDestination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    DASHBOARD("Inicio", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    TASKS("Tareas", Icons.Outlined.CheckCircleOutline, Icons.Filled.CheckCircle),
    CONFLICTS("Convivencia", Icons.Outlined.Forum, Icons.Filled.Forum),
    SHOPPING("Compras", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    MORE("Más", Icons.Outlined.Apps, Icons.Filled.Apps)
}

@Composable
fun WeHabitaBottomBar(
    currentDestination: NavigationDestination,
    onNavigate: (NavigationDestination) -> Unit
) {
    Surface(
        color = SandSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SandOutline.copy(alpha = 0.5f))
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            NavigationDestination.values().forEach { destination ->
                val selected = currentDestination == destination
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OnSageGreenContainer,
                        selectedTextColor = EarthyDarkText,
                        indicatorColor = SageGreenContainer,
                        unselectedIconColor = EarthyMediumGray.copy(alpha = 0.8f),
                        unselectedTextColor = EarthyMediumGray.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun PrivilegeBadge(privilege: String) {
    val (label, color) = when (privilege) {
        "crear_tareas" -> "Crear Tareas" to IndigoPrimaryLight
        "asignar_tareas" -> "Asignar Tareas" to InfoBlue
        "marcar_tareas" -> "Completar Tareas" to SuccessGreen
        "crear_problemas" -> "Reportar Problemas" to WarningOrange
        "crear_necesidades" -> "Crear Compras" to TealTertiary
        "crear_objetivos" -> "Crear Objetivos" to PurpleAccent
        "gestionar_usuarios" -> "Gestionar Miembros" to CoralSecondary
        "aplicar_penalizaciones" -> "Penalizaciones" to ErrorRed
        "ver_reportes" -> "Ver Reportes" to IndigoPrimaryDark
        else -> privilege to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
