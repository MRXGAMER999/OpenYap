package com.openyap.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.openyap.ui.component.RecordingOverlay
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo

@Composable
fun ComposeOverlayWindow(uiState: OverlayUiState) {
    if (!uiState.visible) return

    val dialogState = rememberDialogState(width = 160.dp, height = 40.dp)

    DialogWindow(
        onCloseRequest = {},
        state = dialogState,
        visible = true,
        title = "",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
    ) {
        LaunchedEffect(Unit) {
            try {
                val pointerLocation = MouseInfo.getPointerInfo()?.location
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                val screen = if (pointerLocation != null) {
                    ge.screenDevices.firstOrNull { device ->
                        device.defaultConfiguration.bounds.contains(pointerLocation)
                    }?.defaultConfiguration?.bounds
                        ?: ge.defaultScreenDevice.defaultConfiguration.bounds
                } else {
                    ge.defaultScreenDevice.defaultConfiguration.bounds
                }

                window.pack()
                val w = window.width.coerceAtLeast(100)
                val h = window.height.coerceAtLeast(32)
                window.setLocation(
                    screen.x + (screen.width - w) / 2,
                    screen.y + screen.height - h - 80,
                )
            } catch (e: Exception) {
                System.err.println("Failed to position overlay window: ${e.message}")
                e.printStackTrace(System.err)
            }
        }

        RecordingOverlay(
            state = uiState.state,
            level = uiState.level,
            durationSeconds = uiState.durationSeconds,
            flashMessage = uiState.flashMessage,
        )
    }
}
