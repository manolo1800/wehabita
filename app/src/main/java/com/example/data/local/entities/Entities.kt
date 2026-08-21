package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 1. Usuarios
@Entity(
    tableName = "usuarios",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["username"], unique = true),
        Index(value = ["codigo_usuario"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val email: String,
    val username: String,
    val password_hash: String,
    val codigo_usuario: String,
    val rol_global: String = "usuario", // 'admin' | 'usuario'
    val activo: Boolean = true,
    val fecha_registro: Long = System.currentTimeMillis()
)

// 2. Hogares
@Entity(
    tableName = "hogares",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["creado_por"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["codigo_invitacion"], unique = true),
        Index(value = ["creado_por"])
    ]
)
data class HomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val tipo_hogar: String = "familia", // 'soltero' | 'pareja' | 'familia'
    val codigo_invitacion: String,
    val creado_por: Int,
    val fecha_creacion: Long = System.currentTimeMillis()
)

// 3. Miembros de hogar
@Entity(
    tableName = "miembros_hogar",
    foreignKeys = [
        ForeignKey(
            entity = HomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["hogar_id", "usuario_id"], unique = true),
        Index(value = ["usuario_id"])
    ]
)
data class HomeMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hogar_id: Int,
    val usuario_id: Int,
    val rol: String = "miembro", // 'admin' | 'miembro'
    val activo: Boolean = true,
    val fecha_union: Long = System.currentTimeMillis()
)

// 4. Privilegios
@Entity(
    tableName = "privilegios",
    indices = [Index(value = ["nombre"], unique = true)]
)
data class PrivilegeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String
)

