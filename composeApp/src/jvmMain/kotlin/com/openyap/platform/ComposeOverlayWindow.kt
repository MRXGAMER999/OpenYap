package com.openyap.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import com.openyap.ui.component.RecordingOverlay
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo

@Composable
fun ComposeOverlayWindow(uiState: OverlayUiState) {
    if (!uiState.visible) return

    val dialogState = rememberDialogState(
        width = androidx.compose.ui.unit.Dp.Unspecified,
        height = androidx.compose.ui.unit.Dp.Unspecified
    )

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
        val window = this.window
        
        // This effect runs whenever the layout size inside changes.
        // We capture the global composition size to recenter the window natively
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
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
                }
            }
        ) {
            RecordingOverlay(
                state = uiState.state,
                level = uiState.level,
                durationSeconds = uiState.durationSeconds,
                flashMessage = uiState.flashMessage,
            )
        }
    }
}
