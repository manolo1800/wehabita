package com.example.data.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogSeverity {
    DEBUG, INFO, SUCCESS, WARN, ERROR
}

data class DiagnosticLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val severity: LogSeverity,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    fun toFormattedString(): String {
        val base = "[$formattedTime] [${severity.name}] [$tag] $message"
        return if (details.isNullOrBlank()) base else "$base\nDetalles:\n$details"
    }
}

object SyncLogCollector {
    private const val MAX_LOGS = 250
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun log(severity: LogSeverity, tag: String, message: String, details: String? = null, throwable: Throwable? = null) {
        val fullDetails = when {
            details != null && throwable != null -> "$details\nExcepción: ${throwable.javaClass.name}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
            throwable != null -> "Excepción: ${throwable.javaClass.name}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
            else -> details
        }

        // Standard Android Logcat
        when (severity) {
            LogSeverity.DEBUG -> Log.d(tag, if (fullDetails != null) "$message | $fullDetails" else message)
            LogSeverity.INFO -> Log.i(tag, if (fullDetails != null) "$message | $fullDetails" else message)
            LogSeverity.SUCCESS -> Log.i(tag, "✅ $message ${fullDetails ?: ""}")
            LogSeverity.WARN -> Log.w(tag, "$message ${fullDetails ?: ""}")
            LogSeverity.ERROR -> {
                if (throwable != null) {
                    Log.e(tag, "$message | $fullDetails", throwable)
                } else {
                    Log.e(tag, "$message | ${fullDetails ?: ""}")
                }
            }
        }

        val entry = DiagnosticLogEntry(
            severity = severity,
            tag = tag,
            message = message,
            details = fullDetails
        )

        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_LOGS) {
            _logs.value = current.takeLast(MAX_LOGS)
        } else {
            _logs.value = current
        }
    }

    fun d(tag: String, message: String, details: String? = null) = log(LogSeverity.DEBUG, tag, message, details)
    fun i(tag: String, message: String, details: String? = null) = log(LogSeverity.INFO, tag, message, details)
    fun s(tag: String, message: String, details: String? = null) = log(LogSeverity.SUCCESS, tag, message, details)
    
    fun w(tag: String, message: String, details: String? = null, throwable: Throwable? = null) = log(LogSeverity.WARN, tag, message, details, throwable)
    fun w(tag: String, message: String, throwable: Throwable) = log(LogSeverity.WARN, tag, message, null, throwable)

    fun e(tag: String, message: String, details: String? = null, throwable: Throwable? = null) = log(LogSeverity.ERROR, tag, message, details, throwable)
    fun e(tag: String, message: String, throwable: Throwable) = log(LogSeverity.ERROR, tag, message, null, throwable)

    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllLogsAsText(): String {
        return _logs.value.joinToString("\n") { it.toFormattedString() }
    }
}
