package com.example.data.remote.api

import com.example.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface WeHabitaApiService {

    // --- Health ---
    @GET("health")
    suspend fun checkHealth(): Response<ApiResponse<HealthData>>

    // --- Auth ---
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthData>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthData>>

    @GET("auth/me")
    suspend fun getMe(): Response<ApiResponse<UserProfileDto>>

    @PUT("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<MessageData>>

    // --- Usuarios ---
    @GET("usuarios/me")
    suspend fun getUserProfile(): Response<ApiResponse<UserProfileDto>>

    @GET("usuarios/search")
    suspend fun searchUsers(@Query("q") query: String): Response<ApiResponse<List<UserSummaryDto>>>

    @GET("usuarios/codigo/{codigo}")
    suspend fun getUserByCode(@Path("codigo") code: String): Response<ApiResponse<UserSummaryDto>>

    @GET("usuarios/{id}")
    suspend fun getUserById(@Path("id") id: Int): Response<ApiResponse<UserProfileDto>>

    // --- Hogares ---
    @POST("hogares")
    suspend fun createHome(@Body request: CreateHomeRequest): Response<ApiResponse<HogarDetailDto>>

    @GET("hogares")
    suspend fun getMyHomes(): Response<ApiResponse<List<HogarMembershipSummaryDto>>>

    @GET("hogares/{id}")
    suspend fun getHomeDetail(@Path("id") homeId: Int): Response<ApiResponse<HogarDetailDto>>

    @PUT("hogares/{id}")
    suspend fun updateHome(@Path("id") homeId: Int, @Body request: UpdateHomeRequest): Response<ApiResponse<HogarDetailDto>>

    @DELETE("hogares/{id}")
    suspend fun deleteHome(@Path("id") homeId: Int): Response<ApiResponse<MessageData>>

    @GET("hogares/{id}/miembros")
    suspend fun getHomeMembers(@Path("id") homeId: Int): Response<ApiResponse<List<MiembroDetailDto>>>

    @PUT("hogares/{id}/miembros/{miembroId}/rol")
    suspend fun updateMemberRole(
        @Path("id") homeId: Int,
        @Path("miembroId") memberId: Int,
        @Body request: UpdateMemberRoleRequest
    ): Response<ApiResponse<Any>>

    @PUT("hogares/{id}/miembros/{miembroId}/privilegios")
    suspend fun updateMemberPrivileges(
        @Path("id") homeId: Int,
        @Path("miembroId") memberId: Int,
        @Body request: UpdateMemberPrivilegesRequest
    ): Response<ApiResponse<Any>>

    @DELETE("hogares/{id}/miembros/{miembroId}")
    suspend fun removeMember(
        @Path("id") homeId: Int,
        @Path("miembroId") memberId: Int
    ): Response<ApiResponse<MessageData>>

    @POST("hogares/{id}/salir")
    suspend fun leaveHome(@Path("id") homeId: Int): Response<ApiResponse<MessageData>>

    // --- Invitaciones ---
    @POST("hogares/{hogarId}/invitaciones")
    suspend fun createInvitation(
        @Path("hogarId") homeId: Int,
        @Body request: CreateInvitationRequest
    ): Response<ApiResponse<Any>>

    @GET("hogares/{hogarId}/invitaciones")
    suspend fun getInvitationsForHome(@Path("hogarId") homeId: Int): Response<ApiResponse<List<InvitacionItemDto>>>

    @POST("invitaciones/unirse")
    suspend fun joinHome(@Body request: JoinHomeRequest): Response<ApiResponse<JoinHomeResponseData>>

    // --- Privilegios ---
    @GET("privilegios")
    suspend fun getPrivileges(): Response<ApiResponse<List<PrivilegioDto>>>

    // --- Tareas ---
    @POST("hogares/{hogarId}/tareas")
    suspend fun createTask(
        @Path("hogarId") homeId: Int,
        @Body request: CreateTaskRequest
    ): Response<ApiResponse<TareaDetailDto>>

    @GET("hogares/{hogarId}/tareas")
    suspend fun getTasks(
        @Path("hogarId") homeId: Int,
        @Query("activa") active: Boolean? = null,
        @Query("tipo") type: String? = null
    ): Response<ApiResponse<List<TareaDetailDto>>>

    @GET("hogares/{hogarId}/tareas/{id}")
    suspend fun getTask(
        @Path("hogarId") homeId: Int,
        @Path("id") taskId: Int
    ): Response<ApiResponse<TareaDetailDto>>

    @PUT("hogares/{hogarId}/tareas/{id}")
    suspend fun updateTask(
        @Path("hogarId") homeId: Int,
        @Path("id") taskId: Int,
        @Body request: UpdateTaskRequest
    ): Response<ApiResponse<TareaDetailDto>>

    @DELETE("hogares/{hogarId}/tareas/{id}")
    suspend fun deleteTask(
        @Path("hogarId") homeId: Int,
        @Path("id") taskId: Int
    ): Response<ApiResponse<MessageData>>

    // --- Ocurrencias de Tareas ---
    @GET("hogares/{hogarId}/tareas-ocurrencias")
    suspend fun getTaskOccurrences(
        @Path("hogarId") homeId: Int,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("asignado_a") asignadoA: Int? = null,
        @Query("tarea_id") tareaId: Int? = null,
        @Query("completada") completada: Boolean? = null
    ): Response<ApiResponse<List<OcurrenciaDetailDto>>>

    @PATCH("hogares/{hogarId}/tareas-ocurrencias/{ocurrenciaId}/completar")
    suspend fun completeOccurrence(
        @Path("hogarId") homeId: Int,
        @Path("ocurrenciaId") occurrenceId: Int,
        @Body request: CompleteOccurrenceRequest
    ): Response<ApiResponse<Any>>

    // --- Penalizaciones ---
    @POST("hogares/{hogarId}/penalizaciones")
    suspend fun createPenalty(
        @Path("hogarId") homeId: Int,
        @Body request: CreatePenaltyRequest
    ): Response<ApiResponse<PenalizacionDetailDto>>

    @GET("hogares/{hogarId}/penalizaciones")
    suspend fun getPenalties(
        @Path("hogarId") homeId: Int,
        @Query("estado") estado: String? = null,
        @Query("usuario_id") usuarioId: Int? = null
    ): Response<ApiResponse<List<PenalizacionDetailDto>>>

    @GET("hogares/{hogarId}/penalizaciones/{id}")
    suspend fun getPenalty(
        @Path("hogarId") homeId: Int,
        @Path("id") penaltyId: Int
    ): Response<ApiResponse<PenalizacionDetailDto>>

    @PUT("hogares/{hogarId}/penalizaciones/{id}")
    suspend fun updatePenalty(
        @Path("hogarId") homeId: Int,
        @Path("id") penaltyId: Int,
        @Body request: UpdatePenaltyRequest
    ): Response<ApiResponse<PenalizacionDetailDto>>

    @PATCH("hogares/{hogarId}/penalizaciones/{id}/estado")
    suspend fun updatePenaltyStatus(
        @Path("hogarId") homeId: Int,
        @Path("id") penaltyId: Int,
        @Body request: UpdatePenaltyStatusRequest
    ): Response<ApiResponse<Any>>

    @DELETE("hogares/{hogarId}/penalizaciones/{id}")
    suspend fun deletePenalty(
        @Path("hogarId") homeId: Int,
        @Path("id") penaltyId: Int
    ): Response<ApiResponse<MessageData>>

    // --- Problemas ---
    @POST("hogares/{hogarId}/problemas")
    suspend fun createProblem(
        @Path("hogarId") homeId: Int,
        @Body request: CreateProblemRequest
    ): Response<ApiResponse<ProblemaDetailDto>>

    @GET("hogares/{hogarId}/problemas")
    suspend fun getProblems(
        @Path("hogarId") homeId: Int,
        @Query("hablado") hablado: Boolean? = null,
        @Query("solucionado") solucionado: Boolean? = null
    ): Response<ApiResponse<List<ProblemaDetailDto>>>

    @GET("hogares/{hogarId}/problemas/{id}")
    suspend fun getProblem(
        @Path("hogarId") homeId: Int,
        @Path("id") problemId: Int
    ): Response<ApiResponse<ProblemaDetailDto>>

    @PUT("hogares/{hogarId}/problemas/{id}")
    suspend fun updateProblem(
        @Path("hogarId") homeId: Int,
        @Path("id") problemId: Int,
        @Body request: UpdateProblemRequest
    ): Response<ApiResponse<ProblemaDetailDto>>

    @POST("hogares/{hogarId}/problemas/{id}/puntos-vista")
    suspend fun addPointOfView(
        @Path("hogarId") homeId: Int,
        @Path("id") problemId: Int,
        @Body request: AddPointOfViewRequest
    ): Response<ApiResponse<Any>>

    @PATCH("hogares/{hogarId}/problemas/{id}/estado")
    suspend fun updateProblemStatus(
        @Path("hogarId") homeId: Int,
        @Path("id") problemId: Int,
        @Body request: UpdateProblemStatusRequest
    ): Response<ApiResponse<ProblemaDetailDto>>

    @DELETE("hogares/{hogarId}/problemas/{id}")
    suspend fun deleteProblem(
        @Path("hogarId") homeId: Int,
        @Path("id") problemId: Int
    ): Response<ApiResponse<MessageData>>

    // --- Necesidades ---
    @POST("hogares/{hogarId}/necesidades")
    suspend fun createNeedList(
        @Path("hogarId") homeId: Int,
        @Body request: CreateNeedListRequest
    ): Response<ApiResponse<ListaDetailDto>>

    @GET("hogares/{hogarId}/necesidades")
    suspend fun getNeedLists(
        @Path("hogarId") homeId: Int,
        @Query("estado") estado: String? = null
    ): Response<ApiResponse<List<ListaDetailDto>>>

    @GET("hogares/{hogarId}/necesidades/{id}")
    suspend fun getNeedList(
        @Path("hogarId") homeId: Int,
        @Path("id") listId: Int
    ): Response<ApiResponse<ListaDetailDto>>

    @PUT("hogares/{hogarId}/necesidades/{id}")
    suspend fun updateNeedList(
        @Path("hogarId") homeId: Int,
        @Path("id") listId: Int,
        @Body request: UpdateNeedListRequest
    ): Response<ApiResponse<ListaDetailDto>>

    @DELETE("hogares/{hogarId}/necesidades/{id}")
    suspend fun deleteNeedList(
        @Path("hogarId") homeId: Int,
        @Path("id") listId: Int
    ): Response<ApiResponse<MessageData>>

    @POST("hogares/{hogarId}/necesidades/{listaId}/items")
    suspend fun addNeedItem(
        @Path("hogarId") homeId: Int,
        @Path("listaId") listId: Int,
        @Body request: CreateNeedItemRequest
    ): Response<ApiResponse<ItemDetailDto>>

    @PUT("hogares/{hogarId}/necesidades/items/{itemId}")
    suspend fun updateNeedItem(
        @Path("hogarId") homeId: Int,
        @Path("itemId") itemId: Int,
        @Body request: UpdateNeedItemRequest
    ): Response<ApiResponse<ItemDetailDto>>

    @PATCH("hogares/{hogarId}/necesidades/items/{itemId}/estado")
    suspend fun updateNeedItemStatus(
        @Path("hogarId") homeId: Int,
        @Path("itemId") itemId: Int,
        @Body request: UpdateItemStatusRequest
    ): Response<ApiResponse<Any>>

    @DELETE("hogares/{hogarId}/necesidades/items/{itemId}")
    suspend fun deleteNeedItem(
        @Path("hogarId") homeId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<MessageData>>

    // --- Objetivos ---
    @POST("hogares/{hogarId}/objetivos")
    suspend fun createGoal(
        @Path("hogarId") homeId: Int,
        @Body request: CreateGoalRequest
    ): Response<ApiResponse<ObjetivoDetailDto>>

    @GET("hogares/{hogarId}/objetivos")
    suspend fun getGoals(
        @Path("hogarId") homeId: Int,
        @Query("tipo") tipo: String? = null,
        @Query("completado") completado: Boolean? = null
    ): Response<ApiResponse<List<ObjetivoDetailDto>>>

    @GET("hogares/{hogarId}/objetivos/{id}")
    suspend fun getGoal(
        @Path("hogarId") homeId: Int,
        @Path("id") goalId: Int
    ): Response<ApiResponse<ObjetivoDetailDto>>

    @PUT("hogares/{hogarId}/objetivos/{id}")
    suspend fun updateGoal(
        @Path("hogarId") homeId: Int,
        @Path("id") goalId: Int,
        @Body request: UpdateGoalRequest
    ): Response<ApiResponse<ObjetivoDetailDto>>

    @PATCH("hogares/{hogarId}/objetivos/{id}/aplazar")
    suspend fun postponeGoal(
        @Path("hogarId") homeId: Int,
        @Path("id") goalId: Int,
        @Body request: PostponeGoalRequest
    ): Response<ApiResponse<Any>>

    @PATCH("hogares/{hogarId}/objetivos/{id}/completar")
    suspend fun completeGoal(
        @Path("hogarId") homeId: Int,
        @Path("id") goalId: Int,
        @Body request: CompleteGoalRequest
    ): Response<ApiResponse<ObjetivoDetailDto>>

    @POST("hogares/{hogarId}/objetivos/{id}/puntos")
    suspend fun addGoalPoint(
        @Path("hogarId") homeId: Int,
        @Path("id") goalId: Int,
        @Body request: CreateGoalPointRequest
    ): Response<ApiResponse<ObjetivoDetailDto>>

    @PATCH("hogares/{hogarId}/objetivos/puntos/{puntoId}/completar")
    suspend fun completeGoalPoint(
        @Path("hogarId") homeId: Int,
        @Path("puntoId") pointId: Int,
        @Body request: CompleteGoalRequest
    ): Response<ApiResponse<ObjetivoDetailDto>>

    @DELETE("hogares/{hogarId}/objetivos/puntos/{puntoId}")
    suspend fun deleteGoalPoint(
        @Path("hogarId") homeId: Int,
        @Path("puntoId") pointId: Int
    ): Response<ApiResponse<ObjetivoDetailDto>>

    @DELETE("hogares/{hogarId}/objetivos/{id}")
    suspend fun deleteGoal(
        @Path("hogarId") homeId: Int,
        @Path("id") goalId: Int
    ): Response<ApiResponse<MessageData>>
}
