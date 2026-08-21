package com.example.data.repository

import com.example.data.local.WeHabitaDatabase
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import com.example.data.remote.api.ApiClient
import com.example.data.remote.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WeHabitaRepository(
    private val db: WeHabitaDatabase,
    val restSyncManager: RestSyncManager = RestSyncManager(db)
) {
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
    private val syncQueueDao = db.syncQueueDao()

    private val bgScope = CoroutineScope(Dispatchers.IO)

    // Pending Sync Flow
    fun getPendingSyncCount(): Flow<Int> = syncQueueDao.getPendingCount()
    fun getPendingSyncQueue(): Flow<List<SyncQueueEntity>> = syncQueueDao.getAllPendingQueue()

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
        val localId = userDao.insertUser(user)

        // Try online registration if available
        bgScope.launch {
            try {
                val resp = ApiClient.service.register(
                    RegisterRequest(
                        nombre = nombre,
                        email = email,
                        username = username,
                        password = passwordHash
                    )
                )
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val authData = resp.body()!!.data
                    ApiClient.setAuthToken(authData.access_token)
                } else {
                    // Queue for sync
                    val json = JSONObject().apply {
                        put("nombre", nombre)
                        put("email", email)
                        put("username", username)
                        put("password", passwordHash)
                    }.toString()
                    restSyncManager.enqueueOfflineAction("auth/register", "POST", json, "REGISTRAR_USUARIO", "Registro de usuario $username")
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("nombre", nombre)
                    put("email", email)
                    put("username", username)
                    put("password", passwordHash)
                }.toString()
                restSyncManager.enqueueOfflineAction("auth/register", "POST", json, "REGISTRAR_USUARIO", "Registro de usuario $username")
            }
        }
        return localId
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

        // Online sync
        bgScope.launch {
            try {
                val resp = ApiClient.service.createHome(CreateHomeRequest(nombre = name, tipo_hogar = tipo))
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("nombre", name)
                        put("tipo_hogar", tipo)
                    }.toString()
                    restSyncManager.enqueueOfflineAction("hogares", "POST", json, "CREAR_HOGAR", "Crear hogar $name")
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("nombre", name)
                    put("tipo_hogar", tipo)
                }.toString()
                restSyncManager.enqueueOfflineAction("hogares", "POST", json, "CREAR_HOGAR", "Crear hogar $name")
            }
        }

        return homeId.toLong()
    }

    suspend fun joinHomeByInviteCode(inviteCode: String, userId: Int): Boolean {
        val cleanCode = inviteCode.trim().uppercase(Locale.getDefault())
        val home = homeDao.getHomeByInviteCode(cleanCode)

        // Try online join first
        bgScope.launch {
            try {
                val resp = ApiClient.service.joinHome(JoinHomeRequest(codigo = cleanCode))
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("codigo", cleanCode) }.toString()
                    restSyncManager.enqueueOfflineAction("invitaciones/unirse", "POST", json, "UNIRSE_HOGAR", "Unirse al hogar con código $cleanCode")
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("codigo", cleanCode) }.toString()
                restSyncManager.enqueueOfflineAction("invitaciones/unirse", "POST", json, "UNIRSE_HOGAR", "Unirse al hogar con código $cleanCode")
            }
        }

        if (home != null) {
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
        bgScope.launch {
            try {
                val resp = ApiClient.service.updateMemberRole(
                    homeId = member.hogar_id,
                    memberId = member.id,
                    request = UpdateMemberRoleRequest(rol = newRole)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("rol", newRole) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${member.hogar_id}/miembros/${member.id}/rol",
                        "PUT",
                        json,
                        "ACTUALIZAR_ROL",
                        "Actualizar rol de miembro a $newRole"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("rol", newRole) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/${member.hogar_id}/miembros/${member.id}/rol",
                    "PUT",
                    json,
                    "ACTUALIZAR_ROL",
                    "Actualizar rol de miembro a $newRole"
                )
            }
        }
    }

    suspend fun removeMember(homeId: Int, userId: Int) {
        val member = homeMemberDao.getMember(homeId, userId)
        homeMemberDao.removeMember(homeId, userId)
        if (member != null) {
            bgScope.launch {
                try {
                    val resp = ApiClient.service.removeMember(homeId, member.id)
                    if (!resp.isSuccessful) {
                        restSyncManager.enqueueOfflineAction(
                            "hogares/$homeId/miembros/${member.id}",
                            "DELETE",
                            null,
                            "ELIMINAR_MIEMBRO",
                            "Eliminar miembro de hogar"
                        )
                    }
                } catch (e: Exception) {
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/miembros/${member.id}",
                        "DELETE",
                        null,
                        "ELIMINAR_MIEMBRO",
                        "Eliminar miembro de hogar"
                    )
                }
            }
        }
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
        val invId = invitationDao.insertInvitation(invitation)

        bgScope.launch {
            try {
                val resp = ApiClient.service.createInvitation(
                    homeId = homeId,
                    request = CreateInvitationRequest(
                        email_destino = email,
                        codigo_usuario_destino = userCode
                    )
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("email_destino", email)
                        put("codigo_usuario_destino", userCode)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/invitaciones",
                        "POST",
                        json,
                        "CREAR_INVITACION",
                        "Crear invitación para $email"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("email_destino", email)
                    put("codigo_usuario_destino", userCode)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/invitaciones",
                    "POST",
                    json,
                    "CREAR_INVITACION",
                    "Crear invitación para $email"
                )
            }
        }
        return invId
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
        horarios: List<Pair<String, String?>>,
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

        // Online sync
        bgScope.launch {
            try {
                val schedulesDto = horarios.map { ScheduleDto(hora_inicio = it.first, hora_fin = it.second) }
                val resp = ApiClient.service.createTask(
                    homeId = homeId,
                    request = CreateTaskRequest(
                        titulo = titulo,
                        descripcion = descripcion,
                        tipo = tipo,
                        fecha_inicio = fechaInicio,
                        fecha_fin = fechaFin,
                        horarios = schedulesDto,
                        asignados_ids = assignedUserIds
                    )
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("titulo", titulo)
                        put("descripcion", descripcion)
                        put("tipo", tipo)
                        put("fecha_inicio", fechaInicio)
                        put("fecha_fin", fechaFin)
                        val hArr = JSONArray()
                        for ((hIn, hOut) in horarios) {
                            val hObj = JSONObject()
                            hObj.put("hora_inicio", hIn)
                            if (hOut != null) hObj.put("hora_fin", hOut)
                            hArr.put(hObj)
                        }
                        put("horarios", hArr)
                        val aArr = JSONArray()
                        for (uId in assignedUserIds) aArr.put(uId)
                        put("asignados_ids", aArr)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/tareas",
                        "POST",
                        json,
                        "CREAR_TAREA",
                        "Crear tarea $titulo"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("titulo", titulo)
                    put("descripcion", descripcion)
                    put("tipo", tipo)
                    put("fecha_inicio", fechaInicio)
                    put("fecha_fin", fechaFin)
                    val hArr = JSONArray()
                    for ((hIn, hOut) in horarios) {
                        val hObj = JSONObject()
                        hObj.put("hora_inicio", hIn)
                        if (hOut != null) hObj.put("hora_fin", hOut)
                        hArr.put(hObj)
                    }
                    put("horarios", hArr)
                    val aArr = JSONArray()
                    for (uId in assignedUserIds) aArr.put(uId)
                    put("asignados_ids", aArr)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/tareas",
                    "POST",
                    json,
                    "CREAR_TAREA",
                    "Crear tarea $titulo"
                )
            }
        }

        return taskId.toLong()
    }

    suspend fun deleteTask(taskId: Int) {
        val task = taskDao.getTaskById(taskId)
        taskDao.deleteTask(taskId)
        if (task != null) {
            bgScope.launch {
                try {
                    val resp = ApiClient.service.deleteTask(task.hogar_id, taskId)
                    if (!resp.isSuccessful) {
                        restSyncManager.enqueueOfflineAction(
                            "hogares/${task.hogar_id}/tareas/$taskId",
                            "DELETE",
                            null,
                            "ELIMINAR_TAREA",
                            "Eliminar tarea #${taskId}"
                        )
                    }
                } catch (e: Exception) {
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${task.hogar_id}/tareas/$taskId",
                        "DELETE",
                        null,
                        "ELIMINAR_TAREA",
                        "Eliminar tarea #${taskId}"
                    )
                }
            }
        }
    }

    // --- Occurrences ---
    fun getOccurrencesForHomeAndDate(homeId: Int, date: String): Flow<List<OccurrenceWithDetails>> =
        taskOccurrenceDao.getOccurrencesForHomeAndDate(homeId, date)

    fun getAllOccurrencesForHome(homeId: Int): Flow<List<OccurrenceWithDetails>> =
        taskOccurrenceDao.getAllOccurrencesForHome(homeId)

    suspend fun markOccurrence(occurrenceId: Int, completed: Boolean, userId: Int?, notes: String?) {
        taskOccurrenceDao.markOccurrence(
            occurrenceId = occurrenceId,
            completed = completed,
            completedBy = if (completed) userId else null,
            completedAt = if (completed) System.currentTimeMillis() else null,
            notes = notes
        )

        // Try sync online
        bgScope.launch {
            try {
                // Find occurrence's home id
                val taskOcc = taskOccurrenceDao.getAllOccurrencesList().find { it.id == occurrenceId }
                val task = taskOcc?.let { taskDao.getTaskById(it.tarea_id) }
                val homeId = task?.hogar_id ?: 1

                val resp = ApiClient.service.completeOccurrence(
                    homeId = homeId,
                    occurrenceId = occurrenceId,
                    request = CompleteOccurrenceRequest(completada = completed, notas = notes)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("completada", completed)
                        if (notes != null) put("notas", notes)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/tareas-ocurrencias/$occurrenceId/completar",
                        "PATCH",
                        json,
                        "COMPLETAR_OCURRENCIA",
                        "Marcar ocurrencia #$occurrenceId como ${if (completed) "completada" else "pendiente"}"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("completada", completed)
                    if (notes != null) put("notas", notes)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/1/tareas-ocurrencias/$occurrenceId/completar",
                    "PATCH",
                    json,
                    "COMPLETAR_OCURRENCIA",
                    "Marcar ocurrencia #$occurrenceId como ${if (completed) "completada" else "pendiente"}"
                )
            }
        }
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

        // Online sync
        bgScope.launch {
            try {
                val resp = ApiClient.service.createPenalty(
                    homeId = homeId,
                    request = CreatePenaltyRequest(
                        titulo = titulo,
                        descripcion_motivo = motivo,
                        penalizacion = penalizacion,
                        fecha_inicio = fechaInicio,
                        fecha_fin = fechaFin,
                        usuarios_ids = assignedUserIds
                    )
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("titulo", titulo)
                        put("descripcion_motivo", motivo)
                        put("penalizacion", penalizacion)
                        put("fecha_inicio", fechaInicio)
                        put("fecha_fin", fechaFin)
                        val arr = JSONArray()
                        for (u in assignedUserIds) arr.put(u)
                        put("usuarios_ids", arr)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/penalizaciones",
                        "POST",
                        json,
                        "CREAR_PENALIZACION",
                        "Crear penalización $titulo"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("titulo", titulo)
                    put("descripcion_motivo", motivo)
                    put("penalizacion", penalizacion)
                    put("fecha_inicio", fechaInicio)
                    put("fecha_fin", fechaFin)
                    val arr = JSONArray()
                    for (u in assignedUserIds) arr.put(u)
                    put("usuarios_ids", arr)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/penalizaciones",
                    "POST",
                    json,
                    "CREAR_PENALIZACION",
                    "Crear penalización $titulo"
                )
            }
        }

        return pId.toLong()
    }

    suspend fun updatePenaltyStatus(penalty: PenaltyEntity, newStatus: String) {
        penaltyDao.updatePenalty(penalty.copy(estado = newStatus))
        bgScope.launch {
            try {
                val resp = ApiClient.service.updatePenaltyStatus(
                    homeId = penalty.hogar_id,
                    penaltyId = penalty.id,
                    request = UpdatePenaltyStatusRequest(estado = newStatus)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("estado", newStatus) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${penalty.hogar_id}/penalizaciones/${penalty.id}/estado",
                        "PATCH",
                        json,
                        "ACTUALIZAR_ESTADO_PENALIZACION",
                        "Cambiar estado de penalización #${penalty.id} a $newStatus"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("estado", newStatus) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/${penalty.hogar_id}/penalizaciones/${penalty.id}/estado",
                    "PATCH",
                    json,
                    "ACTUALIZAR_ESTADO_PENALIZACION",
                    "Cambiar estado de penalización #${penalty.id} a $newStatus"
                )
            }
        }
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

        // Online sync
        bgScope.launch {
            try {
                val resp = ApiClient.service.createProblem(
                    homeId = homeId,
                    request = CreateProblemRequest(
                        titulo = titulo,
                        descripcion = descripcion,
                        usuarios_involucrados_ids = involvedUserIds
                    )
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("titulo", titulo)
                        put("descripcion", descripcion)
                        val arr = JSONArray()
                        for (u in involvedUserIds) arr.put(u)
                        put("usuarios_involucrados_ids", arr)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/problemas",
                        "POST",
                        json,
                        "CREAR_PROBLEMA",
                        "Crear problema $titulo"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("titulo", titulo)
                    put("descripcion", descripcion)
                    val arr = JSONArray()
                    for (u in involvedUserIds) arr.put(u)
                    put("usuarios_involucrados_ids", arr)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/problemas",
                    "POST",
                    json,
                    "CREAR_PROBLEMA",
                    "Crear problema $titulo"
                )
            }
        }

        return pId.toLong()
    }

    suspend fun addPointOfView(problemId: Int, userId: Int, contenido: String): Long {
        val povId = problemDao.insertPointOfView(
            ProblemPointOfViewEntity(
                problema_id = problemId,
                usuario_id = userId,
                contenido = contenido
            )
        )

        bgScope.launch {
            try {
                val prob = problemDao.getProblemById(problemId)
                val homeId = prob?.hogar_id ?: 1
                val resp = ApiClient.service.addPointOfView(
                    homeId = homeId,
                    problemId = problemId,
                    request = AddPointOfViewRequest(contenido = contenido)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("contenido", contenido) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/problemas/$problemId/puntos-vista",
                        "POST",
                        json,
                        "AGREGAR_PUNTO_VISTA",
                        "Agregar punto de vista al problema #$problemId"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("contenido", contenido) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/1/problemas/$problemId/puntos-vista",
                    "POST",
                    json,
                    "AGREGAR_PUNTO_VISTA",
                    "Agregar punto de vista al problema #$problemId"
                )
            }
        }
        return povId
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

        bgScope.launch {
            try {
                val resp = ApiClient.service.updateProblemStatus(
                    homeId = problem.hogar_id,
                    problemId = problem.id,
                    request = UpdateProblemStatusRequest(
                        hablado = hablado,
                        solucionado = solucionado,
                        acuerdo = acuerdo
                    )
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("hablado", hablado)
                        put("solucionado", solucionado)
                        if (acuerdo != null) put("acuerdo", acuerdo)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${problem.hogar_id}/problemas/${problem.id}/estado",
                        "PATCH",
                        json,
                        "ACTUALIZAR_ESTADO_PROBLEMA",
                        "Actualizar estado del problema #${problem.id}"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("hablado", hablado)
                    put("solucionado", solucionado)
                    if (acuerdo != null) put("acuerdo", acuerdo)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/${problem.hogar_id}/problemas/${problem.id}/estado",
                    "PATCH",
                    json,
                    "ACTUALIZAR_ESTADO_PROBLEMA",
                    "Actualizar estado del problema #${problem.id}"
                )
            }
        }
    }

    // --- Shopping / Needs ---
    fun getNeedListsForHome(homeId: Int): Flow<List<NeedListEntity>> =
        needListDao.getListsForHome(homeId)

    fun getNeedItemsForList(listId: Int): Flow<List<NeedItemEntity>> =
        needListDao.getItemsForList(listId)

    suspend fun createNeedList(homeId: Int, createdBy: Int, titulo: String): Long {
        val listId = needListDao.insertList(
            NeedListEntity(
                hogar_id = homeId,
                creado_por = createdBy,
                titulo = titulo
            )
        )

        bgScope.launch {
            try {
                val resp = ApiClient.service.createNeedList(
                    homeId = homeId,
                    request = CreateNeedListRequest(titulo = titulo)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("titulo", titulo) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/necesidades",
                        "POST",
                        json,
                        "CREAR_LISTA_COMPRAS",
                        "Crear lista de compras $titulo"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("titulo", titulo) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/necesidades",
                    "POST",
                    json,
                    "CREAR_LISTA_COMPRAS",
                    "Crear lista de compras $titulo"
                )
            }
        }
        return listId
    }

    suspend fun addNeedItem(
        listaId: Int,
        nombre: String,
        cantidad: Int,
        fechaNecesaria: String,
        compradorId: Int?,
        notas: String?
    ): Long {
        val itemId = needListDao.insertItem(
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

        bgScope.launch {
            try {
                val list = needListDao.getAllNeedListsList().find { it.id == listaId }
                val homeId = list?.hogar_id ?: 1
                val resp = ApiClient.service.addNeedItem(
                    homeId = homeId,
                    listId = listaId,
                    request = CreateNeedItemRequest(
                        producto_nombre = nombre,
                        cantidad = cantidad,
                        fecha_necesaria = fechaNecesaria,
                        comprador_id = compradorId,
                        notas = notas
                    )
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("producto_nombre", nombre)
                        put("cantidad", cantidad)
                        put("fecha_necesaria", fechaNecesaria)
                        if (compradorId != null) put("comprador_id", compradorId)
                        if (notas != null) put("notas", notas)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/necesidades/$listaId/items",
                        "POST",
                        json,
                        "AGREGAR_ITEM_COMPRA",
                        "Agregar item $nombre a la lista"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("producto_nombre", nombre)
                    put("cantidad", cantidad)
                    put("fecha_necesaria", fechaNecesaria)
                    if (compradorId != null) put("comprador_id", compradorId)
                    if (notas != null) put("notas", notas)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/1/necesidades/$listaId/items",
                    "POST",
                    json,
                    "AGREGAR_ITEM_COMPRA",
                    "Agregar item $nombre a la lista"
                )
            }
        }
        return itemId
    }

    suspend fun updateNeedItemStatus(item: NeedItemEntity, newStatus: String) {
        needListDao.updateItem(item.copy(estado = newStatus))
        bgScope.launch {
            try {
                val list = needListDao.getAllNeedListsList().find { it.id == item.lista_id }
                val homeId = list?.hogar_id ?: 1
                val resp = ApiClient.service.updateNeedItemStatus(
                    homeId = homeId,
                    itemId = item.id,
                    request = UpdateItemStatusRequest(estado = newStatus)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("estado", newStatus) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/necesidades/items/${item.id}/estado",
                        "PATCH",
                        json,
                        "ACTUALIZAR_ESTADO_ITEM",
                        "Actualizar estado del item #${item.id} a $newStatus"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("estado", newStatus) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/1/necesidades/items/${item.id}/estado",
                    "PATCH",
                    json,
                    "ACTUALIZAR_ESTADO_ITEM",
                    "Actualizar estado del item #${item.id} a $newStatus"
                )
            }
        }
    }

    suspend fun deleteNeedItem(itemId: Int) {
        needListDao.deleteItem(itemId)
        bgScope.launch {
            try {
                val resp = ApiClient.service.deleteNeedItem(homeId = 1, itemId = itemId)
                if (!resp.isSuccessful) {
                    restSyncManager.enqueueOfflineAction(
                        "hogares/1/necesidades/items/$itemId",
                        "DELETE",
                        null,
                        "ELIMINAR_ITEM_COMPRA",
                        "Eliminar item de compra #$itemId"
                    )
                }
            } catch (e: Exception) {
                restSyncManager.enqueueOfflineAction(
                    "hogares/1/necesidades/items/$itemId",
                    "DELETE",
                    null,
                    "ELIMINAR_ITEM_COMPRA",
                    "Eliminar item de compra #$itemId"
                )
            }
        }
    }

    // --- Goals & Compound Missions ---
    fun getGoalsForHome(homeId: Int): Flow<List<GoalEntity>> = goalDao.getGoalsForHome(homeId)

    fun getGoalPoints(goalId: Int): Flow<List<GoalPointEntity>> = goalDao.getPointsForGoal(goalId)

    suspend fun createGoal(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcion: String?,
        tipo: String,
        fechaLimiteOriginal: String,
        puntos: List<Pair<String, Int?>>
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

        // Online sync
        bgScope.launch {
            try {
                val ptDtos = puntos.mapIndexed { idx, (t, a) -> GoalPointInputDto(titulo = t, asignado_a = a, orden = idx) }
                val resp = ApiClient.service.createGoal(
                    homeId = homeId,
                    request = CreateGoalRequest(
                        titulo = titulo,
                        descripcion = descripcion,
                        tipo = tipo,
                        fecha_limite = fechaLimiteOriginal,
                        puntos = ptDtos
                    )
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply {
                        put("titulo", titulo)
                        put("descripcion", descripcion)
                        put("tipo", tipo)
                        put("fecha_limite", fechaLimiteOriginal)
                        val pArr = JSONArray()
                        for (p in ptDtos) {
                            val pObj = JSONObject()
                            pObj.put("titulo", p.titulo)
                            if (p.asignado_a != null) pObj.put("asignado_a", p.asignado_a)
                            pObj.put("orden", p.orden)
                            pArr.put(pObj)
                        }
                        put("puntos", pArr)
                    }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/objetivos",
                        "POST",
                        json,
                        "CREAR_OBJETIVO",
                        "Crear objetivo $titulo"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply {
                    put("titulo", titulo)
                    put("descripcion", descripcion)
                    put("tipo", tipo)
                    put("fecha_limite", fechaLimiteOriginal)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/objetivos",
                    "POST",
                    json,
                    "CREAR_OBJETIVO",
                    "Crear objetivo $titulo"
                )
            }
        }

        return goalId.toLong()
    }

    suspend fun postponeGoal(goal: GoalEntity, newDeadline: String) {
        goalDao.updateGoal(goal.copy(fecha_limite_actual = newDeadline))
        bgScope.launch {
            try {
                val resp = ApiClient.service.postponeGoal(
                    homeId = goal.hogar_id,
                    goalId = goal.id,
                    request = PostponeGoalRequest(nueva_fecha_limite = newDeadline)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("nueva_fecha_limite", newDeadline) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${goal.hogar_id}/objetivos/${goal.id}/aplazar",
                        "PATCH",
                        json,
                        "APLAZAR_OBJETIVO",
                        "Aplazar fecha límite del objetivo #${goal.id} a $newDeadline"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("nueva_fecha_limite", newDeadline) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/${goal.hogar_id}/objetivos/${goal.id}/aplazar",
                    "PATCH",
                    json,
                    "APLAZAR_OBJETIVO",
                    "Aplazar fecha límite del objetivo #${goal.id} a $newDeadline"
                )
            }
        }
    }

    suspend fun setGoalCompleted(goal: GoalEntity, completed: Boolean) {
        goalDao.updateGoal(
            goal.copy(
                completado = completed,
                fecha_completado = if (completed) System.currentTimeMillis() else null
            )
        )
        bgScope.launch {
            try {
                val resp = ApiClient.service.completeGoal(
                    homeId = goal.hogar_id,
                    goalId = goal.id,
                    request = CompleteGoalRequest(completado = completed)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("completado", completed) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${goal.hogar_id}/objetivos/${goal.id}/completar",
                        "PATCH",
                        json,
                        "COMPLETAR_OBJETIVO",
                        "Marcar objetivo #${goal.id} como ${if (completed) "completado" else "pendiente"}"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("completado", completed) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/${goal.hogar_id}/objetivos/${goal.id}/completar",
                    "PATCH",
                    json,
                    "COMPLETAR_OBJETIVO",
                    "Marcar objetivo #${goal.id} como ${if (completed) "completado" else "pendiente"}"
                )
            }
        }
    }

    suspend fun toggleGoalPoint(point: GoalPointEntity, completed: Boolean, userId: Int?) {
        goalDao.updateGoalPoint(
            point.copy(
                completado = completed,
                fecha_completado = if (completed) System.currentTimeMillis() else null,
                completado_por = if (completed) userId else null
            )
        )
        bgScope.launch {
            try {
                val goal = goalDao.getGoalById(point.objetivo_id)
                val homeId = goal?.hogar_id ?: 1
                val resp = ApiClient.service.completeGoalPoint(
                    homeId = homeId,
                    pointId = point.id,
                    request = CompleteGoalRequest(completado = completed)
                )
                if (!resp.isSuccessful) {
                    val json = JSONObject().apply { put("completado", completed) }.toString()
                    restSyncManager.enqueueOfflineAction(
                        "hogares/$homeId/objetivos/puntos/${point.id}/completar",
                        "PATCH",
                        json,
                        "COMPLETAR_PUNTO_OBJETIVO",
                        "Marcar paso #${point.id} de objetivo como ${if (completed) "completado" else "pendiente"}"
                    )
                }
            } catch (e: Exception) {
                val json = JSONObject().apply { put("completado", completed) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/1/objetivos/puntos/${point.id}/completar",
                    "PATCH",
                    json,
                    "COMPLETAR_PUNTO_OBJETIVO",
                    "Marcar paso #${point.id} de objetivo como ${if (completed) "completado" else "pendiente"}"
                )
            }
        }
    }
}
