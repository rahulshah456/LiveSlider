package com.droid2developers.liveslider.views.activities

import android.app.WallpaperManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.views.fragments.ActivateFragment
import com.droid2developers.liveslider.views.fragments.HomeFragment
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }

    private var intro = false
    private var manager: WallpaperManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // Handle system bars and display cutouts
        setupSystemBarsAndCutouts()

        manager = WallpaperManager.getInstance(this)

        if (savedInstanceState == null) {
            setupInitialFragment()
        }
    }

    /**
     * Sets up proper handling of system bars and display cutouts/notches
     * Following Android's modern edge-to-edge guidelines
     */
    private fun setupSystemBarsAndCutouts() {
        val container = findViewById<android.widget.FrameLayout>(R.id.container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
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

            Log.d(TAG, "Applied insets: left=${insets.left}, top=${insets.top}, right=${insets.right}, bottom=${insets.bottom}")

            // Return the insets to continue propagation
            windowInsets
        }
    }

    private fun isWallpaperServiceActive(): Boolean {
        val wallpaperInfo = manager?.wallpaperInfo
        return wallpaperInfo != null && wallpaperInfo.packageName == this.packageName
    }

    private fun setupInitialFragment() {
        val initialFragment = if (isWallpaperServiceActive()) {
            intro = false
            HomeFragment()
        } else {
            intro = true
            ActivateFragment()
        }

        supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
            .replace(R.id.container, initialFragment)
            .commit()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("intro", intro)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        intro = savedInstanceState.getBoolean("intro")
    }

    override fun onStart() {
        super.onStart()
        val isWallpaperActive = isWallpaperServiceActive()

        if (intro && isWallpaperActive) {
            supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.container, HomeFragment())
                .commit()
            intro = false
        } else if (!intro && !isWallpaperActive) {
            supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.container, ActivateFragment())
                .commit()
            intro = true
        }
    }

}