package com.safestep.app.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safestep.app.ui.components.EventCard
import com.safestep.app.ui.theme.SafeStepColors
import com.safestep.app.ui.theme.SafeStepTheme

/**
 * Event History Screen - Lists all fall detection events
 * 
 * Features:
 * - Filter tabs (All / Unacknowledged)
 * - Scrollable event list
 * - Click to view details
 */

data class EventItem(
    val id: String,
    val eventType: String,
    val deviceId: String,
    val timestamp: String,
    val impactG: String?,
    val isAcknowledged: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventHistoryScreen(
    events: List<EventItem> = emptyList(),
    onEventClick: (EventItem) -> Unit = {},
    onAcknowledgeClick: (EventItem) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(0) }
    val filters = listOf("All", "Unacknowledged")
    
    val filteredEvents = when (selectedFilter) {
        1 -> events.filter { !it.isAcknowledged }
        else -> events
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Event History",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SafeStepColors.Background,
                    titleContentColor = SafeStepColors.OnBackground
                )
            )
        },
        containerColor = SafeStepColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Tabs
            TabRow(
                selectedTabIndex = selectedFilter,
                containerColor = SafeStepColors.Surface,
                contentColor = SafeStepColors.OnSurface
            ) {
                filters.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Event List
            if (filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedFilter == 1) 
                            "No unacknowledged events" 
                        else 
                            "No events recorded yet",
                        color = SafeStepColors.OnSurfaceMuted,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredEvents) { event ->
                        EventCard(
                            eventType = event.eventType,
                            deviceId = event.deviceId,
                            timestamp = event.timestamp,
                            impactG = event.impactG,
                            isAcknowledged = event.isAcknowledged,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun EventHistoryScreenPreview() {
    SafeStepTheme {
        EventHistoryScreen(
            events = listOf(
                EventItem(
                    id = "1",
                    eventType = "FALL_CONFIRMED",
                    deviceId = "ESP32_01",
                    timestamp = "2 min ago",
                    impactG = "3.05",
                    isAcknowledged = false
                ),
                EventItem(
                    id = "2",
                    eventType = "FALL_CONFIRMED",
                    deviceId = "ESP32_01",
                    timestamp = "1 hour ago",
                    impactG = "2.10",
                    isAcknowledged = true
                )
            )
        )
    }
}
