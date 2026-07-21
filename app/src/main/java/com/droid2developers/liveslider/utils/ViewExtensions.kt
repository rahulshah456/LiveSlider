package com.droid2developers.liveslider.utils

import android.view.HapticFeedbackConstants
import com.google.android.material.slider.Slider

/** Long-press resets the slider to [defaultValue], with a confirm haptic. */
fun Slider.resetOnLongPress(defaultValue: Float) {
    setOnLongClickListener {
        value = defaultValue
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        true
    }
}
