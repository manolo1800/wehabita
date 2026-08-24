package com.example.data.remote.sse

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SseEventEnvelope(
    val eventType: String,
    val homeId: Int = 0,
    val timestamp: String = "",
    val data: JsonElement? = null
)

sealed interface HomeRealtimeEvent {
    data class TaskEvent(val type: String, val payload: JsonElement?) : HomeRealtimeEvent
    data class NeedEvent(val type: String, val payload: JsonElement?) : HomeRealtimeEvent
    data class ProblemEvent(val type: String, val payload: JsonElement?) : HomeRealtimeEvent
    data class GoalEvent(val type: String, val payload: JsonElement?) : HomeRealtimeEvent
    data class PenaltyEvent(val type: String, val payload: JsonElement?) : HomeRealtimeEvent
    data class UnknownEvent(val type: String, val raw: String) : HomeRealtimeEvent
}

enum class SseConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
