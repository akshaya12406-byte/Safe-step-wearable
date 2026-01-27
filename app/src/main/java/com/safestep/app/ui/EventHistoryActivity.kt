package com.safestep.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.safestep.app.R
import com.safestep.app.data.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventHistoryActivity : AppCompatActivity() {

    private val repository = EventRepository()
    private lateinit var adapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_history)

        val rvEvents = findViewById<RecyclerView>(R.id.rvEvents)
        rvEvents.layoutManager = LinearLayoutManager(this)
        adapter = EventAdapter(emptyList())
        rvEvents.adapter = adapter

        loadEvents()
    }

    private fun loadEvents() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = repository.getRecentEvents()
                withContext(Dispatchers.Main) {
                    if (events.isEmpty()) {
                        Toast.makeText(this@EventHistoryActivity, "No recent events found", Toast.LENGTH_SHORT).show()
                    }
                    adapter.updateEvents(events)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EventHistoryActivity, "Error loading history", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
