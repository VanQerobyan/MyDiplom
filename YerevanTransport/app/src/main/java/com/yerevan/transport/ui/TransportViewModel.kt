package com.yerevan.transport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yerevan.transport.domain.model.RouteOption
import com.yerevan.transport.domain.model.TransportStop
import com.yerevan.transport.data.repository.TransportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransportViewModel @Inject constructor(
    private val repository: TransportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransportUiState>(TransportUiState.Initial)
    val uiState: StateFlow<TransportUiState> = _uiState.asStateFlow()

    private val _stops = MutableStateFlow<List<TransportStop>>(emptyList())
    val stops: StateFlow<List<TransportStop>> = _stops.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            if (!repository.isDatabasePopulated()) {
                _uiState.value = TransportUiState.Loading("Loading transport data...")
                repository.syncDataFromGis().fold(
                    onSuccess = { msg ->
                        _uiState.value = TransportUiState.DataLoaded(msg)
                        loadStopsForMap()
                    },
                    onFailure = {
                        _uiState.value = TransportUiState.Error("Failed to load data: ${it.message}")
                    }
                )
            } else {
                loadStopsForMap()
                _uiState.value = TransportUiState.DataLoaded("Data ready")
            }
        }
    }

    fun loadStopsForMap() {
        viewModelScope.launch {
            _stops.value = repository.getStopsForMap()
        }
    }

    fun searchStops(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.value = TransportUiState.SearchResults(emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = repository.searchStops(query)
            _uiState.value = TransportUiState.SearchResults(results)
        }
    }

    fun calculateRoute(fromStop: TransportStop, toStop: TransportStop) {
        viewModelScope.launch {
            _uiState.value = TransportUiState.Loading("Calculating route...")
            val options = repository.calculateRoutes(fromStop.id, toStop.id)
            _uiState.value = TransportUiState.RouteResults(
                fromStop = fromStop,
                toStop = toStop,
                options = options
            )
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.value = TransportUiState.Loading("Syncing from GIS...")
            repository.syncDataFromGis().fold(
                onSuccess = { msg ->
                    _uiState.value = TransportUiState.DataLoaded(msg)
                    loadStopsForMap()
                },
                onFailure = {
                    _uiState.value = TransportUiState.Error("Sync failed: ${it.message}")
                }
            )
        }
    }

    fun clearSearch() {
        _uiState.value = when (val s = _uiState.value) {
            is TransportUiState.SearchResults -> TransportUiState.DataLoaded("")
            else -> s
        }
    }

    fun selectRouteOption(option: RouteOption) {
        _uiState.value = when (val s = _uiState.value) {
            is TransportUiState.RouteResults -> s.copy(selectedOption = option)
            else -> s
        }
    }

    fun resetToMap() {
        _uiState.value = TransportUiState.DataLoaded("")
    }

    fun findStopsAndCalculateRoute(fromName: String, toName: String) {
        viewModelScope.launch {
            _uiState.value = TransportUiState.Loading("Finding route...")
            val fromStop = withContext(Dispatchers.IO) { repository.getStopByName(fromName) }
            val toStop = withContext(Dispatchers.IO) { repository.getStopByName(toName) }
            if (fromStop == null || toStop == null) {
                val fromSearch = withContext(Dispatchers.IO) { repository.searchStops(fromName) }
                val toSearch = withContext(Dispatchers.IO) { repository.searchStops(toName) }
                val from = fromSearch.firstOrNull { it.name.equals(fromName, ignoreCase = true) }
                val to = toSearch.firstOrNull { it.name.equals(toName, ignoreCase = true) }
                if (from != null && to != null) {
                    calculateRoute(from, to)
                } else {
                    _uiState.value = TransportUiState.Error("Could not find stops. Please select from dropdown.")
                }
            } else {
                calculateRoute(fromStop, toStop)
            }
        }
    }
}

sealed class TransportUiState {
    object Initial : TransportUiState()
    data class Loading(val message: String) : TransportUiState()
    data class DataLoaded(val message: String) : TransportUiState()
    data class Error(val message: String) : TransportUiState()
    data class SearchResults(val stops: List<TransportStop>) : TransportUiState()
    data class RouteResults(
        val fromStop: TransportStop,
        val toStop: TransportStop,
        val options: List<RouteOption>,
        val selectedOption: RouteOption? = null
    ) : TransportUiState()
}
