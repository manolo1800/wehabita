package com.example.data.repository

import com.example.data.local.dao.MemberWithUser
import com.example.data.local.dao.OccurrenceWithDetails
import com.example.data.local.entities.*
import com.example.data.remote.api.ApiClient
import com.example.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeHabitaRepository {
    private val TAG = "WeHabita:Repository"

    val restSyncManager: RestSyncManager = RestSyncManager { this }

    // ==========================================
    // In-Memory Reactive StateFlows
    // ==========================================
    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    private val _homes = MutableStateFlow<List<HomeEntity>>(emptyList())
    private val _members = MutableStateFlow<List<MemberWithUser>>(emptyList())
    private val _privileges = MutableStateFlow<List<PrivilegeEntity>>(emptyList())
    private val _memberPrivileges = MutableStateFlow<Map<Int, List<PrivilegeEntity>>>(emptyMap())
    private val _invitations = MutableStateFlow<List<InvitationEntity>>(emptyList())

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    private val _taskSchedules = MutableStateFlow<Map<Int, List<TaskScheduleEntity>>>(emptyMap())
    private val _taskAssignments = MutableStateFlow<Map<Int, List<TaskAssignmentEntity>>>(emptyMap())
    private val _occurrences = MutableStateFlow<List<OccurrenceWithDetails>>(emptyList())

    private val _penalties = MutableStateFlow<List<PenaltyEntity>>(emptyList())
    private val _penaltyUsers = MutableStateFlow<Map<Int, List<Int>>>(emptyMap())

    private val _problems = MutableStateFlow<List<ProblemEntity>>(emptyList())
    private val _problemUsers = MutableStateFlow<Map<Int, List<Int>>>(emptyMap())
    private val _problemPoints = MutableStateFlow<Map<Int, List<ProblemPointOfViewEntity>>>(emptyMap())

    private val _needLists = MutableStateFlow<List<NeedListEntity>>(emptyList())
    private val _needItems = MutableStateFlow<List<NeedItemEntity>>(emptyList())

    private val _goals = MutableStateFlow<List<GoalEntity>>(emptyList())
    private val _goalPoints = MutableStateFlow<Map<Int, List<GoalPointEntity>>>(emptyMap())

    // Pending Sync Flow (Direct to service, always 0)
    fun getPendingSyncCount(): Flow<Int> = flowOf(0)
    fun getPendingSyncQueue(): Flow<List<SyncQueueEntity>> = flowOf(emptyList())

    // ==========================================
    // Users
    // ==========================================
    fun getAllUsers(): Flow<List<UserEntity>> = _users.asStateFlow()

    suspend fun getUserById(id: Int): UserEntity? = withContext(Dispatchers.IO) {
        _users.value.find { it.id == id } ?: try {
            val resp = ApiClient.service.getUserById(id)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val u = resp.body()!!.data
                val entity = UserEntity(
                    id = u.id,
                    nombre = u.nombre,
                    email = u.email,
                    username = u.username,
                    codigo_usuario = u.codigo_usuario,
                    rol_global = u.rol_global,
                    activo = u.activo
                )
                upsertUserInMemory(entity)
                entity
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserByEmailOrUsername(query: String): UserEntity? =
        _users.value.find { it.email.equals(query, ignoreCase = true) || it.username.equals(query, ignoreCase = true) }

    suspend fun registerUser(nombre: String, email: String, username: String, passwordHash: String): Long = withContext(Dispatchers.IO) {
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
                upsertUserInMemory(userEntity)
                SyncLogCollector.s(TAG, "Usuario registrado online exitosamente: ${p.username}")
                return@withContext p.id.toLong()
            } else {
                val err = resp.errorBody()?.string() ?: "Error de registro HTTP ${resp.code()}"
                SyncLogCollector.e(TAG, "Registro falló: $err")
                throw Exception(err)
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error en registro de usuario: ${e.message}", e)
            throw e
        }
    }

    private fun upsertUserInMemory(user: UserEntity) {
        val current = _users.value.toMutableList()
        val index = current.indexOfFirst { it.id == user.id }
        if (index >= 0) {
            current[index] = user
        } else {
            current.add(user)
        }
        _users.value = current
    }

    // ==========================================
    // Homes
    // ==========================================
    fun getHomesForUser(userId: Int): Flow<List<HomeEntity>> = _homes.asStateFlow()

    suspend fun getHomeById(homeId: Int): HomeEntity? =
        _homes.value.find { it.id == homeId }

    suspend fun createHome(name: String, tipo: String, createdBy: Int): Long = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.createHome(CreateHomeRequest(nombre = name, tipo_hogar = tipo))
            if (resp.isSuccessful && resp.body()?.data != null) {
                val h = resp.body()!!.data
                val homeEntity = HomeEntity(
                    id = h.id,
                    nombre = h.nombre,
                    tipo_hogar = h.tipo_hogar,
                    codigo_invitacion = h.codigo_invitacion,
                    creado_por = h.creador?.id ?: createdBy,
                    fecha_creacion = parseTimestamp(h.fecha_creacion)
                )
                upsertHomeInMemory(homeEntity)
                ApiClient.saveHomeId(h.id)
                fetchHomes()
                fetchAllHomeData(h.id)
                SyncLogCollector.s(TAG, "Hogar creado online exitosamente: ${h.nombre} (ID ${h.id})")
                return@withContext h.id.toLong()
            } else {
                val err = resp.errorBody()?.string() ?: "Error HTTP ${resp.code()}"
                throw Exception(err)
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al crear hogar en el servicio: ${e.message}", e)
            throw e
        }
    }

    suspend fun joinHomeByInviteCode(inviteCode: String, userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.joinHome(JoinHomeRequest(codigo = inviteCode.trim()))
            if (resp.isSuccessful && resp.body()?.data != null) {
                val hogarId = resp.body()!!.data.hogar_id
                ApiClient.saveHomeId(hogarId)
                fetchHomes()
                fetchAllHomeData(hogarId)
                SyncLogCollector.s(TAG, "Unido al hogar #$hogarId exitosamente.")
                return@withContext true
            }
            false
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al unirse al hogar con código: ${e.message}", e)
            false
        }
    }

    suspend fun deleteHome(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.deleteHome(homeId)
            if (resp.isSuccessful) {
                _homes.value = _homes.value.filter { it.id != homeId }
                SyncLogCollector.s(TAG, "Hogar #$homeId eliminado.")
                fetchHomes()
                return@withContext true
            }
            false
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al eliminar hogar: ${e.message}", e)
            false
        }
    }

    suspend fun leaveHome(homeId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.leaveHome(homeId)
            if (resp.isSuccessful) {
                _homes.value = _homes.value.filter { it.id != homeId }
                fetchHomes()
                return@withContext true
            }
            false
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al salir del hogar: ${e.message}", e)
            false
        }
    }

    private fun upsertHomeInMemory(home: HomeEntity) {
        val current = _homes.value.toMutableList()
        val index = current.indexOfFirst { it.id == home.id }
        if (index >= 0) {
            current[index] = home
        } else {
            current.add(home)
        }
        _homes.value = current
    }

    // ==========================================
    // Members & Privileges
    // ==========================================
    fun getMembersWithUserForHome(homeId: Int): Flow<List<MemberWithUser>> = _members.asStateFlow()

    fun getAllPrivileges(): Flow<List<PrivilegeEntity>> = _privileges.asStateFlow()

    fun getPrivilegesForMember(memberId: Int): Flow<List<PrivilegeEntity>> =
        _memberPrivileges.map { it[memberId] ?: emptyList() }

    suspend fun updateMemberRole(member: HomeMemberEntity, newRole: String) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.updateMemberRole(member.hogar_id, member.id, UpdateMemberRoleRequest(rol = newRole))
            if (resp.isSuccessful) {
                fetchMembers(member.hogar_id)
                SyncLogCollector.s(TAG, "Rol de miembro #${member.id} actualizado a $newRole")
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error actualizando rol: ${e.message}", e)
        }
    }

    suspend fun updateMemberPrivileges(homeId: Int, memberId: Int, privileges: List<String>) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.updateMemberPrivileges(
                homeId,
                memberId,
                UpdateMemberPrivilegesRequest(privilegios = privileges)
            )
            if (resp.isSuccessful) {
                fetchMembers(homeId)
                SyncLogCollector.s(TAG, "Privilegios de miembro #$memberId actualizados: $privileges")
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error actualizando privilegios: ${e.message}", e)
        }
    }

    suspend fun removeMember(homeId: Int, userId: Int) = withContext(Dispatchers.IO) {
        try {
            val member = _members.value.find { it.user.id == userId }
            if (member != null) {
                val resp = ApiClient.service.removeMember(homeId, member.member.id)
                if (resp.isSuccessful) {
                    fetchMembers(homeId)
                    SyncLogCollector.s(TAG, "Miembro $userId expulsado del hogar $homeId")
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al remover miembro: ${e.message}", e)
        }
    }

    // ==========================================
    // Invitations
    // ==========================================
    fun getInvitationsForHome(homeId: Int): Flow<List<InvitationEntity>> = _invitations.asStateFlow()

    suspend fun createInvitation(homeId: Int, createdBy: Int, email: String?, userCode: String?): Long = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.createInvitation(
                homeId,
                CreateInvitationRequest(
                    email_destino = email?.ifBlank { null },
                    codigo_usuario_destino = userCode?.ifBlank { null }
                )
            )
            if (resp.isSuccessful) {
                fetchInvitations(homeId)
                SyncLogCollector.s(TAG, "Invitación creada en el servidor para el hogar #$homeId")
                return@withContext 1L
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al crear invitación: ${e.message}", e)
        }
        0L
    }

    // ==========================================
    // Tasks & Occurrences
    // ==========================================
    fun getTasksForHome(homeId: Int): Flow<List<TaskEntity>> = _tasks.asStateFlow()

    fun getSchedulesForTask(taskId: Int): Flow<List<TaskScheduleEntity>> =
        _taskSchedules.map { it[taskId] ?: emptyList() }

    fun getAssignmentsForTask(taskId: Int): Flow<List<TaskAssignmentEntity>> =
        _taskAssignments.map { it[taskId] ?: emptyList() }

    fun getOccurrencesForHomeAndDate(homeId: Int, date: String): Flow<List<OccurrenceWithDetails>> =
        _occurrences.map { list -> list.filter { it.occurrence.fecha == date } }

    fun getAllOccurrencesForHome(homeId: Int): Flow<List<OccurrenceWithDetails>> =
        _occurrences.asStateFlow()

    suspend fun createTask(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcion: String?,
        tipo: String,
        fechaInicio: String,
        fechaFin: String?,
        horarios: List<Pair<String, String?>>,
        asignadosIds: List<Int>
    ): Long = withContext(Dispatchers.IO) {
        try {
            val request = CreateTaskRequest(
                titulo = titulo,
                descripcion = descripcion,
                tipo = tipo,
                fecha_inicio = fechaInicio,
                fecha_fin = fechaFin,
                horarios = horarios.map { ScheduleDto(hora_inicio = it.first, hora_fin = it.second) },
                asignados_ids = asignadosIds
            )
            val resp = ApiClient.service.createTask(homeId, request)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val t = resp.body()!!.data
                fetchTasks(homeId)
                SyncLogCollector.s(TAG, "Tarea '${t.titulo}' creada en el servidor.")
                return@withContext t.id.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error al crear tarea: ${e.message}", e)
        }
        0L
    }

    suspend fun updateTask(
        task: TaskEntity,
        horarios: List<Pair<String, String?>>,
        asignadosIds: List<Int>
    ) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = if (task.hogar_id > 0) task.hogar_id else ApiClient.getSavedHomeId() ?: _homes.value.firstOrNull()?.id ?: 0
            val normalizedTask = if (task.hogar_id <= 0 && resolvedHomeId > 0) task.copy(hogar_id = resolvedHomeId) else task

            // Actualización optimista en memoria
            val currentTasks = _tasks.value.toMutableList()
            val taskIndex = currentTasks.indexOfFirst { it.id == normalizedTask.id }
            if (taskIndex >= 0) {
                currentTasks[taskIndex] = normalizedTask
            } else {
                currentTasks.add(normalizedTask)
            }
            _tasks.value = currentTasks

            val currentSchedules = _taskSchedules.value.toMutableMap()
            currentSchedules[normalizedTask.id] = horarios.mapIndexed { idx, pair ->
                TaskScheduleEntity(
                    id = (normalizedTask.id * 1000 + idx + 1),
                    tarea_id = normalizedTask.id,
                    hora_inicio = pair.first,
                    hora_fin = pair.second
                )
            }
            _taskSchedules.value = currentSchedules

            val currentAssignments = _taskAssignments.value.toMutableMap()
            currentAssignments[normalizedTask.id] = asignadosIds.map {
                TaskAssignmentEntity(tarea_id = normalizedTask.id, usuario_id = it)
            }
            _taskAssignments.value = currentAssignments

            // Actualizar ocurrencias en memoria para hoy
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (normalizedTask.activa && (normalizedTask.tipo == "fija" || normalizedTask.fecha_inicio == todayDate)) {
                val existingOccs = _occurrences.value.filter { it.occurrence.tarea_id != normalizedTask.id }.toMutableList()
                horarios.forEachIndexed { idx, pair ->
                    val synthId = (normalizedTask.id * 10000 + pair.first.hashCode()).let { if (it < 0) -it else it }
                    existingOccs.add(
                        OccurrenceWithDetails(
                            occurrence = TaskOccurrenceEntity(
                                id = synthId,
                                tarea_id = normalizedTask.id,
                                fecha = todayDate,
                                hora_inicio = pair.first,
                                hora_fin = pair.second,
                                completada = false
                            ),
                            task = normalizedTask,
                            completedByUser = null
                        )
                    )
                }
                _occurrences.value = existingOccs
            }

            val request = UpdateTaskRequest(
                titulo = normalizedTask.titulo,
                descripcion = normalizedTask.descripcion,
                tipo = normalizedTask.tipo,
                fecha_inicio = normalizedTask.fecha_inicio,
                fecha_fin = normalizedTask.fecha_fin,
                activa = normalizedTask.activa,
                horarios = horarios.map { ScheduleDto(hora_inicio = it.first, hora_fin = it.second) },
                asignados_ids = asignadosIds
            )

            if (resolvedHomeId > 0) {
                val resp = ApiClient.service.updateTask(resolvedHomeId, normalizedTask.id, request)
                if (resp.isSuccessful) {
                    fetchTasks(resolvedHomeId)
                    SyncLogCollector.s(TAG, "Tarea #${normalizedTask.id} actualizada en servidor (Hogar: $resolvedHomeId).")
                } else {
                    SyncLogCollector.w(TAG, "Respuesta al actualizar tarea: ${resp.code()} ${resp.message()}")
                    fetchTasks(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error actualizando tarea: ${e.message}", e)
        }
    }

    suspend fun deleteTask(taskId: Int, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _tasks.value.find { it.id == taskId }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.deleteTask(resolvedHomeId, taskId)
                if (resp.isSuccessful) {
                    fetchTasks(resolvedHomeId)
                    SyncLogCollector.s(TAG, "Tarea #$taskId eliminada.")
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error eliminando tarea: ${e.message}", e)
        }
    }

    suspend fun addTaskSchedule(homeId: Int, taskId: Int, horaInicio: String, horaFin: String?) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = if (homeId > 0) homeId else _tasks.value.find { it.id == taskId }?.hogar_id ?: ApiClient.getSavedHomeId() ?: _homes.value.firstOrNull()?.id ?: 0
            val currentMap = _taskSchedules.value.toMutableMap()
            val list = (currentMap[taskId] ?: emptyList()).toMutableList()
            val tempId = (System.currentTimeMillis() % 100000).toInt() + 1
            val newEntity = TaskScheduleEntity(
                id = tempId,
                tarea_id = taskId,
                hora_inicio = horaInicio,
                hora_fin = horaFin
            )
            list.add(newEntity)
            currentMap[taskId] = list
            _taskSchedules.value = currentMap

            // Añadir inmediatamente a ocurrencias si la tarea está activa
            val parentTask = _tasks.value.find { it.id == taskId }
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (parentTask != null && parentTask.activa) {
                val occList = _occurrences.value.toMutableList()
                val alreadyExists = occList.any {
                    it.occurrence.tarea_id == taskId &&
                    it.occurrence.fecha == todayDate &&
                    it.occurrence.hora_inicio.trim() == horaInicio.trim()
                }
                if (!alreadyExists) {
                    occList.add(
                        OccurrenceWithDetails(
                            occurrence = TaskOccurrenceEntity(
                                id = tempId * 100,
                                tarea_id = taskId,
                                fecha = todayDate,
                                hora_inicio = horaInicio,
                                hora_fin = horaFin,
                                completada = false
                            ),
                            task = parentTask,
                            completedByUser = null
                        )
                    )
                    _occurrences.value = occList
                }
            }

            if (resolvedHomeId > 0) {
                var directSuccess = false
                try {
                    val resp = ApiClient.service.addTaskSchedule(
                        resolvedHomeId,
                        taskId,
                        ScheduleDto(hora_inicio = horaInicio, hora_fin = horaFin)
                    )
                    directSuccess = resp.isSuccessful
                } catch (e: Exception) {
                    directSuccess = false
                }

                if (!directSuccess && parentTask != null) {
                    // Sincronizamos mediante updateTask con la lista completa de horarios
                    val allPairs = list.map { it.hora_inicio to it.hora_fin }
                    val assignments = _taskAssignments.value[taskId]?.map { it.usuario_id } ?: emptyList()
                    updateTask(parentTask.copy(hogar_id = resolvedHomeId), allPairs, assignments)
                } else {
                    fetchTasks(resolvedHomeId)
                }
            }

            SyncLogCollector.s(TAG, "Horario $horaInicio ($horaFin) agregado a la tarea #$taskId")
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error agregando horario: ${e.message}", e)
            if (homeId > 0) fetchTasks(homeId)
        }
    }

    suspend fun deleteTaskSchedule(homeId: Int, taskId: Int, scheduleId: Int) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = if (homeId > 0) homeId else _tasks.value.find { it.id == taskId }?.hogar_id ?: ApiClient.getSavedHomeId() ?: _homes.value.firstOrNull()?.id ?: 0
            val currentMap = _taskSchedules.value.toMutableMap()
            val targetSchedule = currentMap[taskId]?.find { it.id == scheduleId }
            
            // Inmediatamente quitamos el horario de memoria para actualización instantánea de la UI
            if (currentMap.containsKey(taskId)) {
                currentMap[taskId] = currentMap[taskId]?.filter { it.id != scheduleId } ?: emptyList()
                _taskSchedules.value = currentMap
            }
            if (targetSchedule != null) {
                _occurrences.value = _occurrences.value.filter {
                    !(it.occurrence.tarea_id == taskId && it.occurrence.hora_inicio.trim() == targetSchedule.hora_inicio.trim())
                }
            }

            if (resolvedHomeId > 0) {
                var directSuccess = false
                try {
                    val resp = ApiClient.service.deleteTaskSchedule(resolvedHomeId, taskId, scheduleId)
                    directSuccess = resp.isSuccessful
                    if (!directSuccess) {
                        val respDirect = ApiClient.service.deleteTaskScheduleDirect(resolvedHomeId, scheduleId)
                        directSuccess = respDirect.isSuccessful
                    }
                } catch (e: Exception) {
                    directSuccess = false
                }

                // Sincronizar mediante updateTask con la lista restante
                val parentTask = _tasks.value.find { it.id == taskId }
                if (parentTask != null) {
                    val remainingPairs = (currentMap[taskId] ?: emptyList()).map { it.hora_inicio to it.hora_fin }
                    val assignments = _taskAssignments.value[taskId]?.map { it.usuario_id } ?: emptyList()
                    updateTask(parentTask.copy(hogar_id = resolvedHomeId), remainingPairs, assignments)
                } else {
                    fetchTasks(resolvedHomeId)
                }

                SyncLogCollector.s(TAG, "Horario #$scheduleId de la tarea #$taskId eliminado exitosamente.")
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error eliminando horario: ${e.message}", e)
            if (homeId > 0) fetchTasks(homeId)
        }
    }

    suspend fun markOccurrence(occurrenceId: Int, completed: Boolean, userId: Int?, notes: String? = null, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: ApiClient.getSavedHomeId() ?: _homes.value.firstOrNull()?.id
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.completeOccurrence(
                    resolvedHomeId,
                    occurrenceId,
                    CompleteOccurrenceRequest(completada = completed, notas = notes)
                )
                if (resp.isSuccessful) {
                    fetchTasks(resolvedHomeId)
                    SyncLogCollector.s(TAG, "Ocurrencia #$occurrenceId marcada como completada=$completed")
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error completando ocurrencia: ${e.message}", e)
        }
    }

    suspend fun completeTaskDirectly(taskId: Int, completed: Boolean, userId: Int?) = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val matchingOcc = _occurrences.value.find { it.occurrence.tarea_id == taskId && it.occurrence.fecha == today }
        if (matchingOcc != null) {
            markOccurrence(matchingOcc.occurrence.id, completed, userId)
        }
    }

    // ==========================================
    // Penalties
    // ==========================================
    fun getPenaltiesForHome(homeId: Int): Flow<List<PenaltyEntity>> = _penalties.asStateFlow()

    fun getAssignedUsersForPenalty(penaltyId: Int): Flow<List<Int>> =
        _penaltyUsers.map { it[penaltyId] ?: emptyList() }

    suspend fun createPenalty(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcionMotivo: String,
        penalizacion: String,
        fechaInicio: String,
        fechaFin: String?,
        usuariosIds: List<Int>
    ): Long = withContext(Dispatchers.IO) {
        try {
            val req = CreatePenaltyRequest(
                titulo = titulo,
                descripcion_motivo = descripcionMotivo,
                penalizacion = penalizacion,
                fecha_inicio = fechaInicio,
                fecha_fin = fechaFin,
                usuarios_ids = usuariosIds
            )
            val resp = ApiClient.service.createPenalty(homeId, req)
            if (resp.isSuccessful && resp.body()?.data != null) {
                fetchPenalties(homeId)
                SyncLogCollector.s(TAG, "Penalización creada en el servidor.")
                return@withContext resp.body()!!.data.id.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error creando penalización: ${e.message}", e)
        }
        0L
    }

    suspend fun updatePenaltyStatus(penalty: PenaltyEntity, newStatus: String) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.updatePenaltyStatus(penalty.hogar_id, penalty.id, UpdatePenaltyStatusRequest(estado = newStatus))
            if (resp.isSuccessful) {
                fetchPenalties(penalty.hogar_id)
                SyncLogCollector.s(TAG, "Estado de penalización #${penalty.id} actualizado a $newStatus")
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error actualizando estado de penalización: ${e.message}", e)
        }
    }

    suspend fun deletePenalty(penaltyId: Int, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _penalties.value.find { it.id == penaltyId }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.deletePenalty(resolvedHomeId, penaltyId)
                if (resp.isSuccessful) {
                    fetchPenalties(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error eliminando penalización: ${e.message}", e)
        }
    }

    // ==========================================
    // Problems / Conflicts
    // ==========================================
    fun getProblemsForHome(homeId: Int): Flow<List<ProblemEntity>> = _problems.asStateFlow()

    fun getInvolvedUsersForProblem(problemId: Int): Flow<List<Int>> =
        _problemUsers.map { it[problemId] ?: emptyList() }

    fun getPointsOfViewForProblem(problemId: Int): Flow<List<ProblemPointOfViewEntity>> =
        _problemPoints.map { it[problemId] ?: emptyList() }

    suspend fun createProblem(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcion: String,
        usuariosInvolucradosIds: List<Int>
    ): Long = withContext(Dispatchers.IO) {
        try {
            val req = CreateProblemRequest(
                titulo = titulo,
                descripcion = descripcion,
                usuarios_involucrados_ids = usuariosInvolucradosIds
            )
            val resp = ApiClient.service.createProblem(homeId, req)
            if (resp.isSuccessful && resp.body()?.data != null) {
                fetchProblems(homeId)
                SyncLogCollector.s(TAG, "Problema creado en el servidor.")
                return@withContext resp.body()!!.data.id.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error creando problema: ${e.message}", e)
        }
        0L
    }

    suspend fun addPointOfView(problemId: Int, userId: Int, contenido: String, homeId: Int? = null): Long = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _problems.value.find { it.id == problemId }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.addPointOfView(resolvedHomeId, problemId, AddPointOfViewRequest(contenido = contenido))
                if (resp.isSuccessful) {
                    fetchProblems(resolvedHomeId)
                    return@withContext 1L
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error agregando punto de vista: ${e.message}", e)
        }
        0L
    }

    suspend fun deletePointOfView(problemId: Int, povId: Int, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _problems.value.find { it.id == problemId }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.deletePointOfView(resolvedHomeId, problemId, povId)
                if (resp.isSuccessful) {
                    fetchProblems(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error eliminando punto de vista: ${e.message}", e)
        }
    }

    suspend fun updateProblem(problem: ProblemEntity) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.updateProblem(
                problem.hogar_id,
                problem.id,
                UpdateProblemRequest(titulo = problem.titulo, descripcion = problem.descripcion)
            )
            if (resp.isSuccessful) {
                fetchProblems(problem.hogar_id)
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error actualizando problema: ${e.message}", e)
        }
    }

    suspend fun updateProblemDetails(
        problemId: Int,
        homeId: Int,
        titulo: String,
        descripcion: String,
        hablado: Boolean,
        solucionado: Boolean,
        acuerdo: String?,
        usuariosInvolucradosIds: List<Int>? = null
    ) = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.updateProblem(
                homeId,
                problemId,
                UpdateProblemRequest(titulo = titulo, descripcion = descripcion, usuarios_involucrados_ids = usuariosInvolucradosIds)
            )
            ApiClient.service.updateProblemStatus(
                homeId,
                problemId,
                UpdateProblemStatusRequest(hablado = hablado, solucionado = solucionado, acuerdo = acuerdo)
            )
            fetchProblems(homeId)
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error actualizando detalles de problema: ${e.message}", e)
        }
    }

    suspend fun deleteProblem(problemId: Int, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _problems.value.find { it.id == problemId }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.deleteProblem(resolvedHomeId, problemId)
                if (resp.isSuccessful) {
                    fetchProblems(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error eliminando problema: ${e.message}", e)
        }
    }

    // ==========================================
    // Shopping / Needs
    // ==========================================
    fun getNeedListsForHome(homeId: Int): Flow<List<NeedListEntity>> = _needLists.asStateFlow()

    fun getNeedItemsForList(listId: Int): Flow<List<NeedItemEntity>> =
        _needItems.map { list -> list.filter { it.lista_id == listId } }

    suspend fun createNeedList(homeId: Int, createdBy: Int, titulo: String): Long = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.createNeedList(homeId, CreateNeedListRequest(titulo = titulo))
            if (resp.isSuccessful && resp.body()?.data != null) {
                fetchShopping(homeId)
                return@withContext resp.body()!!.data.id.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error creando lista de compras: ${e.message}", e)
        }
        0L
    }

    suspend fun addNeedItem(
        listId: Int,
        productoNombre: String,
        cantidad: Int,
        fechaNecesaria: String,
        notas: String?,
        compradorId: Int?,
        homeId: Int? = null
    ): Long = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _needLists.value.find { it.id == listId }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val req = CreateNeedItemRequest(
                    producto_nombre = productoNombre,
                    cantidad = cantidad,
                    fecha_necesaria = fechaNecesaria,
                    notas = notas,
                    comprador_id = compradorId
                )
                val resp = ApiClient.service.addNeedItem(resolvedHomeId, listId, req)
                if (resp.isSuccessful && resp.body()?.data != null) {
                    fetchShopping(resolvedHomeId)
                    return@withContext resp.body()!!.data.id.toLong()
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error agregando item de compras: ${e.message}", e)
        }
        0L
    }

    suspend fun updateNeedItemStatus(item: NeedItemEntity, newStatus: String, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _needLists.value.find { it.id == item.lista_id }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.updateNeedItemStatus(
                    resolvedHomeId,
                    item.id,
                    UpdateItemStatusRequest(estado = newStatus)
                )
                if (resp.isSuccessful) {
                    fetchShopping(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error actualizando estado del item: ${e.message}", e)
        }
    }

    suspend fun deleteNeedItem(itemId: Int, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val item = _needItems.value.find { it.id == itemId }
            val resolvedHomeId = homeId ?: (item?.let { itm -> _needLists.value.find { it.id == itm.lista_id }?.hogar_id }) ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.deleteNeedItem(resolvedHomeId, itemId)
                if (resp.isSuccessful) {
                    fetchShopping(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error eliminando item de compra: ${e.message}", e)
        }
    }

    // ==========================================
    // Goals / Objectives
    // ==========================================
    fun getGoalsForHome(homeId: Int): Flow<List<GoalEntity>> = _goals.asStateFlow()

    fun getGoalPoints(goalId: Int): Flow<List<GoalPointEntity>> =
        _goalPoints.map { it[goalId] ?: emptyList() }

    suspend fun createGoal(
        homeId: Int,
        createdBy: Int,
        titulo: String,
        descripcion: String?,
        tipo: String,
        fechaLimite: String,
        puntos: List<Pair<String, Int?>>
    ): Long = withContext(Dispatchers.IO) {
        try {
            val req = CreateGoalRequest(
                titulo = titulo,
                descripcion = descripcion,
                tipo = tipo,
                fecha_limite = fechaLimite,
                puntos = puntos.map { GoalPointInputDto(titulo = it.first, asignado_a = it.second) }
            )
            val resp = ApiClient.service.createGoal(homeId, req)
            if (resp.isSuccessful && resp.body()?.data != null) {
                fetchGoals(homeId)
                SyncLogCollector.s(TAG, "Meta/Objetivo creado en el servidor.")
                return@withContext resp.body()!!.data.id.toLong()
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error creando meta: ${e.message}", e)
        }
        0L
    }

    suspend fun postponeGoal(goal: GoalEntity, newDeadline: String) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.postponeGoal(goal.hogar_id, goal.id, PostponeGoalRequest(nueva_fecha_limite = newDeadline))
            if (resp.isSuccessful) {
                fetchGoals(goal.hogar_id)
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error aplazando meta: ${e.message}", e)
        }
    }

    suspend fun setGoalCompleted(goal: GoalEntity, completed: Boolean) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.completeGoal(goal.hogar_id, goal.id, CompleteGoalRequest(completado = completed))
            if (resp.isSuccessful) {
                fetchGoals(goal.hogar_id)
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error completando meta: ${e.message}", e)
        }
    }

    suspend fun toggleGoalPoint(point: GoalPointEntity, completed: Boolean, userId: Int?, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _goals.value.find { it.id == point.objetivo_id }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.completeGoalPoint(
                    resolvedHomeId,
                    point.id,
                    CompleteGoalRequest(completado = completed)
                )
                if (resp.isSuccessful) {
                    fetchGoals(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error en punto de meta: ${e.message}", e)
        }
    }

    suspend fun deleteGoal(goalId: Int, homeId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            val resolvedHomeId = homeId ?: _goals.value.find { it.id == goalId }?.hogar_id ?: ApiClient.getSavedHomeId()
            if (resolvedHomeId != null) {
                val resp = ApiClient.service.deleteGoal(resolvedHomeId, goalId)
                if (resp.isSuccessful) {
                    fetchGoals(resolvedHomeId)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.e(TAG, "Error eliminando meta: ${e.message}", e)
        }
    }

    // ==========================================
    // Live Data Fetching From REST Services
    // ==========================================
    suspend fun fetchHomes() = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getMyHomes()
            if (resp.isSuccessful && resp.body()?.data != null) {
                val homesList = resp.body()!!.data.map { dto ->
                    HomeEntity(
                        id = dto.hogar_id,
                        nombre = dto.hogar_nombre,
                        tipo_hogar = dto.tipo_hogar,
                        codigo_invitacion = dto.codigo_invitacion,
                        creado_por = 0,
                        fecha_creacion = parseTimestamp(dto.fecha_union)
                    )
                }
                _homes.value = homesList
                SyncLogCollector.d(TAG, "Hogares cargados desde REST: ${homesList.size} hogares")
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar hogares: ${e.message}")
        }
    }

    suspend fun fetchMembers(homeId: Int) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getHomeMembers(homeId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val memberDtos = resp.body()!!.data
                val memberWithUsers = memberDtos.map { dto ->
                    val user = UserEntity(
                        id = dto.usuario_id,
                        nombre = dto.nombre,
                        email = dto.email,
                        username = dto.username,
                        codigo_usuario = dto.codigo_usuario,
                        rol_global = "usuario",
                        activo = true
                    )
                    upsertUserInMemory(user)

                    val member = HomeMemberEntity(
                        id = dto.id,
                        hogar_id = homeId,
                        usuario_id = dto.usuario_id,
                        rol = dto.rol,
                        activo = true,
                        fecha_union = parseTimestamp(dto.fecha_union)
                    )

                    MemberWithUser(member = member, user = user)
                }
                _members.value = memberWithUsers

                // Update member privileges map
                val privMap = mutableMapOf<Int, List<PrivilegeEntity>>()
                memberDtos.forEach { dto ->
                    privMap[dto.id] = dto.privilegios.map { name ->
                        PrivilegeEntity(id = name.hashCode(), nombre = name)
                    }
                }
                _memberPrivileges.value = privMap
                SyncLogCollector.d(TAG, "Miembros cargados desde REST: ${memberWithUsers.size} miembros")
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar miembros: ${e.message}")
        }
    }

    suspend fun fetchTasks(homeId: Int) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getTasks(homeId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val taskDtos = resp.body()!!.data
                val taskEntities = mutableListOf<TaskEntity>()
                val schedulesMap = mutableMapOf<Int, List<TaskScheduleEntity>>()
                val assignmentsMap = mutableMapOf<Int, List<TaskAssignmentEntity>>()

                taskDtos.forEach { dto ->
                    taskEntities.add(
                        TaskEntity(
                            id = dto.id,
                            hogar_id = if (dto.hogar_id > 0) dto.hogar_id else homeId,
                            creado_por = dto.creado_por ?: 0,
                            titulo = dto.titulo,
                            descripcion = dto.descripcion,
                            tipo = dto.tipo,
                            fecha_inicio = dto.fecha_inicio,
                            fecha_fin = dto.fecha_fin,
                            activa = dto.activa,
                            fecha_creacion = parseTimestamp(dto.fecha_creacion)
                        )
                    )
                    schedulesMap[dto.id] = dto.horarios.map { s ->
                        TaskScheduleEntity(
                            id = s.id ?: 0,
                            tarea_id = dto.id,
                            hora_inicio = s.hora_inicio,
                            hora_fin = s.hora_fin
                        )
                    }
                    assignmentsMap[dto.id] = dto.asignados.map { u ->
                        TaskAssignmentEntity(
                            tarea_id = dto.id,
                            usuario_id = u.id
                        )
                    }
                }
                _tasks.value = taskEntities
                _taskSchedules.value = schedulesMap
                _taskAssignments.value = assignmentsMap
            }

            // Also fetch task occurrences
            val occResp = ApiClient.service.getTaskOccurrences(homeId)
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val cleanOccurrences = mutableListOf<OccurrenceWithDetails>()

            if (occResp.isSuccessful && occResp.body()?.data != null) {
                val occDtos = occResp.body()!!.data
                occDtos.forEach { dto ->
                    val parentTask = _tasks.value.find { it.id == dto.tarea_id && it.activa }
                    if (parentTask != null) {
                        val taskSchedules = _taskSchedules.value[parentTask.id] ?: emptyList()
                        // Solo conservar la ocurrencia si el horario sigue existiendo en la tarea activa
                        val isScheduleValid = if (taskSchedules.isEmpty()) {
                            true
                        } else {
                            taskSchedules.any { it.hora_inicio.trim() == dto.hora_inicio.trim() }
                        }

                        if (isScheduleValid) {
                            val user = dto.completada_por?.let { u ->
                                UserEntity(
                                    id = u.id,
                                    nombre = u.nombre,
                                    email = u.email ?: "",
                                    username = u.username,
                                    codigo_usuario = u.codigo_usuario ?: ""
                                )
                            }
                            val occEntity = TaskOccurrenceEntity(
                                id = dto.id,
                                tarea_id = dto.tarea_id,
                                fecha = dto.fecha,
                                hora_inicio = dto.hora_inicio,
                                hora_fin = dto.hora_fin,
                                completada = dto.completada,
                                completada_por = dto.completada_por?.id,
                                fecha_completada = parseTimestamp(dto.fecha_completada),
                                notas = dto.notas
                            )
                            cleanOccurrences.add(
                                OccurrenceWithDetails(
                                    occurrence = occEntity,
                                    task = parentTask,
                                    completedByUser = user
                                )
                            )
                        }
                    }
                }
            }

            // Asegurar que para tareas activas con horarios, los horarios vigentes estén disponibles para hoy si no vinieron en el endpoint
            _tasks.value.filter { it.activa }.forEach { task ->
                val schedules = _taskSchedules.value[task.id] ?: emptyList()
                if (task.tipo == "fija" || task.fecha_inicio == todayDate) {
                    schedules.forEach { s ->
                        val alreadyExists = cleanOccurrences.any {
                            it.occurrence.tarea_id == task.id &&
                            it.occurrence.fecha == todayDate &&
                            it.occurrence.hora_inicio.trim() == s.hora_inicio.trim()
                        }
                        if (!alreadyExists) {
                            val synthId = (task.id * 10000 + s.hora_inicio.hashCode()).let { if (it < 0) -it else it }
                            cleanOccurrences.add(
                                OccurrenceWithDetails(
                                    occurrence = TaskOccurrenceEntity(
                                        id = synthId,
                                        tarea_id = task.id,
                                        fecha = todayDate,
                                        hora_inicio = s.hora_inicio,
                                        hora_fin = s.hora_fin,
                                        completada = false
                                    ),
                                    task = task,
                                    completedByUser = null
                                )
                            )
                        }
                    }
                }
            }

            _occurrences.value = cleanOccurrences
            SyncLogCollector.d(TAG, "Tareas sincronizadas con REST: ${_tasks.value.size} activas, ${cleanOccurrences.size} ocurrencias válidas")
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar tareas: ${e.message}")
        }
    }

    suspend fun fetchPenalties(homeId: Int) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getPenalties(homeId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dtos = resp.body()!!.data
                val penaltyEntities = mutableListOf<PenaltyEntity>()
                val userMap = mutableMapOf<Int, List<Int>>()

                dtos.forEach { dto ->
                    penaltyEntities.add(
                        PenaltyEntity(
                            id = dto.id,
                            hogar_id = dto.hogar_id,
                            creado_por = dto.creado_por ?: 0,
                            titulo = dto.titulo,
                            descripcion_motivo = dto.descripcion_motivo,
                            penalizacion = dto.penalizacion,
                            fecha_inicio = dto.fecha_inicio,
                            fecha_fin = dto.fecha_fin,
                            fecha_creacion = parseTimestamp(dto.fecha_creacion),
                            estado = dto.estado
                        )
                    )
                    userMap[dto.id] = dto.usuarios_penalizados.map { it.id }
                    dto.usuarios_penalizados.forEach { u ->
                        upsertUserInMemory(
                            UserEntity(
                                id = u.id,
                                nombre = u.nombre,
                                email = u.email ?: "",
                                username = u.username,
                                codigo_usuario = u.codigo_usuario ?: ""
                            )
                        )
                    }
                }
                _penalties.value = penaltyEntities
                _penaltyUsers.value = userMap
                SyncLogCollector.d(TAG, "Penalizaciones cargadas desde REST: ${penaltyEntities.size}")
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar penalizaciones: ${e.message}")
        }
    }

    suspend fun fetchProblems(homeId: Int) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getProblems(homeId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dtos = resp.body()!!.data
                val problemEntities = mutableListOf<ProblemEntity>()
                val usersMap = mutableMapOf<Int, List<Int>>()
                val povMap = mutableMapOf<Int, List<ProblemPointOfViewEntity>>()

                dtos.forEach { dto ->
                    problemEntities.add(
                        ProblemEntity(
                            id = dto.id,
                            hogar_id = dto.hogar_id,
                            creado_por = dto.creado_por ?: 0,
                            titulo = dto.titulo,
                            descripcion = dto.descripcion,
                            hablado = dto.hablado,
                            solucionado = dto.solucionado,
                            acuerdo = dto.acuerdo,
                            fecha_creacion = parseTimestamp(dto.fecha_creacion)
                        )
                    )
                    usersMap[dto.id] = dto.usuarios_involucrados.map { it.id }
                    povMap[dto.id] = dto.puntos_vista.map { pov ->
                        ProblemPointOfViewEntity(
                            id = pov.id,
                            problema_id = dto.id,
                            usuario_id = pov.usuario_id,
                            contenido = pov.contenido,
                            fecha_creacion = parseTimestamp(pov.fecha_creacion)
                        )
                    }
                }
                _problems.value = problemEntities
                _problemUsers.value = usersMap
                _problemPoints.value = povMap
                SyncLogCollector.d(TAG, "Problemas cargados desde REST: ${problemEntities.size}")
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar problemas: ${e.message}")
        }
    }

    suspend fun fetchShopping(homeId: Int) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getNeedLists(homeId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val listDtos = resp.body()!!.data
                val listEntities = mutableListOf<NeedListEntity>()
                val allItems = mutableListOf<NeedItemEntity>()

                listDtos.forEach { l ->
                    listEntities.add(
                        NeedListEntity(
                            id = l.id,
                            hogar_id = l.hogar_id,
                            creado_por = l.creado_por ?: 0,
                            titulo = l.titulo,
                            fecha_creacion = parseTimestamp(l.fecha_creacion),
                            estado = l.estado
                        )
                    )
                    l.items.forEach { itemDto ->
                        allItems.add(
                            NeedItemEntity(
                                id = itemDto.id,
                                lista_id = l.id,
                                producto_nombre = itemDto.producto_nombre,
                                cantidad = itemDto.cantidad,
                                fecha_necesaria = itemDto.fecha_necesaria,
                                comprador_id = itemDto.comprador?.id,
                                estado = itemDto.estado,
                                notas = itemDto.notas
                            )
                        )
                    }
                }
                _needLists.value = listEntities
                _needItems.value = allItems
                SyncLogCollector.d(TAG, "Compras cargadas desde REST: ${listEntities.size} listas, ${allItems.size} items")
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar compras: ${e.message}")
        }
    }

    suspend fun fetchGoals(homeId: Int) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getGoals(homeId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                val dtos = resp.body()!!.data
                val goalEntities = mutableListOf<GoalEntity>()
                val pointsMap = mutableMapOf<Int, List<GoalPointEntity>>()

                dtos.forEach { dto ->
                    goalEntities.add(
                        GoalEntity(
                            id = dto.id,
                            hogar_id = dto.hogar_id,
                            creado_por = dto.creado_por ?: 0,
                            titulo = dto.titulo,
                            descripcion = dto.descripcion,
                            tipo = dto.tipo,
                            completado = dto.completado,
                            fecha_creacion = parseTimestamp(dto.fecha_creacion),
                            fecha_limite_original = dto.fecha_limite_original,
                            fecha_limite_actual = dto.fecha_limite_actual,
                            fecha_completado = parseTimestamp(dto.fecha_completado)
                        )
                    )
                    pointsMap[dto.id] = dto.puntos.map { p ->
                        GoalPointEntity(
                            id = p.id,
                            objetivo_id = dto.id,
                            titulo = p.titulo,
                            descripcion = p.descripcion,
                            asignado_a = p.asignado?.id,
                            completado = p.completado,
                            fecha_completado = parseTimestamp(p.fecha_completado),
                            completado_por = p.completado_por?.id,
                            orden = p.orden
                        )
                    }
                }
                _goals.value = goalEntities
                _goalPoints.value = pointsMap
                SyncLogCollector.d(TAG, "Metas cargadas desde REST: ${goalEntities.size}")
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar metas: ${e.message}")
        }
    }

    suspend fun fetchPrivileges() = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getPrivileges()
            if (resp.isSuccessful && resp.body()?.data != null) {
                _privileges.value = resp.body()!!.data.map {
                    PrivilegeEntity(id = it.id, nombre = it.nombre, descripcion = it.descripcion)
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar catálogo de privilegios: ${e.message}")
        }
    }

    suspend fun fetchInvitations(homeId: Int) = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.getInvitationsForHome(homeId)
            if (resp.isSuccessful && resp.body()?.data != null) {
                _invitations.value = resp.body()!!.data.map { dto ->
                    InvitationEntity(
                        id = dto.id,
                        hogar_id = homeId,
                        codigo = dto.codigo,
                        email_destino = dto.email_destino,
                        usuario_destino_id = dto.usuario_destino?.id,
                        creado_por = dto.creado_por?.id ?: 0,
                        estado = dto.estado,
                        fecha_creacion = parseTimestamp(dto.fecha_creacion),
                        fecha_expiracion = dto.fecha_expiracion
                    )
                }
            }
        } catch (e: Exception) {
            SyncLogCollector.w(TAG, "Fallo al consultar invitaciones: ${e.message}")
        }
    }

    suspend fun fetchAllHomeData(homeId: Int) = withContext(Dispatchers.IO) {
        fetchMembers(homeId)
        fetchTasks(homeId)
        fetchShopping(homeId)
        fetchProblems(homeId)
        fetchGoals(homeId)
        fetchPenalties(homeId)
        fetchInvitations(homeId)
    }

    fun clearInMemoryData() {
        _users.value = emptyList()
        _homes.value = emptyList()
        _members.value = emptyList()
        _invitations.value = emptyList()
        _tasks.value = emptyList()
        _taskSchedules.value = emptyMap()
        _taskAssignments.value = emptyMap()
        _occurrences.value = emptyList()
        _penalties.value = emptyList()
        _penaltyUsers.value = emptyMap()
        _problems.value = emptyList()
        _problemUsers.value = emptyMap()
        _problemPoints.value = emptyMap()
        _needLists.value = emptyList()
        _needItems.value = emptyList()
        _goals.value = emptyList()
        _goalPoints.value = emptyMap()
        SyncLogCollector.i(TAG, "Memoria RAM limpiada.")
    }

    private fun parseTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            isoFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                ymd.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
