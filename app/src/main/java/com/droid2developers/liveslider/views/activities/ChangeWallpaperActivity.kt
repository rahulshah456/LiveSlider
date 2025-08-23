package com.droid2developers.liveslider.views.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager.widget.ViewPager
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.adapters.TabAdapter
import com.droid2developers.liveslider.viewmodel.PlaylistViewModel
import com.droid2developers.liveslider.viewmodel.WallpaperViewModel
import com.droid2developers.liveslider.views.fragments.SingleFragment
import com.droid2developers.liveslider.views.fragments.SlideshowFragment
import com.google.android.material.tabs.TabLayout

class ChangeWallpaperActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_change_wallpaper)

        // Handle system bars and display cutouts
        setupSystemBarsAndCutouts()

        // Initializing ViewModels
        val wallpaperViewModel =
            ViewModelProvider(this)[WallpaperViewModel::class.java]
        val playlistViewModel =
            ViewModelProvider(this)[PlaylistViewModel::class.java]

        // Initializing ViewPager
        val viewPager = findViewById<ViewPager>(R.id.viewPagerId)
        val tabAdapter = TabAdapter(supportFragmentManager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutId)

        // Adding required fragments
        tabAdapter.addFragment(SingleFragment(), "Single")
        tabAdapter.addFragment(SlideshowFragment(), "Slideshow")

        // Setting up final preview
        viewPager.adapter = tabAdapter
        tabLayout.setupWithViewPager(viewPager)

        val backButton = findViewById<CardView>(R.id.backButtonId)
        backButton.setOnClickListener { v: View? -> onBackPressedDispatcher.onBackPressed() }
    }

    /**
     * Sets up proper handling of system bars and display cutouts/notches
     * Following Android's modern edge-to-edge guidelines
     */
    private fun setupSystemBarsAndCutouts() {
        val rootView = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            // Apply padding to avoid system bars and cutouts
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )

            // Return the insets to continue propagation
            windowInsets
        }
    }
}
