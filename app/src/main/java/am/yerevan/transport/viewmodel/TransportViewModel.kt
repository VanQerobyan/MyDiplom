package am.yerevan.transport.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import am.yerevan.transport.data.database.TransportDatabase
import am.yerevan.transport.data.model.Route
import am.yerevan.transport.data.model.Stop
import am.yerevan.transport.data.repository.RouteSearchResult
import am.yerevan.transport.data.repository.TransportRepository
import am.yerevan.transport.utils.GISDataExtractor
import kotlinx.coroutines.launch

/**
 * ViewModel for transport data and route search
 */
class TransportViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TransportDatabase.getDatabase(application)
    private val repository = TransportRepository(
        database.stopDao(),
        database.routeDao(),
        database.routeStopDao()
    )

    val allStops: LiveData<List<Stop>> = repository.allStops
    val allRoutes: LiveData<List<Route>> = repository.allRoutes

    private val _searchResults = MutableLiveData<List<Stop>>()
    val searchResults: LiveData<List<Stop>> = _searchResults

    private val _routeSearchResult = MutableLiveData<RouteSearchResult>()
    val routeSearchResult: LiveData<RouteSearchResult> = _routeSearchResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _dataLoadingProgress = MutableLiveData<DataLoadingState>()
    val dataLoadingProgress: LiveData<DataLoadingState> = _dataLoadingProgress

    private val _selectedFromStop = MutableLiveData<Stop?>()
    val selectedFromStop: LiveData<Stop?> = _selectedFromStop

    private val _selectedToStop = MutableLiveData<Stop?>()
    val selectedToStop: LiveData<Stop?> = _selectedToStop

    /**
     * Search for stops by name
     */
    fun searchStops(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val results = repository.searchStops(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _error.value = "Error searching stops: ${e.message}"
            }
        }
    }

    /**
     * Set selected stops for route calculation
     */
    fun setFromStop(stop: Stop?) {
        _selectedFromStop.value = stop
    }

    fun setToStop(stop: Stop?) {
        _selectedToStop.value = stop
    }

    /**
     * Swap from and to stops
     */
    fun swapStops() {
        val temp = _selectedFromStop.value
        _selectedFromStop.value = _selectedToStop.value
        _selectedToStop.value = temp
    }

    /**
     * Find routes between selected stops
     */
    fun findRoutes() {
        val from = _selectedFromStop.value
        val to = _selectedToStop.value

        if (from == null || to == null) {
            _error.value = "Please select both starting and destination stops"
            return
        }

        if (from.id == to.id) {
            _error.value = "Starting and destination stops cannot be the same"
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val result = repository.findRoutes(from.id, to.id)
                _routeSearchResult.value = result
                
                if (!result.hasResults) {
                    _error.value = "No routes found between these stops"
                }
            } catch (e: Exception) {
                _error.value = "Error finding routes: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load transport data from GIS
     */
    fun loadTransportData() {
        _dataLoadingProgress.value = DataLoadingState.Loading
        
        viewModelScope.launch {
            try {
                val extractor = GISDataExtractor(getApplication())
                val result = extractor.extractAndPopulateDatabase()
                
                if (result.isSuccess) {
                    _dataLoadingProgress.value = DataLoadingState.Success
                } else {
                    _dataLoadingProgress.value = DataLoadingState.Error(
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                _dataLoadingProgress.value = DataLoadingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Get stops for a specific route
     */
    fun getStopsForRoute(routeId: Long): LiveData<List<Stop>> {
        val result = MutableLiveData<List<Stop>>()
        viewModelScope.launch {
            try {
                val stops = repository.getStopsForRoute(routeId)
                result.postValue(stops)
            } catch (e: Exception) {
                _error.value = "Error loading route stops: ${e.message}"
            }
        }
        return result
    }

    /**
     * Get nearby stops
     */
    fun getNearbyStops(latitude: Double, longitude: Double): LiveData<List<Stop>> {
        val result = MutableLiveData<List<Stop>>()
        viewModelScope.launch {
            try {
                val stops = repository.getNearbyStops(latitude, longitude)
                result.postValue(stops)
            } catch (e: Exception) {
                _error.value = "Error loading nearby stops: ${e.message}"
            }
        }
        return result
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}

/**
 * States for data loading process
 */
sealed class DataLoadingState {
    object Idle : DataLoadingState()
    object Loading : DataLoadingState()
    object Success : DataLoadingState()
    data class Error(val message: String) : DataLoadingState()
}
