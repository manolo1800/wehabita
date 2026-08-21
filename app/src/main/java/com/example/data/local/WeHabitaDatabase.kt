package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        UserEntity::class,
        HomeEntity::class,
        HomeMemberEntity::class,
        PrivilegeEntity::class,
        MemberPrivilegeEntity::class,
        InvitationEntity::class,
        TaskEntity::class,
        TaskScheduleEntity::class,
        TaskAssignmentEntity::class,
        TaskOccurrenceEntity::class,
        PenaltyEntity::class,
        PenaltyUserEntity::class,
        ProblemEntity::class,
        ProblemUserEntity::class,
        ProblemPointOfViewEntity::class,
        NeedListEntity::class,
        NeedItemEntity::class,
        GoalEntity::class,
        GoalPointEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WeHabitaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun homeDao(): HomeDao
    abstract fun homeMemberDao(): HomeMemberDao
    abstract fun privilegeDao(): PrivilegeDao
    abstract fun invitationDao(): InvitationDao
    abstract fun taskDao(): TaskDao
    abstract fun taskOccurrenceDao(): TaskOccurrenceDao
    abstract fun penaltyDao(): PenaltyDao
    abstract fun problemDao(): ProblemDao
    abstract fun needListDao(): NeedListDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var INSTANCE: WeHabitaDatabase? = null

        fun getDatabase(context: Context): WeHabitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeHabitaDatabase::class.java,
                    "wehabita_db"
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed database asynchronously
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.let { seedDatabase(it) }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedDatabase(db: WeHabitaDatabase) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            // 1. Privilegios de catálogo
            val privs = listOf(
                "crear_tareas", "asignar_tareas", "marcar_tareas",
                "crear_problemas", "crear_necesidades", "crear_objetivos",
                "gestionar_usuarios", "aplicar_penalizaciones", "ver_reportes"
            )
            for (p in privs) {
                db.privilegeDao().insertPrivilege(PrivilegeEntity(nombre = p))
            }

            // 2. Usuarios Iniciales
            val u1Id = db.userDao().insertUser(
                UserEntity(
                    id = 1,
                    nombre = "Carlos Mendoza",
                    email = "carlos@wehabita.app",
                    username = "carlos_admin",
                    password_hash = "hash_carlos_123",
                    codigo_usuario = "USR-4091",
                    rol_global = "admin"
                )
            ).toInt()

            val u2Id = db.userDao().insertUser(
                UserEntity(
                    id = 2,
                    nombre = "Sofía Gómez",
                    email = "sofia@wehabita.app",
                    username = "sofi_g",
                    password_hash = "hash_sofia_123",
                    codigo_usuario = "USR-5182",
                    rol_global = "usuario"
                )
            ).toInt()

            val u3Id = db.userDao().insertUser(
                UserEntity(
                    id = 3,
                    nombre = "David Gómez",
                    email = "david@wehabita.app",
                    username = "david_m",
                    password_hash = "hash_david_123",
                    codigo_usuario = "USR-7730",
                    rol_global = "usuario"
                )
            ).toInt()

            val u4Id = db.userDao().insertUser(
                UserEntity(
                    id = 4,
                    nombre = "Lucía López",
                    email = "lucia@wehabita.app",
                    username = "lucia_l",
                    password_hash = "hash_lucia_123",
                    codigo_usuario = "USR-8821",
                    rol_global = "usuario"
                )
            ).toInt()

            // 3. Hogares
            val h1Id = db.homeDao().insertHome(
                HomeEntity(
                    id = 1,
                    nombre = "Casa Dulce Hogar",
                    tipo_hogar = "familia",
                    codigo_invitacion = "HOG-9481",
                    creado_por = u1Id
                )
            ).toInt()

            // 4. Miembros del Hogar
            val m1Id = db.homeMemberDao().insertMember(
                HomeMemberEntity(id = 1, hogar_id = h1Id, usuario_id = u1Id, rol = "admin")
            ).toInt()

            val m2Id = db.homeMemberDao().insertMember(
                HomeMemberEntity(id = 2, hogar_id = h1Id, usuario_id = u2Id, rol = "miembro")
            ).toInt()

            val m3Id = db.homeMemberDao().insertMember(
                HomeMemberEntity(id = 3, hogar_id = h1Id, usuario_id = u3Id, rol = "miembro")
            ).toInt()

            val m4Id = db.homeMemberDao().insertMember(
                HomeMemberEntity(id = 4, hogar_id = h1Id, usuario_id = u4Id, rol = "miembro")
            ).toInt()

            // Asignar privilegios a Carlos (Admin: todos) y a Sofía (crear y marcar tareas, necesidades, objetivos)
            for (privId in 1..9) {
                db.privilegeDao().insertMemberPrivilege(MemberPrivilegeEntity(m1Id, privId))
            }
            listOf(1, 3, 4, 5, 6, 9).forEach { privId ->
                db.privilegeDao().insertMemberPrivilege(MemberPrivilegeEntity(m2Id, privId))
            }

            // 5. Tareas y Horarios (Ej: Tarea fija con 08:00, 14:00, 20:00)
            val t1Id = db.taskDao().insertTask(
                TaskEntity(
                    id = 1,
                    hogar_id = h1Id,
                    creado_por = u1Id,
                    titulo = "Sacar y pasear a la mascota",
                    descripcion = "Paseo de 20 min y darle agua fresca",
                    tipo = "fija",
                    fecha_inicio = todayStr
                )
            ).toInt()

            db.taskDao().insertSchedule(TaskScheduleEntity(tarea_id = t1Id, hora_inicio = "08:00", hora_fin = "08:30"))
            db.taskDao().insertSchedule(TaskScheduleEntity(tarea_id = t1Id, hora_inicio = "14:00", hora_fin = "14:30"))
            db.taskDao().insertSchedule(TaskScheduleEntity(tarea_id = t1Id, hora_inicio = "20:00", hora_fin = "20:30"))
            db.taskDao().insertAssignment(TaskAssignmentEntity(tarea_id = t1Id, usuario_id = u1Id))
            db.taskDao().insertAssignment(TaskAssignmentEntity(tarea_id = t1Id, usuario_id = u3Id))

            val t2Id = db.taskDao().insertTask(
                TaskEntity(
                    id = 2,
                    hogar_id = h1Id,
                    creado_por = u2Id,
                    titulo = "Lavar los platos y recoger cocina",
                    descripcion = "Secar la vajilla y dejar la encimera limpia",
                    tipo = "fija",
                    fecha_inicio = todayStr
                )
            ).toInt()

            db.taskDao().insertSchedule(TaskScheduleEntity(tarea_id = t2Id, hora_inicio = "09:00", hora_fin = "09:30"))
            db.taskDao().insertSchedule(TaskScheduleEntity(tarea_id = t2Id, hora_inicio = "15:00", hora_fin = "15:30"))
            db.taskDao().insertSchedule(TaskScheduleEntity(tarea_id = t2Id, hora_inicio = "21:30", hora_fin = "22:00"))
            db.taskDao().insertAssignment(TaskAssignmentEntity(tarea_id = t2Id, usuario_id = u2Id))
            db.taskDao().insertAssignment(TaskAssignmentEntity(tarea_id = t2Id, usuario_id = u4Id))

            val t3Id = db.taskDao().insertTask(
                TaskEntity(
                    id = 3,
                    hogar_id = h1Id,
                    creado_por = u1Id,
                    titulo = "Sacar la basura y reciclaje",
                    descripcion = "Separar plásticos, cartón y orgánico",
                    tipo = "fija",
                    fecha_inicio = todayStr
                )
            ).toInt()
            db.taskDao().insertSchedule(TaskScheduleEntity(tarea_id = t3Id, hora_inicio = "19:00", hora_fin = "19:30"))
            db.taskDao().insertAssignment(TaskAssignmentEntity(tarea_id = t3Id, usuario_id = u3Id))

            // Generar Ocurrencias de hoy
            db.taskOccurrenceDao().insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = t1Id,
                    fecha = todayStr,
                    hora_inicio = "08:00",
                    hora_fin = "08:30",
                    completada = true,
                    completada_por = u1Id,
                    fecha_completada = System.currentTimeMillis() - 3600000 * 4,
                    notas = "Paseo completado en el parque"
                )
            )
            db.taskOccurrenceDao().insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = t1Id,
                    fecha = todayStr,
                    hora_inicio = "14:00",
                    hora_fin = "14:30",
                    completada = true,
                    completada_por = u3Id,
                    fecha_completada = System.currentTimeMillis() - 3600000,
                    notas = "Comió y bebió agua"
                )
            )
            db.taskOccurrenceDao().insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = t1Id,
                    fecha = todayStr,
                    hora_inicio = "20:00",
                    hora_fin = "20:30",
                    completada = false
                )
            )
            db.taskOccurrenceDao().insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = t2Id,
                    fecha = todayStr,
                    hora_inicio = "09:00",
                    hora_fin = "09:30",
                    completada = true,
                    completada_por = u2Id,
                    fecha_completada = System.currentTimeMillis() - 3600000 * 3
                )
            )
            db.taskOccurrenceDao().insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = t2Id,
                    fecha = todayStr,
                    hora_inicio = "15:00",
                    hora_fin = "15:30",
                    completada = false
                )
            )
            db.taskOccurrenceDao().insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = t2Id,
                    fecha = todayStr,
                    hora_inicio = "21:30",
                    hora_fin = "22:00",
                    completada = false
                )
            )
            db.taskOccurrenceDao().insertOccurrence(
                TaskOccurrenceEntity(
                    tarea_id = t3Id,
                    fecha = todayStr,
                    hora_inicio = "19:00",
                    hora_fin = "19:30",
                    completada = false
                )
            )

            // 6. Penalizaciones
            val p1Id = db.penaltyDao().insertPenalty(
                PenaltyEntity(
                    id = 1,
                    hogar_id = h1Id,
                    creado_por = u1Id,
                    titulo = "No sacar basura el fin de semana",
                    descripcion_motivo = "Se acumuló la basura orgánica en la cocina el sábado por la noche.",
                    penalizacion = "Lavar los platos y fregar la cocina por 3 días seguidos.",
                    fecha_inicio = todayStr,
                    fecha_fin = todayStr,
                    estado = "activa"
                )
            ).toInt()
            db.penaltyDao().insertPenaltyUser(PenaltyUserEntity(penalizacion_id = p1Id, usuario_id = u3Id))

            // 7. Problemas del hogar y puntos de vista
            val pr1Id = db.problemDao().insertProblem(
                ProblemEntity(
                    id = 1,
                    hogar_id = h1Id,
                    creado_por = u2Id,
                    titulo = "Ruido y música alta después de las 23:00",
                    descripcion = "Los jueves y viernes suele haber música en la sala cuando otros intentan descansar.",
                    hablado = true,
                    solucionado = true,
                    acuerdo = "Usar auriculares con cancelación de ruido después de las 22:30 de domingo a jueves y 23:30 viernes/sábado."
                )
            ).toInt()
            db.problemDao().insertProblemUser(ProblemUserEntity(problema_id = pr1Id, usuario_id = u2Id))
            db.problemDao().insertProblemUser(ProblemUserEntity(problema_id = pr1Id, usuario_id = u3Id))

            db.problemDao().insertPointOfView(
                ProblemPointOfViewEntity(
                    problema_id = pr1Id,
                    usuario_id = u2Id,
                    contenido = "Me levanto a las 06:30 para trabajar y el volumen en la sala no me dejaba dormir."
                )
            )
            db.problemDao().insertPointOfView(
                ProblemPointOfViewEntity(
                    problema_id = pr1Id,
                    usuario_id = u3Id,
                    contenido = "Estaba editando vídeos para la universidad, pero entiendo totalmente. Usaré audífonos."
                )
            )

            val pr2Id = db.problemDao().insertProblem(
                ProblemEntity(
                    id = 2,
                    hogar_id = h1Id,
                    creado_por = u1Id,
                    titulo = "Turno de limpieza profunda del baño compartido",
                    descripcion = "Definir un calendario rotativo para evitar que recaiga siempre en la misma persona.",
                    hablado = true,
                    solucionado = false,
                    acuerdo = "Rotación semanal fija: Carlos semana 1, Sofía semana 2, David semana 3, Lucía semana 4."
                )
            ).toInt()
            db.problemDao().insertProblemUser(ProblemUserEntity(problema_id = pr2Id, usuario_id = u1Id))
            db.problemDao().insertProblemUser(ProblemUserEntity(problema_id = pr2Id, usuario_id = u2Id))
            db.problemDao().insertProblemUser(ProblemUserEntity(problema_id = pr2Id, usuario_id = u3Id))
            db.problemDao().insertProblemUser(ProblemUserEntity(problema_id = pr2Id, usuario_id = u4Id))

            // 8. Listas de Necesidades (Compras)
            val l1Id = db.needListDao().insertList(
                NeedListEntity(
                    id = 1,
                    hogar_id = h1Id,
                    creado_por = u1Id,
                    titulo = "Despensa Semanal & Supermercado",
                    estado = "activa"
                )
            ).toInt()

            db.needListDao().insertItem(
                NeedItemEntity(
                    lista_id = l1Id,
                    producto_nombre = "Leche deslactosada (Pack x6)",
                    cantidad = 2,
                    fecha_necesaria = todayStr,
                    comprador_id = u2Id,
                    estado = "pendiente",
                    notas = "Marca habitual"
                )
            )
            db.needListDao().insertItem(
                NeedItemEntity(
                    lista_id = l1Id,
                    producto_nombre = "Detergente para lavavajillas",
                    cantidad = 1,
                    fecha_necesaria = todayStr,
                    comprador_id = u1Id,
                    estado = "comprado",
                    notas = "Comprado en oferta"
                )
            )
            db.needListDao().insertItem(
                NeedItemEntity(
                    lista_id = l1Id,
                    producto_nombre = "Bolsas para basura grandes",
                    cantidad = 3,
                    fecha_necesaria = todayStr,
                    comprador_id = u3Id,
                    estado = "pendiente"
                )
            )

            // 9. Objetivos Simples y Compuestos
            val g1Id = db.goalDao().insertGoal(
                GoalEntity(
                    id = 1,
                    hogar_id = h1Id,
                    creado_por = u1Id,
                    titulo = "Organizar la terraza para la primavera",
                    descripcion = "Limpiar plantas, pintar las jardineras y comprar luces solares.",
                    tipo = "compuesto",
                    completado = false,
                    fecha_limite_original = todayStr,
                    fecha_limite_actual = todayStr
                )
            ).toInt()

            db.goalDao().insertGoalPoint(
                GoalPointEntity(
                    objetivo_id = g1Id,
                    titulo = "Podar plantas y retirar hojas secas",
                    descripcion = "Tijeras de podar en el armario del patio",
                    asignado_a = u1Id,
                    completado = true,
                    fecha_completado = System.currentTimeMillis() - 86400000,
                    completado_por = u1Id,
                    orden = 1
                )
            )
            db.goalDao().insertGoalPoint(
                GoalPointEntity(
                    objetivo_id = g1Id,
                    titulo = "Lijar y barnizar las mesas de madera",
                    descripcion = "Comprar barniz mate impermeable",
                    asignado_a = u3Id,
                    completado = false,
                    orden = 2
                )
            )
            db.goalDao().insertGoalPoint(
                GoalPointEntity(
                    objetivo_id = g1Id,
                    titulo = "Instalar guirnalda de luces solares",
                    descripcion = "Colocar a lo largo del pasamanos",
                    asignado_a = u2Id,
                    completado = false,
                    orden = 3
                )
            )

            // Objetivo simple
            db.goalDao().insertGoal(
                GoalEntity(
                    id = 2,
                    hogar_id = h1Id,
                    creado_por = u2Id,
                    titulo = "Pagar el seguro de la vivienda anual",
                    descripcion = "Revisar póliza con la compañía y transferir cuota",
                    tipo = "simple",
                    completado = true,
                    fecha_limite_original = todayStr,
                    fecha_limite_actual = todayStr,
                    fecha_completado = System.currentTimeMillis() - 86400000 * 2
                )
            )
        }
    }
}
