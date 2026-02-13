package com.yerevan.transport.ui.map

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.yerevan.transport.R
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.data.local.entity.TransportStop
import com.yerevan.transport.databinding.FragmentMapBinding
import com.yerevan.transport.ui.main.MainViewModel
import com.yerevan.transport.util.RouteResult
import com.yerevan.transport.util.SegmentType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow

@AndroidEntryPoint
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    // Yerevan center coordinates
    companion object {
        private val YEREVAN_CENTER = GeoPoint(40.1792, 44.4991)
        private const val DEFAULT_ZOOM = 13.0
        private const val DETAIL_ZOOM = 15.0
    }

    private val stopMarkers = mutableListOf<Marker>()
    private val routeOverlays = mutableListOf<Polyline>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupControls()
        observeViewModel()
    }

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(YEREVAN_CENTER)
            minZoomLevel = 10.0
            maxZoomLevel = 19.0
        }
    }

    private fun setupControls() {
        binding.fabCenterYerevan.setOnClickListener {
            binding.mapView.controller.animateTo(YEREVAN_CENTER)
            binding.mapView.controller.setZoom(DEFAULT_ZOOM)
        }

        binding.fabShowAllStops.setOnClickListener {
            showAllStops()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allStops.collectLatest { stops ->
                if (stops.isNotEmpty()) {
                    showStopsOnMap(stops)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedRouteResult.collectLatest { result ->
                if (result != null) {
                    showRouteOnMap(result)
                } else {
                    clearRouteOverlays()
                    binding.cardRouteInfo.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedFromStop.collectLatest { stop ->
                stop?.let { highlightStop(it, true) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedToStop.collectLatest { stop ->
                stop?.let { highlightStop(it, false) }
            }
        }
    }

    private fun showStopsOnMap(stops: List<TransportStop>) {
        clearStopMarkers()

        for (stop in stops) {
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(stop.latitude, stop.longitude)
                title = stop.name
                snippet = "${stop.community} - ${stop.stopType.name}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // Color markers by type
                icon = when (stop.stopType) {
                    StopType.METRO -> ContextCompat.getDrawable(requireContext(), R.drawable.ic_metro)
                    else -> ContextCompat.getDrawable(requireContext(), R.drawable.ic_stop_marker)
                }
                infoWindow = BasicInfoWindow(R.layout.item_stop_suggestion, binding.mapView)
            }
            stopMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }

        binding.mapView.invalidate()
    }

    private fun showAllStops() {
        if (stopMarkers.isEmpty()) return

        // Calculate bounding box
        val lats = stopMarkers.map { it.position.latitude }
        val lons = stopMarkers.map { it.position.longitude }

        val boundingBox = BoundingBox(
            lats.max(), lons.max(),
            lats.min(), lons.min()
        )

        binding.mapView.zoomToBoundingBox(boundingBox, true, 50)
    }

    private fun highlightStop(stop: TransportStop, isFrom: Boolean) {
        val geoPoint = GeoPoint(stop.latitude, stop.longitude)

        val marker = Marker(binding.mapView).apply {
            position = geoPoint
            title = if (isFrom) "FROM: ${stop.name}" else "TO: ${stop.name}"
            snippet = stop.community
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_stop_marker)
        }

        binding.mapView.overlays.add(marker)
        binding.mapView.controller.animateTo(geoPoint)
        binding.mapView.invalidate()
    }

    private fun showRouteOnMap(result: RouteResult) {
        clearRouteOverlays()

        val allPoints = mutableListOf<GeoPoint>()

        for (segment in result.segments) {
            val points = segment.stops.map { GeoPoint(it.latitude, it.longitude) }
            allPoints.addAll(points)

            val polyline = Polyline().apply {
                setPoints(points)
                outlinePaint.apply {
                    color = when (segment.segmentType) {
                        SegmentType.TRANSIT -> try {
                            Color.parseColor(segment.route?.color ?: "#1565C0")
                        } catch (e: Exception) {
                            Color.parseColor("#1565C0")
                        }
                        SegmentType.WALKING -> Color.parseColor("#78909C")
                    }
                    strokeWidth = if (segment.segmentType == SegmentType.TRANSIT) 8f else 4f
                    style = if (segment.segmentType == SegmentType.WALKING) {
                        Paint.Style.STROKE
                    } else {
                        Paint.Style.STROKE
                    }
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }

                if (segment.segmentType == SegmentType.WALKING) {
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(
                        floatArrayOf(10f, 10f), 0f
                    )
                }
            }
            routeOverlays.add(polyline)
            binding.mapView.overlays.add(polyline)

            // Add markers for segment stops
            for (stop in segment.stops) {
                val stopMarker = Marker(binding.mapView).apply {
                    position = GeoPoint(stop.latitude, stop.longitude)
                    title = stop.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_stop_marker)
                }
                binding.mapView.overlays.add(stopMarker)
            }
        }

        // Highlight from/to stops
        val fromStop = result.segments.first().fromStop
        val toStop = result.segments.last().toStop

        addSpecialMarker(fromStop, "START: ${fromStop.name}", Color.GREEN)
        addSpecialMarker(toStop, "END: ${toStop.name}", Color.RED)

        // Highlight transfer stops
        for (transferStop in result.transferStops) {
            addSpecialMarker(transferStop, "TRANSFER: ${transferStop.name}", Color.parseColor("#FF6F00"))
        }

        // Zoom to fit route
        if (allPoints.isNotEmpty()) {
            val lats = allPoints.map { it.latitude }
            val lons = allPoints.map { it.longitude }
            val boundingBox = BoundingBox(
                lats.max(), lons.max(),
                lats.min(), lons.min()
            )
            binding.mapView.zoomToBoundingBox(boundingBox, true, 80)
        }

        // Show route info card
        binding.cardRouteInfo.visibility = View.VISIBLE
        binding.tvRouteInfoTitle.text = result.getSummary()
        binding.tvRouteInfoDetails.text = buildString {
            append("${fromStop.name} → ${toStop.name}")
            if (result.transferCount > 0) {
                append("\n${result.transferCount} transfer(s)")
            }
        }
        binding.tvRouteInfoTime.text = "~${result.getFormattedTime()}"
        binding.tvRouteInfoDistance.text = result.getFormattedDistance()

        binding.mapView.invalidate()
    }

    private fun addSpecialMarker(stop: TransportStop, title: String, color: Int) {
        val marker = Marker(binding.mapView).apply {
            position = GeoPoint(stop.latitude, stop.longitude)
            this.title = title
            snippet = stop.community
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_stop_marker)
        }
        binding.mapView.overlays.add(marker)
    }

    private fun clearStopMarkers() {
        binding.mapView.overlays.removeAll(stopMarkers)
        stopMarkers.clear()
    }

    private fun clearRouteOverlays() {
        binding.mapView.overlays.removeAll(routeOverlays)
        routeOverlays.clear()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
