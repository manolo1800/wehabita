package com.example.data.remote.dto

import com.squareup.moshi.JsonClass

// 1. Generic Envelopes
@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T,
    val timestamp: String? = null,
    val message: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageData(
    val message: String = ""
)

@JsonClass(generateAdapter = true)
data class HealthData(
    val status: String = "ok",
    val service: String? = null,
    val timestamp: String? = null,
    val uptime: Double? = null
)

// 2. Auth DTOs
@JsonClass(generateAdapter = true)
data class LoginRequest(
    val identifier: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val nombre: String,
    val email: String,
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@JsonClass(generateAdapter = true)
data class AuthData(
    val user: UserProfileDto,
    val access_token: String
)

@JsonClass(generateAdapter = true)
data class UserSummaryDto(
    val id: Int = 0,
    val nombre: String = "",
    val username: String = "",
    val codigo_usuario: String? = null,
    val email: String? = null,
    val activo: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    val id: Int = 0,
    val nombre: String = "",
    val email: String = "",
    val username: String = "",
    val codigo_usuario: String = "",
    val rol_global: String = "usuario",
    val activo: Boolean = true,
    val fecha_registro: String? = null,
    val hogares: List<HogarMembershipSummaryDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class HogarMembershipSummaryDto(
    val miembro_id: Int = 0,
    val hogar_id: Int = 0,
    val hogar_nombre: String = "",
    val tipo_hogar: String = "familia",
    val codigo_invitacion: String = "",
    val rol: String = "miembro",
    val fecha_union: String? = null,
    val privilegios: List<String> = emptyList()
)

// 3. Hogares DTOs
@JsonClass(generateAdapter = true)
data class CreateHomeRequest(
    val nombre: String,
    val tipo_hogar: String = "familia"
)

@JsonClass(generateAdapter = true)
data class UpdateHomeRequest(
    val nombre: String? = null,
    val tipo_hogar: String? = null
)

@JsonClass(generateAdapter = true)
data class HogarDetailDto(
    val id: Int = 0,
    val nombre: String = "",
    val tipo_hogar: String = "familia",
    val codigo_invitacion: String = "",
    val fecha_creacion: String? = null,
    val creador: UserSummaryDto? = null,
    val total_miembros: Int = 0,
    val miembros: List<MiembroDetailDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MiembroDetailDto(
    val id: Int = 0,
    val usuario_id: Int = 0,
    val nombre: String = "",
    val username: String = "",
    val email: String = "",
    val codigo_usuario: String = "",
    val rol: String = "miembro",
    val fecha_union: String? = null,
    val privilegios: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class UpdateMemberRoleRequest(
    val rol: String
)

@JsonClass(generateAdapter = true)
data class UpdateMemberPrivilegesRequest(
    val privilegios: List<String>
)

// 4. Invitaciones DTOs
@JsonClass(generateAdapter = true)
data class CreateInvitationRequest(
    val email_destino: String? = null,
    val codigo_usuario_destino: String? = null,
    val fecha_expiracion: String? = null
)

@JsonClass(generateAdapter = true)
data class JoinHomeRequest(
    val codigo: String
)

@JsonClass(generateAdapter = true)
data class JoinHomeResponseData(
    val message: String? = null,
    val hogar_id: Int
)

@JsonClass(generateAdapter = true)
data class InvitacionItemDto(
    val id: Int,
    val codigo: String,
    val email_destino: String? = null,
    val usuario_destino: UserSummaryDto? = null,
    val creado_por: UserSummaryDto? = null,
    val fecha_creacion: String? = null,
    val fecha_expiracion: String? = null,
    val estado: String = "pendiente"
)

// 5. Privilegios DTOs
@JsonClass(generateAdapter = true)
data class PrivilegioDto(
    val id: Int,
    val nombre: String,
    val descripcion: String? = null
)

// 6. Tareas DTOs
@JsonClass(generateAdapter = true)
data class ScheduleDto(
    val id: Int? = null,
    val hora_inicio: String,
    val hora_fin: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateTaskRequest(
    val titulo: String,
    val descripcion: String? = null,
    val tipo: String = "unica",
    val fecha_inicio: String,
    val fecha_fin: String? = null,
    val horarios: List<ScheduleDto>? = null,
    val asignados_ids: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateTaskRequest(
    val titulo: String? = null,
    val descripcion: String? = null,
    val tipo: String? = null,
    val fecha_inicio: String? = null,
    val fecha_fin: String? = null,
    val activa: Boolean? = null,
    val horarios: List<ScheduleDto>? = null,
    val asignados_ids: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class TareaDetailDto(
    val id: Int = 0,
    val hogar_id: Int = 0,
    val creado_por: Int? = null,
    val titulo: String = "",
    val descripcion: String? = null,
    val tipo: String = "unica",
    val fecha_inicio: String = "",
    val fecha_fin: String? = null,
    val activa: Boolean = true,
    val fecha_creacion: String? = null,
    val creador: UserSummaryDto? = null,
    val horarios: List<ScheduleDto> = emptyList(),
    val asignados: List<UserSummaryDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OcurrenciaDetailDto(
    val id: Int = 0,
    val tarea_id: Int = 0,
    val titulo: String = "",
    val descripcion: String? = null,
    val tipo: String = "unica",
    val fecha: String = "",
    val hora_inicio: String = "08:00",
    val hora_fin: String? = null,
    val completada: Boolean = false,
    val fecha_completada: String? = null,
    val notas: String? = null,
    val completada_por: UserSummaryDto? = null,
    val asignados: List<UserSummaryDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CompleteOccurrenceRequest(
    val completada: Boolean = true,
    val notas: String? = null
)

// 7. Penalizaciones DTOs
@JsonClass(generateAdapter = true)
data class CreatePenaltyRequest(
    val titulo: String,
    val descripcion_motivo: String,
    val penalizacion: String,
    val fecha_inicio: String,
    val fecha_fin: String? = null,
    val usuarios_ids: List<Int> = emptyList(),
    val estado: String = "activa"
)

@JsonClass(generateAdapter = true)
data class UpdatePenaltyRequest(
    val titulo: String? = null,
    val descripcion_motivo: String? = null,
    val penalizacion: String? = null,
    val fecha_inicio: String? = null,
    val fecha_fin: String? = null,
    val estado: String? = null,
    val usuarios_ids: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class UpdatePenaltyStatusRequest(
    val estado: String
)

@JsonClass(generateAdapter = true)
data class PenalizacionDetailDto(
    val id: Int = 0,
    val hogar_id: Int = 0,
    val creado_por: Int? = null,
    val titulo: String = "",
    val descripcion_motivo: String = "",
    val penalizacion: String = "",
    val fecha_inicio: String = "",
    val fecha_fin: String? = null,
    val fecha_creacion: String? = null,
    val estado: String = "activa",
    val creador: UserSummaryDto? = null,
    val usuarios_penalizados: List<UserSummaryDto> = emptyList()
)

// 8. Problemas DTOs
@JsonClass(generateAdapter = true)
data class CreateProblemRequest(
    val titulo: String,
    val descripcion: String,
    val usuarios_involucrados_ids: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateProblemRequest(
    val titulo: String? = null,
    val descripcion: String? = null,
    val usuarios_involucrados_ids: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class AddPointOfViewRequest(
    val contenido: String
)

@JsonClass(generateAdapter = true)
data class UpdateProblemStatusRequest(
    val hablado: Boolean? = null,
    val solucionado: Boolean? = null,
    val acuerdo: String? = null
)

@JsonClass(generateAdapter = true)
data class ProblemPointOfViewDto(
    val id: Int = 0,
    val usuario_id: Int = 0,
    val usuario: UserSummaryDto? = null,
    val contenido: String = "",
    val fecha_creacion: String? = null
)

@JsonClass(generateAdapter = true)
data class ProblemaDetailDto(
    val id: Int = 0,
    val hogar_id: Int = 0,
    val creado_por: Int? = null,
    val titulo: String = "",
    val descripcion: String = "",
    val hablado: Boolean = false,
    val solucionado: Boolean = false,
    val acuerdo: String? = null,
    val fecha_creacion: String? = null,
    val creador: UserSummaryDto? = null,
    val usuarios_involucrados: List<UserSummaryDto> = emptyList(),
    val puntos_vista: List<ProblemPointOfViewDto> = emptyList()
)

// 9. Necesidades DTOs
@JsonClass(generateAdapter = true)
data class CreateNeedListRequest(
    val titulo: String
)

@JsonClass(generateAdapter = true)
data class UpdateNeedListRequest(
    val titulo: String? = null,
    val estado: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateNeedItemRequest(
    val producto_nombre: String,
    val cantidad: Int = 1,
    val fecha_necesaria: String,
    val comprador_id: Int? = null,
    val notas: String? = null,
    val estado: String = "pendiente"
)

@JsonClass(generateAdapter = true)
data class UpdateNeedItemRequest(
    val producto_nombre: String? = null,
    val cantidad: Int? = null,
    val fecha_necesaria: String? = null,
    val comprador_id: Int? = null,
    val estado: String? = null,
    val notas: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateItemStatusRequest(
    val estado: String
)

@JsonClass(generateAdapter = true)
data class ItemDetailDto(
    val id: Int = 0,
    val producto_nombre: String = "",
    val cantidad: Int = 1,
    val fecha_necesaria: String = "",
    val estado: String = "pendiente",
    val notas: String? = null,
    val comprador: UserSummaryDto? = null
)

@JsonClass(generateAdapter = true)
data class ListaDetailDto(
    val id: Int = 0,
    val hogar_id: Int = 0,
    val creado_por: Int? = null,
    val titulo: String = "",
    val estado: String = "activa",
    val fecha_creacion: String? = null,
    val creador: UserSummaryDto? = null,
    val items: List<ItemDetailDto> = emptyList()
)

// 10. Objetivos DTOs
@JsonClass(generateAdapter = true)
data class GoalPointInputDto(
    val titulo: String,
    val descripcion: String? = null,
    val asignado_a: Int? = null,
    val orden: Int? = 0
)

@JsonClass(generateAdapter = true)
data class CreateGoalRequest(
    val titulo: String,
    val descripcion: String? = null,
    val tipo: String = "simple",
    val fecha_limite: String,
    val puntos: List<GoalPointInputDto>? = null
)

@JsonClass(generateAdapter = true)
data class UpdateGoalRequest(
    val titulo: String? = null,
    val descripcion: String? = null,
    val tipo: String? = null,
    val completado: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class PostponeGoalRequest(
    val nueva_fecha_limite: String
)

@JsonClass(generateAdapter = true)
data class CompleteGoalRequest(
    val completado: Boolean
)

@JsonClass(generateAdapter = true)
data class GoalPointDetailDto(
    val id: Int = 0,
    val titulo: String = "",
    val descripcion: String? = null,
    val orden: Int = 0,
    val completado: Boolean = false,
    val fecha_completado: String? = null,
    val asignado: UserSummaryDto? = null,
    val completado_por: UserSummaryDto? = null
)

@JsonClass(generateAdapter = true)
data class ObjetivoDetailDto(
    val id: Int = 0,
    val hogar_id: Int = 0,
    val creado_por: Int? = null,
    val titulo: String = "",
    val descripcion: String? = null,
    val tipo: String = "simple",
    val completado: Boolean = false,
    val fecha_creacion: String? = null,
    val fecha_limite_original: String = "",
    val fecha_limite_actual: String = "",
    val fecha_completado: String? = null,
    val fue_aplazado: Boolean = false,
    val progreso_porcentaje: Int = 0,
    val creador: UserSummaryDto? = null,
    val puntos: List<GoalPointDetailDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CreateGoalPointRequest(
    val titulo: String,
    val descripcion: String? = null,
    val asignado_a: Int? = null,
    val orden: Int = 0
)
