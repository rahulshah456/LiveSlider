package com.mylaputa.beleco.live_wallpaper;

import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;


public class DoubleTapGestureListener extends SimpleOnGestureListener {

    private LiveWallpaperService.ParallaxEngine parallaxEngine;

    DoubleTapGestureListener(LiveWallpaperService.ParallaxEngine parallaxEngine) {
        this.parallaxEngine = parallaxEngine;
    }

    /** (non-Javadoc)
     * @see android.view.GestureDetector.SimpleOnGestureListener#onDoubleTap(android.view.MotionEvent)
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

