package com.droid2developers.liveslider.views.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
        setContentView(R.layout.activity_change_wallpaper)

        // Initializing ViewModels
        val wallpaperViewModel =
            ViewModelProvider(this)[WallpaperViewModel::class.java]
        val playlistViewModel =
            ViewModelProvider(this)[PlaylistViewModel::class.java]

        // Initializing ViewPager
        val viewPager = findViewById<ViewPager>(R.id.viewPagerId)
        val tabAdapter = TabAdapter(supportFragmentManager)
        // Adding required fragments
        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutId)
        tabAdapter.addFragment(SingleFragment(), "Single")
        tabAdapter.addFragment(SlideshowFragment(), "Slideshow")
        // Setting up final preview
        viewPager.adapter = tabAdapter
        tabLayout.setupWithViewPager(viewPager)

        val backButton = findViewById<CardView>(R.id.backButtonId)
        backButton.setOnClickListener { v: View? -> onBackPressedDispatcher.onBackPressed() }
    }
}
