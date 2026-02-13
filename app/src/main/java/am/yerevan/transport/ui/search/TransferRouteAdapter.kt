package am.yerevan.transport.ui.search

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import am.yerevan.transport.R
import am.yerevan.transport.data.repository.TransferRoute

/**
 * Adapter for displaying transfer routes
 */
class TransferRouteAdapter(
    private var routes: List<TransferRoute> = emptyList(),
    private val onRouteClick: (TransferRoute) -> Unit = {}
) : RecyclerView.Adapter<TransferRouteAdapter.TransferRouteViewHolder>() {

    inner class TransferRouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val firstRouteNumber: TextView = itemView.findViewById(R.id.firstRouteNumber)
        private val secondRouteNumber: TextView = itemView.findViewById(R.id.secondRouteNumber)
        private val transferInfo: TextView = itemView.findViewById(R.id.transferInfo)
        private val walkDistance: TextView = itemView.findViewById(R.id.walkDistance)
        private val estimatedTime: TextView = itemView.findViewById(R.id.estimatedTime)
        private val firstColorIndicator: View = itemView.findViewById(R.id.firstRouteColorIndicator)
        private val secondColorIndicator: View = itemView.findViewById(R.id.secondRouteColorIndicator)

        fun bind(transferRoute: TransferRoute) {
            firstRouteNumber.text = "Route ${transferRoute.firstRoute.routeNumber}"
            secondRouteNumber.text = "Route ${transferRoute.secondRoute.routeNumber}"
            transferInfo.text = "Transfer at: ${transferRoute.transferStop.name}"
            estimatedTime.text = "${transferRoute.estimatedTime} min"

            if (transferRoute.walkingDistance > 0) {
                walkDistance.visibility = View.VISIBLE
                walkDistance.text = "Walk: ${transferRoute.walkingDistance.toInt()}m"
            } else {
                walkDistance.visibility = View.GONE
            }

            try {
                firstColorIndicator.setBackgroundColor(Color.parseColor(transferRoute.firstRoute.color))
                secondColorIndicator.setBackgroundColor(Color.parseColor(transferRoute.secondRoute.color))
            } catch (e: Exception) {
                firstColorIndicator.setBackgroundColor(Color.parseColor("#2196F3"))
                secondColorIndicator.setBackgroundColor(Color.parseColor("#4CAF50"))
            }

            itemView.setOnClickListener {
                onRouteClick(transferRoute)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferRouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transfer_route, parent, false)
        return TransferRouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransferRouteViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount(): Int = routes.size

    fun updateRoutes(newRoutes: List<TransferRoute>) {
        routes = newRoutes
        notifyDataSetChanged()
    }
}
