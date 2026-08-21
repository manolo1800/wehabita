package com.example.data.repository

import com.example.data.local.WeHabitaDatabase
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WeHabitaRepository(private val db: WeHabitaDatabase) {
    private val userDao = db.userDao()
    private val homeDao = db.homeDao()
    private val homeMemberDao = db.homeMemberDao()
    private val privilegeDao = db.privilegeDao()
    private val invitationDao = db.invitationDao()
    private val taskDao = db.taskDao()
    private val taskOccurrenceDao = db.taskOccurrenceDao()
    private val penaltyDao = db.penaltyDao()
    private val problemDao = db.problemDao()
    private val needListDao = db.needListDao()
    private val goalDao = db.goalDao()

    // --- Users ---
    fun getAllUsers(): Flow<List<UserEntity>> = userDao.getAllActiveUsers()

    suspend fun getUserById(id: Int): UserEntity? = userDao.getUserById(id)

    suspend fun getUserByEmailOrUsername(query: String): UserEntity? =
        userDao.getUserByEmailOrUsername(query)

    suspend fun registerUser(nombre: String, email: String, username: String, passwordHash: String): Long {
        val randomCode = "USR-" + (1000..9999).random()
        val user = UserEntity(
            nombre = nombre,
            email = email,
            username = username,
            password_hash = passwordHash,
            codigo_usuario = randomCode
        )
        return userDao.insertUser(user)
    }

    // --- Homes ---
    fun getHomesForUser(userId: Int): Flow<List<HomeEntity>> = homeDao.getHomesForUser(userId)

    suspend fun getHomeById(homeId: Int): HomeEntity? = homeDao.getHomeById(homeId)

    private suspend fun ensureStandardPrivileges(): List<PrivilegeEntity> {
        var list = privilegeDao.getAllPrivilegesList()
        if (list.isEmpty()) {
            val defaults = listOf(
                PrivilegeEntity(nombre = "crear_tareas"),
                PrivilegeEntity(nombre = "asignar_tareas"),
                PrivilegeEntity(nombre = "marcar_tareas"),
                PrivilegeEntity(nombre = "crear_problemas"),
                PrivilegeEntity(nombre = "crear_necesidades"),
                PrivilegeEntity(nombre = "crear_objetivos"),
                PrivilegeEntity(nombre = "gestionar_usuarios"),
                PrivilegeEntity(nombre = "aplicar_penalizaciones"),
                PrivilegeEntity(nombre = "ver_reportes")
            )
            privilegeDao.insertPrivileges(defaults)
            list = privilegeDao.getAllPrivilegesList()
        }
        return list
    }

    suspend fun createHome(name: String, tipo: String, createdBy: Int): Long {
        val inviteCode = "HOG-" + (1000..9999).random()
        val home = HomeEntity(
            nombre = name,
            tipo_hogar = tipo,
            codigo_invitacion = inviteCode,
            creado_por = createdBy
        )
        val homeId = homeDao.insertHome(home).toInt()

        // Make creator admin with all privileges
        val memberId = homeMemberDao.insertMember(
            HomeMemberEntity(
                hogar_id = homeId,
                usuario_id = createdBy,
                rol = "admin"
            )
        ).toInt()

        val privs = ensureStandardPrivileges()
        for (priv in privs) {
            privilegeDao.insertMemberPrivilege(MemberPrivilegeEntity(memberId, priv.id))
        }

        return homeId.toLong()
    }

    suspend fun joinHomeByInviteCode(inviteCode: String, userId: Int): Boolean {
        val home = homeDao.getHomeByInviteCode(inviteCode.trim().uppercase(Locale.getDefault())) ?: return false
        val existing = homeMemberDao.getMember(home.id, userId)
        if (existing != null) return true // Already member

        val memberId = homeMemberDao.insertMember(
            HomeMemberEntity(
                hogar_id = home.id,
                usuario_id = userId,
                rol = "miembro"
            )
        ).toInt()

        val privs = ensureStandardPrivileges()
        val defaultPrivNames = listOf("marcar_tareas", "crear_problemas", "crear_necesidades")
        privs.filter { it.nombre in defaultPrivNames }.forEach { priv ->
            privilegeDao.insertMemberPrivilege(MemberPrivilegeEntity(memberId, priv.id))
        }
        return true
    }

    // --- Members & Privileges ---
    fun getMembersWithUserForHome(homeId: Int): Flow<List<MemberWithUser>> =
        homeMemberDao.getMembersWithUserForHome(homeId)

    fun getAllPrivileges(): Flow<List<PrivilegeEntity>> = privilegeDao.getAllPrivileges()

    fun getPrivilegesForMember(memberId: Int): Flow<List<PrivilegeEntity>> =
        privilegeDao.getPrivilegesForMember(memberId)

    suspend fun setMemberPrivileges(memberId: Int, privilegeIds: List<Int>) {
        privilegeDao.clearMemberPrivileges(memberId)
        for (pId in privilegeIds) {
            privilegeDao.insertMemberPrivilege(MemberPrivilegeEntity(memberId, pId))
        }
    }

    suspend fun updateMemberRole(member: HomeMemberEntity, newRole: String) {
        homeMemberDao.updateMember(member.copy(rol = newRole))
    }

    suspend fun removeMember(homeId: Int, userId: Int) {
        homeMemberDao.removeMember(homeId, userId)
    }

    // --- Invitations ---
    fun getInvitationsForHome(homeId: Int): Flow<List<InvitationEntity>> =
        invitationDao.getInvitationsForHome(homeId)

    suspend fun createInvitation(homeId: Int, createdBy: Int, email: String?, userCode: String?): Long {
        var destUser: UserEntity? = null
        if (!userCode.isNullOrBlank()) {
            destUser = userDao.getUserByCode(userCode.trim())
        }
        val code = "INV-" + UUID.randomUUID().toString().take(6).uppercase(Locale.getDefault())
        val invitation = InvitationEntity(
            hogar_id = homeId,
            codigo = code,
            email_destino = email?.ifBlank { null } ?: destUser?.email,
            usuario_destino_id = destUser?.id,
            creado_por = createdBy
        )
        return invitationDao.insertInvitation(invitation)
    }

    // --- Tasks & Schedules ---
    fun getTasksForHome(homeId: Int): Flow<List<TaskEntity>> = taskDao.getTasksForHome(homeId)

    fun getSchedulesForTask(taskId: Int): Flow<List<TaskScheduleEntity>> =
        taskDao.getSchedulesForTask(taskId)

    fun getAssignmentsForTask(taskId: Int): Flow<List<TaskAssignmentEntity>> =
        taskDao.getAssignmentsForTask(taskId)

    suspend fun createTask(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcion: String?,
        tipo: String, // 'unica' | 'fija'
        fechaInicio: String,
        fechaFin: String?,
        horarios: List<Pair<String, String?>>, // List of (hora_inicio, hora_fin)
        assignedUserIds: List<Int>
    ): Long {
        val task = TaskEntity(
            hogar_id = homeId,
            creado_por = createdBy,
            titulo = titulo,
            descripcion = descripcion,
            tipo = tipo,
            fecha_inicio = fechaInicio,
            fecha_fin = fechaFin,
            activa = true
        )
        val taskId = taskDao.insertTask(task).toInt()

        for ((hInicio, hFin) in horarios) {
            taskDao.insertSchedule(
                TaskScheduleEntity(
                    tarea_id = taskId,
                    hora_inicio = hInicio,
                    hora_fin = hFin
                )
            )
            // Generate occurrence for start date
            taskOccurrenceDao.insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = taskId,
                    fecha = fechaInicio,
                    hora_inicio = hInicio,
                    hora_fin = hFin,
                    completada = false
                )
            )
        }

        for (userId in assignedUserIds) {
            taskDao.insertAssignment(TaskAssignmentEntity(tarea_id = taskId, usuario_id = userId))
        }

        return taskId.toLong()
    }

    suspend fun deleteTask(taskId: Int) {
        taskDao.deleteTask(taskId)
    }

    // --- Occurrences ---
    fun getOccurrencesForHomeAndDate(homeId: Int, date: String): Flow<List<OccurrenceWithDetails>> =
        taskOccurrenceDao.getOccurrencesForHomeAndDate(homeId, date)

    fun getAllOccurrencesForHome(homeId: Int): Flow<List<OccurrenceWithDetails>> =
        taskOccurrenceDao.getAllOccurrencesForHome(homeId)

    suspend fun ensureTodayOccurrences(homeId: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        // Generate today occurrences for active tasks if missing
    }

    suspend fun markOccurrence(occurrenceId: Int, completed: Boolean, userId: Int?, notes: String?) {
        taskOccurrenceDao.markOccurrence(
            occurrenceId = occurrenceId,
            completed = completed,
            completedBy = if (completed) userId else null,
            completedAt = if (completed) System.currentTimeMillis() else null,
            notes = notes
        )
    }

    // --- Penalties ---
    fun getPenaltiesForHome(homeId: Int): Flow<List<PenaltyEntity>> =
        penaltyDao.getPenaltiesForHome(homeId)

    fun getAssignedUsersForPenalty(penaltyId: Int): Flow<List<Int>> =
        penaltyDao.getAssignedUserIds(penaltyId)

    suspend fun createPenalty(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        motivo: String,
        penalizacion: String,
        fechaInicio: String,
        fechaFin: String?,
        assignedUserIds: List<Int>
    ): Long {
        val penalty = PenaltyEntity(
            hogar_id = homeId,
            creado_por = createdBy,
            titulo = titulo,
            descripcion_motivo = motivo,
            penalizacion = penalizacion,
            fecha_inicio = fechaInicio,
            fecha_fin = fechaFin,
            estado = "activa"
        )
        val pId = penaltyDao.insertPenalty(penalty).toInt()
        for (uId in assignedUserIds) {
            penaltyDao.insertPenaltyUser(PenaltyUserEntity(penalizacion_id = pId, usuario_id = uId))
        }
        return pId.toLong()
    }

    suspend fun updatePenaltyStatus(penalty: PenaltyEntity, newStatus: String) {
        penaltyDao.updatePenalty(penalty.copy(estado = newStatus))
    }

    // --- Problems & Conflicts ---
    fun getProblemsForHome(homeId: Int): Flow<List<ProblemEntity>> =
        problemDao.getProblemsForHome(homeId)

    fun getInvolvedUsersForProblem(problemId: Int): Flow<List<Int>> =
        problemDao.getInvolvedUserIds(problemId)

    fun getPointsOfViewForProblem(problemId: Int): Flow<List<ProblemPointOfViewEntity>> =
        problemDao.getPointsOfViewForProblem(problemId)

    suspend fun createProblem(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcion: String,
        involvedUserIds: List<Int>
    ): Long {
        val problem = ProblemEntity(
            hogar_id = homeId,
            creado_por = createdBy,
            titulo = titulo,
            descripcion = descripcion,
            hablado = false,
            solucionado = false
        )
        val pId = problemDao.insertProblem(problem).toInt()
        for (uId in involvedUserIds) {
            problemDao.insertProblemUser(ProblemUserEntity(problema_id = pId, usuario_id = uId))
        }
        return pId.toLong()
    }

    suspend fun addPointOfView(problemId: Int, userId: Int, contenido: String): Long {
        return problemDao.insertPointOfView(
            ProblemPointOfViewEntity(
                problema_id = problemId,
                usuario_id = userId,
                contenido = contenido
            )
        )
    }

    suspend fun updateProblemDetails(
        problem: ProblemEntity,
        hablado: Boolean,
        solucionado: Boolean,
        acuerdo: String?
    ) {
        problemDao.updateProblem(
            problem.copy(
                hablado = hablado,
                solucionado = solucionado,
                acuerdo = acuerdo
            )
        )
    }

    // --- Shopping / Needs ---
    fun getNeedListsForHome(homeId: Int): Flow<List<NeedListEntity>> =
        needListDao.getListsForHome(homeId)

    fun getNeedItemsForList(listId: Int): Flow<List<NeedItemEntity>> =
        needListDao.getItemsForList(listId)

    suspend fun createNeedList(homeId: Int, createdBy: Int, titulo: String): Long {
        return needListDao.insertList(
            NeedListEntity(
                hogar_id = homeId,
                creado_por = createdBy,
                titulo = titulo
            )
        )
    }

    suspend fun addNeedItem(
        listaId: Int,
        nombre: String,
        cantidad: Int,
        fechaNecesaria: String,
        compradorId: Int?,
        notas: String?
    ): Long {
        return needListDao.insertItem(
            NeedItemEntity(
                lista_id = listaId,
                producto_nombre = nombre,
                cantidad = cantidad,
                fecha_necesaria = fechaNecesaria,
                comprador_id = compradorId,
                estado = "pendiente",
                notas = notas
            )
        )
    }

    suspend fun updateNeedItemStatus(item: NeedItemEntity, newStatus: String) {
        needListDao.updateItem(item.copy(estado = newStatus))
    }

    suspend fun deleteNeedItem(itemId: Int) {
        needListDao.deleteItem(itemId)
    }

    // --- Goals & Compound Missions ---
    fun getGoalsForHome(homeId: Int): Flow<List<GoalEntity>> = goalDao.getGoalsForHome(homeId)

    fun getGoalPoints(goalId: Int): Flow<List<GoalPointEntity>> = goalDao.getPointsForGoal(goalId)

    suspend fun createGoal(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcion: String?,
        tipo: String, // 'simple' | 'compuesto'
        fechaLimiteOriginal: String,
        puntos: List<Pair<String, Int?>> // (title, assignedUserId)
    ): Long {
        val goal = GoalEntity(
            hogar_id = homeId,
            creado_por = createdBy,
            titulo = titulo,
            descripcion = descripcion,
            tipo = tipo,
            completado = false,
            fecha_limite_original = fechaLimiteOriginal,
            fecha_limite_actual = fechaLimiteOriginal
        )
        val goalId = goalDao.insertGoal(goal).toInt()

        puntos.forEachIndexed { index, (ptTitle, assignedTo) ->
            goalDao.insertGoalPoint(
                GoalPointEntity(
                    objetivo_id = goalId,
                    titulo = ptTitle,
                    asignado_a = assignedTo,
                    orden = index + 1
                )
            )
        }

        return goalId.toLong()
    }

    suspend fun postponeGoal(goal: GoalEntity, newDeadline: String) {
        // Keeps original deadline intact, updates actual deadline
        goalDao.updateGoal(goal.copy(fecha_limite_actual = newDeadline))
    }

    suspend fun setGoalCompleted(goal: GoalEntity, completed: Boolean) {
        goalDao.updateGoal(
            goal.copy(
                completado = completed,
                fecha_completado = if (completed) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun toggleGoalPoint(point: GoalPointEntity, completed: Boolean, userId: Int?) {
        goalDao.updateGoalPoint(
            point.copy(
                completado = completed,
                fecha_completado = if (completed) System.currentTimeMillis() else null,
                completado_por = if (completed) userId else null
            )
        )
    }
}
