package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WeHabitaDatabase
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import com.example.data.repository.WeHabitaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserSession(
    val currentUser: UserEntity? = null,
    val currentHome: HomeEntity? = null,
    val currentMember: HomeMemberEntity? = null,
    val userPrivileges: Set<String> = emptySet()
)

data class TaskStats(
    val totalOccurrences: Int = 0,
    val completedCount: Int = 0,
    val pendingCount: Int = 0,
    val completionRate: Float = 0f
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeHabitaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WeHabitaRepository

    init {
        val db = WeHabitaDatabase.getDatabase(application)
        repository = WeHabitaRepository(db)
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
    val userPrivileges: StateFlow<Set<String>> = currentMember.flatMapLatest { member ->
        if (member == null) {
            flowOf(emptySet())
        } else if (member.rol == "admin") {
            // Admins inherently have all privileges
            flowOf(
                setOf(
                    "crear_tareas", "asignar_tareas", "marcar_tareas",
                    "crear_problemas", "crear_necesidades", "crear_objetivos",
                    "gestionar_usuarios", "aplicar_penalizaciones", "ver_reportes"
                )
            )
        } else {
            repository.getPrivilegesForMember(member.id).map { list -> list.map { it.nombre }.toSet() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Date filter for task occurrences (defaults to today)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val _selectedDate = MutableStateFlow(dateFormat.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Task Occurrences for current home and selected date
    val todayOccurrences: StateFlow<List<OccurrenceWithDetails>> = combine(_currentHome, _selectedDate) { home, date ->
        Pair(home, date)
    }.flatMapLatest { (home, date) ->
        if (home != null) {
            repository.getOccurrencesForHomeAndDate(home.id, date)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Occurrences for current home (for stats/recuento)
    val allOccurrences: StateFlow<List<OccurrenceWithDetails>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getAllOccurrencesForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Task definitions for current home
    val tasks: StateFlow<List<TaskEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getTasksForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Penalties for current home
    val penalties: StateFlow<List<PenaltyEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getPenaltiesForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Problems for current home
    val problems: StateFlow<List<ProblemEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getProblemsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Need Lists for current home
    val needLists: StateFlow<List<NeedListEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getNeedListsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Goals for current home
    val goals: StateFlow<List<GoalEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getGoalsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Invitations for current home
    val invitations: StateFlow<List<InvitationEntity>> = _currentHome.flatMapLatest { home ->
        if (home != null) {
            repository.getInvitationsForHome(home.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Available privileges catalog
    val allPrivileges: StateFlow<List<PrivilegeEntity>> = repository.getAllPrivileges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Auto-select initial user (Carlos) and initial home if available
        viewModelScope.launch {
            allUsers.collect { users ->
                if (_currentUser.value == null && users.isNotEmpty()) {
                    _currentUser.value = users.first()
                }
            }
        }
        viewModelScope.launch {
            userHomes.collect { homes ->
                if (_currentHome.value == null && homes.isNotEmpty()) {
                    _currentHome.value = homes.first()
                } else if (_currentHome.value != null && !homes.any { it.id == _currentHome.value?.id }) {
                    _currentHome.value = homes.firstOrNull()
                }
            }
        }
    }

    // Permission Check Helper
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

    // Actions
    fun switchUser(user: UserEntity) {
        _currentUser.value = user
        _currentHome.value = null
    }

    fun switchHome(home: HomeEntity) {
        _currentHome.value = home
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun registerNewUser(nombre: String, email: String, username: String, pass: String, onSuccess: (UserEntity) -> Unit) {
        viewModelScope.launch {
            val id = repository.registerUser(nombre, email, username, pass).toInt()
            val user = repository.getUserById(id)
            if (user != null) {
                _currentUser.value = user
                onSuccess(user)
            }
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

    // Penalty Actions
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

    fun updatePenaltyStatus(penalty: PenaltyEntity, status: String) {
        viewModelScope.launch {
            repository.updatePenaltyStatus(penalty, status)
        }
    }

    // Problem Actions
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

    fun getPointsOfView(problemId: Int): Flow<List<ProblemPointOfViewEntity>> =
        repository.getPointsOfViewForProblem(problemId)

    fun getInvolvedUsers(problemId: Int): Flow<List<Int>> =
        repository.getInvolvedUsersForProblem(problemId)

    fun getAssignedUsersForPenalty(penaltyId: Int): Flow<List<Int>> =
        repository.getAssignedUsersForPenalty(penaltyId)

    // Shopping / Needs
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
        productoNombre: String,
        cantidad: Int,
        fechaNecesaria: String,
        compradorId: Int?,
        notas: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addNeedItem(
                listaId = listaId,
                nombre = productoNombre,
                cantidad = cantidad,
                fechaNecesaria = fechaNecesaria,
                compradorId = compradorId,
                notas = notas
            )
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

    fun getItemsForList(listId: Int): Flow<List<NeedItemEntity>> =
        repository.getNeedItemsForList(listId)

    // Goals
    fun createGoal(
        titulo: String,
        descripcion: String?,
        tipo: String,
        fechaLimite: String,
        puntos: List<Pair<String, Int?>>,
        onDone: () -> Unit
    ) {
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.createGoal(
                homeId = home.id,
                createdBy = user.id,
                titulo = titulo,
                descripcion = descripcion,
                tipo = tipo,
                fechaLimiteOriginal = fechaLimite,
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
        val user = _currentUser.value
        viewModelScope.launch {
            repository.toggleGoalPoint(point, completed, user?.id)
        }
    }

    fun getPointsForGoal(goalId: Int): Flow<List<GoalPointEntity>> =
        repository.getGoalPoints(goalId)

    // Member Management
    fun createInvitation(email: String?, userCode: String?, onDone: (String) -> Unit) {
        val home = _currentHome.value ?: return
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val invId = repository.createInvitation(home.id, user.id, email, userCode)
            onDone("Invitación creada exitosamente")
        }
    }

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

    fun getPrivilegesForMember(memberId: Int): Flow<List<PrivilegeEntity>> =
        repository.getPrivilegesForMember(memberId)

    fun removeMember(userId: Int) {
        val home = _currentHome.value ?: return
        viewModelScope.launch {
            repository.removeMember(home.id, userId)
        }
    }
}
