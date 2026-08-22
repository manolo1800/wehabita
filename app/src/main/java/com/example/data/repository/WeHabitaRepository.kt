package com.example.data.repository

import com.example.data.local.WeHabitaDatabase
import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import com.example.data.remote.api.ApiClient
import com.example.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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

    // Pending Sync Flow
    fun getPendingSyncCount(): Flow<Int> = syncQueueDao.getPendingCount()
    fun getPendingSyncQueue(): Flow<List<SyncQueueEntity>> = syncQueueDao.getAllPendingQueue()

    // --- Users ---
    fun getAllUsers(): Flow<List<UserEntity>> = userDao.getAllActiveUsers()

    suspend fun getUserById(id: Int): UserEntity? = userDao.getUserById(id)

    suspend fun getUserByEmailOrUsername(query: String): UserEntity? =
        userDao.getUserByEmailOrUsername(query)

    suspend fun registerUser(nombre: String, email: String, username: String, passwordHash: String): Long = withContext(Dispatchers.IO) {
        val randomCode = "USR-" + (1000..9999).random()

        // 1. Try online registration first
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
                val p = authData.user
                ApiClient.saveUserId(p.id)

                val userEntity = UserEntity(
                    id = p.id,
                    nombre = p.nombre,
                    email = p.email,
                    username = p.username,
                    password_hash = passwordHash,
                    codigo_usuario = p.codigo_usuario,
                    rol_global = p.rol_global,
                    activo = p.activo
                )
                userDao.insertUser(userEntity)
                return@withContext p.id.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.w("WeHabita:Repo", "Registro online falló (${e.message}), guardando local...")
        }

        // 2. Fallback to local SQLite + enqueue offline action
        val user = UserEntity(
            nombre = nombre,
            email = email,
            username = username,
            password_hash = passwordHash,
            codigo_usuario = randomCode
        )
        val localId = userDao.insertUser(user)

        val json = JSONObject().apply {
            put("nombre", nombre)
            put("email", email)
            put("username", username)
            put("password", passwordHash)
        }.toString()
        restSyncManager.enqueueOfflineAction("auth/register", "POST", json, "REGISTRAR_USUARIO", "Registro de usuario $username")

        return@withContext localId
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

    suspend fun createHome(name: String, tipo: String, createdBy: Int): Long = withContext(Dispatchers.IO) {
        // 1. Try online REST creation first
        val token = ApiClient.getAuthToken()
        if (!token.isNullOrBlank()) {
            try {
                val resp = ApiClient.service.createHome(CreateHomeRequest(nombre = name, tipo_hogar = tipo))
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val h = resp.body()!!.data
                    val homeEntity = HomeEntity(
                        id = h.id,
                        nombre = h.nombre,
                        tipo_hogar = h.tipo_hogar,
                        codigo_invitacion = h.codigo_invitacion,
                        creado_por = createdBy
                    )
                    homeDao.insertHome(homeEntity)

                    val memberId = homeMemberDao.insertMember(
                        HomeMemberEntity(
                            hogar_id = h.id,
                            usuario_id = createdBy,
                            rol = "admin",
                            activo = true
                        )
                    ).toInt()

                    val privs = ensureStandardPrivileges()
                    for (priv in privs) {
                        privilegeDao.insertMemberPrivilege(MemberPrivilegeEntity(memberId, priv.id))
                    }

                    ApiClient.saveHomeId(h.id)
                    SyncLogCollector.s("WeHabita:Repo", "Hogar '${h.nombre}' (#${h.id}) creado en el servidor REST con código: ${h.codigo_invitacion}")
                    return@withContext h.id.toLong()
                } else {
                    SyncLogCollector.w("WeHabita:Repo", "Error al crear hogar online: HTTP ${resp.code()} ${resp.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                SyncLogCollector.w("WeHabita:Repo", "Fallo de conexión al crear hogar: ${e.message}")
            }
        }

        // 2. Offline fallback in SQLite
        val inviteCode = "HOG-" + (1000..9999).random()
        val home = HomeEntity(
            nombre = name,
            tipo_hogar = tipo,
            codigo_invitacion = inviteCode,
            creado_por = createdBy
        )
        val localHomeId = homeDao.insertHome(home).toInt()

        val memberId = homeMemberDao.insertMember(
            HomeMemberEntity(
                hogar_id = localHomeId,
                usuario_id = createdBy,
                rol = "admin"
            )
        ).toInt()

        val privs = ensureStandardPrivileges()
        for (priv in privs) {
            privilegeDao.insertMemberPrivilege(MemberPrivilegeEntity(memberId, priv.id))
        }

        val json = JSONObject().apply {
            put("nombre", name)
            put("tipo_hogar", tipo)
        }.toString()
        restSyncManager.enqueueOfflineAction("hogares", "POST", json, "CREAR_HOGAR", "Crear hogar $name")

        ApiClient.saveHomeId(localHomeId)
        return@withContext localHomeId.toLong()
    }

    suspend fun joinHomeByInviteCode(inviteCode: String, userId: Int): Boolean = withContext(Dispatchers.IO) {
        val cleanCode = inviteCode.trim().uppercase(Locale.getDefault())

        // 1. Try online join
        val token = ApiClient.getAuthToken()
        if (!token.isNullOrBlank()) {
            try {
                val resp = ApiClient.service.joinHome(JoinHomeRequest(codigo = cleanCode))
                if (resp.isSuccessful && resp.body()?.data != null) {
                    val serverHomeId = resp.body()!!.data.hogar_id
                    SyncLogCollector.s("WeHabita:Repo", "Unido al hogar #$serverHomeId exitosamente vía REST")
                    // Sincronizar datos del nuevo hogar
                    restSyncManager.syncWithServer(serverHomeId)
                    ApiClient.saveHomeId(serverHomeId)
                    return@withContext true
                }
            } catch (e: Exception) {
                SyncLogCollector.w("WeHabita:Repo", "Fallo al unirse online al hogar: ${e.message}")
            }
        }

        // 2. Offline fallback
        val home = homeDao.getHomeByInviteCode(cleanCode)
        val json = JSONObject().apply { put("codigo", cleanCode) }.toString()
        restSyncManager.enqueueOfflineAction("invitaciones/unirse", "POST", json, "UNIRSE_HOGAR", "Unirse al hogar con código $cleanCode")

        if (home != null) {
            val existing = homeMemberDao.getMember(home.id, userId)
            if (existing != null) return@withContext true

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
            ApiClient.saveHomeId(home.id)
            return@withContext true
        }

        return@withContext false
    }

    // --- Members & Privileges ---
    fun getMembersWithUserForHome(homeId: Int): Flow<List<MemberWithUser>> =
        homeMemberDao.getMembersWithUserForHome(homeId)

    fun getAllPrivileges(): Flow<List<PrivilegeEntity>> = privilegeDao.getAllPrivileges()

    fun getPrivilegesForMember(memberId: Int): Flow<List<PrivilegeEntity>> =
        privilegeDao.getPrivilegesForMember(memberId)

    suspend fun setMemberPrivileges(memberId: Int, privilegeIds: List<Int>) = withContext(Dispatchers.IO) {
        privilegeDao.clearMemberPrivileges(memberId)
        for (pId in privilegeIds) {
            privilegeDao.insertMemberPrivilege(MemberPrivilegeEntity(memberId, pId))
        }
    }

    suspend fun updateMemberRole(member: HomeMemberEntity, newRole: String) = withContext(Dispatchers.IO) {
        homeMemberDao.updateMember(member.copy(rol = newRole))
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

    suspend fun removeMember(homeId: Int, userId: Int) = withContext(Dispatchers.IO) {
        val member = homeMemberDao.getMember(homeId, userId)
        homeMemberDao.removeMember(homeId, userId)
        if (member != null) {
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

    // --- Invitations ---
    fun getInvitationsForHome(homeId: Int): Flow<List<InvitationEntity>> =
        invitationDao.getInvitationsForHome(homeId)

    suspend fun createInvitation(homeId: Int, createdBy: Int, email: String?, userCode: String?): Long = withContext(Dispatchers.IO) {
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
        return@withContext invId
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
    ): Long = withContext(Dispatchers.IO) {
        val schedulesDto = horarios.map { ScheduleDto(hora_inicio = it.first, hora_fin = it.second) }

        // 1. Try online creation
        try {
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
            if (resp.isSuccessful && resp.body()?.data != null) {
                val t = resp.body()!!.data
                val serverTaskId = if (t.id > 0) t.id else (System.currentTimeMillis() % 1000000).toInt()
                taskDao.insertTask(
                    TaskEntity(
                        id = serverTaskId,
                        hogar_id = homeId,
                        creado_por = createdBy,
                        titulo = t.titulo.ifBlank { titulo },
                        descripcion = t.descripcion ?: descripcion,
                        tipo = t.tipo.ifBlank { tipo },
                        fecha_inicio = t.fecha_inicio.ifBlank { fechaInicio },
                        fecha_fin = t.fecha_fin ?: fechaFin,
                        activa = t.activa
                    )
                )
                
                val schedulesToInsert = if (t.horarios.isNotEmpty()) {
                    t.horarios.map { (it.id ?: 0) to (it.hora_inicio to it.hora_fin) }
                } else if (horarios.isNotEmpty()) {
                    horarios.map { 0 to it }
                } else {
                    listOf(0 to ("08:00" to null))
                }

                for ((sId, sched) in schedulesToInsert) {
                    val (hIn, hOut) = sched
                    taskDao.insertSchedule(
                        TaskScheduleEntity(
                            id = sId,
                            tarea_id = serverTaskId,
                            hora_inicio = hIn,
                            hora_fin = hOut
                        )
                    )
                    // Generate initial occurrence
                    taskOccurrenceDao.insertOccurrence(
                        TaskOccurrenceEntity(
                            tarea_id = serverTaskId,
                            fecha = fechaInicio,
                            hora_inicio = hIn,
                            hora_fin = hOut,
                            completada = false
                        )
                    )
                }

                val assigneesToSave = if (t.asignados.isNotEmpty()) t.asignados.map { it.id } else assignedUserIds
                for (uId in assigneesToSave) {
                    taskDao.insertAssignment(TaskAssignmentEntity(tarea_id = serverTaskId, usuario_id = uId))
                }
                SyncLogCollector.s("WeHabita:Repo", "Tarea '$titulo' (#$serverTaskId) creada en el servidor y guardada en Room")
                return@withContext serverTaskId.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.w("WeHabita:Repo", "Fallo online al crear tarea: ${e.message}")
        }

        // 2. Offline fallback
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

        return@withContext taskId.toLong()
    }

    suspend fun updateTask(
        taskId: Int,
        titulo: String,
        descripcion: String?,
        tipo: String,
        fechaInicio: String,
        fechaFin: String?,
        activa: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val existing = taskDao.getTaskById(taskId) ?: return@withContext
        val updated = existing.copy(
            titulo = titulo,
            descripcion = descripcion,
            tipo = tipo,
            fecha_inicio = fechaInicio,
            fecha_fin = fechaFin,
            activa = activa
        )
        taskDao.updateTask(updated)

        try {
            val json = JSONObject().apply {
                put("titulo", titulo)
                put("descripcion", descripcion)
                put("tipo", tipo)
                put("fecha_inicio", fechaInicio)
                put("fecha_fin", fechaFin)
                put("activa", activa)
            }.toString()
            val resp = ApiClient.service.updateTask(
                homeId = existing.hogar_id,
                taskId = taskId,
                request = UpdateTaskRequest(
                    titulo = titulo,
                    descripcion = descripcion,
                    tipo = tipo,
                    fecha_inicio = fechaInicio,
                    fecha_fin = fechaFin
                )
            )
            if (!resp.isSuccessful) {
                restSyncManager.enqueueOfflineAction(
                    "hogares/${existing.hogar_id}/tareas/$taskId",
                    "PATCH",
                    json,
                    "ACTUALIZAR_TAREA",
                    "Actualizar tarea #$taskId"
                )
            }
        } catch (e: Exception) {
            val json = JSONObject().apply {
                put("titulo", titulo)
                put("descripcion", descripcion)
                put("tipo", tipo)
                put("fecha_inicio", fechaInicio)
                put("fecha_fin", fechaFin)
                put("activa", activa)
            }.toString()
            restSyncManager.enqueueOfflineAction(
                "hogares/${existing.hogar_id}/tareas/$taskId",
                "PATCH",
                json,
                "ACTUALIZAR_TAREA",
                "Actualizar tarea #$taskId"
            )
        }
    }

    suspend fun completeTaskDirectly(taskId: Int, completed: Boolean, userId: Int?) = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val occurrences = taskOccurrenceDao.getOccurrencesForTask(taskId)
        val todayOcc = occurrences.find { it.fecha == todayStr }
        if (todayOcc != null) {
            markOccurrence(todayOcc.id, completed, userId, "Completada directamente desde la lista de tareas")
        } else {
            val newOccId = taskOccurrenceDao.insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = taskId,
                    fecha = todayStr,
                    hora_inicio = "08:00",
                    completada = completed,
                    completada_por = if (completed) userId else null,
                    fecha_completada = if (completed) System.currentTimeMillis() else null,
                    notas = "Completada directamente"
                )
            ).toInt()
            if (completed) {
                markOccurrence(newOccId, true, userId, "Completada directamente")
            }
        }
    }

    suspend fun deleteTask(taskId: Int) = withContext(Dispatchers.IO) {
        val task = taskDao.getTaskById(taskId)
        taskOccurrenceDao.deleteOccurrencesForTask(taskId)
        taskDao.deleteTask(taskId)
        if (task != null) {
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

    // --- Occurrences ---
    fun getOccurrencesForHomeAndDate(homeId: Int, date: String): Flow<List<OccurrenceWithDetails>> =
        taskOccurrenceDao.getOccurrencesForHomeAndDate(homeId, date)

    fun getAllOccurrencesForHome(homeId: Int): Flow<List<OccurrenceWithDetails>> =
        taskOccurrenceDao.getAllOccurrencesForHome(homeId)

    suspend fun markOccurrence(occurrenceId: Int, completed: Boolean, userId: Int?, notes: String?) = withContext(Dispatchers.IO) {
        taskOccurrenceDao.markOccurrence(
            occurrenceId = occurrenceId,
            completed = completed,
            completedBy = if (completed) userId else null,
            completedAt = if (completed) System.currentTimeMillis() else null,
            notes = notes
        )

        try {
            val taskOcc = taskOccurrenceDao.getAllOccurrencesList().find { it.id == occurrenceId }
            val task = taskOcc?.let { taskDao.getTaskById(it.tarea_id) }
            val homeId = task?.hogar_id ?: ApiClient.getSavedHomeId() ?: 1

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
            val hId = ApiClient.getSavedHomeId() ?: 1
            restSyncManager.enqueueOfflineAction(
                "hogares/$hId/tareas-ocurrencias/$occurrenceId/completar",
                "PATCH",
                json,
                "COMPLETAR_OCURRENCIA",
                "Marcar ocurrencia #$occurrenceId como ${if (completed) "completada" else "pendiente"}"
            )
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
    ): Long = withContext(Dispatchers.IO) {
        // 1. Try online
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
            if (resp.isSuccessful && resp.body()?.data != null) {
                val p = resp.body()!!.data
                val serverPenaltyId = if (p.id > 0) p.id else (System.currentTimeMillis() % 1000000).toInt()
                penaltyDao.insertPenalty(
                    PenaltyEntity(
                        id = serverPenaltyId,
                        hogar_id = homeId,
                        creado_por = createdBy,
                        titulo = p.titulo.ifBlank { titulo },
                        descripcion_motivo = p.descripcion_motivo.ifBlank { motivo },
                        penalizacion = p.penalizacion.ifBlank { penalizacion },
                        fecha_inicio = p.fecha_inicio.ifBlank { fechaInicio },
                        fecha_fin = p.fecha_fin ?: fechaFin,
                        estado = p.estado.ifBlank { "activa" }
                    )
                )
                val uIdsToSave = if (p.usuarios_penalizados.isNotEmpty()) p.usuarios_penalizados.map { it.id } else assignedUserIds
                for (uId in uIdsToSave) {
                    penaltyDao.insertPenaltyUser(PenaltyUserEntity(penalizacion_id = serverPenaltyId, usuario_id = uId))
                }
                SyncLogCollector.s("WeHabita:Repo", "Penalización '$titulo' (#$serverPenaltyId) creada en el servidor y guardada en Room")
                return@withContext serverPenaltyId.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.w("WeHabita:Repo", "Fallo online al crear penalización: ${e.message}")
        }

        // 2. Offline fallback
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

        return@withContext pId.toLong()
    }

    suspend fun updatePenaltyStatus(penalty: PenaltyEntity, newStatus: String) = withContext(Dispatchers.IO) {
        penaltyDao.updatePenalty(penalty.copy(estado = newStatus))
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
    ): Long = withContext(Dispatchers.IO) {
        // 1. Try online
        try {
            val resp = ApiClient.service.createProblem(
                homeId = homeId,
                request = CreateProblemRequest(
                    titulo = titulo,
                    descripcion = descripcion,
                    usuarios_involucrados_ids = involvedUserIds
                )
            )
            if (resp.isSuccessful && resp.body()?.data != null) {
                val pr = resp.body()!!.data
                val serverProblemId = if (pr.id > 0) pr.id else (System.currentTimeMillis() % 1000000).toInt()
                problemDao.insertProblem(
                    ProblemEntity(
                        id = serverProblemId,
                        hogar_id = homeId,
                        creado_por = createdBy,
                        titulo = pr.titulo.ifBlank { titulo },
                        descripcion = pr.descripcion.ifBlank { descripcion },
                        hablado = pr.hablado,
                        solucionado = pr.solucionado,
                        acuerdo = pr.acuerdo
                    )
                )
                val uIdsToSave = if (pr.usuarios_involucrados.isNotEmpty()) pr.usuarios_involucrados.map { it.id } else involvedUserIds
                for (uId in uIdsToSave) {
                    problemDao.insertProblemUser(ProblemUserEntity(problema_id = serverProblemId, usuario_id = uId))
                }
                SyncLogCollector.s("WeHabita:Repo", "Problema/Acuerdo '$titulo' (#$serverProblemId) creado en el servidor y guardado en Room")
                return@withContext serverProblemId.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.w("WeHabita:Repo", "Fallo online al crear problema: ${e.message}")
        }

        // 2. Offline fallback
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

        return@withContext pId.toLong()
    }

    suspend fun addPointOfView(problemId: Int, userId: Int, contenido: String): Long = withContext(Dispatchers.IO) {
        val povId = problemDao.insertPointOfView(
            ProblemPointOfViewEntity(
                problema_id = problemId,
                usuario_id = userId,
                contenido = contenido
            )
        )

        try {
            val prob = problemDao.getProblemById(problemId)
            val homeId = prob?.hogar_id ?: ApiClient.getSavedHomeId() ?: 1
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
            val hId = ApiClient.getSavedHomeId() ?: 1
            restSyncManager.enqueueOfflineAction(
                "hogares/$hId/problemas/$problemId/puntos-vista",
                "POST",
                json,
                "AGREGAR_PUNTO_VISTA",
                "Agregar punto de vista al problema #$problemId"
            )
        }
        return@withContext povId
    }

    suspend fun updateProblem(
        problemId: Int,
        titulo: String,
        descripcion: String,
        involvedUserIds: List<Int>? = null
    ) = withContext(Dispatchers.IO) {
        val existing = problemDao.getProblemById(problemId) ?: return@withContext
        val updated = existing.copy(titulo = titulo, descripcion = descripcion)
        problemDao.updateProblem(updated)

        if (involvedUserIds != null) {
            for (uId in involvedUserIds) {
                problemDao.insertProblemUser(ProblemUserEntity(problema_id = problemId, usuario_id = uId))
            }
        }

        try {
            val resp = ApiClient.service.updateProblem(
                homeId = existing.hogar_id,
                problemId = problemId,
                request = UpdateProblemRequest(
                    titulo = titulo,
                    descripcion = descripcion,
                    usuarios_involucrados_ids = involvedUserIds
                )
            )
            if (!resp.isSuccessful) {
                val json = JSONObject().apply {
                    put("titulo", titulo)
                    put("descripcion", descripcion)
                }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/${existing.hogar_id}/problemas/$problemId",
                    "PATCH",
                    json,
                    "ACTUALIZAR_PROBLEMA",
                    "Actualizar problema #$problemId"
                )
            }
        } catch (e: Exception) {
            val json = JSONObject().apply {
                put("titulo", titulo)
                put("descripcion", descripcion)
            }.toString()
            restSyncManager.enqueueOfflineAction(
                "hogares/${existing.hogar_id}/problemas/$problemId",
                "PATCH",
                json,
                "ACTUALIZAR_PROBLEMA",
                "Actualizar problema #$problemId"
            )
        }
    }

    suspend fun deleteProblem(problemId: Int) = withContext(Dispatchers.IO) {
        val prob = problemDao.getProblemById(problemId)
        problemDao.deleteProblem(problemId)
        if (prob != null) {
            try {
                val resp = ApiClient.service.deleteProblem(prob.hogar_id, problemId)
                if (!resp.isSuccessful) {
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${prob.hogar_id}/problemas/$problemId",
                        "DELETE",
                        null,
                        "ELIMINAR_PROBLEMA",
                        "Eliminar problema #$problemId"
                    )
                }
            } catch (e: Exception) {
                restSyncManager.enqueueOfflineAction(
                    "hogares/${prob.hogar_id}/problemas/$problemId",
                    "DELETE",
                    null,
                    "ELIMINAR_PROBLEMA",
                    "Eliminar problema #$problemId"
                )
            }
        }
    }

    suspend fun deletePointOfView(problemId: Int, povId: Int) = withContext(Dispatchers.IO) {
        problemDao.deletePointOfView(povId)
        val prob = problemDao.getProblemById(problemId)
        val homeId = prob?.hogar_id ?: ApiClient.getSavedHomeId() ?: 1
        try {
            val resp = ApiClient.service.deletePointOfView(homeId, problemId, povId)
            if (!resp.isSuccessful) {
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/problemas/$problemId/puntos-vista/$povId",
                    "DELETE",
                    null,
                    "ELIMINAR_PUNTO_VISTA",
                    "Eliminar punto de vista #$povId"
                )
            }
        } catch (e: Exception) {
            restSyncManager.enqueueOfflineAction(
                "hogares/$homeId/problemas/$problemId/puntos-vista/$povId",
                "DELETE",
                null,
                "ELIMINAR_PUNTO_VISTA",
                "Eliminar punto de vista #$povId"
            )
        }
    }

    suspend fun deletePenalty(penaltyId: Int) = withContext(Dispatchers.IO) {
        val pen = penaltyDao.getPenaltyById(penaltyId)
        penaltyDao.deletePenalty(penaltyId)
        if (pen != null) {
            try {
                val resp = ApiClient.service.deletePenalty(pen.hogar_id, penaltyId)
                if (!resp.isSuccessful) {
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${pen.hogar_id}/penalizaciones/$penaltyId",
                        "DELETE",
                        null,
                        "ELIMINAR_PENALIZACION",
                        "Eliminar penalización #$penaltyId"
                    )
                }
            } catch (e: Exception) {
                restSyncManager.enqueueOfflineAction(
                    "hogares/${pen.hogar_id}/penalizaciones/$penaltyId",
                    "DELETE",
                    null,
                    "ELIMINAR_PENALIZACION",
                    "Eliminar penalización #$penaltyId"
                )
            }
        }
    }

    suspend fun updateProblemDetails(
        problem: ProblemEntity,
        hablado: Boolean,
        solucionado: Boolean,
        acuerdo: String?
    ) = withContext(Dispatchers.IO) {
        problemDao.updateProblem(
            problem.copy(
                hablado = hablado,
                solucionado = solucionado,
                acuerdo = acuerdo
            )
        )

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

    // --- Shopping / Needs ---
    fun getNeedListsForHome(homeId: Int): Flow<List<NeedListEntity>> =
        needListDao.getListsForHome(homeId)

    fun getNeedItemsForList(listId: Int): Flow<List<NeedItemEntity>> =
        needListDao.getItemsForList(listId)

    suspend fun createNeedList(homeId: Int, createdBy: Int, titulo: String): Long = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.createNeedList(
                homeId = homeId,
                request = CreateNeedListRequest(titulo = titulo)
            )
            if (resp.isSuccessful && resp.body()?.data != null) {
                val nl = resp.body()!!.data
                val serverListId = if (nl.id > 0) nl.id else (System.currentTimeMillis() % 1000000).toInt()
                val listId = needListDao.insertList(
                    NeedListEntity(
                        id = serverListId,
                        hogar_id = homeId,
                        creado_por = createdBy,
                        titulo = nl.titulo.ifBlank { titulo },
                        estado = nl.estado.ifBlank { "activa" }
                    )
                )
                SyncLogCollector.s("WeHabita:Repo", "Lista de compras '$titulo' (#$serverListId) creada en el servidor y guardada en Room")
                return@withContext listId
            }
        } catch (e: Exception) {
            SyncLogCollector.w("WeHabita:Repo", "Fallo online al crear lista de compras: ${e.message}")
        }

        val listId = needListDao.insertList(
            NeedListEntity(
                hogar_id = homeId,
                creado_por = createdBy,
                titulo = titulo
            )
        )
        val json = JSONObject().apply { put("titulo", titulo) }.toString()
        restSyncManager.enqueueOfflineAction(
            "hogares/$homeId/necesidades",
            "POST",
            json,
            "CREAR_LISTA_COMPRAS",
            "Crear lista de compras $titulo"
        )
        return@withContext listId
    }

    suspend fun addNeedItem(
        listaId: Int,
        nombre: String,
        cantidad: Int,
        fechaNecesaria: String,
        compradorId: Int?,
        notas: String?
    ): Long = withContext(Dispatchers.IO) {
        val list = needListDao.getAllNeedListsList().find { it.id == listaId }
        val homeId = list?.hogar_id ?: ApiClient.getSavedHomeId() ?: 1

        try {
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
            if (resp.isSuccessful && resp.body()?.data != null) {
                val it = resp.body()!!.data
                val serverItemId = if (it.id > 0) it.id else (System.currentTimeMillis() % 1000000).toInt()
                val itemId = needListDao.insertItem(
                    NeedItemEntity(
                        id = serverItemId,
                        lista_id = listaId,
                        producto_nombre = it.producto_nombre.ifBlank { nombre },
                        cantidad = if (it.cantidad > 0) it.cantidad else cantidad,
                        fecha_necesaria = it.fecha_necesaria.ifBlank { fechaNecesaria },
                        comprador_id = it.comprador?.id ?: compradorId,
                        estado = it.estado.ifBlank { "pendiente" },
                        notas = it.notas ?: notas
                    )
                )
                SyncLogCollector.s("WeHabita:Repo", "Item '$nombre' (#$serverItemId) agregado a lista de compras en Room")
                return@withContext itemId
            }
        } catch (e: Exception) {
            SyncLogCollector.w("WeHabita:Repo", "Fallo online al agregar item a lista: ${e.message}")
        }

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
        return@withContext itemId
    }

    suspend fun updateNeedItemStatus(item: NeedItemEntity, newStatus: String) = withContext(Dispatchers.IO) {
        needListDao.updateItem(item.copy(estado = newStatus))
        try {
            val list = needListDao.getAllNeedListsList().find { it.id == item.lista_id }
            val homeId = list?.hogar_id ?: ApiClient.getSavedHomeId() ?: 1
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
            val hId = ApiClient.getSavedHomeId() ?: 1
            val json = JSONObject().apply { put("estado", newStatus) }.toString()
            restSyncManager.enqueueOfflineAction(
                "hogares/$hId/necesidades/items/${item.id}/estado",
                "PATCH",
                json,
                "ACTUALIZAR_ESTADO_ITEM",
                "Actualizar estado del item #${item.id} a $newStatus"
            )
        }
    }

    suspend fun deleteNeedItem(itemId: Int) = withContext(Dispatchers.IO) {
        needListDao.deleteItem(itemId)
        val homeId = ApiClient.getSavedHomeId() ?: 1
        try {
            val resp = ApiClient.service.deleteNeedItem(homeId = homeId, itemId = itemId)
            if (!resp.isSuccessful) {
                restSyncManager.enqueueOfflineAction(
                    "hogares/$homeId/necesidades/items/$itemId",
                    "DELETE",
                    null,
                    "ELIMINAR_ITEM_COMPRA",
                    "Eliminar item de compra #$itemId"
                )
            }
        } catch (e: Exception) {
            restSyncManager.enqueueOfflineAction(
                "hogares/$homeId/necesidades/items/$itemId",
                "DELETE",
                null,
                "ELIMINAR_ITEM_COMPRA",
                "Eliminar item de compra #$itemId"
            )
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
    ): Long = withContext(Dispatchers.IO) {
        val ptDtos = puntos.mapIndexed { idx, (t, a) -> GoalPointInputDto(titulo = t, asignado_a = a, orden = idx) }

        // 1. Try online
        try {
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
            if (resp.isSuccessful && resp.body()?.data != null) {
                val g = resp.body()!!.data
                val serverGoalId = if (g.id > 0) g.id else (System.currentTimeMillis() % 1000000).toInt()
                val goalId = goalDao.insertGoal(
                    GoalEntity(
                        id = serverGoalId,
                        hogar_id = homeId,
                        creado_por = createdBy,
                        titulo = g.titulo.ifBlank { titulo },
                        descripcion = g.descripcion ?: descripcion,
                        tipo = g.tipo.ifBlank { tipo },
                        completado = g.completado,
                        fecha_limite_original = g.fecha_limite_original.ifBlank { fechaLimiteOriginal },
                        fecha_limite_actual = g.fecha_limite_actual.ifBlank { fechaLimiteOriginal }
                    )
                ).toInt()

                if (g.puntos.isNotEmpty()) {
                    for (pt in g.puntos) {
                        goalDao.insertGoalPoint(
                            GoalPointEntity(
                                id = pt.id,
                                objetivo_id = serverGoalId,
                                titulo = pt.titulo,
                                descripcion = pt.descripcion,
                                asignado_a = pt.asignado?.id,
                                completado = pt.completado,
                                completado_por = pt.completado_por?.id,
                                orden = pt.orden
                            )
                        )
                    }
                } else {
                    for ((idx, pPair) in puntos.withIndex()) {
                        val (pTitle, pAssignee) = pPair
                        goalDao.insertGoalPoint(
                            GoalPointEntity(
                                objetivo_id = serverGoalId,
                                titulo = pTitle,
                                asignado_a = pAssignee,
                                orden = idx
                            )
                        )
                    }
                }
                SyncLogCollector.s("WeHabita:Repo", "Meta '$titulo' (#$serverGoalId) creada en el servidor y guardada en Room")
                return@withContext goalId.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.w("WeHabita:Repo", "Fallo online al crear meta: ${e.message}")
        }

        // 2. Offline fallback
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
            "Crear meta o misión compuesta: $titulo"
        )

        return@withContext goalId.toLong()
    }

    suspend fun postponeGoal(goal: GoalEntity, newDeadline: String) = withContext(Dispatchers.IO) {
        goalDao.updateGoal(goal.copy(fecha_limite_actual = newDeadline))
        try {
            val resp = ApiClient.service.postponeGoal(
                homeId = goal.hogar_id,
                goalId = goal.id,
                request = PostponeGoalRequest(nueva_fecha_limite = newDeadline)
            )
            if (!resp.isSuccessful) {
                val json = JSONObject().apply { put("nueva_fecha_limite", newDeadline) }.toString()
                restSyncManager.enqueueOfflineAction(
                    "hogares/${goal.hogar_id}/objetivos/${goal.id}/posponer",
                    "PATCH",
                    json,
                    "POSPONER_OBJETIVO",
                    "Posponer meta #${goal.id} hasta $newDeadline"
                )
            }
        } catch (e: Exception) {
            val json = JSONObject().apply { put("nueva_fecha_limite", newDeadline) }.toString()
            restSyncManager.enqueueOfflineAction(
                "hogares/${goal.hogar_id}/objetivos/${goal.id}/posponer",
                "PATCH",
                json,
                "POSPONER_OBJETIVO",
                "Posponer meta #${goal.id} hasta $newDeadline"
            )
        }
    }

    suspend fun setGoalCompleted(goal: GoalEntity, completed: Boolean) = withContext(Dispatchers.IO) {
        goalDao.updateGoal(goal.copy(completado = completed))
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
                    "ACTUALIZAR_ESTADO_OBJETIVO",
                    "Marcar meta #${goal.id} como ${if (completed) "completada" else "pendiente"}"
                )
            }
        } catch (e: Exception) {
            val json = JSONObject().apply { put("completado", completed) }.toString()
            restSyncManager.enqueueOfflineAction(
                "hogares/${goal.hogar_id}/objetivos/${goal.id}/completar",
                "PATCH",
                json,
                "ACTUALIZAR_ESTADO_OBJETIVO",
                "Marcar meta #${goal.id} como ${if (completed) "completada" else "pendiente"}"
            )
        }
    }

    suspend fun toggleGoalPoint(point: GoalPointEntity, completed: Boolean, userId: Int?) = withContext(Dispatchers.IO) {
        goalDao.updateGoalPoint(
            point.copy(
                completado = completed,
                completado_por = if (completed) userId else null
            )
        )
        try {
            val goal = goalDao.getGoalById(point.objetivo_id)
            val homeId = goal?.hogar_id ?: ApiClient.getSavedHomeId() ?: 1
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
                    "ACTUALIZAR_PUNTO_OBJETIVO",
                    "Marcar punto #${point.id} como ${if (completed) "completado" else "pendiente"}"
                )
            }
        } catch (e: Exception) {
            val hId = ApiClient.getSavedHomeId() ?: 1
            val json = JSONObject().apply { put("completado", completed) }.toString()
            restSyncManager.enqueueOfflineAction(
                "hogares/$hId/objetivos/puntos/${point.id}/completar",
                "PATCH",
                json,
                "ACTUALIZAR_PUNTO_OBJETIVO",
                "Marcar punto #${point.id} como ${if (completed) "completado" else "pendiente"}"
            )
        }
    }

    suspend fun deleteGoal(goalId: Int) = withContext(Dispatchers.IO) {
        val goal = goalDao.getGoalById(goalId)
        goalDao.deletePointsForGoal(goalId)
        goalDao.deleteGoal(goalId)
        if (goal != null) {
            try {
                val resp = ApiClient.service.deleteGoal(goal.hogar_id, goalId)
                if (!resp.isSuccessful) {
                    restSyncManager.enqueueOfflineAction(
                        "hogares/${goal.hogar_id}/objetivos/$goalId",
                        "DELETE",
                        null,
                        "ELIMINAR_OBJETIVO",
                        "Eliminar objetivo #$goalId"
                    )
                }
            } catch (e: Exception) {
                restSyncManager.enqueueOfflineAction(
                    "hogares/${goal.hogar_id}/objetivos/$goalId",
                    "DELETE",
                    null,
                    "ELIMINAR_OBJETIVO",
                    "Eliminar objetivo #$goalId"
                )
            }
        }
    }
}
