package com.yerevan.transport.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yerevan.transport.data.local.AppDatabase
import com.yerevan.transport.data.local.entity.StopEntity
import com.yerevan.transport.data.remote.ArcGisSyncService
import com.yerevan.transport.data.repository.TransportRepository
import com.yerevan.transport.domain.RoutePlanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransportRepository(
        database = AppDatabase.get(application),
        syncService = ArcGisSyncService(),
        planner = RoutePlanner()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var startSearchJob: Job? = null
    private var endSearchJob: Job? = null

    init {
        syncAndLoad(force = false)
    }

    fun syncAndLoad(force: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncing = true,
                    syncMessage = if (force) "Refreshing GIS data…" else "Downloading GIS transport layers…",
                    errorMessage = null
                )
            }
            runCatching {
                val metadata = repository.ensureSynced(force = force)
                val mapData = repository.loadMapData()
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Synced ${metadata.stopCount} stops and ${metadata.routeCount} routes",
                        mapData = mapData
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = throwable.message ?: "Failed to sync GIS transport data."
                    )
                }
            }
        }
    }

    fun onStartQueryChanged(value: String) {
        _uiState.update {
            it.copy(
                startQuery = value,
                selectedStartStop = if (it.selectedStartStop?.name == value) it.selectedStartStop else null
            )
        }
        startSearchJob?.cancel()
        startSearchJob = viewModelScope.launch {
            val suggestions = repository.searchStops(value)
            _uiState.update { it.copy(startSuggestions = suggestions) }
        }
    }

    fun onEndQueryChanged(value: String) {
        _uiState.update {
            it.copy(
                endQuery = value,
                selectedEndStop = if (it.selectedEndStop?.name == value) it.selectedEndStop else null
            )
        }
        endSearchJob?.cancel()
        endSearchJob = viewModelScope.launch {
            val suggestions = repository.searchStops(value)
            _uiState.update { it.copy(endSuggestions = suggestions) }
        }
    }

    fun onStartStopSelected(stop: StopEntity) {
        _uiState.update {
            it.copy(
                selectedStartStop = stop,
                startQuery = stop.name,
                startSuggestions = emptyList()
            )
        }
    }

    fun onEndStopSelected(stop: StopEntity) {
        _uiState.update {
            it.copy(
                selectedEndStop = stop,
                endQuery = stop.name,
                endSuggestions = emptyList()
            )
        }
    }

    fun selectRouteOption(index: Int) {
        _uiState.update { state ->
            state.copy(selectedOptionIndex = index.coerceIn(0, state.routeOptions.lastIndex.coerceAtLeast(0)))
        }
    }

    fun findRoutes() {
        val current = _uiState.value
        val start = current.selectedStartStop
        val end = current.selectedEndStop
        if (start == null || end == null) {
            _uiState.update { it.copy(errorMessage = "Please select both start and end stops from suggestions.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            runCatching {
                repository.findRouteOptions(start.id, end.id)
            }.onSuccess { options ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        routeOptions = options,
                        selectedOptionIndex = 0,
                        errorMessage = if (options.isEmpty()) {
                            "No direct route found. Try nearby stops or refresh data."
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = throwable.message ?: "Failed to calculate routes."
                    )
                }
            }
        }
    }
}
