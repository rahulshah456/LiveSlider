package com.droid2developers.liveslider.utils

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import org.jetbrains.annotations.Contract

object DeviceMetrics {
    val TAG: String = DeviceMetrics::class.java.simpleName


    //Obtain DisplayMetrics
    @Contract("null -> null")
    private fun getDisplayMetrics(context: Context?): DisplayMetrics? {
        if (context == null) {
            return null
        }
        return context.resources.displayMetrics
    }

    //Get display width
    fun getDisplayWidth(context: Context?): Int {
        return getDisplayMetrics(context)!!.widthPixels
    }


    //Get display height
    fun getDisplayHeight(context: Context?): Int {
        return getDisplayMetrics(context)!!.heightPixels
    }


    fun getNavigationBarHeight(context: Context): Int {
        val resources = context.resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId)
        }
        return 0
    }


    // Get Real Display Metrics
    private fun getRealDisplayMetrics(windowManager: WindowManager): DisplayMetrics {
        val display = windowManager.defaultDisplay
        val realDisplayMetrics = DisplayMetrics()
        display.getRealMetrics(realDisplayMetrics)
        return realDisplayMetrics
    }

    //Get display width
    fun getRealDisplayWidth(windowManager: WindowManager): Int {
        return getRealDisplayMetrics(windowManager).widthPixels
    }


    //Get display height
    fun getRealDisplayHeight(windowManager: WindowManager): Int {
        return getRealDisplayMetrics(windowManager).heightPixels
    }


    @RequiresApi(api = Build.VERSION_CODES.R)
    fun getMetrics(windowManager: WindowManager): Size {
        val metrics = windowManager.currentWindowMetrics
        // Gets all excluding insets
        val windowInsets = metrics.windowInsets
        val insets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.navigationBars()
                    or WindowInsets.Type.displayCutout()
        )

        val insetsWidth = insets.right + insets.left
        val insetsHeight = insets.top + insets.bottom

        // Legacy size that Display#getSize reports
        val bounds = metrics.bounds
        return Size(
            bounds.width() - insetsWidth,
            bounds.height() + insetsHeight
        )
    }
}
