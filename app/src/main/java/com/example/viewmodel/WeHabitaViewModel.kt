package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import com.example.data.remote.api.ApiClient
import com.example.data.remote.dto.LoginRequest
import com.example.data.remote.dto.RegisterRequest
import com.example.data.remote.sse.HomeRealtimeEvent
import com.example.data.remote.sse.SseConnectionStatus
import com.example.data.remote.sse.SseEventsRepository
import com.example.data.repository.RestSyncManager
import com.example.data.repository.SyncLogCollector
import com.example.data.repository.WeHabitaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class WeHabitaViewModel(application: Application) : AndroidViewModel(application) {

    val repository: WeHabitaRepository = WeHabitaRepository()
    val restSyncManager: RestSyncManager = repository.restSyncManager
    val sseRepository: SseEventsRepository

    // Realtime SSE State
    private val _sseStatus = MutableStateFlow(SseConnectionStatus.DISCONNECTED)
    val sseStatus: StateFlow<SseConnectionStatus> = _sseStatus.asStateFlow()
    private var realtimeSseJob: Job? = null
    private var currentListeningHomeId: Int? = null

    // REST Server Base URL
    private val _restServerUrl = MutableStateFlow(ApiClient.getBaseUrl())
    val restServerUrl: StateFlow<String> = _restServerUrl.asStateFlow()

    // Current User Session
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Current Home
    private val _currentHome = MutableStateFlow<HomeEntity?>(null)
    val currentHome: StateFlow<HomeEntity?> = _currentHome.asStateFlow()

    // All Users
    val allUsers: StateFlow<List<UserEntity>>

    // Homes for Current User
    val userHomes: StateFlow<List<HomeEntity>>

    // Members of Current Home
    val homeMembers: StateFlow<List<MemberWithUser>>

    // Current User's Member record & privileges in current home
    val currentMember: StateFlow<HomeMemberEntity?>

    // Current User's Privileges in active home
    val userPrivileges: StateFlow<List<String>>

    // All Privileges catalog
    val allPrivileges: StateFlow<List<PrivilegeEntity>>

    // Selected Date for Agenda View
    private val _selectedDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Tasks & Occurrences for Current Home
    val tasksForHome: StateFlow<List<TaskEntity>>
    val tasks: StateFlow<List<TaskEntity>>
    val occurrencesForDate: StateFlow<List<OccurrenceWithDetails>>
    val todayOccurrences: StateFlow<List<OccurrenceWithDetails>>
    val allOccurrences: StateFlow<List<OccurrenceWithDetails>>

    // Penalties for Current Home
    val penaltiesForHome: StateFlow<List<PenaltyEntity>>
    val penalties: StateFlow<List<PenaltyEntity>>

    // Problems for Current Home
    val problemsForHome: StateFlow<List<ProblemEntity>>
    val problems: StateFlow<List<ProblemEntity>>

    // Need Lists for Current Home
    val needListsForHome: StateFlow<List<NeedListEntity>>
    val needLists: StateFlow<List<NeedListEntity>>

    // Goals for Current Home
    val goalsForHome: StateFlow<List<GoalEntity>>
    val goals: StateFlow<List<GoalEntity>>

    // Invitations for Current Home
    val invitationsForHome: StateFlow<List<InvitationEntity>>

    // Pending Sync Flow (Always 0 because data operations are direct to services)
    val pendingSyncCount: StateFlow<Int>
    val pendingSyncQueue: StateFlow<List<SyncQueueEntity>>

    // Sync State
    private val _lastSyncStatus = MutableStateFlow("Conectado a servicios REST")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Diagnostic Logs Flow
    val diagnosticLogs: StateFlow<List<com.example.data.repository.DiagnosticLogEntry>> = SyncLogCollector.logs

    init {
        // 1. Initialize persistent ApiClient (stores Token & Home ID in SharedPreferences)
        ApiClient.init(application)
        _restServerUrl.value = ApiClient.getBaseUrl()
        sseRepository = SseEventsRepository({ ApiClient.getBaseUrl() })

        // 2. Initialize in-memory StateFlows bound to Repository
        allUsers = repository.getAllUsers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        userHomes = repository.getHomesForUser(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        homeMembers = repository.getMembersWithUserForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        currentMember = combine(_currentUser, homeMembers) { user, members ->
            if (user == null) null
            else members.find { it.user.id == user.id }?.member
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        userPrivileges = currentMember.flatMapLatest { member ->
            if (member != null) {
                repository.getPrivilegesForMember(member.id).map { list -> list.map { it.nombre } }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allPrivileges = repository.getAllPrivileges()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        tasksForHome = repository.getTasksForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        tasks = tasksForHome

        occurrencesForDate = combine(repository.getAllOccurrencesForHome(0), _selectedDate) { list, date ->
            list.filter { it.occurrence.fecha == date }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        todayOccurrences = repository.getAllOccurrencesForHome(0).map { list ->
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            list.filter { it.occurrence.fecha == today }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allOccurrences = repository.getAllOccurrencesForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        penaltiesForHome = repository.getPenaltiesForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        penalties = penaltiesForHome

        problemsForHome = repository.getProblemsForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        problems = problemsForHome

        needListsForHome = repository.getNeedListsForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        needLists = needListsForHome

        goalsForHome = repository.getGoalsForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        goals = goalsForHome

        invitationsForHome = repository.getInvitationsForHome(0)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        pendingSyncCount = repository.getPendingSyncCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        pendingSyncQueue = repository.getPendingSyncQueue()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // 3. Auto-select active home whenever userHomes changes
        viewModelScope.launch {
            userHomes.collect { homes ->
                if (homes.isNotEmpty()) {
                    val current = _currentHome.value
                    val savedHomeId = ApiClient.getSavedHomeId()
                    if (current == null || homes.none { it.id == current.id }) {
                        val matching = homes.find { it.id == savedHomeId } ?: homes.first()
                        _currentHome.value = matching
                        ApiClient.saveHomeId(matching.id)
                        repository.fetchAllHomeData(matching.id)
                    }
                }
            }
        }

        // 4. Restore saved user session on app launch
        restoreUserSession()

        // 5. Realtime SSE: automatically maintain connection for the active home
        viewModelScope.launch {
            combine(_currentUser, _currentHome) { user, home ->
                Pair(user, home)
            }.collect { (user, home) ->
                if (user != null && home != null) {
                    startListeningRealtime(home.id)
                } else {
                    stopListeningRealtime()
                }
            }
        }
    }

    private fun restoreUserSession() {
        viewModelScope.launch {
            val token = ApiClient.getAuthToken()
            val savedHomeId = ApiClient.getSavedHomeId()

            if (token.isNullOrBlank()) {
                SyncLogCollector.d("WeHabita:Session", "No hay token guardado. Pantalla de login requerida.")
                _currentUser.value = null
                _currentHome.value = null
                return@launch
            }

            SyncLogCollector.i("WeHabita:Session", "Restaurando sesión desde servicios REST...")
            try {
                val meResp = ApiClient.service.getMe()
                if (meResp.isSuccessful && meResp.body()?.data != null) {
                    val profile = meResp.body()!!.data
                    val userEntity = UserEntity(
                        id = profile.id,
                        nombre = profile.nombre,
                        email = profile.email,
                        username = profile.username,
                        codigo_usuario = profile.codigo_usuario,
                        rol_global = profile.rol_global,
                        activo = profile.activo
                    )
                    _currentUser.value = userEntity
                    ApiClient.saveUserId(profile.id)
                    SyncLogCollector.s("WeHabita:Session", "Sesión restaurada para '${profile.nombre}' (${profile.username})")

                    // Fetch homes from server
                    repository.fetchHomes()
                    repository.fetchPrivileges()

                    val homesList = profile.hogares.map { h ->
                        HomeEntity(
                            id = h.hogar_id,
                            nombre = h.hogar_nombre,
                            tipo_hogar = h.tipo_hogar,
                            codigo_invitacion = h.codigo_invitacion,
                            creado_por = 0
                        )
                    }
                    val selectedHome = if (savedHomeId != null) {
                        homesList.find { it.id == savedHomeId } ?: homesList.firstOrNull()
                    } else {
                        homesList.firstOrNull()
                    }

                    if (selectedHome != null) {
                        _currentHome.value = selectedHome
                        ApiClient.saveHomeId(selectedHome.id)
                        repository.fetchAllHomeData(selectedHome.id)
                    }
                } else {
                    SyncLogCollector.w("WeHabita:Session", "Token inválido o expirado. Se requiere nuevo inicio de sesión.")
                    ApiClient.clearSession()
                    _currentUser.value = null
                    _currentHome.value = null
                }
            } catch (e: Exception) {
                SyncLogCollector.e("WeHabita:Session", "Fallo al restaurar sesión online: ${e.message}")
            }
        }
    }

    fun updateRestServerUrl(url: String) {
        val cleanUrl = url.trim()
        ApiClient.setBaseUrl(cleanUrl)
        _restServerUrl.value = ApiClient.getBaseUrl()
        SyncLogCollector.i("WeHabita:Config", "URL de Servidor REST actualizada a: ${ApiClient.getBaseUrl()}")
        // Reconnect SSE to new base URL if currently in a home
        val currentHId = _currentHome.value?.id
        if (currentHId != null) {
            startListeningRealtime(currentHId)
        }
    }

    fun clearDiagnosticLogs() {
        SyncLogCollector.clear()
    }

    fun getDiagnosticLogsAsText(): String {
        return SyncLogCollector.getAllLogsAsText()
    }

    // Permission Checking Helper
    fun hasPrivilege(privilegeName: String): Boolean {
        val user = _currentUser.value ?: return false
        if (user.rol_global == "admin") return true
        val home = _currentHome.value
        if (home != null && home.creado_por == user.id) return true
        val member = currentMember.value
        if (member?.rol == "admin" || member?.rol == "creador" || member?.rol == "propietario") return true
        if (userPrivileges.value.contains(privilegeName)) return true

        // Standard household privileges for members of the active home
        val standardHouseholdPrivileges = setOf(
            "crear_tareas",
            "asignar_tareas",
            "marcar_tareas",
            "crear_problemas",
            "crear_necesidades",
            "crear_objetivos",
            "ver_reportes"
        )
        return privilegeName in standardHouseholdPrivileges
    }

    fun isHomeAdmin(): Boolean {
        val user = _currentUser.value ?: return false
        if (user.rol_global == "admin") return true
        val home = _currentHome.value
        if (home != null && home.creado_por == user.id) return true
        val member = currentMember.value ?: return false
        return member.rol == "admin" || member.rol == "creador" || member.rol == "propietario"
    }

    // Authentication & User Session Actions
    fun login(identifier: String, pass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val cleanId = identifier.trim()
            val cleanPass = pass.trim()

            _isSyncing.value = true
            try {
                val resp = ApiClient.service.login(LoginRequest(identifier = cleanId, password = cleanPass))
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val authData = resp.body()!!.data
                    ApiClient.setAuthToken(authData.access_token)

                    val p = authData.user
                    ApiClient.saveUserId(p.id)

                    val userEntity = UserEntity(
                        id = p.id,
                        nombre = p.nombre,
                        email = p.email,
                        username = p.username,
                        password_hash = cleanPass,
                        codigo_usuario = p.codigo_usuario,
                        rol_global = p.rol_global,
                        activo = p.activo
                    )
                    _currentUser.value = userEntity

                    // Fetch fresh homes and privileges
                    repository.fetchHomes()
                    repository.fetchPrivileges()

                    val homesList = p.hogares.map { h ->
                        HomeEntity(
                            id = h.hogar_id,
                            nombre = h.hogar_nombre,
                            tipo_hogar = h.tipo_hogar,
                            codigo_invitacion = h.codigo_invitacion,
                            creado_por = p.id
                        )
                    }

                    val savedHomeId = ApiClient.getSavedHomeId()
                    val selectedHome = if (savedHomeId != null) {
                        homesList.find { it.id == savedHomeId } ?: homesList.firstOrNull()
                    } else {
                        homesList.firstOrNull()
                    }

                    if (selectedHome != null) {
                        _currentHome.value = selectedHome
                        ApiClient.saveHomeId(selectedHome.id)
                        repository.fetchAllHomeData(selectedHome.id)
                    }

                    _isSyncing.value = false
                    onResult(true, "¡Bienvenido, ${p.nombre}!")
                } else {
                    _isSyncing.value = false
                    val err = resp.errorBody()?.string() ?: "Credenciales incorrectas"
                    onResult(false, err)
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                SyncLogCollector.e("WeHabita:Auth", "Error al iniciar sesión: ${e.message}", e)
                onResult(false, "Error de conexión con el servidor REST: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun registerAndLogin(
        nombre: String,
        email: String,
        username: String,
        pass: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val cleanNombre = nombre.trim()
            val cleanEmail = email.trim().lowercase(Locale.getDefault())
            val cleanUsername = username.trim().lowercase(Locale.getDefault())
            val cleanPass = pass.trim()

            _isSyncing.value = true
            try {
                val resp = ApiClient.service.register(
                    RegisterRequest(
                        nombre = cleanNombre,
                        email = cleanEmail,
                        username = cleanUsername,
                        password = cleanPass
                    )
                )
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val authData = resp.body()!!.data
                    ApiClient.setAuthToken(authData.access_token)

                    val p = authData.user
                    ApiClient.saveUserId(p.id)

                    val userEntity = UserEntity(
                        id = p.id,
                        nombre = p.nombre,
                        email = p.email,
                        username = p.username,
                        password_hash = cleanPass,
                        codigo_usuario = p.codigo_usuario,
                        rol_global = p.rol_global,
                        activo = p.activo
                    )
                    _currentUser.value = userEntity
                    _currentHome.value = null
                    repository.fetchHomes()
                    repository.fetchPrivileges()

                    _isSyncing.value = false
                    onResult(true, "Cuenta registrada exitosamente en el servidor. ¡Bienvenido, ${p.nombre}!")
                } else {
                    _isSyncing.value = false
                    val err = resp.errorBody()?.string() ?: "Error al registrar usuario"
                    onResult(false, err)
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                SyncLogCollector.e("WeHabita:Auth", "Error en registro: ${e.message}", e)
                onResult(false, "Error de conexión: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    fun registerNewUser(
        nombre: String,
        email: String,
        username: String,
        pass: String,
        onSuccess: (UserEntity) -> Unit
    ) {
        registerAndLogin(nombre, email, username, pass) { success, _ ->
            if (success && _currentUser.value != null) {
                onSuccess(_currentUser.value!!)
            }
        }
    }

    fun logout() {
        stopListeningRealtime()
        ApiClient.clearSession()
        _currentUser.value = null
        _currentHome.value = null
        repository.clearInMemoryData()
        SyncLogCollector.i("WeHabita:Auth", "Sesión cerrada.")
    }

    fun switchUser(user: UserEntity) {
        _currentUser.value = user
        ApiClient.saveUserId(user.id)
        _currentHome.value = null
    }

    fun switchHome(home: HomeEntity) {
        _currentHome.value = home
        ApiClient.saveHomeId(home.id)
        viewModelScope.launch {
            repository.fetchAllHomeData(home.id)
        }
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    // REST Sync Actions
    fun testRestApiConnection(url: String? = null, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val (success, msg) = restSyncManager.testApiConnection(url)
            _isSyncing.value = false
            onResult(success, msg)
        }
    }

    fun syncWithRestServer(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _lastSyncStatus.value = "Sincronizando con servicios REST..."
            val activeHomeId = _currentHome.value?.id
            val (success, msg) = restSyncManager.syncWithServer(activeHomeId)
            _isSyncing.value = false
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _lastSyncStatus.value = if (success) "Sincronizado ($time)" else "Error sync ($time)"
            onResult(success, msg)
        }
    }

    fun refreshTasks() {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            repository.fetchTasks(homeId)
            _isSyncing.value = false
        }
    }

    fun refreshPenalties() {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            repository.fetchPenalties(homeId)
            _isSyncing.value = false
        }
    }

    fun refreshProblems() {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            repository.fetchProblems(homeId)
            _isSyncing.value = false
        }
    }

    fun refreshShopping() {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            repository.fetchShopping(homeId)
            _isSyncing.value = false
        }
    }

    fun refreshGoals() {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            repository.fetchGoals(homeId)
            _isSyncing.value = false
        }
    }

    fun refreshMembers() {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            repository.fetchMembers(homeId)
            _isSyncing.value = false
        }
    }

    fun refreshDashboard() {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId()
        viewModelScope.launch {
            _isSyncing.value = true
            if (homeId != null) {
                repository.fetchAllHomeData(homeId)
            } else {
                repository.fetchHomes()
            }
            _isSyncing.value = false
        }
    }

    fun createHome(name: String, tipo: String, onCreated: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val homeId = repository.createHome(name, tipo, user.id).toInt()
            val created = repository.getHomeById(homeId)
            if (created != null) {
                _currentHome.value = created
                ApiClient.saveHomeId(created.id)
            }
            onCreated()
        }
    }

    fun joinHome(code: String, onResult: (Boolean, String) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val success = repository.joinHomeByInviteCode(code, user.id)
            if (success) {
                onResult(true, "¡Te has unido al hogar exitosamente!")
            } else {
                onResult(false, "Código de hogar no encontrado o inválido.")
            }
        }
    }

    private suspend fun getActiveUserAndHome(): Pair<UserEntity, HomeEntity>? {
        val user = _currentUser.value ?: return null
        val home = _currentHome.value ?: return null
        return Pair(user, home)
    }

    // Task Actions
    fun createTask(
        titulo: String,
        descripcion: String?,
        tipo: String,
        fechaInicio: String,
        fechaFin: String?,
        horarios: List<Pair<String, String?>>,
        assignedUserIds: List<Int>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val (user, home) = getActiveUserAndHome() ?: run {
                    onDone()
                    return@launch
                }
                val finalAssignees = if (assignedUserIds.isEmpty()) listOf(user.id) else assignedUserIds
                repository.createTask(
                    homeId = home.id,
                    createdBy = user.id,
                    titulo = titulo,
                    descripcion = descripcion,
                    tipo = tipo,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    horarios = horarios,
                    asignadosIds = finalAssignees
                )
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al crear tarea: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun updateTask(
        taskId: Int,
        titulo: String,
        descripcion: String?,
        tipo: String,
        fechaInicio: String,
        fechaFin: String?,
        activa: Boolean = true,
        horarios: List<Pair<String, String?>> = emptyList(),
        asignadosIds: List<Int> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val task = repository.getTasksForHome(0).first().find { it.id == taskId }
                if (task != null) {
                    val updated = task.copy(
                        titulo = titulo,
                        descripcion = descripcion,
                        tipo = tipo,
                        fecha_inicio = fechaInicio,
                        fecha_fin = fechaFin,
                        activa = activa
                    )
                    repository.updateTask(updated, horarios, asignadosIds)
                }
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al actualizar tarea: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun completeTaskDirectly(taskId: Int, completed: Boolean) {
        viewModelScope.launch {
            try {
                val user = _currentUser.value
                repository.completeTaskDirectly(taskId, completed, user?.id)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al completar tarea directamente: ${e.message}")
            }
        }
    }

    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.deleteTask(taskId, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar tarea: ${e.message}")
            }
        }
    }

    fun deleteTaskSchedule(taskId: Int, scheduleId: Int) {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            try {
                repository.deleteTaskSchedule(homeId, taskId, scheduleId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar horario de tarea: ${e.message}")
            }
        }
    }

    fun addTaskSchedule(taskId: Int, horaInicio: String, horaFin: String?) {
        val homeId = _currentHome.value?.id ?: ApiClient.getSavedHomeId() ?: return
        viewModelScope.launch {
            try {
                repository.addTaskSchedule(homeId, taskId, horaInicio, horaFin)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al agregar horario a tarea: ${e.message}")
            }
        }
    }

    fun markOccurrence(occurrenceId: Int, completed: Boolean, notes: String? = null) {
        viewModelScope.launch {
            try {
                val user = _currentUser.value
                val homeId = _currentHome.value?.id
                repository.markOccurrence(
                    occurrenceId = occurrenceId,
                    completed = completed,
                    userId = user?.id,
                    notes = notes,
                    homeId = homeId
                )
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al marcar ocurrencia: ${e.message}")
            }
        }
    }

    // Penalties Actions
    fun createPenalty(
        titulo: String,
        motivo: String,
        penalizacion: String,
        fechaInicio: String,
        fechaFin: String?,
        assignedUserIds: List<Int>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val (user, home) = getActiveUserAndHome() ?: run {
                    onDone()
                    return@launch
                }
                val finalAssignees = if (assignedUserIds.isEmpty()) listOf(user.id) else assignedUserIds
                repository.createPenalty(
                    homeId = home.id,
                    createdBy = user.id,
                    titulo = titulo,
                    descripcionMotivo = motivo,
                    penalizacion = penalizacion,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    usuariosIds = finalAssignees
                )
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al crear penalización: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun updatePenaltyStatus(penalty: PenaltyEntity, newStatus: String) {
        viewModelScope.launch {
            try {
                repository.updatePenaltyStatus(penalty, newStatus)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al actualizar penalización: ${e.message}")
            }
        }
    }

    // Problems Actions
    fun createProblem(
        titulo: String,
        descripcion: String,
        involvedUserIds: List<Int>,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val (user, home) = getActiveUserAndHome() ?: run {
                    onDone()
                    return@launch
                }
                val finalInvolved = if (involvedUserIds.isEmpty()) listOf(user.id) else involvedUserIds
                repository.createProblem(
                    homeId = home.id,
                    createdBy = user.id,
                    titulo = titulo,
                    descripcion = descripcion,
                    usuariosInvolucradosIds = finalInvolved
                )
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al crear problema: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun addPointOfView(problemId: Int, contenido: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val user = _currentUser.value ?: run {
                    onDone()
                    return@launch
                }
                val homeId = _currentHome.value?.id
                repository.addPointOfView(problemId, user.id, contenido, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al agregar punto de vista: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun updateProblem(
        problemId: Int,
        titulo: String,
        descripcion: String,
        involvedUserIds: List<Int>? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val home = _currentHome.value
                if (home != null) {
                    repository.updateProblemDetails(
                        problemId = problemId,
                        homeId = home.id,
                        titulo = titulo,
                        descripcion = descripcion,
                        hablado = false,
                        solucionado = false,
                        acuerdo = null,
                        usuariosInvolucradosIds = involvedUserIds
                    )
                }
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al editar problema: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun deleteProblem(problemId: Int) {
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.deleteProblem(problemId, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar problema: ${e.message}")
            }
        }
    }

    fun deletePointOfView(problemId: Int, povId: Int) {
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.deletePointOfView(problemId, povId, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar punto de vista: ${e.message}")
            }
        }
    }

    fun deletePenalty(penaltyId: Int) {
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.deletePenalty(penaltyId, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar penalización: ${e.message}")
            }
        }
    }

    fun updateProblemDetails(
        problem: ProblemEntity,
        hablado: Boolean,
        solucionado: Boolean,
        acuerdo: String?
    ) {
        viewModelScope.launch {
            try {
                repository.updateProblemDetails(
                    problemId = problem.id,
                    homeId = problem.hogar_id,
                    titulo = problem.titulo,
                    descripcion = problem.descripcion,
                    hablado = hablado,
                    solucionado = solucionado,
                    acuerdo = acuerdo
                )
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al actualizar problema: ${e.message}")
            }
        }
    }

    // Needs & Shopping Actions
    fun createNeedList(titulo: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val (user, home) = getActiveUserAndHome() ?: run {
                    onDone()
                    return@launch
                }
                repository.createNeedList(home.id, user.id, titulo)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al crear lista: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun addNeedItem(
        listaId: Int,
        nombre: String = "",
        productoNombre: String = nombre,
        cantidad: Int = 1,
        fechaNecesaria: String = "",
        compradorId: Int? = null,
        notas: String? = null,
        onDone: () -> Unit = {}
    ) {
        val finalNombre = if (productoNombre.isNotBlank()) productoNombre else nombre
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.addNeedItem(listaId, finalNombre, cantidad, fechaNecesaria, notas, compradorId, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al agregar item: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun updateNeedItemStatus(item: NeedItemEntity, newStatus: String) {
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.updateNeedItemStatus(item, newStatus, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al actualizar item: ${e.message}")
            }
        }
    }

    fun deleteNeedItem(itemId: Int) {
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.deleteNeedItem(itemId, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar item: ${e.message}")
            }
        }
    }

    // Goals Actions
    fun createGoal(
        titulo: String,
        descripcion: String? = null,
        tipo: String,
        fechaLimite: String = "",
        fechaLimiteOriginal: String = fechaLimite,
        puntos: List<Pair<String, Int?>> = emptyList(),
        onDone: () -> Unit = {}
    ) {
        val finalDate = if (fechaLimiteOriginal.isNotBlank()) fechaLimiteOriginal else fechaLimite
        viewModelScope.launch {
            try {
                val (user, home) = getActiveUserAndHome() ?: run {
                    onDone()
                    return@launch
                }
                repository.createGoal(
                    homeId = home.id,
                    createdBy = user.id,
                    titulo = titulo,
                    descripcion = descripcion,
                    tipo = tipo,
                    fechaLimite = finalDate,
                    puntos = puntos
                )
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al crear meta: ${e.message}")
            } finally {
                onDone()
            }
        }
    }

    fun postponeGoal(goal: GoalEntity, newDeadline: String) {
        viewModelScope.launch {
            repository.postponeGoal(goal, newDeadline)
        }
    }

    fun setGoalCompleted(goal: GoalEntity, completed: Boolean) {
        viewModelScope.launch {
            repository.setGoalCompleted(goal, completed)
        }
    }

    fun toggleGoalPoint(point: GoalPointEntity, completed: Boolean) {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val homeId = _currentHome.value?.id
            repository.toggleGoalPoint(point, completed, user.id, homeId)
        }
    }

    fun deleteGoal(goalId: Int) {
        viewModelScope.launch {
            try {
                val homeId = _currentHome.value?.id
                repository.deleteGoal(goalId, homeId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar objetivo: ${e.message}")
            }
        }
    }

    fun getGoalPoints(goalId: Int): Flow<List<GoalPointEntity>> = repository.getGoalPoints(goalId)
    fun getPointsForGoal(goalId: Int): Flow<List<GoalPointEntity>> = repository.getGoalPoints(goalId)

    // Member Management Actions
    fun updateMemberRole(member: HomeMemberEntity, newRole: String) {
        viewModelScope.launch {
            repository.updateMemberRole(member, newRole)
        }
    }

    fun updateMemberPrivileges(memberId: Int, privilegeIds: List<Int>) {
        viewModelScope.launch {
            val homeId = _currentHome.value?.id ?: return@launch
            val allPrivs = allPrivileges.value
            val privilegeNames = privilegeIds.mapNotNull { id -> allPrivs.find { it.id == id }?.nombre }
            repository.updateMemberPrivileges(homeId, memberId, privilegeNames)
        }
    }

    fun removeMember(userId: Int) {
        viewModelScope.launch {
            val (_, home) = getActiveUserAndHome() ?: return@launch
            repository.removeMember(home.id, userId)
        }
    }

    fun createInvitation(
        email: String?,
        userCode: String?,
        onResult: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val (user, home) = getActiveUserAndHome() ?: run {
                onResult("No hay un hogar activo seleccionado.")
                return@launch
            }
            val id = repository.createInvitation(home.id, user.id, email, userCode)
            if (id > 0) {
                onResult("Invitación generada exitosamente.")
            } else {
                onResult("No se pudo generar la invitación.")
            }
        }
    }

    fun getNeedItemsForList(listId: Int): Flow<List<NeedItemEntity>> =
        repository.getNeedItemsForList(listId)

    fun getItemsForList(listId: Int): Flow<List<NeedItemEntity>> =
        repository.getNeedItemsForList(listId)

    fun getSchedulesForTask(taskId: Int): Flow<List<TaskScheduleEntity>> =
        repository.getSchedulesForTask(taskId)

    fun getAssignmentsForTask(taskId: Int): Flow<List<TaskAssignmentEntity>> =
        repository.getAssignmentsForTask(taskId)

    fun getAssignedUsersForPenalty(penaltyId: Int): Flow<List<Int>> =
        repository.getAssignedUsersForPenalty(penaltyId)

    fun getInvolvedUsersForProblem(problemId: Int): Flow<List<Int>> =
        repository.getInvolvedUsersForProblem(problemId)

    fun getInvolvedUsers(problemId: Int): Flow<List<Int>> =
        repository.getInvolvedUsersForProblem(problemId)

    fun getPointsOfViewForProblem(problemId: Int): Flow<List<ProblemPointOfViewEntity>> =
        repository.getPointsOfViewForProblem(problemId)

    fun getPointsOfView(problemId: Int): Flow<List<ProblemPointOfViewEntity>> =
        repository.getPointsOfViewForProblem(problemId)

    fun getPrivilegesForMember(memberId: Int): Flow<List<PrivilegeEntity>> =
        repository.getPrivilegesForMember(memberId)

    // ==========================================
    // Server-Sent Events (SSE) Realtime Listener
    // ==========================================
    fun startListeningRealtime(forcedHomeId: Int? = null) {
        val token = ApiClient.getAuthToken()
        val homeId = forcedHomeId ?: _currentHome.value?.id ?: ApiClient.getSavedHomeId()

        if (token.isNullOrBlank() || homeId == null) {
            _sseStatus.value = SseConnectionStatus.DISCONNECTED
            currentListeningHomeId = null
            realtimeSseJob?.cancel()
            realtimeSseJob = null
            return
        }

        if (currentListeningHomeId == homeId && realtimeSseJob?.isActive == true) {
            // Already actively listening to this home
            return
        }

        realtimeSseJob?.cancel()
        currentListeningHomeId = homeId

        realtimeSseJob = viewModelScope.launch {
            var backoffMs = 1000L
            val maxBackoffMs = 30000L

            while (isActive && currentListeningHomeId == homeId) {
                val currentToken = ApiClient.getAuthToken()
                if (currentToken.isNullOrBlank()) {
                    _sseStatus.value = SseConnectionStatus.DISCONNECTED
                    break
                }

                try {
                    sseRepository.subscribeToHome(
                        hogarId = homeId,
                        jwtToken = currentToken,
                        onStatusChanged = { status ->
                            _sseStatus.value = status
                            if (status == SseConnectionStatus.CONNECTED) {
                                backoffMs = 1000L
                            }
                        }
                    ).collect { event ->
                        when (event) {
                            is HomeRealtimeEvent.TaskEvent -> {
                                SyncLogCollector.i("WeHabita:SSE", "Refrescando tareas por evento ${event.type}")
                                refreshTasks()
                            }
                            is HomeRealtimeEvent.NeedEvent -> {
                                SyncLogCollector.i("WeHabita:SSE", "Refrescando compras por evento ${event.type}")
                                refreshShopping()
                            }
                            is HomeRealtimeEvent.ProblemEvent -> {
                                SyncLogCollector.i("WeHabita:SSE", "Refrescando problemas por evento ${event.type}")
                                refreshProblems()
                            }
                            is HomeRealtimeEvent.GoalEvent -> {
                                SyncLogCollector.i("WeHabita:SSE", "Refrescando metas por evento ${event.type}")
                                refreshGoals()
                            }
                            is HomeRealtimeEvent.PenaltyEvent -> {
                                SyncLogCollector.i("WeHabita:SSE", "Refrescando penalizaciones por evento ${event.type}")
                                refreshPenalties()
                            }
                            is HomeRealtimeEvent.UnknownEvent -> {
                                SyncLogCollector.d("WeHabita:SSE", "Evento sin acción directa: ${event.type}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    SyncLogCollector.w("WeHabita:SSE", "Stream SSE interrumpido: ${e.message}")
                }

                if (!isActive || currentListeningHomeId != homeId) break

                SyncLogCollector.i("WeHabita:SSE", "Reconectando SSE al hogar #$homeId en ${backoffMs / 1000}s...")
                _sseStatus.value = SseConnectionStatus.CONNECTING
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
            }
        }
    }

    fun stopListeningRealtime() {
        currentListeningHomeId = null
        realtimeSseJob?.cancel()
        realtimeSseJob = null
        _sseStatus.value = SseConnectionStatus.DISCONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningRealtime()
    }
}
