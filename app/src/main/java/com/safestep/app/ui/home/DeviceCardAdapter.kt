package com.safestep.app.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.safestep.app.R
import com.safestep.app.model.Device
import com.safestep.app.model.DeviceStatus

/**
 * RecyclerView adapter for displaying device cards.
 */
class DeviceCardAdapter(
    private val onDeviceClick: (Device) -> Unit = {}
) : ListAdapter<Device, DeviceCardAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_card, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position), onDeviceClick)
    }

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewStatusIndicator: View = itemView.findViewById(R.id.viewStatusIndicator)
        private val ivDeviceIcon: ImageView = itemView.findViewById(R.id.ivDeviceIcon)
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvPrimaryBadge: TextView = itemView.findViewById(R.id.tvPrimaryBadge)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvBattery: TextView = itemView.findViewById(R.id.tvBattery)
        private val tvLastSeen: TextView = itemView.findViewById(R.id.tvLastSeen)
        private val tvFirmware: TextView = itemView.findViewById(R.id.tvFirmware)

        fun bind(device: Device, onDeviceClick: (Device) -> Unit) {
            val context = itemView.context
            
            // Device name
            tvDeviceName.text = device.name.ifEmpty { device.device_id }
            
            // Primary badge visibility
            tvPrimaryBadge.visibility = if (device.is_primary) View.VISIBLE else View.GONE
            
            // Status indicator color and text
            val status = device.getStatus()
            val (statusColor, statusText) = when (status) {
                DeviceStatus.ONLINE -> Pair(R.color.statusOnline, R.string.status_online)
                DeviceStatus.WARNING -> Pair(R.color.statusWarning, R.string.status_warning)
                DeviceStatus.OFFLINE -> Pair(R.color.statusOffline, R.string.status_offline)
                DeviceStatus.UNKNOWN -> Pair(R.color.statusUnknown, R.string.status_unknown)
            }
            
            // Update status indicator
            val indicatorDrawable = viewStatusIndicator.background as? GradientDrawable
            indicatorDrawable?.setColor(ContextCompat.getColor(context, statusColor))
            
            // Update status text
            tvStatus.text = context.getString(statusText)
            tvStatus.setTextColor(ContextCompat.getColor(context, statusColor))
            
            // Metrics
            tvBattery.text = device.getBatteryText()
            tvLastSeen.text = device.getLastSeenText()
            tvFirmware.text = device.fw_version.ifEmpty { "—" }
            
            // Click listener
            itemView.setOnClickListener { onDeviceClick(device) }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.device_id == newItem.device_id
        }

        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem == newItem
        }
    }
}
