package com.droid2developers.liveslider.views.activities

import android.app.WallpaperManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.views.activities.MainPagerAdapter.Companion.PAGE_HOME
import com.droid2developers.liveslider.views.activities.MainPagerAdapter.Companion.PAGE_LOCK
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var bottomNavCard: MaterialCardView
    private var manager: WallpaperManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        manager = WallpaperManager.getInstance(this)

        viewPager    = findViewById(R.id.viewPager)
        bottomNav    = findViewById(R.id.bottomNav)
        bottomNavCard = findViewById(R.id.bottomNavCard)

        setupViewPager()
        setupBottomNav()
        setupSystemBarsAndCutouts()

        // Navigate to the sensible starting tab
        if (savedInstanceState == null) {
            viewPager.setCurrentItem(PAGE_HOME, false)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ViewPager2 + BottomNavigationView wiring
    // ──────────────────────────────────────────────────────────────────────────

    private fun setupViewPager() {
        viewPager.adapter = MainPagerAdapter(this)
        viewPager.offscreenPageLimit = 1          // keep both pages alive

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = pageToMenuId(position)
                if (bottomNav.selectedItemId != itemId) {
                    bottomNav.selectedItemId = itemId
                }
            }
        })
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            val page = menuIdToPage(item.itemId)
            if (page >= 0 && viewPager.currentItem != page) {
                viewPager.setCurrentItem(page, true)
            }
            true
        }
    }

    private fun pageToMenuId(page: Int) = when (page) {
        PAGE_HOME                              -> R.id.nav_home
        PAGE_LOCK                              -> R.id.nav_lock
        else                                   -> R.id.nav_home
    }

    private fun menuIdToPage(itemId: Int) = when (itemId) {
        R.id.nav_home     -> PAGE_HOME
        R.id.nav_lock     -> PAGE_LOCK
        else              -> -1
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Edge-to-edge inset handling
    // ──────────────────────────────────────────────────────────────────────────

    private fun setupSystemBarsAndCutouts() {
        ViewCompat.setOnApplyWindowInsetsListener(viewPager) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left  = insets.left,
                top   = insets.top,
                right = insets.right
            )
            Log.d(TAG, "Applied insets: l=${insets.left} t=${insets.top} r=${insets.right} b=${insets.bottom}")
            windowInsets
        }

        // Push the floating card above the system navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavCard) { view, windowInsets ->
            val navBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = view.layoutParams as MarginLayoutParams
            // 24 dp base gap + navigation bar height
            val baseDp = (24 * resources.displayMetrics.density).toInt()
            params.bottomMargin = baseDp + navBarHeight
            view.layoutParams = params
            windowInsets
        }
    }


}