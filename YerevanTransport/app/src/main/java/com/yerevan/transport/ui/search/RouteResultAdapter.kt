package com.yerevan.transport.ui.search

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yerevan.transport.R
import com.yerevan.transport.data.local.entity.StopType
import com.yerevan.transport.util.RouteResult
import com.yerevan.transport.util.SegmentType

class RouteResultAdapter(
    private val onItemClick: (RouteResult) -> Unit
) : ListAdapter<RouteResult, RouteResultAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivRouteType: ImageView = itemView.findViewById(R.id.ivRouteType)
        private val tvRouteSummary: TextView = itemView.findViewById(R.id.tvRouteSummary)
        private val tvTransferBadge: TextView = itemView.findViewById(R.id.tvTransferBadge)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvStops: TextView = itemView.findViewById(R.id.tvStops)
        private val layoutWalking: LinearLayout = itemView.findViewById(R.id.layoutWalking)
        private val tvWalkingInfo: TextView = itemView.findViewById(R.id.tvWalkingInfo)
        private val layoutSegments: LinearLayout = itemView.findViewById(R.id.layoutSegments)

        fun bind(result: RouteResult) {
            tvRouteSummary.text = result.getSummary()
            tvTime.text = "~${result.getFormattedTime()}"
            tvDistance.text = result.getFormattedDistance()

            // Count total stops
            val totalStops = result.segments
                .filter { it.segmentType == SegmentType.TRANSIT }
                .sumOf { it.stops.size }
            tvStops.text = "$totalStops stops"

            // Set route type icon
            val firstTransitSegment = result.segments.firstOrNull { it.segmentType == SegmentType.TRANSIT }
            when (firstTransitSegment?.route?.routeType) {
                StopType.METRO -> ivRouteType.setImageResource(R.drawable.ic_metro)
                else -> ivRouteType.setImageResource(R.drawable.ic_bus)
            }

            // Transfer badge
            if (result.transferCount > 0) {
                tvTransferBadge.visibility = View.VISIBLE
                tvTransferBadge.text = "${result.transferCount} transfer${if (result.transferCount > 1) "s" else ""}"
            } else {
                tvTransferBadge.visibility = View.GONE
            }

            // Walking info
            if (result.totalWalkingMeters > 0) {
                layoutWalking.visibility = View.VISIBLE
                tvWalkingInfo.text = "Walk ${result.totalWalkingMeters}m to transfer"
            } else {
                layoutWalking.visibility = View.GONE
            }

            // Segments visualization
            layoutSegments.removeAllViews()
            for (segment in result.segments) {
                val dot = View(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(12, 12).apply {
                        marginEnd = 4
                    }
                    background = itemView.context.getDrawable(R.drawable.bg_route_type_badge)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        when (segment.segmentType) {
                            SegmentType.TRANSIT -> try {
                                Color.parseColor(segment.route?.color ?: "#2196F3")
                            } catch (e: Exception) {
                                Color.parseColor("#2196F3")
                            }
                            SegmentType.WALKING -> Color.parseColor("#78909C")
                        }
                    )
                }
                layoutSegments.addView(dot)

                // Add a line between segments
                if (segment != result.segments.last()) {
                    val line = View(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(24, 2).apply {
                            marginEnd = 4
                            topMargin = 5
                        }
                        setBackgroundColor(Color.parseColor("#E0E0E0"))
                    }
                    layoutSegments.addView(line)
                }
            }

            itemView.setOnClickListener { onItemClick(result) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RouteResult>() {
        override fun areItemsTheSame(oldItem: RouteResult, newItem: RouteResult): Boolean {
            return oldItem.getSummary() == newItem.getSummary() &&
                    oldItem.totalTimeSeconds == newItem.totalTimeSeconds
        }

        override fun areContentsTheSame(oldItem: RouteResult, newItem: RouteResult): Boolean {
            return oldItem == newItem
        }
    }
}
