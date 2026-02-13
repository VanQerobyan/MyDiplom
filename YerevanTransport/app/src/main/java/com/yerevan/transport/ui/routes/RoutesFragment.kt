package com.yerevan.transport.ui.routes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.data.local.entity.TransportRoute
import com.yerevan.transport.databinding.FragmentRoutesBinding
import com.yerevan.transport.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RoutesFragment : Fragment() {

    private var _binding: FragmentRoutesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var routeListAdapter: RouteListAdapter

    private var allRoutes: List<TransportRoute> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilters()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        routeListAdapter = RouteListAdapter { route ->
            showRouteDetails(route)
        }
        binding.rvRoutesList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRoutesList.adapter = routeListAdapter
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener {
            routeListAdapter.submitList(allRoutes)
        }

        binding.chipBus.setOnClickListener {
            routeListAdapter.submitList(allRoutes.filter { it.routeType == StopType.BUS })
        }

        binding.chipMetro.setOnClickListener {
            routeListAdapter.submitList(allRoutes.filter { it.routeType == StopType.METRO })
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allRoutes.collectLatest { routes ->
                allRoutes = routes
                routeListAdapter.submitList(routes)
            }
        }
    }

    private fun showRouteDetails(route: TransportRoute) {
        viewLifecycleOwner.lifecycleScope.launch {
            val stops = viewModel.getStopsForRoute(route.id)
            val stopsText = stops.joinToString("\n") { "  ${it.name} (${it.community})" }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Route ${route.routeNumber}")
                .setMessage(buildString {
                    appendLine("Name: ${route.routeName}")
                    appendLine("Type: ${route.routeType.name}")
                    appendLine("Interval: Every ${route.avgIntervalMinutes} min")
                    appendLine("Hours: ${route.operatingHours}")
                    appendLine("\nStops (${stops.size}):")
                    appendLine(stopsText)
                })
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
