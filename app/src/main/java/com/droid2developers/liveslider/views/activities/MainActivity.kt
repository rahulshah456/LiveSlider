package com.droid2developers.liveslider.views.activities

import android.app.WallpaperManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.views.fragments.ActivateFragment
import com.droid2developers.liveslider.views.fragments.HomeFragment

class MainActivity : AppCompatActivity() {

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }

    private var intro = false
    private var manager: WallpaperManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        manager = WallpaperManager.getInstance(this)
        if (savedInstanceState == null) {
            if (manager?.wallpaperInfo == null ||
                manager?.wallpaperInfo?.packageName != this.packageName
            ) {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.container, ActivateFragment())
                    .commit()
                intro = true
            } else {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.container, HomeFragment())
                    .commit()
                intro = false
            }
        }
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
        if (intro && manager?.wallpaperInfo != null && manager?.wallpaperInfo?.packageName == this.packageName) {
            supportFragmentManager
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.container, HomeFragment())
                .commit()
            intro = false
        } else if (!intro && (manager?.wallpaperInfo == null ||
                    manager?.wallpaperInfo?.packageName != this.packageName)
        ) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, ActivateFragment())
                .commit()
            intro = true
        }
    }


}