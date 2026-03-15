package com.openyap.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.openyap.ui.component.RecordingOverlay
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import javax.swing.SwingUtilities

/**
 * Fixed-size transparent window that the overlay pill floats inside.
 *
 * Previous approach tried to resize the AWT window to match Compose content on
 * every frame, but that is inherently racy — the AWT resize and the Compose
 * layout/draw cycle can never be perfectly synchronised, resulting in clipped
 * content when the state changes (e.g. RECORDING → PROCESSING). Because the
 * window is fully transparent the extra space is invisible, so we simply make
 * the window large enough for every possible pill state and centre the content
 * at the bottom.
 */
private val OVERLAY_WIDTH = 300.dp
private val OVERLAY_HEIGHT = 56.dp

@Composable
fun ComposeOverlayWindow(uiState: OverlayUiState) {
    if (!uiState.visible) return

    val dialogState = rememberDialogState(
        width = OVERLAY_WIDTH,
        height = OVERLAY_HEIGHT,
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
        // Position once on first composition.
        val window = this.window
        LaunchedEffect(Unit) {
            SwingUtilities.invokeLater {
                try {
                    val pointerLocation = MouseInfo.getPointerInfo()?.location
                    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    val gd = if (pointerLocation != null) {
                        ge.screenDevices.firstOrNull { device ->
                            device.defaultConfiguration.bounds.contains(pointerLocation)
                        } ?: ge.defaultScreenDevice
                    } else {
                        ge.defaultScreenDevice
                    }
                    val bounds = gd.defaultConfiguration.bounds
                    val insets = java.awt.Toolkit.getDefaultToolkit()
                        .getScreenInsets(gd.defaultConfiguration)

                    // Working area = full bounds minus taskbar / system chrome
                    val workX = bounds.x + insets.left
                    val workY = bounds.y + insets.top
                    val workW = bounds.width - insets.left - insets.right
                    val workH = bounds.height - insets.top - insets.bottom

                    val w = window.width
                    val h = window.height
                    window.setLocation(
                        workX + (workW - w) / 2,
                        workY + workH - h - 8,  // 8px gap above taskbar
                    )
                } catch (e: Exception) {
                    System.err.println("Failed to position overlay window: ${e.message}")
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
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
