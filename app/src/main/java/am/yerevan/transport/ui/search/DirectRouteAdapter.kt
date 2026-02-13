package am.yerevan.transport.ui.search

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import am.yerevan.transport.R
import am.yerevan.transport.data.repository.DirectRoute

/**
 * Adapter for displaying direct routes
 */
class DirectRouteAdapter(
    private var routes: List<DirectRoute> = emptyList(),
    private val onRouteClick: (DirectRoute) -> Unit = {}
) : RecyclerView.Adapter<DirectRouteAdapter.DirectRouteViewHolder>() {

    inner class DirectRouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val routeNumber: TextView = itemView.findViewById(R.id.routeNumber)
        private val routeName: TextView = itemView.findViewById(R.id.routeName)
        private val stopCount: TextView = itemView.findViewById(R.id.stopCount)
        private val estimatedTime: TextView = itemView.findViewById(R.id.estimatedTime)
        private val colorIndicator: View = itemView.findViewById(R.id.routeColorIndicator)

        fun bind(directRoute: DirectRoute) {
            routeNumber.text = "Route ${directRoute.route.routeNumber}"
            routeName.text = directRoute.route.routeName
            stopCount.text = "${directRoute.stops.size} stops"
            estimatedTime.text = "${directRoute.estimatedTime} min"

            try {
                colorIndicator.setBackgroundColor(Color.parseColor(directRoute.route.color))
            } catch (e: Exception) {
                colorIndicator.setBackgroundColor(Color.parseColor("#2196F3"))
            }

            itemView.setOnClickListener {
                onRouteClick(directRoute)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectRouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_direct_route, parent, false)
        return DirectRouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: DirectRouteViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount(): Int = routes.size

    fun updateRoutes(newRoutes: List<DirectRoute>) {
        routes = newRoutes
        notifyDataSetChanged()
    }
}
