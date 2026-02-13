package com.yerevan.transport.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.yerevan.transport.domain.RouteOption
import com.yerevan.transport.ui.components.StopAutocompleteField
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedOption = uiState.routeOptions.getOrNull(uiState.selectedOptionIndex)
    val stopById = uiState.mapData.stops.associateBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yerevan Transport Planner") },
                actions = {
                    IconButton(onClick = { viewModel.syncAndLoad(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh GIS data")
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text(
                text = uiState.syncMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            StopAutocompleteField(
                label = "Start stop",
                value = uiState.startQuery,
                suggestions = uiState.startSuggestions,
                onValueChange = viewModel::onStartQueryChanged,
                onStopSelected = viewModel::onStartStopSelected
            )
            StopAutocompleteField(
                label = "End stop",
                value = uiState.endQuery,
                suggestions = uiState.endSuggestions,
                onValueChange = viewModel::onEndQueryChanged,
                onStopSelected = viewModel::onEndStopSelected
            )

            Button(
                onClick = viewModel::findRoutes,
                enabled = !uiState.isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Find route options")
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.routeOptions.isNotEmpty()) {
                Text(
                    text = "Available transport options",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier.height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.routeOptions) { index, option ->
                        RouteOptionCard(
                            option = option,
                            stopById = stopById,
                            selected = index == uiState.selectedOptionIndex,
                            onClick = { viewModel.selectRouteOption(index) }
                        )
                    }
                }
            }

            RouteMap(
                uiState = uiState,
                selectedOption = selectedOption,
                stopById = stopById,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun RouteOptionCard(
    option: RouteOption,
    stopById: Map<String, com.yerevan.transport.data.local.entity.StopEntity>,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cardColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (option) {
                is RouteOption.Direct -> {
                    Text(
                        text = "Direct • ${option.routeName}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Mode: ${option.mode} • Headway ~${option.headwayMinutes} min",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                is RouteOption.Transfer -> {
                    val fromName = stopById[option.transferFromStopId]?.name ?: "Transfer stop A"
                    val toName = stopById[option.transferToStopId]?.name ?: "Transfer stop B"
                    Text(
                        text = "Transfer • ${option.firstRouteName} -> ${option.secondRouteName}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Walk: ${formatDistance(option.walkingDistanceMeters)} between $fromName and $toName",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = "Distance ${formatDistance(option.totalDistanceMeters)} • ~${formatMinutes(option.estimatedMinutes)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RouteMap(
    uiState: MainUiState,
    selectedOption: RouteOption?,
    stopById: Map<String, com.yerevan.transport.data.local.entity.StopEntity>,
    modifier: Modifier = Modifier
) {
    val highlightedRouteIds = when (selectedOption) {
        is RouteOption.Direct -> setOf(selectedOption.routeId)
        is RouteOption.Transfer -> setOf(selectedOption.firstRouteId, selectedOption.secondRouteId)
        null -> emptySet()
    }
    val transferFrom = (selectedOption as? RouteOption.Transfer)?.let { stopById[it.transferFromStopId] }
    val transferTo = (selectedOption as? RouteOption.Transfer)?.let { stopById[it.transferToStopId] }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.1772, 44.5035), 11.4f)
    }
    val focusStop = uiState.selectedStartStop ?: uiState.selectedEndStop
    LaunchedEffect(focusStop?.id) {
        focusStop ?: return@LaunchedEffect
        cameraPositionState.animate(
            update = CameraUpdateFactory.newLatLngZoom(
                LatLng(focusStop.lat, focusStop.lng),
                13f
            ),
            durationMs = 700
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false
        )
    ) {
        uiState.mapData.routes.forEach { route ->
            val highlighted = route.routeId in highlightedRouteIds
            val strokeColor = routeColor(route.mode, highlighted)
            val fillColor = strokeColor.copy(alpha = if (highlighted) 0.28f else 0.10f)
            if (route.geometryType == "esriGeometryPolygon") {
                route.parts.forEach { part ->
                    if (part.size >= 3) {
                        Polygon(
                            points = part.map { LatLng(it.lat, it.lng) },
                            strokeColor = strokeColor,
                            strokeWidth = if (highlighted) 5f else 2f,
                            fillColor = fillColor
                        )
                    }
                }
            } else {
                route.parts.forEach { part ->
                    if (part.size >= 2) {
                        Polyline(
                            points = part.map { LatLng(it.lat, it.lng) },
                            color = strokeColor,
                            width = if (highlighted) 7f else 3f
                        )
                    }
                }
            }
        }

        val renderAllStops = uiState.mapData.stops.size <= 300
        if (renderAllStops) {
            uiState.mapData.stops.forEach { stop ->
                Marker(
                    state = MarkerState(LatLng(stop.lat, stop.lng)),
                    title = stop.name
                )
            }
        }

        uiState.selectedStartStop?.let { start ->
            Marker(
                state = MarkerState(LatLng(start.lat, start.lng)),
                title = "Start: ${start.name}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
            )
        }
        uiState.selectedEndStop?.let { end ->
            Marker(
                state = MarkerState(LatLng(end.lat, end.lng)),
                title = "End: ${end.name}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        }

        if (transferFrom != null && transferTo != null) {
            Marker(
                state = MarkerState(LatLng(transferFrom.lat, transferFrom.lng)),
                title = "Transfer from: ${transferFrom.name}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
            Marker(
                state = MarkerState(LatLng(transferTo.lat, transferTo.lng)),
                title = "Transfer to: ${transferTo.name}",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
            )
            Polyline(
                points = listOf(
                    LatLng(transferFrom.lat, transferFrom.lng),
                    LatLng(transferTo.lat, transferTo.lng)
                ),
                color = Color(0xFF5E35B1),
                width = 6f
            )
        }
    }
}

private fun routeColor(mode: String, highlighted: Boolean): Color {
    val base = when (mode) {
        "METRO" -> Color(0xFF0057B8)
        "MONORAIL" -> Color(0xFF43A047)
        "RAIL" -> Color(0xFF6D4C41)
        else -> Color(0xFF616161)
    }
    return if (highlighted) base else base.copy(alpha = 0.60f)
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        String.format(Locale.US, "%.0f m", meters)
    }
}

private fun formatMinutes(minutes: Double): String {
    return String.format(Locale.US, "%.0f min", minutes)
}
