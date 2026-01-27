package com.safestep.app.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.safestep.app.R
import com.safestep.app.model.Event
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * RecyclerView adapter for displaying event cards.
 */
class EventAdapter(
    private var events: List<Event> = emptyList(),
    private val onEventClick: (Event) -> Unit = {}
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position], onEventClick)
    }

    override fun getItemCount(): Int = events.size

    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivEventType: ImageView = itemView.findViewById(R.id.ivEventType)
        private val tvEventType: TextView = itemView.findViewById(R.id.tvEventType)
        private val tvDeviceId: TextView = itemView.findViewById(R.id.tvDeviceId)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvImpact: TextView = itemView.findViewById(R.id.tvImpact)
        private val tvHandledStatus: TextView = itemView.findViewById(R.id.tvHandledStatus)

        fun bind(event: Event, onEventClick: (Event) -> Unit) {
            val context = itemView.context
            
            // Event type
            val eventTypeText = when (event.event_type) {
                "FALL_CONFIRMED" -> context.getString(R.string.event_type_fall)
                "IMPACT_ALERT" -> context.getString(R.string.event_type_impact)
                else -> event.event_type
            }
            tvEventType.text = eventTypeText
            
            // Icon color based on event type
            val iconColor = if (event.event_type == "FALL_CONFIRMED") {
                R.color.alertPrimary
            } else {
                R.color.colorSecondary
            }
            ivEventType.setColorFilter(ContextCompat.getColor(context, iconColor))
            
            // Device ID
            tvDeviceId.text = event.device_id
            
            // Timestamp
            tvTimestamp.text = formatTimestamp(event.timestamp)
            
            // Impact
            tvImpact.text = if (event.impact_g > 0) "${event.impact_g}g" else "—"
            
            // Handled status
            if (event.handled) {
                tvHandledStatus.text = context.getString(R.string.event_handled)
                tvHandledStatus.setTextColor(ContextCompat.getColor(context, R.color.statusOnline))
            } else {
                tvHandledStatus.text = context.getString(R.string.event_unhandled)
                tvHandledStatus.setTextColor(ContextCompat.getColor(context, R.color.colorSecondary))
            }
            
            // Click listener
            itemView.setOnClickListener { onEventClick(event) }
        }

        private fun formatTimestamp(isoTimestamp: String): String {
            if (isoTimestamp.isEmpty()) return "—"
            
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val outputFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)
                val date = inputFormat.parse(isoTimestamp)
                date?.let { outputFormat.format(it) } ?: isoTimestamp
            } catch (e: Exception) {
                isoTimestamp.take(16) // Fallback: show first 16 chars
            }
        }
    }
}
