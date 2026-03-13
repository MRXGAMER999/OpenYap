package com.openyap.platform

import com.openyap.model.OverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

data class OverlayUiState(
    val visible: Boolean = false,
    val state: OverlayState = OverlayState.RECORDING,
    val level: Float = 0f,
    val durationSeconds: Int = 0,
    val flashMessage: String? = null,
)

class ComposeOverlayController : OverlayController, Closeable {

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastFlashJob: Job? = null

    override fun show() {
        _uiState.update {
            OverlayUiState(
                visible = true,
                state = OverlayState.RECORDING,
                level = 0f,
                durationSeconds = 0,
            )
        }
    }

    override fun updateState(state: OverlayState) {
        _uiState.update {
            it.copy(
                state = state,
                visible = state != OverlayState.ERROR || it.visible,
            )
        }
    }

    override fun updateLevel(level: Float) {
        _uiState.update { it.copy(level = level) }
    }

    override fun updateDuration(seconds: Int) {
        _uiState.update { it.copy(durationSeconds = seconds) }
    }

    override fun dismiss() {
        _uiState.update { OverlayUiState() }
    }

    override fun flashMessage(message: String) {
        lastFlashJob?.cancel()
        _uiState.update { it.copy(flashMessage = message) }
        lastFlashJob = scope.launch {
            delay(1500)
            _uiState.update { it.copy(flashMessage = null) }
        }
    }

    override fun flashProcessing() {
        flashMessage("Still processing...")
    }

    override fun close() {
        scope.cancel()
    }
}
