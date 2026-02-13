package com.yerevan.transport.ui

import com.yerevan.transport.data.local.entity.StopEntity
import com.yerevan.transport.data.repository.MapData
import com.yerevan.transport.domain.RouteOption

data class MainUiState(
    val isSyncing: Boolean = true,
    val syncMessage: String = "Preparing GIS data",
    val errorMessage: String? = null,
    val startQuery: String = "",
    val endQuery: String = "",
    val selectedStartStop: StopEntity? = null,
    val selectedEndStop: StopEntity? = null,
    val startSuggestions: List<StopEntity> = emptyList(),
    val endSuggestions: List<StopEntity> = emptyList(),
    val mapData: MapData = MapData(stops = emptyList(), routes = emptyList()),
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedOptionIndex: Int = 0
)
