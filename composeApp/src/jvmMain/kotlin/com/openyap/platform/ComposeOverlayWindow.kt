package com.openyap.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.openyap.ui.component.RecordingOverlay
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo

@Composable
fun ComposeOverlayWindow(uiState: OverlayUiState) {
    if (!uiState.visible) return

    val dialogState = rememberDialogState(
        width = Dp.Unspecified,
        height = Dp.Unspecified,
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
        var lastMeasuredSize by remember { mutableStateOf(IntSize.Zero) }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier.onGloballyPositioned { coordinates ->
                try {
                    val size = coordinates.size
                    if (size.width <= 0 || size.height <= 0 || size == lastMeasuredSize) {
                        return@onGloballyPositioned
                    }
                    lastMeasuredSize = size

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
                        screen.y + screen.height - h - 40,
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
