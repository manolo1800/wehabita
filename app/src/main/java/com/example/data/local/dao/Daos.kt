package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM usuarios WHERE activo = 1 ORDER BY nombre ASC")
    fun getAllActiveUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM usuarios")
    suspend fun getAllUsersList(): List<UserEntity>

    @Query("SELECT * FROM usuarios WHERE id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?

    @Query("SELECT * FROM usuarios WHERE email = :email OR username = :email LIMIT 1")
    suspend fun getUserByEmailOrUsername(email: String): UserEntity?

    @Query("SELECT * FROM usuarios WHERE codigo_usuario = :code LIMIT 1")
    suspend fun getUserByCode(code: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface HomeDao {
    @Query("SELECT * FROM hogares WHERE TRIM(nombre) != '' ORDER BY fecha_creacion DESC")
    fun getAllHomes(): Flow<List<HomeEntity>>

    @Query("SELECT * FROM hogares WHERE TRIM(nombre) != ''")
    suspend fun getAllHomesList(): List<HomeEntity>

    @Query("""
        SELECT DISTINCT h.* FROM hogares h
        LEFT JOIN miembros_hogar m ON h.id = m.hogar_id
        WHERE ((m.usuario_id = :userId AND m.activo = 1) OR h.creado_por = :userId)
          AND TRIM(h.nombre) != ''
        ORDER BY h.fecha_creacion DESC
    """)
    fun getHomesForUser(userId: Int): Flow<List<HomeEntity>>

    @Query("SELECT * FROM hogares WHERE id = :homeId")
    suspend fun getHomeById(homeId: Int): HomeEntity?

    @Query("SELECT * FROM hogares WHERE codigo_invitacion = :code LIMIT 1")
    suspend fun getHomeByInviteCode(code: String): HomeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHome(home: HomeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomes(homes: List<HomeEntity>)

    @Update
    suspend fun updateHome(home: HomeEntity)

    @Query("DELETE FROM hogares WHERE id = :homeId OR TRIM(nombre) = ''")
    suspend fun deleteHome(homeId: Int)
}

data class MemberWithUser(
    @Embedded val member: HomeMemberEntity,
    @Relation(
        parentColumn = "usuario_id",
        entityColumn = "id"
    )
    val user: UserEntity
)

@Dao
interface HomeMemberDao {
    @Transaction
    @Query("SELECT * FROM miembros_hogar WHERE hogar_id = :homeId AND activo = 1")
    fun getMembersWithUserForHome(homeId: Int): Flow<List<MemberWithUser>>

    @Query("SELECT * FROM miembros_hogar")
    suspend fun getAllMembersList(): List<HomeMemberEntity>

    @Query("SELECT * FROM miembros_hogar WHERE hogar_id = :homeId AND usuario_id = :userId LIMIT 1")
    suspend fun getMember(homeId: Int, userId: Int): HomeMemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: HomeMemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<HomeMemberEntity>)

    @Update
    suspend fun updateMember(member: HomeMemberEntity)

    @Query("DELETE FROM miembros_hogar WHERE hogar_id = :homeId AND usuario_id = :userId")
    suspend fun removeMember(homeId: Int, userId: Int)
}

@Dao
interface PrivilegeDao {
    @Query("SELECT * FROM privilegios")
    fun getAllPrivileges(): Flow<List<PrivilegeEntity>>

    @Query("SELECT * FROM privilegios")
    suspend fun getAllPrivilegesList(): List<PrivilegeEntity>

    @Query("""
        SELECT p.* FROM privilegios p
        INNER JOIN miembro_privilegios mp ON p.id = mp.privilegio_id
        WHERE mp.miembro_hogar_id = :memberId
    """)
    fun getPrivilegesForMember(memberId: Int): Flow<List<PrivilegeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrivilege(privilege: PrivilegeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrivileges(list: List<PrivilegeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberPrivilege(mp: MemberPrivilegeEntity)

    @Query("DELETE FROM miembro_privilegios WHERE miembro_hogar_id = :memberId AND privilegio_id = :privilegeId")
    suspend fun deleteMemberPrivilege(memberId: Int, privilegeId: Int)

    @Query("DELETE FROM miembro_privilegios WHERE miembro_hogar_id = :memberId")
    suspend fun clearMemberPrivileges(memberId: Int)
}

@Dao
interface InvitationDao {
    @Query("SELECT * FROM invitaciones WHERE hogar_id = :homeId ORDER BY fecha_creacion DESC")
    fun getInvitationsForHome(homeId: Int): Flow<List<InvitationEntity>>

    @Query("""
        SELECT * FROM invitaciones 
        WHERE (usuario_destino_id = :userId OR email_destino = :email) 
        AND estado = 'pendiente'
        ORDER BY fecha_creacion DESC
    """)
    fun getPendingInvitationsForUser(userId: Int, email: String): Flow<List<InvitationEntity>>

    @Query("SELECT * FROM invitaciones WHERE codigo = :code LIMIT 1")
    suspend fun getInvitationByCode(code: String): InvitationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitation(invitation: InvitationEntity): Long

    @Update
    suspend fun updateInvitation(invitation: InvitationEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tareas WHERE hogar_id = :homeId AND activa = 1 ORDER BY fecha_creacion DESC")
    fun getTasksForHome(homeId: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tareas")
    suspend fun getAllTasksList(): List<TaskEntity>

    @Query("SELECT * FROM tareas WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tareas WHERE id = :taskId")
    suspend fun deleteTask(taskId: Int)

    // Horarios
    @Query("SELECT * FROM tarea_horarios WHERE tarea_id = :taskId ORDER BY hora_inicio ASC")
    fun getSchedulesForTask(taskId: Int): Flow<List<TaskScheduleEntity>>

    @Query("SELECT * FROM tarea_horarios")
    suspend fun getAllSchedulesList(): List<TaskScheduleEntity>

    @Query("SELECT * FROM tarea_horarios WHERE tarea_id = :taskId ORDER BY hora_inicio ASC")
    suspend fun getSchedulesForTaskSync(taskId: Int): List<TaskScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: TaskScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<TaskScheduleEntity>)

    @Query("DELETE FROM tarea_horarios WHERE tarea_id = :taskId")
    suspend fun clearSchedulesForTask(taskId: Int)

    // Asignaciones
    @Query("SELECT * FROM tarea_asignaciones WHERE tarea_id = :taskId")
    fun getAssignmentsForTask(taskId: Int): Flow<List<TaskAssignmentEntity>>

    @Query("SELECT * FROM tarea_asignaciones")
    suspend fun getAllAssignmentsList(): List<TaskAssignmentEntity>

    @Query("SELECT usuario_id FROM tarea_asignaciones WHERE tarea_id = :taskId")
    suspend fun getAssignedUserIdsForTask(taskId: Int): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: TaskAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignments(assignments: List<TaskAssignmentEntity>)

    @Query("DELETE FROM tarea_asignaciones WHERE tarea_id = :taskId")
    suspend fun clearAssignmentsForTask(taskId: Int)
}

data class OccurrenceWithDetails(
    @Embedded val occurrence: TaskOccurrenceEntity,
    @Relation(
        parentColumn = "tarea_id",
        entityColumn = "id"
    )
    val task: TaskEntity? = null,
    @Relation(
        parentColumn = "completada_por",
        entityColumn = "id"
    )
    val completedByUser: UserEntity? = null
)

@Dao
interface TaskOccurrenceDao {
    @Transaction
    @Query("""
        SELECT o.* FROM tarea_ocurrencias o
        INNER JOIN tareas t ON o.tarea_id = t.id
        WHERE t.hogar_id = :homeId AND o.fecha = :date
        ORDER BY o.hora_inicio ASC
    """)
    fun getOccurrencesForHomeAndDate(homeId: Int, date: String): Flow<List<OccurrenceWithDetails>>

    @Transaction
    @Query("""
        SELECT o.* FROM tarea_ocurrencias o
        INNER JOIN tareas t ON o.tarea_id = t.id
        WHERE t.hogar_id = :homeId
        ORDER BY o.fecha DESC, o.hora_inicio ASC
    """)
    fun getAllOccurrencesForHome(homeId: Int): Flow<List<OccurrenceWithDetails>>

    @Query("SELECT * FROM tarea_ocurrencias")
    suspend fun getAllOccurrencesList(): List<TaskOccurrenceEntity>

    @Query("SELECT * FROM tarea_ocurrencias WHERE tarea_id = :taskId AND fecha = :date AND hora_inicio = :time LIMIT 1")
    suspend fun getOccurrence(taskId: Int, date: String, time: String): TaskOccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOccurrence(occurrence: TaskOccurrenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOccurrences(occurrences: List<TaskOccurrenceEntity>)

    @Update
    suspend fun updateOccurrence(occurrence: TaskOccurrenceEntity)

    @Query("""
        UPDATE tarea_ocurrencias 
        SET completada = :completed, completada_por = :completedBy, fecha_completada = :completedAt, notas = :notes
        WHERE id = :occurrenceId
    """)
    suspend fun markOccurrence(occurrenceId: Int, completed: Boolean, completedBy: Int?, completedAt: Long?, notes: String?)
    @Query("SELECT * FROM tarea_ocurrencias WHERE tarea_id = :taskId")
    suspend fun getOccurrencesForTask(taskId: Int): List<TaskOccurrenceEntity>

    @Query("DELETE FROM tarea_ocurrencias WHERE id = :occurrenceId")
    suspend fun deleteOccurrence(occurrenceId: Int)

    @Query("DELETE FROM tarea_ocurrencias WHERE tarea_id = :taskId")
    suspend fun deleteOccurrencesForTask(taskId: Int)
}

@Dao
interface PenaltyDao {
    @Query("SELECT * FROM penalizaciones WHERE hogar_id = :homeId ORDER BY fecha_creacion DESC")
    fun getPenaltiesForHome(homeId: Int): Flow<List<PenaltyEntity>>

    @Query("SELECT * FROM penalizaciones")
    suspend fun getAllPenaltiesList(): List<PenaltyEntity>

    @Query("SELECT * FROM penalizaciones WHERE id = :penaltyId")
    suspend fun getPenaltyById(penaltyId: Int): PenaltyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenalty(penalty: PenaltyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenalties(penalties: List<PenaltyEntity>)

    @Update
    suspend fun updatePenalty(penalty: PenaltyEntity)

    @Query("DELETE FROM penalizaciones WHERE id = :penaltyId")
    suspend fun deletePenalty(penaltyId: Int)

    @Query("SELECT usuario_id FROM penalizacion_usuarios WHERE penalizacion_id = :penaltyId")
    fun getAssignedUserIds(penaltyId: Int): Flow<List<Int>>

    @Query("SELECT * FROM penalizacion_usuarios")
    suspend fun getAllPenaltyUsersList(): List<PenaltyUserEntity>

    @Query("SELECT usuario_id FROM penalizacion_usuarios WHERE penalizacion_id = :penaltyId")
    suspend fun getAssignedUserIdsSync(penaltyId: Int): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenaltyUser(penaltyUser: PenaltyUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPenaltyUsers(penaltyUsers: List<PenaltyUserEntity>)

    @Query("DELETE FROM penalizacion_usuarios WHERE penalizacion_id = :penaltyId")
    suspend fun clearPenaltyUsers(penaltyId: Int)
}

@Dao
interface ProblemDao {
    @Query("SELECT * FROM problemas WHERE hogar_id = :homeId ORDER BY fecha_creacion DESC")
    fun getProblemsForHome(homeId: Int): Flow<List<ProblemEntity>>

    @Query("SELECT * FROM problemas")
    suspend fun getAllProblemsList(): List<ProblemEntity>

    @Query("SELECT * FROM problemas WHERE id = :problemId")
    suspend fun getProblemById(problemId: Int): ProblemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblem(problem: ProblemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblems(problems: List<ProblemEntity>)

    @Update
    suspend fun updateProblem(problem: ProblemEntity)

    @Query("DELETE FROM problemas WHERE id = :problemId")
    suspend fun deleteProblem(problemId: Int)

    @Query("SELECT usuario_id FROM problema_usuarios WHERE problema_id = :problemId")
    fun getInvolvedUserIds(problemId: Int): Flow<List<Int>>

    @Query("SELECT * FROM problema_usuarios")
    suspend fun getAllProblemUsersList(): List<ProblemUserEntity>

    @Query("SELECT usuario_id FROM problema_usuarios WHERE problema_id = :problemId")
    suspend fun getInvolvedUserIdsSync(problemId: Int): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblemUser(pu: ProblemUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProblemUsers(pus: List<ProblemUserEntity>)

    @Query("SELECT * FROM problema_puntos_vista WHERE problema_id = :problemId ORDER BY fecha_creacion ASC")
    fun getPointsOfViewForProblem(problemId: Int): Flow<List<ProblemPointOfViewEntity>>

    @Query("SELECT * FROM problema_puntos_vista")
    suspend fun getAllPointsOfViewList(): List<ProblemPointOfViewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPointOfView(pov: ProblemPointOfViewEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPointsOfView(povs: List<ProblemPointOfViewEntity>)

    @Query("DELETE FROM problema_puntos_vista WHERE id = :povId")
    suspend fun deletePointOfView(povId: Int)
}

@Dao
interface NeedListDao {
    @Query("SELECT * FROM listas_necesidades WHERE hogar_id = :homeId ORDER BY fecha_creacion DESC")
    fun getListsForHome(homeId: Int): Flow<List<NeedListEntity>>

    @Query("SELECT * FROM listas_necesidades")
    suspend fun getAllNeedListsList(): List<NeedListEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: NeedListEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<NeedListEntity>)

    @Update
    suspend fun updateList(list: NeedListEntity)

    @Query("DELETE FROM listas_necesidades WHERE id = :listId")
    suspend fun deleteList(listId: Int)

    // Items
    @Query("SELECT * FROM items_necesidad WHERE lista_id = :listId ORDER BY id DESC")
    fun getItemsForList(listId: Int): Flow<List<NeedItemEntity>>

    @Query("SELECT * FROM items_necesidad")
    suspend fun getAllNeedItemsList(): List<NeedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: NeedItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<NeedItemEntity>)

    @Update
    suspend fun updateItem(item: NeedItemEntity)

    @Query("DELETE FROM items_necesidad WHERE id = :itemId")
    suspend fun deleteItem(itemId: Int)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM objetivos WHERE hogar_id = :homeId ORDER BY fecha_creacion DESC")
    fun getGoalsForHome(homeId: Int): Flow<List<GoalEntity>>

    @Query("SELECT * FROM objetivos")
    suspend fun getAllGoalsList(): List<GoalEntity>

    @Query("SELECT * FROM objetivos WHERE id = :goalId")
    suspend fun getGoalById(goalId: Int): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("DELETE FROM objetivos WHERE id = :goalId")
    suspend fun deleteGoal(goalId: Int)

    // Points
    @Query("SELECT * FROM objetivo_puntos WHERE objetivo_id = :goalId ORDER BY orden ASC, id ASC")
    fun getPointsForGoal(goalId: Int): Flow<List<GoalPointEntity>>

    @Query("SELECT * FROM objetivo_puntos")
    suspend fun getAllGoalPointsList(): List<GoalPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalPoint(point: GoalPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalPoints(points: List<GoalPointEntity>)

    @Update
    suspend fun updateGoalPoint(point: GoalPointEntity)

    @Query("DELETE FROM objetivo_puntos WHERE id = :pointId")
    suspend fun deleteGoalPoint(pointId: Int)

    @Query("DELETE FROM objetivo_puntos WHERE objetivo_id = :goalId")
    suspend fun deletePointsForGoal(goalId: Int)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM cola_sincronizacion ORDER BY id ASC")
    fun getAllPendingQueue(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM cola_sincronizacion ORDER BY id ASC")
    suspend fun getAllPendingList(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM cola_sincronizacion")
    fun getPendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Delete
    suspend fun deleteItem(item: SyncQueueEntity)

    @Query("DELETE FROM cola_sincronizacion WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Update
    suspend fun updateItem(item: SyncQueueEntity)

    @Query("DELETE FROM cola_sincronizacion")
    suspend fun clearAll()
}
