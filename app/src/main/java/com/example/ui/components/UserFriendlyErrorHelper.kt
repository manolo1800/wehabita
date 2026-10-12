package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class PopupMessageType {
    ERROR,
    WARNING,
    INFO,
    SUCCESS
}

data class PopupMessage(
    val title: String,
    val description: String,
    val type: PopupMessageType = PopupMessageType.ERROR,
    val icon: ImageVector = Icons.Outlined.ErrorOutline,
    val buttonText: String = "Entendido"
)

object UserFriendlyErrorHelper {

    /**
     * Convierte cualquier mensaje técnico, código de error HTTP, excepción de red
     * o respuesta de backend en un mensaje comprensible, cálido y profesional para el usuario.
     */
    fun formatMessage(rawMessage: String?, defaultTitle: String = "Ha ocurrido un problema"): PopupMessage {
        if (rawMessage.isNullOrBlank()) {
            return PopupMessage(
                title = defaultTitle,
                description = "Ocurrió un imprevisto al procesar la solicitud. Por favor intenta de nuevo en unos instantes.",
                type = PopupMessageType.ERROR,
                icon = Icons.Outlined.ErrorOutline
            )
        }

        val lower = rawMessage.lowercase()

        // 1. Errores de Conexión / Red / Timeout / Servidor apagado o IP no alcanzable
        if (lower.contains("failed to connect") ||
            lower.contains("connectexception") ||
            lower.contains("econnrefused") ||
            lower.contains("sockettimeoutexception") ||
            lower.contains("timeout") ||
            lower.contains("timed out") ||
            lower.contains("unknownhostexception") ||
            lower.contains("unable to resolve host") ||
            lower.contains("no route to host") ||
            lower.contains("network is unreachable") ||
            lower.contains("ehostunreach") ||
            lower.contains("connection refused") ||
            lower.contains("192.168.") ||
            lower.contains("mayorealo.com") ||
            lower.contains("servidor rest") ||
            lower.contains("error de conexión")
        ) {
            return PopupMessage(
                title = "Sin Conexión con el Servidor",
                description = "No pudimos comunicarnos con el servidor en este momento. Revisa tu conexión a internet o verifica que el servidor esté activo.",
                type = PopupMessageType.WARNING,
                icon = Icons.Outlined.WifiOff,
                buttonText = "Aceptar"
            )
        }

        // 2. Errores de Credenciales / Autenticación / 401
        if (lower.contains("credenciales incorrectas") ||
            lower.contains("invalid credentials") ||
            lower.contains("unauthorized") ||
            lower.contains("401") ||
            lower.contains("contraseña incorrecta") ||
            lower.contains("usuario no encontrado") ||
            lower.contains("user not found") ||
            lower.contains("wrong password")
        ) {
            return PopupMessage(
                title = "Credenciales Incorrectas",
                description = "El usuario o la contraseña ingresados no coinciden con ninguna cuenta activa. Por favor verifica tus datos e inténtalo nuevamente.",
                type = PopupMessageType.ERROR,
                icon = Icons.Outlined.LockReset,
                buttonText = "Revisar Datos"
            )
        }

        // 3. Duplicados / Conflictos / 409
        if (lower.contains("409") ||
            lower.contains("conflict") ||
            lower.contains("ya está registrado") ||
            lower.contains("already exists") ||
            lower.contains("duplicate") ||
            lower.contains("duplicado") ||
            lower.contains("unique constraint")
        ) {
            return PopupMessage(
                title = "Datos ya Registrados",
                description = "Este nombre de usuario o correo electrónico ya se encuentra registrado. Por favor utiliza uno diferente.",
                type = PopupMessageType.WARNING,
                icon = Icons.Outlined.PersonOff,
                buttonText = "Modificar Datos"
            )
        }

        // 4. No Encontrado / 404
        if (lower.contains("404") ||
            lower.contains("not found") ||
            lower.contains("no encontrado") ||
            lower.contains("inválido") ||
            lower.contains("código de hogar")
        ) {
            return PopupMessage(
                title = "Elemento no Encontrado",
                description = "No fue posible encontrar el hogar, usuario o información solicitada. Verifica el código e intenta de nuevo.",
                type = PopupMessageType.INFO,
                icon = Icons.Outlined.SearchOff,
                buttonText = "Aceptar"
            )
        }

        // 5. Permisos / 403
        if (lower.contains("403") ||
            lower.contains("forbidden") ||
            lower.contains("permiso") ||
            lower.contains("no autorizado")
        ) {
            return PopupMessage(
                title = "Acceso Restringido",
                description = "No dispones de los permisos necesarios en este hogar para realizar esta acción.",
                type = PopupMessageType.WARNING,
                icon = Icons.Outlined.Shield,
                buttonText = "Entendido"
            )
        }

        // 6. Errores Internos del Servidor / 500 / 502 / 503
        if (lower.contains("500") ||
            lower.contains("502") ||
            lower.contains("503") ||
            lower.contains("504") ||
            lower.contains("internal server error") ||
            lower.contains("bad gateway")
        ) {
            return PopupMessage(
                title = "Servicio Temporalmente No Disponible",
                description = "El servidor ha tenido un inconveniente temporal. Por favor inténtalo de nuevo en unos minutos.",
                type = PopupMessageType.ERROR,
                icon = Icons.Outlined.Dns,
                buttonText = "Entendido"
            )
        }

        // 7. Éxito
        if (lower.contains("éxito") || lower.contains("exitosamente") || lower.contains("bienvenido")) {
            return PopupMessage(
                title = "¡Operación Exitosa!",
                description = rawMessage,
                type = PopupMessageType.SUCCESS,
                icon = Icons.Outlined.CheckCircle,
                buttonText = "Continuar"
            )
        }

        // 8. Mensaje genérico limpio (sin mostrar URLs, IPs ni JSON crudo)
        val cleanMsg = rawMessage
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("/\\d+\\.\\d+\\.\\d+\\.\\d+(:\\d+)?"), "")
            .replace(Regex("/?[\\w.-]*mayorealo\\.com(:\\d+)?"), "")
            .replace(Regex("\\{.*\\}"), "")
            .trim()

        return PopupMessage(
            title = defaultTitle,
            description = if (cleanMsg.length > 5) cleanMsg else "No fue posible completar la operación. Por favor revisa los datos e inténtalo de nuevo.",
            type = PopupMessageType.INFO,
            icon = Icons.Outlined.Info,
            buttonText = "Aceptar"
        )
    }
}
