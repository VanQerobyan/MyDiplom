package com.yerevan.transport.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yerevan.transport.data.local.database.DatabaseSeeder
import com.yerevan.transport.data.local.entity.TransportRoute
import com.yerevan.transport.data.local.entity.TransportStop
import com.yerevan.transport.data.repository.TransportRepository
import com.yerevan.transport.util.RouteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: TransportRepository,
    private val databaseSeeder: DatabaseSeeder
) : AndroidViewModel(application) {

    // Database initialization state
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("Initializing...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    // Search state
    private val _fromStopQuery = MutableStateFlow("")
    val fromStopQuery: StateFlow<String> = _fromStopQuery.asStateFlow()

    private val _toStopQuery = MutableStateFlow("")
    val toStopQuery: StateFlow<String> = _toStopQuery.asStateFlow()

    private val _fromSuggestions = MutableStateFlow<List<TransportStop>>(emptyList())
    val fromSuggestions: StateFlow<List<TransportStop>> = _fromSuggestions.asStateFlow()

    private val _toSuggestions = MutableStateFlow<List<TransportStop>>(emptyList())
    val toSuggestions: StateFlow<List<TransportStop>> = _toSuggestions.asStateFlow()

    private val _selectedFromStop = MutableStateFlow<TransportStop?>(null)
    val selectedFromStop: StateFlow<TransportStop?> = _selectedFromStop.asStateFlow()

    private val _selectedToStop = MutableStateFlow<TransportStop?>(null)
    val selectedToStop: StateFlow<TransportStop?> = _selectedToStop.asStateFlow()

    // Route results
    private val _routeResults = MutableStateFlow<List<RouteResult>>(emptyList())
    val routeResults: StateFlow<List<RouteResult>> = _routeResults.asStateFlow()

    private val _selectedRouteResult = MutableStateFlow<RouteResult?>(null)
    val selectedRouteResult: StateFlow<RouteResult?> = _selectedRouteResult.asStateFlow()

    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

    // All stops for map
    private val _allStops = MutableStateFlow<List<TransportStop>>(emptyList())
    val allStops: StateFlow<List<TransportStop>> = _allStops.asStateFlow()

    // All routes
    private val _allRoutes = MutableStateFlow<List<TransportRoute>>(emptyList())
    val allRoutes: StateFlow<List<TransportRoute>> = _allRoutes.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Statistics
    private val _stopCount = MutableStateFlow(0)
    val stopCount: StateFlow<Int> = _stopCount.asStateFlow()

    private val _routeCount = MutableStateFlow(0)
    val routeCount: StateFlow<Int> = _routeCount.asStateFlow()

    private var searchFromJob: Job? = null
    private var searchToJob: Job? = null

    init {
        initializeDatabase()
    }

    private fun initializeDatabase() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _loadingMessage.value = "Checking database..."

                if (databaseSeeder.needsSeeding()) {
                    _loadingMessage.value = "Loading Yerevan transport data...\n(384 bus stops, 10 metro stations)"
                    val result = databaseSeeder.seedDatabase()
                    if (result.isFailure) {
                        _error.value = "Failed to initialize database: ${result.exceptionOrNull()?.message}"
                        _isLoading.value = false
                        return@launch
                    }
                }

                _loadingMessage.value = "Loading stops and routes..."
                _allStops.value = repository.getAllStopsList()
                _allRoutes.value = repository.getAllRoutesList()
                _stopCount.value = repository.getStopCount()
                _routeCount.value = repository.getRouteCount()

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Initialization failed: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun searchFromStop(query: String) {
        _fromStopQuery.value = query
        _selectedFromStop.value = null
        searchFromJob?.cancel()
        searchFromJob = viewModelScope.launch {
            delay(300) // Debounce
            if (query.length >= 2) {
                _fromSuggestions.value = repository.searchStopsList(query)
            } else {
                _fromSuggestions.value = emptyList()
            }
        }
    }

    fun searchToStop(query: String) {
        _toStopQuery.value = query
        _selectedToStop.value = null
        searchToJob?.cancel()
        searchToJob = viewModelScope.launch {
            delay(300) // Debounce
            if (query.length >= 2) {
                _toSuggestions.value = repository.searchStopsList(query)
            } else {
                _toSuggestions.value = emptyList()
            }
        }
    }

    fun selectFromStop(stop: TransportStop) {
        _selectedFromStop.value = stop
        _fromStopQuery.value = stop.name
        _fromSuggestions.value = emptyList()
        tryCalculateRoute()
    }

    fun selectToStop(stop: TransportStop) {
        _selectedToStop.value = stop
        _toStopQuery.value = stop.name
        _toSuggestions.value = emptyList()
        tryCalculateRoute()
    }

    fun swapStops() {
        val tempStop = _selectedFromStop.value
        val tempQuery = _fromStopQuery.value

        _selectedFromStop.value = _selectedToStop.value
        _fromStopQuery.value = _toStopQuery.value

        _selectedToStop.value = tempStop
        _toStopQuery.value = tempQuery

        tryCalculateRoute()
    }

    fun selectRouteResult(result: RouteResult) {
        _selectedRouteResult.value = result
    }

    fun clearRouteResults() {
        _routeResults.value = emptyList()
        _selectedRouteResult.value = null
    }

    fun clearError() {
        _error.value = null
    }

    private fun tryCalculateRoute() {
        val from = _selectedFromStop.value ?: return
        val to = _selectedToStop.value ?: return

        if (from.id == to.id) {
            _error.value = "Start and end stops are the same"
            return
        }

        viewModelScope.launch {
            _isCalculating.value = true
            _routeResults.value = emptyList()
            _selectedRouteResult.value = null

            try {
                val results = repository.calculateRoutes(from.id, to.id)
                _routeResults.value = results
                if (results.isEmpty()) {
                    _error.value = "No routes found between these stops"
                } else {
                    _selectedRouteResult.value = results.first()
                }
            } catch (e: Exception) {
                _error.value = "Route calculation failed: ${e.message}"
            } finally {
                _isCalculating.value = false
            }
        }
    }

    suspend fun getStopsForRoute(routeId: Long): List<TransportStop> {
        return repository.getOrderedStopsForRoute(routeId)
    }

    suspend fun getRoutesForStop(stopId: Long): List<TransportRoute> {
        return repository.getRoutesForStop(stopId)
    }
}
