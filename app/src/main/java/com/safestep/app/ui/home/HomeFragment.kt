package com.safestep.app.ui.home

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.safestep.app.R
import com.safestep.app.data.DeviceRepository
import com.safestep.app.data.EventRepository
import com.safestep.app.data.PostureRepository
import com.safestep.app.model.Device
import com.safestep.app.model.DeviceStatus
import com.safestep.app.model.Event
import com.safestep.app.model.Posture
import com.safestep.app.ui.events.EventAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HomeFragment displays the professional dashboard with:
 * - Current posture status (from Firestore, ESP32-written)
 * - Device status card
 * - Last fall summary
 * - Recent events list
 * 
 * The Android app ONLY displays data - all detection happens on ESP32.
 */
class HomeFragment : Fragment() {

    private val deviceRepository by lazy { DeviceRepository(requireContext()) }
    private val eventRepository = EventRepository()
    private val postureRepository = PostureRepository()
    
    private lateinit var eventAdapter: EventAdapter
    
    // Views
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var ivPostureIcon: ImageView
    private lateinit var tvPostureState: TextView
    private lateinit var tvPostureDuration: TextView
    private lateinit var tvPostureUpdated: TextView
    private lateinit var viewDeviceStatus: View
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceStatusText: TextView
    private lateinit var tvDeviceLastSeen: TextView
    private lateinit var tvDeviceBattery: TextView
    private lateinit var cardLastFall: MaterialCardView
    private lateinit var tvLastFallTime: TextView
    private lateinit var tvLastFallStatus: TextView
    private lateinit var rvRecentEvents: RecyclerView
    private lateinit var tvNoEvents: TextView
    
    private var postureJob: Job? = null
    private var primaryDeviceId: String = "ESP32_01"  // Default, or from SharedPrefs

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupEventAdapter()
        setupSwipeRefresh()
        setupClickListeners(view)
        
        // Get primary device ID
        primaryDeviceId = deviceRepository.getPrimaryDeviceId() ?: "ESP32_01"
        
