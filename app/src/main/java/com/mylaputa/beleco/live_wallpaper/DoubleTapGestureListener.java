package com.mylaputa.beleco.live_wallpaper;

import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;


public class DoubleTapGestureListener extends SimpleOnGestureListener {

    private LiveWallpaperService.MyEngine myEngine;

    DoubleTapGestureListener(LiveWallpaperService.MyEngine myEngine) {
        this.myEngine = myEngine;
    }

    /* (non-Javadoc)
     * @see android.view.GestureDetector.SimpleOnGestureListener#onDoubleTap(android.view.MotionEvent)
     */
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (this.myEngine.isAllowClickToChange() && this.myEngine.isSlideShowEnabled()) {
            myEngine.incrementWallpaper();
            myEngine.changeWallpaper();
        }
        return true;
    }


}

