package am.yerevan.transport.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import am.yerevan.transport.R
import am.yerevan.transport.data.model.Stop
import am.yerevan.transport.ui.map.MapActivity
import am.yerevan.transport.ui.search.DirectRouteAdapter
import am.yerevan.transport.ui.search.StopAutoCompleteAdapter
import am.yerevan.transport.ui.search.TransferRouteAdapter
import am.yerevan.transport.viewmodel.DataLoadingState
import am.yerevan.transport.viewmodel.TransportViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: TransportViewModel
    
    private lateinit var fromStopInput: AutoCompleteTextView
    private lateinit var toStopInput: AutoCompleteTextView
    private lateinit var swapButton: ImageButton
    private lateinit var findRouteButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var resultsContainer: View
    private lateinit var directRoutesRecycler: RecyclerView
    private lateinit var transferRoutesRecycler: RecyclerView
    private lateinit var directRoutesTitle: TextView
    private lateinit var transferRoutesTitle: TextView
    private lateinit var mapFab: FloatingActionButton
    
    private lateinit var fromAdapter: StopAutoCompleteAdapter
    private lateinit var toAdapter: StopAutoCompleteAdapter
    private lateinit var directRouteAdapter: DirectRouteAdapter
    private lateinit var transferRouteAdapter: TransferRouteAdapter
    
    private var selectedFromStop: Stop? = null
    private var selectedToStop: Stop? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[TransportViewModel::class.java]

        initViews()
        setupAdapters()
        setupListeners()
        observeViewModel()

        // Load transport data on first launch
        viewModel.loadTransportData()
    }

    private fun initViews() {
        fromStopInput = findViewById(R.id.fromStopInput)
        toStopInput = findViewById(R.id.toStopInput)
        swapButton = findViewById(R.id.swapButton)
        findRouteButton = findViewById(R.id.findRouteButton)
        progressBar = findViewById(R.id.progressBar)
        errorText = findViewById(R.id.errorText)
        resultsContainer = findViewById(R.id.resultsContainer)
        directRoutesRecycler = findViewById(R.id.directRoutesRecycler)
        transferRoutesRecycler = findViewById(R.id.transferRoutesRecycler)
        directRoutesTitle = findViewById(R.id.directRoutesTitle)
        transferRoutesTitle = findViewById(R.id.transferRoutesTitle)
        mapFab = findViewById(R.id.mapFab)
    }

    private fun setupAdapters() {
        // Autocomplete adapters
        fromAdapter = StopAutoCompleteAdapter(this) { stop ->
            selectedFromStop = stop
            viewModel.setFromStop(stop)
            fromStopInput.setText(stop.name)
            fromStopInput.dismissDropDown()
        }
        
        toAdapter = StopAutoCompleteAdapter(this) { stop ->
            selectedToStop = stop
            viewModel.setToStop(stop)
            toStopInput.setText(stop.name)
            toStopInput.dismissDropDown()
        }
        
        fromStopInput.setAdapter(fromAdapter)
        toStopInput.setAdapter(toAdapter)
        fromStopInput.threshold = 2
        toStopInput.threshold = 2

        // Route result adapters
        directRouteAdapter = DirectRouteAdapter { directRoute ->
            // Show route details or open map
            openMapWithRoute(directRoute.route.id)
        }
        
        transferRouteAdapter = TransferRouteAdapter { transferRoute ->
            // Show route details or open map
            Toast.makeText(this, 
                "Transfer at: ${transferRoute.transferStop.name}", 
                Toast.LENGTH_SHORT).show()
        }

        directRoutesRecycler.layoutManager = LinearLayoutManager(this)
        directRoutesRecycler.adapter = directRouteAdapter

        transferRoutesRecycler.layoutManager = LinearLayoutManager(this)
        transferRoutesRecycler.adapter = transferRouteAdapter
    }

    private fun setupListeners() {
        fromStopInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length >= 2) {
                    viewModel.searchStops(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        toStopInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length >= 2) {
                    viewModel.searchStops(s.toString())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        swapButton.setOnClickListener {
            viewModel.swapStops()
            val tempText = fromStopInput.text.toString()
            fromStopInput.setText(toStopInput.text.toString())
            toStopInput.setText(tempText)
            
            val temp = selectedFromStop
            selectedFromStop = selectedToStop
            selectedToStop = temp
        }

        findRouteButton.setOnClickListener {
            if (selectedFromStop != null && selectedToStop != null) {
                viewModel.findRoutes()
            } else {
                Toast.makeText(this, "Please select both stops", Toast.LENGTH_SHORT).show()
            }
        }

        mapFab.setOnClickListener {
            openMap()
        }

        fromStopInput.setOnItemClickListener { _, _, position, _ ->
            val stop = fromAdapter.getItem(position)
            stop?.let {
                selectedFromStop = it
                viewModel.setFromStop(it)
            }
        }

        toStopInput.setOnItemClickListener { _, _, position, _ ->
            val stop = toAdapter.getItem(position)
            stop?.let {
                selectedToStop = it
                viewModel.setToStop(it)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(this) { stops ->
            fromAdapter.updateStops(stops)
            toAdapter.updateStops(stops)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            findRouteButton.isEnabled = !isLoading
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                errorText.text = error
                errorText.visibility = View.VISIBLE
            } else {
                errorText.visibility = View.GONE
            }
        }

        viewModel.routeSearchResult.observe(this) { result ->
            if (result.hasResults) {
                resultsContainer.visibility = View.VISIBLE

                if (result.directRoutes.isNotEmpty()) {
                    directRoutesTitle.visibility = View.VISIBLE
                    directRoutesRecycler.visibility = View.VISIBLE
                    directRouteAdapter.updateRoutes(result.directRoutes)
                } else {
                    directRoutesTitle.visibility = View.GONE
                    directRoutesRecycler.visibility = View.GONE
                }

                if (result.transferRoutes.isNotEmpty()) {
                    transferRoutesTitle.visibility = View.VISIBLE
                    transferRoutesRecycler.visibility = View.VISIBLE
                    transferRouteAdapter.updateRoutes(result.transferRoutes)
                } else {
                    transferRoutesTitle.visibility = View.GONE
                    transferRoutesRecycler.visibility = View.GONE
                }
            } else {
                resultsContainer.visibility = View.GONE
            }
        }

        viewModel.dataLoadingProgress.observe(this) { state ->
            when (state) {
                is DataLoadingState.Loading -> {
                    Toast.makeText(this, R.string.loading_data, Toast.LENGTH_SHORT).show()
                }
                is DataLoadingState.Success -> {
                    Toast.makeText(this, R.string.data_loaded, Toast.LENGTH_SHORT).show()
                }
                is DataLoadingState.Error -> {
                    Toast.makeText(this, 
                        "${getString(R.string.error_loading_data)}: ${state.message}", 
                        Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        viewModel.allStops.observe(this) { stops ->
            // Update adapters with all stops initially
            if (stops.isNotEmpty()) {
                fromAdapter.updateStops(stops)
                toAdapter.updateStops(stops)
            }
        }
    }

    private fun openMap() {
        val intent = Intent(this, MapActivity::class.java)
        startActivity(intent)
    }

    private fun openMapWithRoute(routeId: Long) {
        val intent = Intent(this, MapActivity::class.java)
        intent.putExtra("route_id", routeId)
        startActivity(intent)
    }
}
