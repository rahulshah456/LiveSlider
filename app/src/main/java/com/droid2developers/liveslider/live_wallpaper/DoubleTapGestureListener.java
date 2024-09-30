package com.droid2developers.liveslider.live_wallpaper;

import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;


public class DoubleTapGestureListener extends SimpleOnGestureListener {

    private final LiveWallpaperService.ParallaxEngine parallaxEngine;

    DoubleTapGestureListener(LiveWallpaperService.ParallaxEngine parallaxEngine) {
        this.parallaxEngine = parallaxEngine;
    }

    /** (non-Javadoc)
     * @see SimpleOnGestureListener#onDoubleTap(MotionEvent)
     */
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (this.parallaxEngine.isAllowClickToChange() && this.parallaxEngine.isSlideShowEnabled()) {
            parallaxEngine.incrementWallpaper();
            parallaxEngine.changeWallpaper();
        }
        return true;
    }


}