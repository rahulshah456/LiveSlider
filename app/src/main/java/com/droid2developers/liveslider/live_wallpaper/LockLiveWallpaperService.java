package com.droid2developers.liveslider.live_wallpaper;

import android.content.SharedPreferences;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCK_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.PREFS_LOCK;

/**
 * A second live wallpaper service intended for the Lock screen.
 *
 * It extends {@link LiveWallpaperService} but operates on a completely separate
 * SharedPreferences file ("prefs_lock") so its wallpaper selection, playlist,
 * slideshow timer, and all other settings are fully independent of the home
 * screen instance.  The default image shown on first activation is
 * {@code wallpaper_default_lock.jpg} so the user can immediately tell it apart
 * from the home-screen service.
 */
public class LockLiveWallpaperService extends LiveWallpaperService {

    @Override
    protected SharedPreferences getEnginePrefs() {
        return getSharedPreferences(PREFS_LOCK, MODE_PRIVATE);
    }

    @Override
    protected String getDefaultWallpaperPath() {
        return DEFAULT_LOCK_LOCAL_PATH;
    }

    /** This service runs on the lock screen — advance on ACTION_SCREEN_ON. */
    @Override
    protected boolean isLockScreenService() {
        return true;
    }
}

