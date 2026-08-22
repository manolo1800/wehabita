package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*
import com.example.data.repository.SyncLogCollector
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
        GoalPointEntity::class,
        SyncQueueEntity::class
    ],
    version = 4,
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
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        private const val TAG = "WeHabita:LocalDB"

        @Volatile
        private var INSTANCE: WeHabitaDatabase? = null

        fun getDatabase(context: Context): WeHabitaDatabase {
            return INSTANCE ?: synchronized(this) {
                SyncLogCollector.i(TAG, "Inicializando capa de persistencia local Room (SQLite)...")
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        WeHabitaDatabase::class.java,
                        "wehabita_db"
                    )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            SyncLogCollector.i(TAG, "Callback Room onCreate disparado. Base de datos creada en SQLite (limpia sin datos de muestra).")
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    INSTANCE?.let { seedDatabase(it) }
                                } catch (e: Throwable) {
                                    SyncLogCollector.e(TAG, "Error durante la inicialización de catálogo en Room", throwable = e)
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            SyncLogCollector.d(TAG, "Callback Room onOpen disparado. Base de datos abierta exitosamente. Versión: ${db.version}, Path: ${db.path}")
                            try {
                                val cursor = db.query("SELECT count(*) FROM sqlite_master WHERE type='table';")
                                if (cursor.moveToFirst()) {
                                    val count = cursor.getInt(0)
                                    SyncLogCollector.d(TAG, "Tablas SQLite locales verificadas: $count tablas registradas.")
                                }
                                cursor.close()
                            } catch (e: Throwable) {
                                SyncLogCollector.w(TAG, "No se pudo consultar conteo de tablas SQLite", throwable = e)
                            }
                        }

                        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                            super.onDestructiveMigration(db)
                            SyncLogCollector.w(TAG, "Se ejecutó migración destructiva en SQLite local. Base de datos reseteada a estado limpio.")
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    INSTANCE?.let { seedDatabase(it) }
                                } catch (e: Throwable) {
                                    SyncLogCollector.e(TAG, "Error durante la inicialización de catálogo tras migración", throwable = e)
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()

                    INSTANCE = instance
                    SyncLogCollector.s(TAG, "Instancia única de Room (wehabita_db) inicializada correctamente.")
                    instance
                } catch (e: Throwable) {
                    SyncLogCollector.e(TAG, "Fallo fatal al inicializar la base de datos Room", throwable = e)
                    throw e
                }
            }
        }

        private suspend fun seedDatabase(db: WeHabitaDatabase) {
            val startTime = System.currentTimeMillis()
            SyncLogCollector.i(TAG, "Inicializando catálogo de privilegios del sistema (sin datos de usuario de muestra)...")
            try {
                // Solo se registra el catálogo base de privilegios para permitir la asignación de permisos
                val privs = listOf(
                    "crear_tareas", "asignar_tareas", "marcar_tareas",
                    "crear_problemas", "crear_necesidades", "crear_objetivos",
                    "gestionar_usuarios", "aplicar_penalizaciones", "ver_reportes"
                )
                for (p in privs) {
                    db.privilegeDao().insertPrivilege(PrivilegeEntity(nombre = p))
                }

                val elapsed = System.currentTimeMillis() - startTime
                SyncLogCollector.s(TAG, "Inicialización de catálogo de privilegios completada en ${elapsed}ms. BD limpia para nuevos usuarios.")
            } catch (e: Throwable) {
                SyncLogCollector.e(TAG, "Fallo al inicializar catálogo de privilegios", throwable = e)
            }
        }
    }
}
