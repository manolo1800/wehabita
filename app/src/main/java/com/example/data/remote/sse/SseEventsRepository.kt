package com.example.data.remote.sse

import com.example.data.repository.SyncLogCollector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class SseEventsRepository(
    private val baseUrlProvider: () -> String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
) {
    private val tag = "WeHabita:SSE"

    private val sseClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Requerido para mantener stream SSE vivo
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun subscribeToHome(
        hogarId: Int,
        jwtToken: String,
        onStatusChanged: ((SseConnectionStatus) -> Unit)? = null
    ): Flow<HomeRealtimeEvent> = callbackFlow {
        val base = baseUrlProvider().trim().trimEnd('/')
        val finalUrl = if (base.endsWith("/api")) {
            "$base/events/$hogarId"
        } else {
            "$base/api/events/$hogarId"
        }

        SyncLogCollector.i(tag, "Iniciando suscripción SSE al hogar #$hogarId en $finalUrl")
        onStatusChanged?.invoke(SseConnectionStatus.CONNECTING)

        val request = Request.Builder()
            .url(finalUrl)
            .header("Authorization", "Bearer $jwtToken")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                SyncLogCollector.s(tag, "Conexión SSE establecida con éxito con el hogar #$hogarId (HTTP ${response.code})")
                onStatusChanged?.invoke(SseConnectionStatus.CONNECTED)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                val rawType = type ?: "message"
                // Heartbeat / ping check
                if (rawType.equals("ping", ignoreCase = true) || data.trim() == "ping") {
                    SyncLogCollector.d(tag, "Heartbeat (ping) recibido del servidor SSE")
                    return
                }

                try {
                    val rootJson = json.parseToJsonElement(data).jsonObject
                    val eventType = rootJson["eventType"]?.jsonPrimitive?.content
                        ?: (if (rawType != "message") rawType else "unknown")
                    val homeId = rootJson["homeId"]?.jsonPrimitive?.content?.toIntOrNull() ?: hogarId
                    val timestamp = rootJson["timestamp"]?.jsonPrimitive?.content ?: ""
                    val eventData = rootJson["data"]

                    val envelope = SseEventEnvelope(
                        eventType = eventType,
                        homeId = homeId,
                        timestamp = timestamp,
                        data = eventData
                    )

                    SyncLogCollector.s(tag, "Evento en tiempo real recibido: [${envelope.eventType}] (Hogar: ${envelope.homeId})")

                    val domainEvent: HomeRealtimeEvent = when {
                        envelope.eventType.startsWith("tarea.") ||
                        envelope.eventType.startsWith("horario.") ||
                        envelope.eventType.startsWith("ocurrencia.") ||
                        envelope.eventType.contains("tarea") ||
                        envelope.eventType.contains("horario") ||
                        envelope.eventType.contains("schedule") ||
                        envelope.eventType.contains("occurrence") ->
                            HomeRealtimeEvent.TaskEvent(envelope.eventType, envelope.data)

                        envelope.eventType.startsWith("item.") ||
                        envelope.eventType.startsWith("lista.") ||
                        envelope.eventType.contains("necesidad") ||
                        envelope.eventType.contains("compra") ->
                            HomeRealtimeEvent.NeedEvent(envelope.eventType, envelope.data)

                        envelope.eventType.startsWith("problema.") ||
                        envelope.eventType.contains("problema") ||
                        envelope.eventType.contains("conflicto") ->
                            HomeRealtimeEvent.ProblemEvent(envelope.eventType, envelope.data)

                        envelope.eventType.startsWith("objetivo.") ||
                        envelope.eventType.startsWith("meta.") ||
                        envelope.eventType.contains("objetivo") ||
                        envelope.eventType.contains("meta") ->
                            HomeRealtimeEvent.GoalEvent(envelope.eventType, envelope.data)

                        envelope.eventType.startsWith("penalizacion.") ||
                        envelope.eventType.contains("penalizacion") ||
                        envelope.eventType.contains("sancion") ->
                            HomeRealtimeEvent.PenaltyEvent(envelope.eventType, envelope.data)

                        else ->
                            HomeRealtimeEvent.UnknownEvent(envelope.eventType, data)
                    }

                    trySend(domainEvent)
                } catch (e: Exception) {
                    SyncLogCollector.w(tag, "No se pudo parsear sobre JSON del evento SSE '$rawType': ${e.message}. Data: ${data.take(100)}")
                    trySend(HomeRealtimeEvent.UnknownEvent(rawType, data))
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                val msg = t?.message ?: response?.message ?: "Error desconocido"
                SyncLogCollector.e(tag, "Fallo en conexión SSE (HTTP $code): $msg")
                onStatusChanged?.invoke(SseConnectionStatus.ERROR)

                if (code == 401 || code == 403) {
                    SyncLogCollector.w(tag, "Fallo crítico de autenticación o permisos SSE (HTTP $code). No se fuerza reintento inmediato.")
                    close(Exception("SSE Auth Error: $code"))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                SyncLogCollector.i(tag, "Conexión SSE cerrada para el hogar #$hogarId")
                onStatusChanged?.invoke(SseConnectionStatus.DISCONNECTED)
                close()
            }
        }

        val eventSource = EventSources.createFactory(sseClient).newEventSource(request, listener)

        awaitClose {
            SyncLogCollector.i(tag, "Cancelando EventSource SSE del hogar #$hogarId")
            onStatusChanged?.invoke(SseConnectionStatus.DISCONNECTED)
            eventSource.cancel()
        }
    }
}
