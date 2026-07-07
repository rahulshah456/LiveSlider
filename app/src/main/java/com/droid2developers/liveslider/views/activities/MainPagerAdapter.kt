package com.droid2developers.liveslider.views.activities

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.droid2developers.liveslider.views.fragments.HomeFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        PAGE_HOME     -> HomeFragment.newInstance(false)
        PAGE_LOCK     -> HomeFragment.newInstance(true)
        else          -> throw IllegalStateException("Unknown page position: $position")
    }

    companion object {
        const val PAGE_HOME     = 0
        const val PAGE_LOCK     = 1
    }
}

