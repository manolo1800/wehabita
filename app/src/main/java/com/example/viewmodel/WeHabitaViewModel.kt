package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WeHabitaDatabase
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import com.example.data.remote.api.ApiClient
import com.example.data.remote.dto.LoginRequest
import com.example.data.remote.dto.RegisterRequest
import com.example.data.repository.RestSyncManager
import com.example.data.repository.SyncLogCollector
import com.example.data.repository.WeHabitaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class WeHabitaViewModel(application: Application) : AndroidViewModel(application) {

    val repository: WeHabitaRepository
    val restSyncManager: RestSyncManager

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

    // Pending Sync Flow
    val pendingSyncCount: StateFlow<Int>
    val pendingSyncQueue: StateFlow<List<SyncQueueEntity>>

    // Sync State
    private val _lastSyncStatus = MutableStateFlow("Local Room SQLite Activo (Offline-First)")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Diagnostic Logs Flow
    val diagnosticLogs: StateFlow<List<com.example.data.repository.DiagnosticLogEntry>> = SyncLogCollector.logs

    init {
        // 1. Initialize persistent ApiClient with Application Context
        ApiClient.init(application)
        _restServerUrl.value = ApiClient.getBaseUrl()

        // 2. Initialize Room Database & Repositories
        val db = WeHabitaDatabase.getDatabase(application)
        restSyncManager = RestSyncManager(db)
        repository = WeHabitaRepository(db, restSyncManager)

        // 3. Initialize StateFlows
        allUsers = repository.getAllUsers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        userHomes = _currentUser.flatMapLatest { user ->
            if (user != null) {
                repository.getHomesForUser(user.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        homeMembers = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getMembersWithUserForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

        tasksForHome = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getTasksForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        tasks = tasksForHome

        occurrencesForDate = combine(_currentHome, _selectedDate) { home, date ->
            Pair(home, date)
        }.flatMapLatest { (home, date) ->
            if (home != null) {
                repository.getOccurrencesForHomeAndDate(home.id, date)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        todayOccurrences = occurrencesForDate
        allOccurrences = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getAllOccurrencesForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        penaltiesForHome = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getPenaltiesForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        penalties = penaltiesForHome

        problemsForHome = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getProblemsForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        problems = problemsForHome

        needListsForHome = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getNeedListsForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        needLists = needListsForHome

        goalsForHome = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getGoalsForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        goals = goalsForHome

        invitationsForHome = _currentHome.flatMapLatest { home ->
            if (home != null) {
                repository.getInvitationsForHome(home.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        pendingSyncCount = repository.getPendingSyncCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        pendingSyncQueue = repository.getPendingSyncQueue()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // 4. Auto-select active home whenever userHomes changes
        viewModelScope.launch {
            userHomes.collect { homes ->
                if (homes.isNotEmpty()) {
                    val current = _currentHome.value
                    val savedHomeId = ApiClient.getSavedHomeId()
                    if (current == null || homes.none { it.id == current.id }) {
                        val matching = homes.find { it.id == savedHomeId } ?: homes.first()
                        _currentHome.value = matching
                        ApiClient.saveHomeId(matching.id)
                    }
                }
            }
        }

        // 5. Restore saved user session on app launch
        restoreUserSession()
    }

    private fun restoreUserSession() {
        viewModelScope.launch {
            val savedUserId = ApiClient.getSavedUserId()
            val savedHomeId = ApiClient.getSavedHomeId()
            val token = ApiClient.getAuthToken()

            if (savedUserId != null) {
                val user = repository.getUserById(savedUserId)
                if (user != null) {
                    _currentUser.value = user
                    if (savedHomeId != null) {
                        val home = repository.getHomeById(savedHomeId)
                        if (home != null) {
                            _currentHome.value = home
                        }
                    }
                    SyncLogCollector.s("WeHabita:Session", "Sesión de '${user.nombre}' restaurada.")
                    
                    if (!token.isNullOrBlank()) {
                        launch {
                            val (ok, _) = restSyncManager.syncWithServer(savedHomeId)
                            if (ok) {
                                repository.getUserById(savedUserId)?.let { _currentUser.value = it }
                                val activeHId = _currentHome.value?.id ?: ApiClient.getSavedHomeId()
                                if (activeHId != null) {
                                    repository.getHomeById(activeHId)?.let { _currentHome.value = it }
                                }
                            }
                        }
                    }
                    return@launch
                }
            }

            // If user was not found locally but we have a token, pull from server
            if (!token.isNullOrBlank()) {
                SyncLogCollector.i("WeHabita:Session", "Restaurando sesión desde el servidor REST...")
                launch {
                    val (ok, _) = restSyncManager.syncWithServer(savedHomeId)
                    if (ok) {
                        val newUserId = ApiClient.getSavedUserId()
                        if (newUserId != null) {
                            repository.getUserById(newUserId)?.let { _currentUser.value = it }
                        }
                        val newHomeId = ApiClient.getSavedHomeId()
                        if (newHomeId != null) {
                            repository.getHomeById(newHomeId)?.let { _currentHome.value = it }
                        }
                    }
                }
                return@launch
            }

            // Fallback: Check if there is any local user
            val allUsersList = repository.getAllUsers().first()
            if (allUsersList.isNotEmpty()) {
                val firstUser = allUsersList.first()
                _currentUser.value = firstUser
                ApiClient.saveUserId(firstUser.id)
                val hId = ApiClient.getSavedHomeId()
                if (hId != null) {
                    repository.getHomeById(hId)?.let { _currentHome.value = it }
                }
            }
        }
    }

    fun updateRestServerUrl(url: String) {
        val cleanUrl = url.trim()
        ApiClient.setBaseUrl(cleanUrl)
        _restServerUrl.value = ApiClient.getBaseUrl()
        SyncLogCollector.i("WeHabita:Config", "URL de Servidor REST actualizada a: ${ApiClient.getBaseUrl()}")
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

            // 1. Try online REST login
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
                    val db = WeHabitaDatabase.getDatabase(getApplication())
                    db.userDao().insertUser(userEntity)

                    var firstHome: HomeEntity? = null
                    if (p.hogares.isNotEmpty()) {
                        for (h in p.hogares) {
                            val homeEnt = HomeEntity(
                                id = h.hogar_id,
                                nombre = h.hogar_nombre,
                                tipo_hogar = h.tipo_hogar,
                                codigo_invitacion = h.codigo_invitacion,
                                creado_por = p.id
                            )
                            db.homeDao().insertHome(homeEnt)
                            db.homeMemberDao().insertMember(
                                HomeMemberEntity(
                                    id = h.miembro_id,
                                    hogar_id = h.hogar_id,
                                    usuario_id = p.id,
                                    rol = h.rol,
                                    activo = true
                                )
                            )
                            if (firstHome == null) {
                                firstHome = homeEnt
                            }
                        }
                    }

                    val savedHomeId = ApiClient.getSavedHomeId()
                    val selectedHome = if (savedHomeId != null) {
                        db.homeDao().getHomeById(savedHomeId) ?: firstHome
                    } else {
                        firstHome
                    }

                    _currentUser.value = userEntity
                    if (selectedHome != null) {
                        _currentHome.value = selectedHome
                        ApiClient.saveHomeId(selectedHome.id)
                    }

                    // Auto-sync user's complete data from server
                    val activeHomeId = selectedHome?.id ?: savedHomeId
                    launch {
                        restSyncManager.syncWithServer(activeHomeId)
                    }

                    onResult(true, "¡Bienvenido, ${p.nombre} (Conectado a REST API)!")
                    return@launch
                }
            } catch (e: Exception) {
                SyncLogCollector.w("WeHabita:Auth", "Login online falló (${e.message}), recurriendo a SQLite local...")
            }

            // 2. Offline Fallback using Room SQLite
            val localUser = repository.getUserByEmailOrUsername(cleanId.lowercase(Locale.getDefault()))
                ?: repository.getUserByEmailOrUsername(cleanId)

            if (localUser == null) {
                onResult(false, "No se encontró usuario con '$cleanId' ni en línea ni en la base local.")
                return@launch
            }
            if (localUser.password_hash.isNotBlank() && cleanPass.isNotBlank() && localUser.password_hash != cleanPass) {
                onResult(false, "Contraseña incorrecta.")
                return@launch
            }

            ApiClient.saveUserId(localUser.id)
            _currentUser.value = localUser
            
            // Auto restore local home
            val savedHomeId = ApiClient.getSavedHomeId()
            if (savedHomeId != null) {
                val localHome = repository.getHomeById(savedHomeId)
                if (localHome != null) {
                    _currentHome.value = localHome
                }
            }
            onResult(true, "¡Bienvenido, ${localUser.nombre} (Modo Offline Local)!")
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

            // 1. Try online registration
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
                    val db = WeHabitaDatabase.getDatabase(getApplication())
                    db.userDao().insertUser(userEntity)

                    _currentUser.value = userEntity
                    _currentHome.value = null

                    launch {
                        restSyncManager.syncWithServer(null)
                    }

                    onResult(true, "Cuenta registrada en el servidor REST. ¡Bienvenido, ${p.nombre}!")
                    return@launch
                }
            } catch (e: Exception) {
                SyncLogCollector.w("WeHabita:Auth", "Registro online falló (${e.message}), guardando en SQLite local...")
            }

            // 2. Offline registration in SQLite
            val existingEmail = repository.getUserByEmailOrUsername(cleanEmail)
            if (existingEmail != null) {
                onResult(false, "Ya existe una cuenta local con el correo $cleanEmail.")
                return@launch
            }

            val id = repository.registerUser(
                nombre = cleanNombre,
                email = cleanEmail,
                username = cleanUsername,
                passwordHash = cleanPass
            ).toInt()

            val createdUser = repository.getUserById(id)
            if (createdUser != null) {
                ApiClient.saveUserId(createdUser.id)
                _currentUser.value = createdUser
                _currentHome.value = null
                onResult(true, "Cuenta creada en modo local. Se sincronizará automáticamente al conectar.")
            } else {
                onResult(false, "Ocurrió un error al registrar el usuario localmente.")
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
        viewModelScope.launch {
            val id = repository.registerUser(
                nombre = nombre.trim(),
                email = email.trim().lowercase(Locale.getDefault()),
                username = username.trim().lowercase(Locale.getDefault()),
                passwordHash = pass
            ).toInt()
            val user = repository.getUserById(id)
            if (user != null) {
                ApiClient.saveUserId(user.id)
                _currentUser.value = user
                onSuccess(user)
            }
        }
    }

    fun logout() {
        ApiClient.clearSession()
        _currentUser.value = null
        _currentHome.value = null
    }

    fun switchUser(user: UserEntity) {
        _currentUser.value = user
        ApiClient.saveUserId(user.id)
        _currentHome.value = null
    }

    fun switchHome(home: HomeEntity) {
        _currentHome.value = home
        ApiClient.saveHomeId(home.id)
        // Auto pull updates for this home from REST API
        viewModelScope.launch {
            restSyncManager.syncWithServer(home.id)
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

    fun createHome(name: String, tipo: String, onCreated: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val homeId = repository.createHome(name, tipo, user.id).toInt()
            val created = repository.getHomeById(homeId)
            if (created != null) {
                _currentHome.value = created
                ApiClient.saveHomeId(created.id)
            }
            // Auto sync this new home with REST API
            launch {
                restSyncManager.syncWithServer(homeId)
            }
            onCreated()
        }
    }

    fun joinHome(code: String, onResult: (Boolean, String) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val success = repository.joinHomeByInviteCode(code, user.id)
            if (success) {
                // Refresh homes from server
                restSyncManager.syncWithServer(null)
                onResult(true, "¡Te has unido al hogar exitosamente!")
            } else {
                onResult(false, "Código de hogar no encontrado o inválido.")
            }
        }
    }

    private suspend fun getActiveUserAndHome(): Pair<UserEntity, HomeEntity>? {
        val user = _currentUser.value ?: run {
            val sId = ApiClient.getSavedUserId()
            if (sId != null) repository.getUserById(sId)?.also { _currentUser.value = it } else null
        } ?: return null

        val home = _currentHome.value ?: run {
            val sHId = ApiClient.getSavedHomeId()
            if (sHId != null) {
                repository.getHomeById(sHId)?.also { _currentHome.value = it }
            } else {
                val homes = repository.getHomesForUser(user.id).firstOrNull() ?: emptyList()
                homes.firstOrNull()?.also {
                    _currentHome.value = it
                    ApiClient.saveHomeId(it.id)
                }
            }
        } ?: return null

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
                    assignedUserIds = finalAssignees
                )
                launch {
                    restSyncManager.syncWithServer(home.id)
                }
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
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.updateTask(taskId, titulo, descripcion, tipo, fechaInicio, fechaFin, activa)
                val home = _currentHome.value
                if (home != null) {
                    launch { restSyncManager.syncWithServer(home.id) }
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
                val user = _currentUser.value ?: run {
                    val sId = ApiClient.getSavedUserId()
                    if (sId != null) repository.getUserById(sId)?.also { _currentUser.value = it } else null
                }
                repository.completeTaskDirectly(taskId, completed, user?.id)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al completar tarea directamente: ${e.message}")
            }
        }
    }

    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteTask(taskId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar tarea: ${e.message}")
            }
        }
    }

    fun markOccurrence(occurrenceId: Int, completed: Boolean, notes: String? = null) {
        viewModelScope.launch {
            try {
                val user = _currentUser.value ?: run {
                    val sId = ApiClient.getSavedUserId()
                    if (sId != null) repository.getUserById(sId)?.also { _currentUser.value = it } else null
                }
                repository.markOccurrence(
                    occurrenceId = occurrenceId,
                    completed = completed,
                    userId = user?.id,
                    notes = notes
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
                    motivo = motivo,
                    penalizacion = penalizacion,
                    fechaInicio = fechaInicio,
                    fechaFin = fechaFin,
                    assignedUserIds = finalAssignees
                )
                launch {
                    restSyncManager.syncWithServer(home.id)
                }
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
                    involvedUserIds = finalInvolved
                )
                launch {
                    restSyncManager.syncWithServer(home.id)
                }
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
                    val sId = ApiClient.getSavedUserId()
                    if (sId != null) repository.getUserById(sId)?.also { _currentUser.value = it } else null
                } ?: run {
                    onDone()
                    return@launch
                }
                repository.addPointOfView(problemId, user.id, contenido)
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
                repository.updateProblem(problemId, titulo, descripcion, involvedUserIds)
                val home = _currentHome.value
                if (home != null) {
                    launch { restSyncManager.syncWithServer(home.id) }
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
                repository.deleteProblem(problemId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar problema: ${e.message}")
            }
        }
    }

    fun deletePointOfView(problemId: Int, povId: Int) {
        viewModelScope.launch {
            try {
                repository.deletePointOfView(problemId, povId)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al eliminar punto de vista: ${e.message}")
            }
        }
    }

    fun deletePenalty(penaltyId: Int) {
        viewModelScope.launch {
            try {
                repository.deletePenalty(penaltyId)
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
                repository.updateProblemDetails(problem, hablado, solucionado, acuerdo)
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
                launch {
                    restSyncManager.syncWithServer(home.id)
                }
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
                val home = _currentHome.value
                repository.addNeedItem(listaId, finalNombre, cantidad, fechaNecesaria, compradorId, notas)
                if (home != null) {
                    launch {
                        restSyncManager.syncWithServer(home.id)
                    }
                }
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
                repository.updateNeedItemStatus(item, newStatus)
            } catch (e: Throwable) {
                SyncLogCollector.e("WeHabita:ViewModel", "Error al actualizar item: ${e.message}")
            }
        }
    }

    fun deleteNeedItem(itemId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteNeedItem(itemId)
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
                    fechaLimiteOriginal = finalDate,
                    puntos = puntos
                )
                launch {
                    restSyncManager.syncWithServer(home.id)
                }
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
            val user = _currentUser.value ?: run {
                val sId = ApiClient.getSavedUserId()
                if (sId != null) repository.getUserById(sId)?.also { _currentUser.value = it } else null
            } ?: return@launch
            repository.toggleGoalPoint(point, completed, user.id)
        }
    }

    fun deleteGoal(goalId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteGoal(goalId)
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
            repository.setMemberPrivileges(memberId, privilegeIds)
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
}
