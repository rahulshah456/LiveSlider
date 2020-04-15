package com.mylaputa.beleco.live_wallpaper;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.mylaputa.beleco.sensor.RotationSensor;
import com.mylaputa.beleco.utils.Constant;

import net.rbgrn.android.glwallpaperservice.GLWallpaperService;

public class LiveWallpaperService extends GLWallpaperService {
    public static final int SENSOR_RATE = 60;
    private final static String TAG = "LiveWallpaperService";

    @Override
    public Engine onCreateEngine() {
        return new MyEngine();
    }

    private class MyEngine extends GLEngine implements LiveWallpaperRenderer.Callbacks,
            SharedPreferences.OnSharedPreferenceChangeListener, RotationSensor.Callback {
        private SharedPreferences preference;
        private SharedPreferences.Editor editor;
        private LiveWallpaperRenderer renderer;
        private RotationSensor rotationSensor;
        private BroadcastReceiver powerSaverChangeReceiver;
        private boolean pauseInSavePowerMode = false;
        private boolean savePowerMode = false;

        // time related parameters
        private long TIME_SECOND = 1000;
        private long timer = 30 * TIME_SECOND;
        private long timeStarted = 0;
        private int mImagesArrayIndex = 0;

        // Runnable Threads
        private final Handler handler = new Handler();
        private final Runnable slideshow = new Runnable() {
            public void run() {
                Log.d(TAG, "run: Current Wallpaper Changed!");
                incrementWallpaper();
                changeWallpaper();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8, 8, 8, 0, 0, 0);
            renderer = new LiveWallpaperRenderer(LiveWallpaperService.this.getApplicationContext
                    (), this);
            setRenderer(renderer);
            setRenderMode(RENDERMODE_WHEN_DIRTY);
            rotationSensor = new RotationSensor(LiveWallpaperService.this.getApplicationContext()
                    , this, SENSOR_RATE);
            preference = PreferenceManager.getDefaultSharedPreferences(LiveWallpaperService.this);
            preference.registerOnSharedPreferenceChangeListener(this);
            editor = preference.edit();
            renderer.setBiasRange(preference.getInt("range", 10));
            renderer.setDelay(21 - preference.getInt("deny", 10));
            renderer.setScrollMode(preference.getBoolean("scroll", true));
            renderer.setIsDefaultWallpaper(preference.getInt("default_picture", 0) == 0);
            renderer.setCurrentWallpaper(preference.getString("current_wallpaper", Constant.DEFAULT));
            setPowerSaverEnabled(preference.getBoolean("power_saver", true));

            /*
             * This is how it animates. After drawing a frame, ask it to draw another
             * one.
             */
            handler.removeCallbacks(slideshow);
            if (isVisible()){
                handler.postDelayed(slideshow, timer);
                timeStarted = systemTime();
            }
        }

        @Override
        public void onDestroy() {
            // Unregister this as listener
            rotationSensor.unregister();
            handler.removeCallbacks(slideshow);
            if (Build.VERSION.SDK_INT >= 21) {
                unregisterReceiver(powerSaverChangeReceiver);
            }
            preference.unregisterOnSharedPreferenceChangeListener(this);
            // Kill renderer
            if (renderer != null) {
                renderer.release(); // assuming yours has this method - it
                // should!
            }
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (!pauseInSavePowerMode || !savePowerMode) {
                if (visible) {
                    rotationSensor.register();
                    renderer.startTransition();
                    if (systemTime() - timeStarted + 100 < timer) {
                        // left over timer
                        Log.d(TAG, "onVisibilityChanged: drawBitmap Called!");
                        handler.postDelayed(slideshow, timer - (systemTime() - timeStarted));
                    } else {
                        // otherwise draw a new one since it's time for a new one
                        Log.d(TAG, "onVisibilityChanged: showNewImage Called!");
                        incrementWallpaper();
                        changeWallpaper();
                    }
                } else {
                    rotationSensor.unregister();
                    handler.removeCallbacks(slideshow);
                    renderer.stopTransition();
                }
            } else {
                if (visible) {
                    renderer.startTransition();
                    if (systemTime() - timeStarted + 100 < timer) {
                        // left over timer
                        Log.d(TAG, "onVisibilityChanged: drawBitmap Called!");
                        handler.postDelayed(slideshow, timer - (systemTime() - timeStarted));
                    } else {
                        // otherwise draw a new one since it's time for a new one
                        Log.d(TAG, "onVisibilityChanged: showNewImage Called!");
                        incrementWallpaper();
                        changeWallpaper();
                    }
                } else {
                    handler.removeCallbacks(slideshow);
                    renderer.stopTransition();
                }
            }
        }


        void setPowerSaverEnabled(boolean enabled) {
            if (pauseInSavePowerMode == enabled) return;
            pauseInSavePowerMode = enabled;
            if (Build.VERSION.SDK_INT >= 21) {
                final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pauseInSavePowerMode) {
                    powerSaverChangeReceiver = new BroadcastReceiver() {
                        @TargetApi(21)
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            savePowerMode = pm.isPowerSaveMode();
                            if (savePowerMode && isVisible()) {
                                rotationSensor.unregister();
                                renderer.setOrientationAngle(0, 0);
                            } else if (!savePowerMode && isVisible()) {
                                rotationSensor.register();
                            }
                        }
                    };

                    IntentFilter filter = new IntentFilter();
                    filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                    registerReceiver(powerSaverChangeReceiver, filter);
                    savePowerMode = pm.isPowerSaveMode();
                    if (savePowerMode && isVisible()) {
                        rotationSensor.unregister();
                        renderer.setOrientationAngle(0, 0);
                    }
                } else {
                    unregisterReceiver(powerSaverChangeReceiver);
                    savePowerMode = pm.isPowerSaveMode();
                    if (savePowerMode && isVisible()) {
                        rotationSensor.register();
                    }

                }
            }
        }

        // Functions for wallpapers slideshow
        void incrementWallpaper(){
            // TODO - Change max Index size based on local images too
            mImagesArrayIndex++;
            if (mImagesArrayIndex >= 5) {
                mImagesArrayIndex = 0;
            }
            Log.d(TAG, "incrementCounter: " + mImagesArrayIndex);
        }
        private void changeWallpaper(){
            String wallpaper = "wallpaper_" + mImagesArrayIndex + ".jpg";
            editor.putString("current_wallpaper", wallpaper);
            editor.apply();
            handler.removeCallbacks(slideshow);
            if (isVisible()){
                handler.postDelayed(slideshow, timer);
                timeStarted = systemTime();
            }
        }
        private long systemTime() {
            return System.nanoTime() / 1000000;
        }


        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                                     float xOffsetStep, float yOffsetStep, int xPixelOffset,
                                     int yPixelOffset) {
            if (!isPreview()) {
                renderer.setOffset(xOffset, yOffset);
                renderer.setOffsetStep(xOffsetStep, yOffsetStep);
                Log.i(TAG, xOffset + ", " + yOffset + ", " + xOffsetStep + ", " + yOffsetStep);
            }
        }

        @Override
        public void requestRender() {
            super.requestRender();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case "range":
                    renderer.setBiasRange(sharedPreferences.getInt(key, 10));
                    break;
                case "delay":
                    renderer.setDelay(21 - sharedPreferences.getInt(key, 10));
                    break;
                case "scroll":
                    renderer.setScrollMode(sharedPreferences.getBoolean(key, true));
                    break;
                case "power_saver":
                    setPowerSaverEnabled(sharedPreferences.getBoolean(key, true));
                    break;
                case "default_picture":
                    renderer.setIsDefaultWallpaper(sharedPreferences.getInt(key, 0) == 0);
                case "current_wallpaper":
                    renderer.setCurrentWallpaper(sharedPreferences.getString(key,Constant.DEFAULT));
                case "slideshow":
                    break;
                case "interval":
                    break;
            }
        }

        @Override
        public void onSensorChanged(float[] angle) {
            if (getResources().getConfiguration().orientation == Configuration
                    .ORIENTATION_LANDSCAPE)
                renderer.setOrientationAngle(angle[1], angle[2]);
            else renderer.setOrientationAngle(-angle[2], angle[1]);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            handler.removeCallbacks(slideshow);
        }
    }

}