        loadData()
        startPostureObserver()
    }

    private fun initViews(view: View) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        ivPostureIcon = view.findViewById(R.id.ivPostureIcon)
        tvPostureState = view.findViewById(R.id.tvPostureState)
        tvPostureDuration = view.findViewById(R.id.tvPostureDuration)
        tvPostureUpdated = view.findViewById(R.id.tvPostureUpdated)
        viewDeviceStatus = view.findViewById(R.id.viewDeviceStatus)
        tvDeviceName = view.findViewById(R.id.tvDeviceName)
        tvDeviceStatusText = view.findViewById(R.id.tvDeviceStatusText)
        tvDeviceLastSeen = view.findViewById(R.id.tvDeviceLastSeen)
        tvDeviceBattery = view.findViewById(R.id.tvDeviceBattery)
        cardLastFall = view.findViewById(R.id.cardLastFall)
        tvLastFallTime = view.findViewById(R.id.tvLastFallTime)
        tvLastFallStatus = view.findViewById(R.id.tvLastFallStatus)
        rvRecentEvents = view.findViewById(R.id.rvRecentEvents)
        tvNoEvents = view.findViewById(R.id.tvNoEvents)
    }

    private fun setupEventAdapter() {
        eventAdapter = EventAdapter { event ->
            Toast.makeText(requireContext(), "Event: ${event.event_id}", Toast.LENGTH_SHORT).show()
        }
        rvRecentEvents.layoutManager = LinearLayoutManager(requireContext())
        rvRecentEvents.adapter = eventAdapter
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary, R.color.colorSecondary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.cardBackground)
        swipeRefresh.setOnRefreshListener { loadData() }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.btnViewAllEvents)?.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }
        
        cardLastFall.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }
    }

    /**
     * Start real-time posture observer using Flow.
     */
    private fun startPostureObserver() {
        postureJob = CoroutineScope(Dispatchers.IO).launch {
            postureRepository.observePosture(primaryDeviceId).collect { posture ->
                withContext(Dispatchers.Main) {
                    updatePostureUI(posture)
                }
            }
        }
    }

    private fun updatePostureUI(posture: Posture?) {
        if (posture == null) {
            tvPostureState.text = getString(R.string.status_unknown)
            tvPostureDuration.text = ""
            tvPostureUpdated.text = ""
            ivPostureIcon.setImageResource(R.drawable.ic_posture_good)
            ivPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.onSurfaceMuted))
            return
        }
        
        tvPostureState.text = posture.getStateText()
        tvPostureDuration.text = "for ${posture.getDurationText()}"
        tvPostureUpdated.text = "Updated ${posture.getLastUpdatedText()}"
        
        if (posture.isGood()) {
            ivPostureIcon.setImageResource(R.drawable.ic_posture_good)
            ivPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.statusOnline))
            tvPostureState.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusOnline))
        } else {
            ivPostureIcon.setImageResource(R.drawable.ic_posture_bad)
            ivPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.statusWarning))
            tvPostureState.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusWarning))
        }
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load primary device
                val device = deviceRepository.getDevice(primaryDeviceId)
                
                // Load recent events
                val events = eventRepository.getRecentEvents(5)
                
                // Find last fall event
                val lastFall = events.find { it.event_type == "FALL_CONFIRMED" }
                
                withContext(Dispatchers.Main) {
                    updateDeviceUI(device)
                    updateLastFallUI(lastFall)
                    updateEventsUI(events)
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), "Error loading data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateDeviceUI(device: Device?) {
        if (device == null) {
            tvDeviceName.text = primaryDeviceId
            tvDeviceStatusText.text = getString(R.string.status_unknown)
            tvDeviceLastSeen.text = ""
            tvDeviceBattery.text = ""
            return
        }
        
        tvDeviceName.text = device.name.ifEmpty { device.device_id }
        tvDeviceBattery.text = "🔋 ${device.getBatteryText()}"
        tvDeviceLastSeen.text = "• Last seen ${device.getLastSeenText()}"
        
        val status = device.getStatus()
        val (statusColor, statusText) = when (status) {
            DeviceStatus.ONLINE -> Pair(R.color.statusOnline, R.string.status_online)
            DeviceStatus.WARNING -> Pair(R.color.statusWarning, R.string.status_warning)
            DeviceStatus.OFFLINE -> Pair(R.color.statusOffline, R.string.status_offline)
            DeviceStatus.UNKNOWN -> Pair(R.color.statusUnknown, R.string.status_unknown)
        }
        
        tvDeviceStatusText.text = getString(statusText)
        tvDeviceStatusText.setTextColor(ContextCompat.getColor(requireContext(), statusColor))
        
        val indicatorDrawable = viewDeviceStatus.background as? GradientDrawable
        indicatorDrawable?.setColor(ContextCompat.getColor(requireContext(), statusColor))
    }

    private fun updateLastFallUI(lastFall: Event?) {
        if (lastFall == null) {
            cardLastFall.visibility = View.GONE
            return
        }
        
        cardLastFall.visibility = View.VISIBLE
        tvLastFallTime.text = formatEventTime(lastFall.timestamp)
        
        if (lastFall.handled) {
            tvLastFallStatus.text = getString(R.string.event_handled)
            tvLastFallStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusOnline))
        } else {
            tvLastFallStatus.text = getString(R.string.event_unhandled)
            tvLastFallStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.colorSecondary))
        }
    }

    private fun updateEventsUI(events: List<Event>) {
        eventAdapter.updateEvents(events)
        tvNoEvents.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        rvRecentEvents.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun formatEventTime(isoTimestamp: String): String {
        if (isoTimestamp.isEmpty()) return "—"
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            val outputFormat = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
            inputFormat.parse(isoTimestamp)?.let { outputFormat.format(it) } ?: isoTimestamp
        } catch (e: Exception) {
            isoTimestamp.take(16)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        postureJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
