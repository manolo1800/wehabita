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

    init {
        val db = WeHabitaDatabase.getDatabase(application)
        restSyncManager = RestSyncManager(db)
        repository = WeHabitaRepository(db, restSyncManager)
    }

    // REST Server Base URL
    private val _restServerUrl = MutableStateFlow(ApiClient.getBaseUrl())
    val restServerUrl: StateFlow<String> = _restServerUrl.asStateFlow()

    fun updateRestServerUrl(url: String) {
        val cleanUrl = url.trim()
        ApiClient.setBaseUrl(cleanUrl)
        _restServerUrl.value = ApiClient.getBaseUrl()
        SyncLogCollector.i("WeHabita:Config", "URL de Servidor REST actualizada a: ${ApiClient.getBaseUrl()}")
    }

    // Pending Sync Flow
    val pendingSyncCount: StateFlow<Int> = repository.getPendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingSyncQueue: StateFlow<List<SyncQueueEntity>> = repository.getPendingSyncQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sync State
    private val _lastSyncStatus = MutableStateFlow("Local Room SQLite Activo (Offline-First)")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Diagnostic Logs Flow
    val diagnosticLogs: StateFlow<List<com.example.data.repository.DiagnosticLogEntry>> = SyncLogCollector.logs

    fun clearDiagnosticLogs() {
        SyncLogCollector.clear()
    }

    fun getDiagnosticLogsAsText(): String {
        return SyncLogCollector.getAllLogsAsText()
    }

    // All Users
    val allUsers: StateFlow<List<UserEntity>> = repository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current User Session
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    // Homes for Current User
    val userHomes: StateFlow<List<HomeEntity>> = _currentUser.flatMapLatest { user ->
        if (user != null) {
            repository.getHomesForUser(user.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Home
    private val _currentHome = MutableStateFlow<HomeEntity?>(null)
    val currentHome: StateFlow<HomeEntity?> = _currentHome.asStateFlow()

    // Members of Current Home
    val homeMembers: StateFlow<List<MemberWithUser>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getMembersWithUserForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current User's Member record & privileges in current home
    val currentMember: StateFlow<HomeMemberEntity?> = combine(_currentUser, homeMembers) { user, members ->
        if (user == null) null
        else members.find { it.user.id == user.id }?.member
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current User's Privileges in active home
    val userPrivileges: StateFlow<List<String>> = currentMember.flatMapLatest { member ->
        if (member != null) {
            repository.getPrivilegesForMember(member.id).map { list -> list.map { it.nombre } }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Privileges catalog
    val allPrivileges: StateFlow<List<PrivilegeEntity>> = repository.getAllPrivileges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Date for Agenda View
    private val _selectedDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Tasks for Current Home
    val tasksForHome: StateFlow<List<TaskEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getTasksForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = tasksForHome

    // Occurrences for Current Home and Selected Date
    val occurrencesForDate: StateFlow<List<OccurrenceWithDetails>> = combine(_currentHome, _selectedDate) { home, date ->
        Pair(home, date)
    }.flatMapLatest { (home, date) ->
        if (home != null) {
            repository.getOccurrencesForHomeAndDate(home.id, date)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayOccurrences: StateFlow<List<OccurrenceWithDetails>> = occurrencesForDate
    val allOccurrences: StateFlow<List<OccurrenceWithDetails>> = occurrencesForDate

    // Penalties for Current Home
    val penaltiesForHome: StateFlow<List<PenaltyEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getPenaltiesForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val penalties: StateFlow<List<PenaltyEntity>> = penaltiesForHome

    // Problems for Current Home
    val problemsForHome: StateFlow<List<ProblemEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getProblemsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val problems: StateFlow<List<ProblemEntity>> = problemsForHome

    // Need Lists for Current Home
    val needListsForHome: StateFlow<List<NeedListEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getNeedListsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val needLists: StateFlow<List<NeedListEntity>> = needListsForHome

    // Goals for Current Home
    val goalsForHome: StateFlow<List<GoalEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getGoalsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goals: StateFlow<List<GoalEntity>> = goalsForHome

    // Invitations for Current Home
    val invitationsForHome: StateFlow<List<InvitationEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getInvitationsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Permission Checking Helper
    fun hasPrivilege(privilegeName: String): Boolean {
        val user = _currentUser.value ?: return false
        if (user.rol_global == "admin") return true
        val member = currentMember.value ?: return false
        if (member.rol == "admin") return true
        return userPrivileges.value.contains(privilegeName)
    }

    fun isHomeAdmin(): Boolean {
        val user = _currentUser.value ?: return false
        if (user.rol_global == "admin") return true
        val member = currentMember.value ?: return false
        return member.rol == "admin"
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

                    // Auto-sync user's data from server
                    launch {
                        restSyncManager.syncWithServer(null)
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

            _currentUser.value = localUser
            _currentHome.value = null
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
                _currentUser.value = user
                onSuccess(user)
            }
        }
    }

    fun logout() {
        ApiClient.setAuthToken(null)
        _currentUser.value = null
        _currentHome.value = null
    }

    fun switchUser(user: UserEntity) {
        _currentUser.value = user
        _currentHome.value = null
    }

    fun switchHome(home: HomeEntity) {
        _currentHome.value = home
        // Auto pull updates for this home if online
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
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.createTask(
                homeId = home.id,
                createdBy = user.id,
                titulo = titulo,
                descripcion = descripcion,
                tipo = tipo,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                horarios = horarios,
                assignedUserIds = assignedUserIds
            )
            onDone()
        }
    }

    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun markOccurrence(occurrenceId: Int, completed: Boolean, notes: String? = null) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.markOccurrence(
                occurrenceId = occurrenceId,
                completed = completed,
                userId = user.id,
                notes = notes
            )
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
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.createPenalty(
                homeId = home.id,
                createdBy = user.id,
                titulo = titulo,
                motivo = motivo,
                penalizacion = penalizacion,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                assignedUserIds = assignedUserIds
            )
            onDone()
        }
    }

    fun updatePenaltyStatus(penalty: PenaltyEntity, newStatus: String) {
        viewModelScope.launch {
            repository.updatePenaltyStatus(penalty, newStatus)
        }
    }

    // Problems Actions
    fun createProblem(
        titulo: String,
        descripcion: String,
        involvedUserIds: List<Int>,
        onDone: () -> Unit
    ) {
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.createProblem(
                homeId = home.id,
                createdBy = user.id,
                titulo = titulo,
                descripcion = descripcion,
                involvedUserIds = involvedUserIds
            )
            onDone()
        }
    }

    fun addPointOfView(problemId: Int, contenido: String, onDone: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.addPointOfView(problemId, user.id, contenido)
            onDone()
        }
    }

    fun updateProblemDetails(
        problem: ProblemEntity,
        hablado: Boolean,
        solucionado: Boolean,
        acuerdo: String?
    ) {
        viewModelScope.launch {
            repository.updateProblemDetails(problem, hablado, solucionado, acuerdo)
        }
    }

    // Needs & Shopping Actions
    fun createNeedList(titulo: String, onDone: () -> Unit) {
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.createNeedList(home.id, user.id, titulo)
            onDone()
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
            repository.addNeedItem(listaId, finalNombre, cantidad, fechaNecesaria, compradorId, notas)
            onDone()
        }
    }

    fun updateNeedItemStatus(item: NeedItemEntity, newStatus: String) {
        viewModelScope.launch {
            repository.updateNeedItemStatus(item, newStatus)
        }
    }

    fun deleteNeedItem(itemId: Int) {
        viewModelScope.launch {
            repository.deleteNeedItem(itemId)
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
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        val finalDate = if (fechaLimiteOriginal.isNotBlank()) fechaLimiteOriginal else fechaLimite
        viewModelScope.launch {
            repository.createGoal(
                homeId = home.id,
                createdBy = user.id,
                titulo = titulo,
                descripcion = descripcion,
                tipo = tipo,
                fechaLimiteOriginal = finalDate,
                puntos = puntos
            )
            onDone()
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
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.toggleGoalPoint(point, completed, user.id)
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
        val home = _currentHome.value ?: return
        viewModelScope.launch {
            repository.removeMember(home.id, userId)
        }
    }

    fun createInvitation(
        email: String?,
        userCode: String?,
        onResult: (String) -> Unit = {}
    ) {
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
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
