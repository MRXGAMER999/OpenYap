package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val totalRecordings: Int = 0,
    val totalDurationSeconds: Int = 0,
    val totalCharacters: Int = 0,
    val averageDurationSeconds: Int = 0,
    val topApps: List<Pair<String, Int>> = emptyList(),
    val isLoading: Boolean = true,
)

class StatsViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        loadStats()
    }

    fun refresh() = loadStats()

    private fun loadStats() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val entries = historyRepository.loadEntries()
            val totalDuration = entries.sumOf { it.durationSeconds }
            val totalChars = entries.sumOf { it.response.length }
            val avgDuration = if (entries.isNotEmpty()) totalDuration / entries.size else 0

            val topApps = entries
                .filter { it.targetApp.isNotBlank() }
                .groupBy { it.targetApp }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)

            _state.update {
                StatsUiState(
                    totalRecordings = entries.size,
                    totalDurationSeconds = totalDuration,
                    totalCharacters = totalChars,
                    averageDurationSeconds = avgDuration,
                    topApps = topApps,
                    isLoading = false,
                )
            }
        }
    }
}
