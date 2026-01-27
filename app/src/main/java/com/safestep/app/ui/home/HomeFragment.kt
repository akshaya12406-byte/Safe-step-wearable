package com.safestep.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.safestep.app.R
import com.safestep.app.data.DeviceRepository
import com.safestep.app.data.EventRepository
import com.safestep.app.model.Device
import com.safestep.app.ui.events.EventAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HomeFragment displays the main dashboard with:
 * - Reliability metrics (events count, uptime, last sync)
 * - Device cards with status indicators
 * - Recent events list
 */
class HomeFragment : Fragment() {

    private val deviceRepository by lazy { DeviceRepository(requireContext()) }
    private val eventRepository = EventRepository()
    
    private lateinit var deviceAdapter: DeviceCardAdapter
    private lateinit var eventAdapter: EventAdapter
    
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvDevices: RecyclerView
    private lateinit var rvRecentEvents: RecyclerView
    private lateinit var tvNoDevices: TextView
    private lateinit var tvNoEvents: TextView
    private lateinit var tvEventsCount: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvLastSync: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        rvDevices = view.findViewById(R.id.rvDevices)
        rvRecentEvents = view.findViewById(R.id.rvRecentEvents)
        tvNoDevices = view.findViewById(R.id.tvNoDevices)
        tvNoEvents = view.findViewById(R.id.tvNoEvents)
        tvEventsCount = view.findViewById(R.id.tvEventsCount)
        tvUptime = view.findViewById(R.id.tvUptime)
        tvLastSync = view.findViewById(R.id.tvLastSync)
        
        setupAdapters()
        setupClickListeners(view)
        setupSwipeRefresh()
        
        loadData()
    }

    private fun setupAdapters() {
        // Device adapter
        deviceAdapter = DeviceCardAdapter { device ->
            Toast.makeText(requireContext(), "Device: ${device.device_id}", Toast.LENGTH_SHORT).show()
        }
        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        rvDevices.adapter = deviceAdapter
        
        // Event adapter
        eventAdapter = EventAdapter { event ->
            // Navigate to event detail
            Toast.makeText(requireContext(), "Event: ${event.event_id}", Toast.LENGTH_SHORT).show()
        }
        rvRecentEvents.layoutManager = LinearLayoutManager(requireContext())
        rvRecentEvents.adapter = eventAdapter
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<MaterialButton>(R.id.btnAddDevice)?.setOnClickListener {
            findNavController().navigate(R.id.pairingFragment)
        }
        
        view.findViewById<MaterialButton>(R.id.btnViewAllEvents)?.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary, R.color.colorSecondary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.cardBackground)
        swipeRefresh.setOnRefreshListener {
            loadData()
        }
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load devices
                val devices = deviceRepository.getPairedDevices()
                
                // Load recent events (limit 5 for home screen)
                val events = eventRepository.getRecentEvents(5)
                
                // Load metrics
                val eventCount = eventRepository.getEventCountLast24Hours()
                
                withContext(Dispatchers.Main) {
                    // Update devices
                    deviceAdapter.submitList(devices)
                    tvNoDevices.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                    rvDevices.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
                    
                    // Update events
                    eventAdapter.updateEvents(events)
                    tvNoEvents.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                    rvRecentEvents.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
                    
                    // Update metrics
                    tvEventsCount.text = eventCount.toString()
                    tvUptime.text = calculateUptime(devices)
                    tvLastSync.text = calculateLastSync(devices)
                    
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

    /**
     * Calculate uptime percentage based on online devices.
     */
    private fun calculateUptime(devices: List<Device>): String {
        if (devices.isEmpty()) return "—"
        val onlineCount = devices.count { it.getStatus() == com.safestep.app.model.DeviceStatus.ONLINE }
        val percentage = (onlineCount * 100) / devices.size
        return "$percentage%"
    }

    /**
     * Calculate last sync from most recent device.
     */
    private fun calculateLastSync(devices: List<Device>): String {
        if (devices.isEmpty()) return "—"
        val primaryDevice = devices.find { it.is_primary } ?: devices.firstOrNull()
        return primaryDevice?.getLastSeenText() ?: "—"
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
