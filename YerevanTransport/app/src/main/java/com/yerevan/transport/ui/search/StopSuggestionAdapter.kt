package com.yerevan.transport.ui.search

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.yerevan.transport.R
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.data.local.entity.TransportStop

/**
 * Custom adapter for stop name autocomplete suggestions.
 */
class StopSuggestionAdapter(
    context: Context,
    private var stops: List<TransportStop> = emptyList()
) : ArrayAdapter<TransportStop>(context, R.layout.item_stop_suggestion, stops), Filterable {

    private var filteredStops: List<TransportStop> = stops

    fun updateStops(newStops: List<TransportStop>) {
        stops = newStops
        filteredStops = newStops
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filteredStops.size

    override fun getItem(position: Int): TransportStop? =
        if (position < filteredStops.size) filteredStops[position] else null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_stop_suggestion, parent, false)

        val stop = getItem(position) ?: return view

        view.findViewById<TextView>(R.id.tvStopName).text = stop.name
        view.findViewById<TextView>(R.id.tvStopCommunity).text = stop.community
        view.findViewById<TextView>(R.id.tvStopType).text = when (stop.stopType) {
            StopType.BUS -> "Bus"
            StopType.METRO -> "Metro"
            StopType.TROLLEYBUS -> "Trolleybus"
            StopType.MINIBUS -> "Minibus"
        }

        return view
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            results.values = stops
            results.count = stops.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredStops = results?.values as? List<TransportStop> ?: emptyList()
            if (filteredStops.isNotEmpty()) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return (resultValue as? TransportStop)?.name ?: ""
        }
    }
}
