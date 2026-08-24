package com.example.data.local.entities

// 1. Usuarios
data class UserEntity(
    val id: Int = 0,
    val nombre: String,
    val email: String,
    val username: String,
    val password_hash: String = "",
    val codigo_usuario: String,
    val rol_global: String = "usuario", // 'admin' | 'usuario'
    val activo: Boolean = true,
    val fecha_registro: Long = System.currentTimeMillis()
)

// 2. Hogares
data class HomeEntity(
    val id: Int = 0,
    val nombre: String,
    val tipo_hogar: String = "familia", // 'soltero' | 'pareja' | 'familia'
    val codigo_invitacion: String,
    val creado_por: Int,
    val fecha_creacion: Long = System.currentTimeMillis()
)

// 3. Miembros de hogar
data class HomeMemberEntity(
    val id: Int = 0,
    val hogar_id: Int,
    val usuario_id: Int,
    val rol: String = "miembro", // 'admin' | 'miembro'
    val activo: Boolean = true,
    val fecha_union: Long = System.currentTimeMillis()
)

// 4. Privilegios
data class PrivilegeEntity(
    val id: Int = 0,
    val nombre: String,
    val descripcion: String? = null
)

// 5. Miembro_privilegios
data class MemberPrivilegeEntity(
    val miembro_hogar_id: Int,
    val privilegio_id: Int
)

// 6. Invitaciones
data class InvitationEntity(
    val id: Int = 0,
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
data class TaskEntity(
    val id: Int = 0,
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
data class TaskScheduleEntity(
    val id: Int = 0,
    val tarea_id: Int,
    val hora_inicio: String, // HH:MM
    val hora_fin: String? = null // HH:MM
)

// 9. Tarea_asignaciones
data class TaskAssignmentEntity(
    val tarea_id: Int,
    val usuario_id: Int
)

// 10. Tarea_ocurrencias
data class TaskOccurrenceEntity(
    val id: Int = 0,
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
data class PenaltyEntity(
    val id: Int = 0,
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
data class PenaltyUserEntity(
    val penalizacion_id: Int,
    val usuario_id: Int
)

// 13. Problemas
data class ProblemEntity(
    val id: Int = 0,
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
data class ProblemUserEntity(
    val problema_id: Int,
    val usuario_id: Int
)

// 15. Problema_puntos_vista
data class ProblemPointOfViewEntity(
    val id: Int = 0,
    val problema_id: Int,
    val usuario_id: Int,
    val contenido: String,
    val fecha_creacion: Long = System.currentTimeMillis()
)

// 16. Listas_necesidades
data class NeedListEntity(
    val id: Int = 0,
    val hogar_id: Int,
    val creado_por: Int,
    val titulo: String,
    val fecha_creacion: Long = System.currentTimeMillis(),
    val estado: String = "activa" // 'activa' | 'completada' | 'cancelada'
)

// 17. Items_necesidad
data class NeedItemEntity(
    val id: Int = 0,
    val lista_id: Int,
    val producto_nombre: String,
    val cantidad: Int = 1,
    val fecha_necesaria: String, // YYYY-MM-DD
    val comprador_id: Int? = null,
    val estado: String = "pendiente", // 'pendiente' | 'comprado' | 'cancelado'
    val notas: String? = null
)

// 18. Objetivos
data class GoalEntity(
    val id: Int = 0,
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
data class GoalPointEntity(
    val id: Int = 0,
    val objetivo_id: Int,
    val titulo: String,
    val descripcion: String? = null,
    val asignado_a: Int? = null,
    val completado: Boolean = false,
    val fecha_completado: Long? = null,
    val completado_por: Int? = null,
    val orden: Int = 0
)

// 20. Cola de Sincronización
data class SyncQueueEntity(
    val id: Int = 0,
    val endpoint: String = "",
    val http_method: String = "POST",
    val payload_json: String? = null,
    val accion: String = "",
    val descripcion: String = "",
    val fecha_creacion: Long = System.currentTimeMillis(),
    val intentos: Int = 0,
    val ultimo_error: String? = null
)
