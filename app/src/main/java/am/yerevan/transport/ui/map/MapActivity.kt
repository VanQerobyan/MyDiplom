package am.yerevan.transport.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import am.yerevan.transport.R
import am.yerevan.transport.data.database.TransportDatabase
import am.yerevan.transport.data.model.Stop
import am.yerevan.transport.viewmodel.TransportViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MapActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var viewModel: TransportViewModel
    private var routeId: Long = -1
    private val stopMarkers = mutableListOf<Marker>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1
        private val YEREVAN_CENTER = LatLng(40.1792, 44.4991)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        viewModel = ViewModelProvider(this)[TransportViewModel::class.java]
        routeId = intent.getLongExtra("route_id", -1)

        // Setup toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Setup map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Setup location button
        val myLocationFab = findViewById<FloatingActionButton>(R.id.myLocationFab)
        myLocationFab.setOnClickListener {
            if (::map.isInitialized) {
                enableMyLocation()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Configure map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMapToolbarEnabled = true

        // Move camera to Yerevan
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(YEREVAN_CENTER, 12f))

        // Enable location if permitted
        enableMyLocation()

        // Load and display stops
        loadStops()

        // If a specific route was requested, display it
        if (routeId != -1L) {
            loadAndDisplayRoute(routeId)
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            }
        }
    }

    private fun loadStops() {
        viewModel.allStops.observe(this) { stops ->
            if (stops.isNotEmpty()) {
                displayStops(stops)
            }
        }
    }

    private fun displayStops(stops: List<Stop>) {
        // Clear existing markers
        stopMarkers.forEach { it.remove() }
        stopMarkers.clear()

        // Add markers for each stop
        for (stop in stops) {
            val position = LatLng(stop.latitude, stop.longitude)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(stop.name)
                    .snippet(stop.nameEn)
                    .icon(BitmapDescriptorFactory.defaultMarker(
                        when (stop.type) {
                            "metro" -> BitmapDescriptorFactory.HUE_RED
                            "trolleybus_stop" -> BitmapDescriptorFactory.HUE_GREEN
                            "minibus_stop" -> BitmapDescriptorFactory.HUE_ORANGE
                            else -> BitmapDescriptorFactory.HUE_BLUE
                        }
                    ))
            )
            marker?.let { stopMarkers.add(it) }
        }
    }

    private fun loadAndDisplayRoute(routeId: Long) {
        lifecycleScope.launch {
            try {
                val database = TransportDatabase.getDatabase(this@MapActivity)
                val route = database.routeDao().getRouteById(routeId)
                val stops = database.routeStopDao().getStopsForRouteDetailed(routeId)

                route?.let { r ->
                    // Draw polyline connecting stops
                    if (stops.isNotEmpty()) {
                        val polylineOptions = PolylineOptions()
                            .width(8f)
                            .color(parseColor(r.color))
                            .geodesic(true)

                        for (stop in stops) {
                            polylineOptions.add(LatLng(stop.latitude, stop.longitude))
                        }

                        runOnUiThread {
                            map.addPolyline(polylineOptions)

                            // Highlight stops on this route
                            for (stop in stops) {
                                map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(stop.latitude, stop.longitude))
                                        .title(stop.name)
                                        .snippet("Route ${r.routeNumber}")
                                        .icon(BitmapDescriptorFactory.defaultMarker(
                                            BitmapDescriptorFactory.HUE_VIOLET
                                        ))
                                )
                            }

                            // Zoom to show entire route
                            if (stops.size > 1) {
                                val boundsBuilder = LatLngBounds.Builder()
                                stops.forEach { stop ->
                                    boundsBuilder.include(LatLng(stop.latitude, stop.longitude))
                                }
                                val bounds = boundsBuilder.build()
                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MapActivity, 
                        "Error loading route: ${e.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseColor(colorString: String): Int {
        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            Color.BLUE
        }
    }
}
