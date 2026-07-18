package com.droid2developers.liveslider.views

import android.app.Application
import com.google.android.material.color.DynamicColors
import org.greenrobot.eventbus.EventBus

class LiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        EventBus.builder()
            .logNoSubscriberMessages(false)
            .sendNoSubscriberEvent(false)
            .installDefaultEventBus()
    }
}