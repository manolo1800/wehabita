package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@Composable
fun AppMessagePopupDialog(
    popupMessage: PopupMessage?,
    onDismiss: () -> Unit
) {
    if (popupMessage == null) return

    val (iconColor, containerColor, buttonBgColor, buttonContentColor) = when (popupMessage.type) {
        PopupMessageType.ERROR -> Quadruple(
            ElegantPenaltiesRed,
            ElegantPenaltiesRed.copy(alpha = 0.12f),
            ElegantPenaltiesRed,
            Color.White
        )
        PopupMessageType.WARNING -> Quadruple(
            ElegantShoppingOrange,
            ElegantShoppingOrange.copy(alpha = 0.14f),
            ElegantShoppingOrange,
            Color.White
        )
        PopupMessageType.SUCCESS -> Quadruple(
            ElegantSuccessGreen,
            ElegantSuccessGreen.copy(alpha = 0.14f),
            ElegantSuccessGreen,
            Color.White
        )
        PopupMessageType.INFO -> Quadruple(
            SageGreenPrimary,
            SageGreenPrimary.copy(alpha = 0.14f),
            SageGreenPrimary,
            Color.White
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .testTag("app_message_popup_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close icon
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                            .testTag("popup_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Icon with gentle animated aura
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = popupMessage.icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title
                Text(
                    text = popupMessage.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Description
                Text(
                    text = popupMessage.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Button
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBgColor,
                        contentColor = buttonContentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("popup_action_button")
                ) {
                    Text(
                        text = popupMessage.buttonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
