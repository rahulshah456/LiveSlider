package com.droid2developers.liveslider.views.activities

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.droid2developers.liveslider.utils.Constant
import com.droid2developers.liveslider.views.fragments.HomeFragment
import com.droid2developers.liveslider.views.fragments.SimpleHomeFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = if (Constant.supportsIndependentLockWallpaper()) 2 else 1

    override fun createFragment(position: Int): Fragment = when (position) {
        PAGE_HOME -> if (Constant.supportsIndependentLockWallpaper())
            HomeFragment.newInstance(false) else SimpleHomeFragment()
        PAGE_LOCK -> HomeFragment.newInstance(true)
        else      -> throw IllegalStateException("Unknown page position: $position")
    }

    companion object {
        const val PAGE_HOME     = 0
        const val PAGE_LOCK     = 1
    }
}

