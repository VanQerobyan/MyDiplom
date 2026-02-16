package com.yerevan.transport.ui.routes

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yerevan.transport.R
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.data.local.entity.TransportRoute

class RouteListAdapter(
    private val onItemClick: (TransportRoute) -> Unit
) : ListAdapter<TransportRoute, RouteListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRouteNumber: TextView = itemView.findViewById(R.id.tvRouteNumber)
        private val tvRouteName: TextView = itemView.findViewById(R.id.tvRouteName)
        private val tvRouteDetails: TextView = itemView.findViewById(R.id.tvRouteDetails)
        private val tvRouteType: TextView = itemView.findViewById(R.id.tvRouteType)

        fun bind(route: TransportRoute) {
            tvRouteNumber.text = route.routeNumber
            tvRouteName.text = route.routeName

            tvRouteDetails.text = buildString {
                append("Every ${route.avgIntervalMinutes} min")
                append(" | ")
                append(route.operatingHours)
            }

            tvRouteType.text = when (route.routeType) {
                StopType.BUS -> "Bus"
                StopType.METRO -> "Metro"
                StopType.TROLLEYBUS -> "Trolleybus"
                StopType.MINIBUS -> "Minibus"
            }

            // Set badge color
            try {
                tvRouteNumber.background.setTint(Color.parseColor(route.color))
            } catch (e: Exception) {
                tvRouteNumber.background.setTint(Color.parseColor("#2196F3"))
            }

            itemView.setOnClickListener { onItemClick(route) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TransportRoute>() {
        override fun areItemsTheSame(oldItem: TransportRoute, newItem: TransportRoute): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransportRoute, newItem: TransportRoute): Boolean {
            return oldItem == newItem
        }
    }
}
