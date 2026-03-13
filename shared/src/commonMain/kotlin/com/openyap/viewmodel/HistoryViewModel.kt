package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.RecordingEntry
import com.openyap.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val entries: List<RecordingEntry> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface HistoryEvent {
    data class DeleteEntry(val id: String) : HistoryEvent
    data class CopyEntry(val text: String) : HistoryEvent
    data object ClearAll : HistoryEvent
    data object Refresh : HistoryEvent
}

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        loadEntries()
    }

    fun refresh() = loadEntries()

    fun onEvent(event: HistoryEvent) {
        when (event) {
            is HistoryEvent.DeleteEntry -> deleteEntry(event.id)
            is HistoryEvent.CopyEntry -> { /* clipboard copy handled by UI */
            }

            is HistoryEvent.ClearAll -> clearAll()
            is HistoryEvent.Refresh -> refresh()
        }
    }

    private fun loadEntries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val entries = historyRepository.loadEntries()
            _state.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    private fun deleteEntry(id: String) {
        viewModelScope.launch {
            historyRepository.removeEntry(id)
            _state.update { it.copy(entries = it.entries.filter { e -> e.id != id }) }
        }
    }

    private fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAll()
            _state.update { it.copy(entries = emptyList()) }
        }
    }
}
