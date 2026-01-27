package com.safestep.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.safestep.app.R
import com.safestep.app.model.Event

class EventAdapter(private var events: List<Event>) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.tvEventType)
        val time: TextView = view.findViewById(R.id.tvEventTime)
        val device: TextView = view.findViewById(R.id.tvDeviceId)
        val status: TextView = view.findViewById(R.id.tvHandledStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.type.text = event.event_type
        holder.time.text = event.timestamp
        holder.device.text = "Device: ${event.device_id}"
        holder.status.text = if (event.handled) "Status: Handled" else "Status: New/Unhandled"
        
        if (event.event_type == "FALL_CONFIRMED") {
            holder.type.setTextColor(holder.itemView.context.getColor(com.google.android.material.R.color.design_default_color_error))
        } else {
            holder.type.setTextColor(holder.itemView.context.getColor(android.R.color.black))
        }
    }

    override fun getItemCount() = events.size
    
    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
