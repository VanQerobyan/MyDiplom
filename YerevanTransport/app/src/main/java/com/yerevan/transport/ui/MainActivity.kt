package com.yerevan.transport.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.yerevan.transport.R
import com.yerevan.transport.databinding.ActivityMainBinding
import com.yerevan.transport.domain.model.RouteOption
import com.yerevan.transport.domain.model.TransportStop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TransportViewModel by viewModels()
    private var googleMap: GoogleMap? = null
    private lateinit var fromAdapter: ArrayAdapter<TransportStop>
    private lateinit var toAdapter: ArrayAdapter<TransportStop>

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            googleMap?.isMyLocationEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fromAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        toAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.fromStop.setAdapter(fromAdapter)
        binding.toStop.setAdapter(toAdapter)

        (supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment)?.getMapAsync(this)

        setupSearch()
        observeState()
        setupRefresh()
    }

    private fun setupSearch() {
        binding.fromStop.setOnItemClickListener { _, _, position, _ ->
            viewModel.clearSearch()
        }
        binding.toStop.setOnItemClickListener { _, _, position, _ ->
            viewModel.clearSearch()
        }

        var fromSearchJob: kotlinx.coroutines.Job? = null
        binding.fromStop.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                fromSearchJob?.cancel()
                fromSearchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    viewModel.searchStops(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        var toSearchJob: kotlinx.coroutines.Job? = null
        binding.toStop.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                toSearchJob?.cancel()
                toSearchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    viewModel.searchStops(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.searchButton.setOnClickListener {
            val fromText = binding.fromStop.text.toString().trim()
            val toText = binding.toStop.text.toString().trim()
            if (fromText.isBlank() || toText.isBlank()) {
                Toast.makeText(this, "Enter both starting and ending stops", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.findStopsAndCalculateRoute(fromText, toText)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is TransportUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.routeResults.visibility = View.GONE
                        }
                        is TransportUiState.DataLoaded -> {
                            binding.progressBar.visibility = View.GONE
                            binding.routeResults.visibility = View.GONE
                        }
                        is TransportUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                        is TransportUiState.SearchResults -> {
                            binding.progressBar.visibility = View.GONE
                            fromAdapter.clear()
                            fromAdapter.addAll(state.stops)
                            fromAdapter.notifyDataSetChanged()
                            toAdapter.clear()
                            toAdapter.addAll(state.stops)
                            toAdapter.notifyDataSetChanged()
                        }
                        is TransportUiState.RouteResults -> {
                            binding.progressBar.visibility = View.GONE
                            binding.routeResults.visibility = View.VISIBLE
                            displayRouteResults(state)
                            drawRouteOnMap(state.selectedOption ?: state.options.firstOrNull())
                        }
                        else -> {}
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stops.collectLatest { stops ->
                    updateMapMarkers(stops)
                }
            }
        }
    }

    private fun displayRouteResults(state: TransportUiState.RouteResults) {
        binding.routeFromText.text = state.fromStop.name
        binding.routeToText.text = state.toStop.name
        binding.routeOptionsList.removeAllViews()

        state.options.forEach { option ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = buildRouteSummary(option)
                setOnClickListener { viewModel.selectRouteOption(option) }
                isCheckable = true
                isChecked = option == state.selectedOption
            }
            binding.routeOptionsList.addView(chip)
        }
    }

    private fun buildRouteSummary(option: RouteOption): String {
        val segments = option.segments.joinToString(" → ") { "№${it.routeNumber}" }
        return "$segments • ${option.totalEstimatedMinutes} min"
    }

    private fun drawRouteOnMap(option: RouteOption?) {
        option ?: return
        googleMap?.clear()
        viewModel.stops.value.forEach { stop ->
            googleMap?.addMarker(MarkerOptions().position(LatLng(stop.latitude, stop.longitude)).title(stop.name))
        }
        val polyline = PolylineOptions().width(12f).color(ContextCompat.getColor(this, R.color.route_line))
        option.segments.flatMap { it.stops }.forEach { stop ->
            polyline.add(LatLng(stop.latitude, stop.longitude))
        }
        googleMap?.addPolyline(polyline)
        val first = option.segments.first().stops.first()
        val last = option.segments.last().stops.last()
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(
            com.google.android.gms.maps.model.LatLngBounds(
                LatLng(first.latitude, first.longitude),
                LatLng(last.latitude, last.longitude)
            ), 100
        ))
    }

    private fun updateMapMarkers(stops: List<TransportStop>) {
        googleMap ?: return
        googleMap!!.clear()
        stops.forEach { stop ->
            googleMap!!.addMarker(MarkerOptions().position(LatLng(stop.latitude, stop.longitude)).title(stop.name))
        }
        if (stops.isNotEmpty()) {
            val center = LatLng(stops.map { it.latitude }.average(), stops.map { it.longitude }.average())
            googleMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 12f))
        }
    }

    private fun setupRefresh() {
        binding.refreshButton.setOnClickListener { viewModel.refreshData() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isZoomControlsEnabled = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(40.1872, 44.5152), 12f))
        viewModel.loadStopsForMap()
    }
}
