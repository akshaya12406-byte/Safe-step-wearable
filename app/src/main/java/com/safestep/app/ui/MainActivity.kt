package com.safestep.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.safestep.app.R

/**
 * MainActivity serves as the container for Fragment-based navigation.
 * Uses Navigation Component with BottomNavigationView.
 * 
 * Navigation destinations:
 * - Home (Dashboard with Device Cards)
 * - Events (Event History)
 * - Settings (Configuration)
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupNavigation()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
        
        // Handle reselection (scroll to top or refresh)
        bottomNav.setOnItemReselectedListener { menuItem ->
            // Optional: Implement scroll to top or refresh behavior
            when (menuItem.itemId) {
                R.id.homeFragment -> {
                    // Could scroll to top of device list
                }
                R.id.eventsFragment -> {
                    // Could scroll to top of events list
                }
            }
        }
    }
    
    /**
     * Handle back navigation properly with Navigation Component
     */
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }
}
