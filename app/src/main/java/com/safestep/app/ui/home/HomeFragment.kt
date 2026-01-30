package com.safestep.app.ui.home

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.google.firebase.Timestamp
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
 * HomeFragment displays the premium dashboard with:
 * - Hero posture card with gradient background
 * - Device status with battery progress bar
 * - Last fall alert with action buttons
 * - Recent events list
 * - Emergency call button
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
    
    // Posture Card
    private lateinit var cardPosture: MaterialCardView
    private lateinit var postureContainer: LinearLayout
    private lateinit var ivPostureIcon: ImageView
    private lateinit var tvPostureState: TextView
    private lateinit var tvPostureActionGuidance: TextView
    private lateinit var tvPostureDuration: TextView
    private lateinit var tvPostureUpdated: TextView
    
    // Device Card
    private lateinit var cardDeviceStatus: MaterialCardView
    private lateinit var deviceIconContainer: FrameLayout
    private lateinit var ivDeviceIcon: ImageView
    private lateinit var viewDeviceStatus: View
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceStatusText: TextView
    private lateinit var tvDeviceLastSeen: TextView
    private lateinit var ivBatteryIcon: ImageView
    private lateinit var batteryProgress: ProgressBar
    private lateinit var tvDeviceBattery: TextView
    
    // Fall Alert Card
    private lateinit var cardLastFall: MaterialCardView
    private lateinit var tvLastFallTime: TextView
    private lateinit var tvLastFallStatus: TextView
    private lateinit var btnViewFall: MaterialButton
    private lateinit var btnMarkHandled: MaterialButton
    
    // Events
    private lateinit var rvRecentEvents: RecyclerView
    private lateinit var cardNoEvents: MaterialCardView
    
    // Emergency Button
    private lateinit var btnEmergencyCall: MaterialButton
    
    // Coroutine jobs for real-time observers
    private var postureJob: Job? = null
    private var deviceJob: Job? = null
    
    // Handler for periodic UI refresh (elapsed time updates)
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val REFRESH_INTERVAL_MS = 30_000L  // 30 seconds
    
    // Cached data for periodic refresh
    private var currentDevice: Device? = null
    private var currentPosture: Posture? = null
    private var lastFallEvent: Event? = null
    
    private var primaryDeviceId: String = "ESP32_01"

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
        
        loadEventsData()
        startRealTimeObservers()
        startPeriodicRefresh()
    }

    private fun initViews(view: View) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        
        // Posture Card
        cardPosture = view.findViewById(R.id.cardPosture)
        postureContainer = view.findViewById(R.id.postureContainer)
        ivPostureIcon = view.findViewById(R.id.ivPostureIcon)
        tvPostureState = view.findViewById(R.id.tvPostureState)
        tvPostureActionGuidance = view.findViewById(R.id.tvPostureActionGuidance)
        tvPostureDuration = view.findViewById(R.id.tvPostureDuration)
        tvPostureUpdated = view.findViewById(R.id.tvPostureUpdated)
        
        // Device Card
        cardDeviceStatus = view.findViewById(R.id.cardDeviceStatus)
        deviceIconContainer = view.findViewById(R.id.deviceIconContainer)
        ivDeviceIcon = view.findViewById(R.id.ivDeviceIcon)
        viewDeviceStatus = view.findViewById(R.id.viewDeviceStatus)
        tvDeviceName = view.findViewById(R.id.tvDeviceName)
        tvDeviceStatusText = view.findViewById(R.id.tvDeviceStatusText)
        tvDeviceLastSeen = view.findViewById(R.id.tvDeviceLastSeen)
        ivBatteryIcon = view.findViewById(R.id.ivBatteryIcon)
        batteryProgress = view.findViewById(R.id.batteryProgress)
        tvDeviceBattery = view.findViewById(R.id.tvDeviceBattery)
        
        // Fall Card
        cardLastFall = view.findViewById(R.id.cardLastFall)
        tvLastFallTime = view.findViewById(R.id.tvLastFallTime)
        tvLastFallStatus = view.findViewById(R.id.tvLastFallStatus)
        btnViewFall = view.findViewById(R.id.btnViewFall)
        btnMarkHandled = view.findViewById(R.id.btnMarkHandled)
        
        // Events
        rvRecentEvents = view.findViewById(R.id.rvRecentEvents)
        cardNoEvents = view.findViewById(R.id.cardNoEvents)
        
        // Emergency Button
        btnEmergencyCall = view.findViewById(R.id.btnEmergencyCall)
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
        swipeRefresh.setOnRefreshListener { 
            loadEventsData()
            refreshElapsedTimeDisplays()
        }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.btnViewAllEvents)?.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }
        
        cardLastFall.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }
        
        btnViewFall.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }
        
        btnMarkHandled.setOnClickListener {
            markLastFallAsHandled()
        }
        
        btnEmergencyCall.setOnClickListener {
            makeEmergencyCall()
        }
    }

    private fun startRealTimeObservers() {
        startPostureObserver()
        startDeviceObserver()
    }

    private fun startPostureObserver() {
        postureJob = CoroutineScope(Dispatchers.IO).launch {
            postureRepository.observePosture(primaryDeviceId).collect { posture ->
                currentPosture = posture
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        updatePostureUI(posture)
                    }
                }
            }
        }
    }

    private fun startDeviceObserver() {
        deviceJob = CoroutineScope(Dispatchers.IO).launch {
            deviceRepository.observeDevice(primaryDeviceId).collect { device ->
                currentDevice = device
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        updateDeviceUI(device)
                    }
                }
            }
        }
    }

    private fun startPeriodicRefresh() {
        refreshHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isAdded) {
                    refreshElapsedTimeDisplays()
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
                }
            }
        }, REFRESH_INTERVAL_MS)
    }

    private fun refreshElapsedTimeDisplays() {
        currentDevice?.let { device ->
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
        
        currentPosture?.let { posture ->
            tvPostureUpdated.text = "Updated ${posture.getLastUpdatedText()}"
        }
    }

    private fun updatePostureUI(posture: Posture?) {
        if (!isAdded) return
        
        if (posture == null) {
            tvPostureState.text = getString(R.string.status_unknown)
            tvPostureActionGuidance.visibility = View.GONE
            tvPostureDuration.visibility = View.GONE
            tvPostureUpdated.text = ""
            ivPostureIcon.setImageResource(R.drawable.ic_posture_good)
            ivPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.onSurfaceMuted))
            postureContainer.setBackgroundResource(R.drawable.bg_posture_good)
            return
        }
        
        // Show pitch/roll angles
        if (posture.pitch != 0.0 || posture.roll != 0.0) {
            tvPostureDuration.visibility = View.VISIBLE
            tvPostureDuration.text = "Pitch: ${posture.pitch.toInt()}° • Roll: ${posture.roll.toInt()}°"
        } else {
            tvPostureDuration.visibility = View.GONE
        }
        
        tvPostureUpdated.text = "Updated ${posture.getLastUpdatedText()}"
        
        if (posture.isGood()) {
            // Good Posture State
            tvPostureState.text = getString(R.string.posture_status_good)
            tvPostureActionGuidance.text = getString(R.string.posture_action_good)
            tvPostureActionGuidance.visibility = View.VISIBLE
            
            ivPostureIcon.setImageResource(R.drawable.ic_posture_good)
            ivPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
            tvPostureState.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            postureContainer.setBackgroundResource(R.drawable.bg_posture_good)
        } else {
            // Poor Posture State
            tvPostureState.text = getString(R.string.posture_status_poor)
            tvPostureActionGuidance.text = getString(R.string.posture_action_poor)
            tvPostureActionGuidance.visibility = View.VISIBLE
            
            ivPostureIcon.setImageResource(R.drawable.ic_posture_bad)
            ivPostureIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
            tvPostureState.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            postureContainer.setBackgroundResource(R.drawable.bg_posture_poor)
        }
    }

    private fun loadEventsData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = eventRepository.getRecentEvents(5)
                val lastFall = events.find { it.event_type == "FALL_CONFIRMED" }
                lastFallEvent = lastFall
                
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        updateLastFallUI(lastFall)
                        updateEventsUI(events)
                        swipeRefresh.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        swipeRefresh.isRefreshing = false
                        Toast.makeText(requireContext(), "Error loading events", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateDeviceUI(device: Device?) {
        if (!isAdded) return
        
        if (device == null) {
            tvDeviceName.text = primaryDeviceId
            tvDeviceStatusText.text = getString(R.string.status_unknown)
            tvDeviceStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.statusUnknown))
            tvDeviceLastSeen.text = ""
            tvDeviceBattery.text = "—"
            batteryProgress.progress = 0
            
            ivDeviceIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.statusUnknown))
            deviceIconContainer.setBackgroundResource(R.drawable.bg_badge_attention)
            
            val indicatorDrawable = viewDeviceStatus.background as? GradientDrawable
            indicatorDrawable?.setColor(ContextCompat.getColor(requireContext(), R.color.statusUnknown))
            return
        }
        
        tvDeviceName.text = device.name.ifEmpty { device.device_id }
        tvDeviceLastSeen.text = "• Last seen ${device.getLastSeenText()}"
        
        // Battery display
        val batteryPct = if (device.battery_pct >= 0) device.battery_pct else 0
        tvDeviceBattery.text = "${batteryPct}%"
        batteryProgress.progress = batteryPct
        
        // Battery icon color based on level
        val batteryColor = when {
            batteryPct >= 50 -> R.color.batteryFull
            batteryPct >= 20 -> R.color.batteryMedium
            else -> R.color.batteryLow
        }
        ivBatteryIcon.setColorFilter(ContextCompat.getColor(requireContext(), batteryColor))
        
        // Status
        val status = device.getStatus()
        val (statusColor, statusText, iconBg) = when (status) {
            DeviceStatus.ONLINE -> Triple(R.color.statusOnline, R.string.status_online, R.drawable.bg_badge_handled)
            DeviceStatus.WARNING -> Triple(R.color.statusWarning, R.string.status_warning, R.drawable.bg_badge_impact)
            DeviceStatus.OFFLINE -> Triple(R.color.statusOffline, R.string.status_offline, R.drawable.bg_badge_attention)
            DeviceStatus.UNKNOWN -> Triple(R.color.statusUnknown, R.string.status_unknown, R.drawable.bg_badge_attention)
        }
        
        tvDeviceStatusText.text = getString(statusText)
        tvDeviceStatusText.setTextColor(ContextCompat.getColor(requireContext(), statusColor))
        
        ivDeviceIcon.setColorFilter(ContextCompat.getColor(requireContext(), statusColor))
        deviceIconContainer.setBackgroundResource(iconBg)
        
        val indicatorDrawable = viewDeviceStatus.background as? GradientDrawable
        indicatorDrawable?.setColor(ContextCompat.getColor(requireContext(), statusColor))
    }

    private fun updateLastFallUI(lastFall: Event?) {
        if (!isAdded) return
        
        if (lastFall == null) {
            cardLastFall.visibility = View.GONE
            return
        }
        
        cardLastFall.visibility = View.VISIBLE
        tvLastFallTime.text = formatEventTime(lastFall.timestamp)
        
        if (lastFall.handled) {
            tvLastFallStatus.text = getString(R.string.event_handled)
            tvLastFallStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.badgeHandledText))
            tvLastFallStatus.setBackgroundResource(R.drawable.bg_badge_handled)
            btnMarkHandled.visibility = View.GONE
        } else {
            tvLastFallStatus.text = getString(R.string.event_unhandled)
            tvLastFallStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.badgeAttentionText))
            tvLastFallStatus.setBackgroundResource(R.drawable.bg_badge_attention)
            btnMarkHandled.visibility = View.VISIBLE
        }
    }

    private fun updateEventsUI(events: List<Event>) {
        if (!isAdded) return
        
        eventAdapter.updateEvents(events)
        cardNoEvents.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        rvRecentEvents.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun formatEventTime(timestamp: Timestamp?): String {
        if (timestamp == null) return "—"
        return try {
            val outputFormat = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
            outputFormat.format(timestamp.toDate())
        } catch (e: Exception) {
            "—"
        }
    }
    
    private fun markLastFallAsHandled() {
        lastFallEvent?.let { event ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    eventRepository.markEventAsHandled(event.event_id)
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Marked as handled", Toast.LENGTH_SHORT).show()
                            loadEventsData()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun makeEmergencyCall() {
        // Get emergency number from SharedPreferences
        val prefs = requireContext().getSharedPreferences("com.safestep.app.SETTINGS", 0)
        val emergencyNumber = prefs.getString("emergency_number", "911") ?: "911"
        val isDemoMode = prefs.getBoolean("demo_mode", true)
        
        if (isDemoMode) {
            Toast.makeText(requireContext(), "📞 DEMO: Would call $emergencyNumber", Toast.LENGTH_LONG).show()
        } else {
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$emergencyNumber")
                }
                startActivity(intent)
            } catch (e: SecurityException) {
                // No CALL_PHONE permission, open dialer instead
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$emergencyNumber")
                }
                startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        postureJob?.cancel()
        deviceJob?.cancel()
        refreshHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        loadEventsData()
        refreshElapsedTimeDisplays()
    }
    
    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacksAndMessages(null)
    }
}
