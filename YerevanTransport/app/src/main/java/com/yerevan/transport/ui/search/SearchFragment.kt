package com.yerevan.transport.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yerevan.transport.data.local.entity.TransportStop
import com.yerevan.transport.databinding.FragmentSearchBinding
import com.yerevan.transport.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var fromAdapter: StopSuggestionAdapter
    private lateinit var toAdapter: StopSuggestionAdapter
    private lateinit var routeResultAdapter: RouteResultAdapter

    private var isSettingFromText = false
    private var isSettingToText = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupSearchFields()
        setupSwapButton()
        observeViewModel()
    }

    private fun setupAdapters() {
        fromAdapter = StopSuggestionAdapter(requireContext())
        toAdapter = StopSuggestionAdapter(requireContext())

        binding.etFromStop.setAdapter(fromAdapter)
        binding.etToStop.setAdapter(toAdapter)

        routeResultAdapter = RouteResultAdapter { result ->
            viewModel.selectRouteResult(result)
        }
        binding.rvRouteResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRouteResults.adapter = routeResultAdapter
    }

    private fun setupSearchFields() {
        binding.etFromStop.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSettingFromText) {
                    viewModel.searchFromStop(s?.toString() ?: "")
                }
            }
        })

        binding.etToStop.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isSettingToText) {
                    viewModel.searchToStop(s?.toString() ?: "")
                }
            }
        })

        binding.etFromStop.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val stop = fromAdapter.getItem(position) ?: return@OnItemClickListener
                isSettingFromText = true
                viewModel.selectFromStop(stop)
                binding.etFromStop.dismissDropDown()
                isSettingFromText = false
            }

        binding.etToStop.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val stop = toAdapter.getItem(position) ?: return@OnItemClickListener
                isSettingToText = true
                viewModel.selectToStop(stop)
                binding.etToStop.dismissDropDown()
                isSettingToText = false
            }
    }

    private fun setupSwapButton() {
        binding.btnSwap.setOnClickListener {
            isSettingFromText = true
            isSettingToText = true

            viewModel.swapStops()

            viewModel.fromStopQuery.value.let { binding.etFromStop.setText(it) }
            viewModel.toStopQuery.value.let { binding.etToStop.setText(it) }

            isSettingFromText = false
            isSettingToText = false
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fromSuggestions.collectLatest { suggestions ->
                fromAdapter.updateStops(suggestions)
                if (suggestions.isNotEmpty() && !isSettingFromText) {
                    binding.etFromStop.showDropDown()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.toSuggestions.collectLatest { suggestions ->
                toAdapter.updateStops(suggestions)
                if (suggestions.isNotEmpty() && !isSettingToText) {
                    binding.etToStop.showDropDown()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routeResults.collectLatest { results ->
                routeResultAdapter.submitList(results)
                if (results.isNotEmpty()) {
                    binding.tvResultsHeader.visibility = View.VISIBLE
                    binding.tvResultsHeader.text = "${results.size} route(s) found"
                } else {
                    binding.tvResultsHeader.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isCalculating.collectLatest { isCalculating ->
                binding.progressCalculating.visibility =
                    if (isCalculating) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stopCount.collectLatest { count ->
                binding.tvStopCount.text = count.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routeCount.collectLatest { count ->
                binding.tvRouteCount.text = count.toString()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
