package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.components.*
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.conflicts.CreateProblemDialog
import com.example.ui.screens.conflicts.ProblemsScreen
import com.example.ui.screens.goals.CreateGoalDialog
import com.example.ui.screens.home.DashboardScreen
import com.example.ui.screens.more.MoreHubScreen
import com.example.ui.screens.penalties.CreatePenaltyDialog
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.shopping.ShoppingScreen
import com.example.ui.screens.tasks.CreateTaskDialog
import com.example.ui.screens.tasks.TasksScreen
import com.example.ui.theme.WeHabitaTheme
import com.example.viewmodel.WeHabitaViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: WeHabitaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeHabitaTheme {
                WeHabitaApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeHabitaApp(viewModel: WeHabitaViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val currentHome by viewModel.currentHome.collectAsState()
    val userHomes by viewModel.userHomes.collectAsState()

    var currentDestination by remember { mutableStateOf(NavigationDestination.DASHBOARD) }

    // If not logged in, render AuthScreen
    if (currentUser == null) {
        AuthScreen(viewModel = viewModel)
        return
    }

    // Dialog & Sheet States
    var showUserSwitchSheet by remember { mutableStateOf(false) }
    var showHomeSwitchSheet by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var showCreateProblemDialog by remember { mutableStateOf(false) }
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var showCreatePenaltyDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showSettingsScreen) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { showSettingsScreen = false }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                WeHabitaTopAppBar(
                    currentHome = currentHome,
                    currentUser = currentUser,
                    onSwitchHomeClick = { showHomeSwitchSheet = true },
                    onSwitchUserClick = { showUserSwitchSheet = true },
                    onOpenSettings = { showSettingsScreen = true }
                )
            },
            bottomBar = {
                WeHabitaBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { currentDestination = it }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    NavigationDestination.DASHBOARD -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigate = { dest -> currentDestination = dest },
                            onOpenTaskCreate = { showCreateTaskDialog = true },
                            onOpenProblemCreate = { showCreateProblemDialog = true },
                            onOpenShoppingCreate = { currentDestination = NavigationDestination.SHOPPING },
                            onOpenGoalCreate = { showCreateGoalDialog = true }
                        )
                    }
                    NavigationDestination.TASKS -> {
                        TasksScreen(
                            viewModel = viewModel,
                            onOpenCreateTask = { showCreateTaskDialog = true }
                        )
                    }
                    NavigationDestination.CONFLICTS -> {
                        ProblemsScreen(
                            viewModel = viewModel,
                            onOpenCreateProblem = { showCreateProblemDialog = true }
                        )
                    }
                    NavigationDestination.SHOPPING -> {
                        ShoppingScreen(
                            viewModel = viewModel
                        )
                    }
                    NavigationDestination.MORE -> {
                        MoreHubScreen(
                            viewModel = viewModel,
                            onOpenCreateGoal = { showCreateGoalDialog = true },
                            onOpenCreatePenalty = { showCreatePenaltyDialog = true }
                        )
                    }
                }
            }
        }
    }

    // User Switch Sheet
    if (showUserSwitchSheet) {
        UserSwitchBottomSheet(
            currentUser = currentUser,
            allUsers = allUsers,
            onSelectUser = { u ->
                viewModel.switchUser(u)
                scope.launch { snackbarHostState.showSnackbar("Sesión cambiada a ${u.nombre}") }
            },
            onRegisterUser = { nombre, email, username, pass ->
                viewModel.registerNewUser(nombre, email, username, pass) { u ->
                    scope.launch { snackbarHostState.showSnackbar("¡Usuario ${u.nombre} creado con éxito!") }
                }
            },
            onDismiss = { showUserSwitchSheet = false }
        )
    }

    // Home Switch Sheet
    if (showHomeSwitchSheet) {
        HomeSwitchBottomSheet(
            currentHome = currentHome,
            userHomes = userHomes,
            onSelectHome = { h ->
                viewModel.switchHome(h)
                scope.launch { snackbarHostState.showSnackbar("Hogar activo: ${h.nombre}") }
            },
            onCreateHome = { name, tipo ->
                viewModel.createHome(name, tipo) {
                    scope.launch { snackbarHostState.showSnackbar("¡Hogar $name creado con éxito!") }
                }
            },
            onJoinHome = { code ->
                viewModel.joinHome(code) { success, msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            onDismiss = { showHomeSwitchSheet = false }
        )
    }

    // Create Task Dialog
    if (showCreateTaskDialog) {
        CreateTaskDialog(
            viewModel = viewModel,
            onDismiss = { showCreateTaskDialog = false }
        )
    }

    // Create Problem Dialog
    if (showCreateProblemDialog) {
        CreateProblemDialog(
            viewModel = viewModel,
            onDismiss = { showCreateProblemDialog = false }
        )
    }

    // Create Goal Dialog
    if (showCreateGoalDialog) {
        CreateGoalDialog(
            viewModel = viewModel,
            onDismiss = { showCreateGoalDialog = false }
        )
    }

    // Create Penalty Dialog
    if (showCreatePenaltyDialog) {
        CreatePenaltyDialog(
            viewModel = viewModel,
            onDismiss = { showCreatePenaltyDialog = false }
        )
    }
}
