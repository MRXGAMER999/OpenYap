package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.DictionaryEntry
import com.openyap.repository.DictionaryRepository
import com.openyap.service.DictionaryEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DictionaryUiState(
    val entries: List<DictionaryEntry> = emptyList(),
    val isLoading: Boolean = true,
)

sealed interface DictionaryEvent {
    data class AddEntry(val original: String, val replacement: String) : DictionaryEvent
    data class RemoveEntry(val id: String) : DictionaryEvent
    data class ToggleEntry(val id: String) : DictionaryEvent
    data object Refresh : DictionaryEvent
}

class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val dictionaryEngine: DictionaryEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(DictionaryUiState())
    val state: StateFlow<DictionaryUiState> = _state.asStateFlow()

    init {
        loadEntries()
    }

    fun refresh() = loadEntries()

    fun onEvent(event: DictionaryEvent) {
        when (event) {
            is DictionaryEvent.AddEntry -> addEntry(event.original, event.replacement)
            is DictionaryEvent.RemoveEntry -> removeEntry(event.id)
            is DictionaryEvent.ToggleEntry -> toggleEntry(event.id)
            is DictionaryEvent.Refresh -> refresh()
        }
    }

    private fun loadEntries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val entries = dictionaryRepository.loadEntries()
            _state.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    private fun addEntry(original: String, replacement: String) {
        viewModelScope.launch {
            dictionaryEngine.addManualEntry(original, replacement)
            loadEntries()
        }
    }

    private fun removeEntry(id: String) {
        viewModelScope.launch {
            dictionaryRepository.remove(id)
            _state.update { it.copy(entries = it.entries.filter { e -> e.id != id }) }
        }
    }

    private fun toggleEntry(id: String) {
        viewModelScope.launch {
            val updated = _state.value.entries.map { e ->
                if (e.id == id) e.copy(isEnabled = !e.isEnabled) else e
            }
            _state.update { it.copy(entries = updated) }
            // Persist the toggled entry
            updated.firstOrNull { it.id == id }?.let { dictionaryRepository.addOrUpdate(it) }
        }
    }
}
