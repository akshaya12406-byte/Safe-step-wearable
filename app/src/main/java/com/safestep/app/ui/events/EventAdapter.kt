package com.safestep.app.ui.events

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.safestep.app.R
import com.safestep.app.model.Event
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * RecyclerView adapter for displaying premium event cards with badges.
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
        private val eventIconContainer: FrameLayout = itemView.findViewById(R.id.eventIconContainer)
        private val ivEventType: ImageView = itemView.findViewById(R.id.ivEventType)
        private val tvEventType: TextView = itemView.findViewById(R.id.tvEventType)
        private val tvDeviceId: TextView = itemView.findViewById(R.id.tvDeviceId)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val impactBadge: TextView = itemView.findViewById(R.id.impactBadge)
        private val statusBanner: LinearLayout = itemView.findViewById(R.id.statusBanner)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)
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
            
            // Icon styling based on event type
            if (event.event_type == "FALL_CONFIRMED") {
                ivEventType.setImageResource(R.drawable.ic_fall_alert)
                ivEventType.setColorFilter(ContextCompat.getColor(context, R.color.alertPrimary))
                eventIconContainer.setBackgroundResource(R.drawable.bg_badge_attention)
            } else {
                ivEventType.setImageResource(R.drawable.ic_warning)
                ivEventType.setColorFilter(ContextCompat.getColor(context, R.color.statusWarning))
                eventIconContainer.setBackgroundResource(R.drawable.bg_badge_impact)
            }
            
            // Device ID
            tvDeviceId.text = event.device_id
            
            // Timestamp with bullet
            tvTimestamp.text = "• ${formatTimestamp(event.timestamp)}"
            
            // Impact badge
            if (event.impact_g > 0) {
                impactBadge.visibility = View.VISIBLE
                impactBadge.text = "${event.impact_g}g"
            } else {
                impactBadge.visibility = View.GONE
            }
            
            // Status banner styling
            if (event.handled) {
                tvHandledStatus.text = context.getString(R.string.event_handled)
                tvHandledStatus.setTextColor(ContextCompat.getColor(context, R.color.badgeHandledText))
                statusBanner.setBackgroundColor(ContextCompat.getColor(context, R.color.badgeHandledBackground))
                ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.badgeHandledText))
            } else {
                tvHandledStatus.text = context.getString(R.string.event_unhandled)
                tvHandledStatus.setTextColor(ContextCompat.getColor(context, R.color.badgeAttentionText))
                statusBanner.setBackgroundColor(ContextCompat.getColor(context, R.color.badgeAttentionBackground))
                ivStatusIcon.setImageResource(R.drawable.ic_warning)
                ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.badgeAttentionText))
            }
            
            // Click listener
            itemView.setOnClickListener { onEventClick(event) }
        }

        private fun formatTimestamp(timestamp: Timestamp?): String {
            if (timestamp == null) return "—"
            
            return try {
                val outputFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)
                outputFormat.format(timestamp.toDate())
            } catch (e: Exception) {
                "—"
            }
        }
    }
}
