package am.yerevan.transport.ui.search

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import am.yerevan.transport.R
import am.yerevan.transport.data.model.Stop

/**
 * Adapter for autocomplete stop search
 */
class StopAutoCompleteAdapter(
    context: Context,
    private val onStopSelected: (Stop) -> Unit
) : ArrayAdapter<Stop>(context, android.R.layout.simple_dropdown_item_1line) {

    private var stops: List<Stop> = emptyList()
    private var filteredStops: List<Stop> = emptyList()

    fun updateStops(newStops: List<Stop>) {
        stops = newStops
        filteredStops = newStops
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filteredStops.size

    override fun getItem(position: Int): Stop? = filteredStops.getOrNull(position)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)
        
        val stop = getItem(position)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        
        stop?.let {
            val displayText = if (it.nameEn != null && it.nameEn.isNotEmpty()) {
                "${it.name} (${it.nameEn})"
            } else {
                it.name
            }
            textView.text = displayText
        }

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                
                if (constraint.isNullOrEmpty()) {
                    results.values = emptyList<Stop>()
                    results.count = 0
                } else {
                    val query = constraint.toString().lowercase()
                    val filtered = stops.filter {
                        it.name.lowercase().contains(query) ||
                        (it.nameEn?.lowercase()?.contains(query) == true)
                    }
                    results.values = filtered
                    results.count = filtered.size
                }
                
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredStops = (results?.values as? List<Stop>) ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}