// 5. Miembro_privilegios
@Entity(
    tableName = "miembro_privilegios",
    primaryKeys = ["miembro_hogar_id", "privilegio_id"],
    foreignKeys = [
        ForeignKey(
            entity = HomeMemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["miembro_hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PrivilegeEntity::class,
            parentColumns = ["id"],
            childColumns = ["privilegio_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["privilegio_id"])]
)
data class MemberPrivilegeEntity(
    val miembro_hogar_id: Int,
    val privilegio_id: Int
)

// 6. Invitaciones
@Entity(
    tableName = "invitaciones",
    foreignKeys = [
        ForeignKey(
            entity = HomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_destino_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["creado_por"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["codigo"], unique = true),
        Index(value = ["hogar_id"]),
        Index(value = ["usuario_destino_id"]),
        Index(value = ["creado_por"])
    ]
)
data class InvitationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hogar_id: Int,
    val codigo: String,
    val email_destino: String? = null,
    val usuario_destino_id: Int? = null,
    val creado_por: Int,
    val estado: String = "pendiente", // 'pendiente' | 'aceptada' | 'rechazada' | 'expirada'
    val fecha_creacion: Long = System.currentTimeMillis(),
    val fecha_expiracion: String? = null
)

// 7. Tareas
@Entity(
    tableName = "tareas",
    foreignKeys = [
        ForeignKey(
            entity = HomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["creado_por"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["hogar_id"]),
        Index(value = ["creado_por"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hogar_id: Int,
    val creado_por: Int,
    val titulo: String,
    val descripcion: String? = null,
    val tipo: String = "unica", // 'unica' | 'fija'
    val fecha_inicio: String, // YYYY-MM-DD
    val fecha_fin: String? = null,
    val activa: Boolean = true,
    val fecha_creacion: Long = System.currentTimeMillis()
)

// 8. Tarea_horarios
@Entity(
    tableName = "tarea_horarios",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["tarea_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tarea_id"])]
)
data class TaskScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tarea_id: Int,
    val hora_inicio: String, // HH:MM
    val hora_fin: String? = null // HH:MM
)

// 9. Tarea_asignaciones
@Entity(
    tableName = "tarea_asignaciones",
    primaryKeys = ["tarea_id", "usuario_id"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["tarea_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["usuario_id"])]
)
data class TaskAssignmentEntity(
    val tarea_id: Int,
    val usuario_id: Int
)

// 10. Tarea_ocurrencias
@Entity(
    tableName = "tarea_ocurrencias",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["tarea_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["completada_por"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["tarea_id", "fecha", "hora_inicio"], unique = true),
        Index(value = ["fecha"]),
        Index(value = ["completada_por"])
    ]
)
data class TaskOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tarea_id: Int,
    val fecha: String, // YYYY-MM-DD
    val hora_inicio: String, // HH:MM
    val hora_fin: String? = null,
    val completada: Boolean = false,
    val completada_por: Int? = null,
    val fecha_completada: Long? = null,
    val notas: String? = null
)

// 11. Penalizaciones
@Entity(
    tableName = "penalizaciones",
    foreignKeys = [
        ForeignKey(
            entity = HomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["creado_por"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["hogar_id"]),
        Index(value = ["creado_por"])
    ]
)
data class PenaltyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hogar_id: Int,
    val creado_por: Int,
    val titulo: String,
    val descripcion_motivo: String,
    val penalizacion: String,
    val fecha_inicio: String, // YYYY-MM-DD
    val fecha_fin: String? = null,
    val fecha_creacion: Long = System.currentTimeMillis(),
    val estado: String = "activa" // 'activa' | 'cumplida' | 'cancelada'
)

// 12. Penalizacion_usuarios
@Entity(
    tableName = "penalizacion_usuarios",
    primaryKeys = ["penalizacion_id", "usuario_id"],
    foreignKeys = [
        ForeignKey(
            entity = PenaltyEntity::class,
            parentColumns = ["id"],
            childColumns = ["penalizacion_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["usuario_id"])]
)
data class PenaltyUserEntity(
    val penalizacion_id: Int,
    val usuario_id: Int
)

// 13. Problemas
@Entity(
    tableName = "problemas",
    foreignKeys = [
        ForeignKey(
            entity = HomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["creado_por"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["hogar_id"]),
        Index(value = ["creado_por"])
    ]
)
data class ProblemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hogar_id: Int,
    val creado_por: Int,
    val titulo: String,
    val descripcion: String,
    val hablado: Boolean = false,
    val solucionado: Boolean = false,
    val acuerdo: String? = null,
    val fecha_creacion: Long = System.currentTimeMillis()
)

// 14. Problema_usuarios
@Entity(
    tableName = "problema_usuarios",
    primaryKeys = ["problema_id", "usuario_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problema_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["usuario_id"])]
)
data class ProblemUserEntity(
    val problema_id: Int,
    val usuario_id: Int
)

// 15. Problema_puntos_vista
@Entity(
    tableName = "problema_puntos_vista",
    foreignKeys = [
        ForeignKey(
            entity = ProblemEntity::class,
            parentColumns = ["id"],
            childColumns = ["problema_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["problema_id"]),
        Index(value = ["usuario_id"])
    ]
)
data class ProblemPointOfViewEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val problema_id: Int,
    val usuario_id: Int,
    val contenido: String,
    val fecha_creacion: Long = System.currentTimeMillis()
)

// 16. Listas_necesidades
@Entity(
    tableName = "listas_necesidades",
    foreignKeys = [
        ForeignKey(
            entity = HomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["creado_por"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["hogar_id"]),
        Index(value = ["creado_por"])
    ]
)
data class NeedListEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hogar_id: Int,
    val creado_por: Int,
    val titulo: String,
    val fecha_creacion: Long = System.currentTimeMillis(),
    val estado: String = "activa" // 'activa' | 'completada' | 'cancelada'
)

// 17. Items_necesidad
@Entity(
    tableName = "items_necesidad",
    foreignKeys = [
        ForeignKey(
            entity = NeedListEntity::class,
            parentColumns = ["id"],
            childColumns = ["lista_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["comprador_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["lista_id"]),
        Index(value = ["comprador_id"])
    ]
)
data class NeedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val lista_id: Int,
    val producto_nombre: String,
    val cantidad: Int = 1,
    val fecha_necesaria: String, // YYYY-MM-DD
    val comprador_id: Int? = null,
    val estado: String = "pendiente", // 'pendiente' | 'comprado' | 'cancelado'
    val notas: String? = null
)

// 18. Objetivos
@Entity(
    tableName = "objetivos",
    foreignKeys = [
        ForeignKey(
            entity = HomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["hogar_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["creado_por"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["hogar_id"]),
        Index(value = ["creado_por"])
    ]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hogar_id: Int,
    val creado_por: Int,
    val titulo: String,
    val descripcion: String? = null,
    val tipo: String = "simple", // 'simple' | 'compuesto'
    val completado: Boolean = false,
    val fecha_creacion: Long = System.currentTimeMillis(),
    val fecha_limite_original: String, // YYYY-MM-DD
    val fecha_limite_actual: String, // YYYY-MM-DD
    val fecha_completado: Long? = null
)

// 19. Objetivo_puntos
@Entity(
    tableName = "objetivo_puntos",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["objetivo_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["asignado_a"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["completado_por"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["objetivo_id"]),
        Index(value = ["asignado_a"]),
        Index(value = ["completado_por"])
    ]
)
data class GoalPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val objetivo_id: Int,
    val titulo: String,
    val descripcion: String? = null,
    val asignado_a: Int? = null,
    val completado: Boolean = false,
    val fecha_completado: Long? = null,
    val completado_por: Int? = null,
    val orden: Int = 0
)
