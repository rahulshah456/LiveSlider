package com.droid2developers.liveslider.views.activities

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.droid2developers.liveslider.utils.Constant
import com.droid2developers.liveslider.views.fragments.HelpFragment
import com.droid2developers.liveslider.views.fragments.HomeFragment
import com.droid2developers.liveslider.views.fragments.SimpleHomeFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = if (Constant.supportsIndependentLockWallpaper()) 3 else 2

    override fun createFragment(position: Int): Fragment = when (position) {
        PAGE_HOME -> if (Constant.supportsIndependentLockWallpaper())
            HomeFragment.newInstance(false) else SimpleHomeFragment()
        PAGE_LOCK -> HomeFragment.newInstance(true)
        PAGE_HELP -> HelpFragment()
        else      -> throw IllegalStateException("Unknown page position: $position")
    }

    companion object {
        const val PAGE_HOME = 0
        const val PAGE_LOCK = 1

        // Help occupies position 1 when Lock isn't available, else position 2.
        val PAGE_HELP: Int
            get() = if (Constant.supportsIndependentLockWallpaper()) 2 else 1
    }
}

