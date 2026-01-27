package com.safestep.app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.safestep.app.R
import com.safestep.app.data.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * EventsFragment displays the full event history list.
 */
class EventsFragment : Fragment() {

    private val eventRepository = EventRepository()
    private lateinit var adapter: EventAdapter
    
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvEvents: RecyclerView
    private lateinit var tvNoEvents: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_events, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        rvEvents = view.findViewById(R.id.rvEvents)
        tvNoEvents = view.findViewById(R.id.tvNoEvents)
        
        setupAdapter()
        setupSwipeRefresh()
        loadEvents()
    }

    private fun setupAdapter() {
        adapter = EventAdapter { event ->
            Toast.makeText(requireContext(), "Event: ${event.event_id}", Toast.LENGTH_SHORT).show()
        }
        rvEvents.layoutManager = LinearLayoutManager(requireContext())
        rvEvents.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary, R.color.colorSecondary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.cardBackground)
        swipeRefresh.setOnRefreshListener {
            loadEvents()
        }
    }

    private fun loadEvents() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = eventRepository.getRecentEvents(50)
                
                withContext(Dispatchers.Main) {
                    adapter.updateEvents(events)
                    tvNoEvents.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                    rvEvents.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
                    swipeRefresh.isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(requireContext(), "Error loading events", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadEvents()
    }
}
