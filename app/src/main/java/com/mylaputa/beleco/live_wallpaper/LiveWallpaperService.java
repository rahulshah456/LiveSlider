package com.mylaputa.beleco.live_wallpaper;

import android.annotation.SuppressLint;
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
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;

import com.mylaputa.beleco.database.models.LocalWallpaper;
import com.mylaputa.beleco.database.models.Playlist;
import com.mylaputa.beleco.database.repository.WallpaperRepository;
import com.mylaputa.beleco.utils.Constant;

import net.rbgrn.android.glwallpaperservice.GLWallpaperService;

import java.util.ArrayList;
import java.util.List;

import static com.mylaputa.beleco.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.mylaputa.beleco.utils.Constant.PLAYLIST_NONE;
import static com.mylaputa.beleco.utils.Constant.TYPE_SINGLE;

public class LiveWallpaperService extends GLWallpaperService {

    private final static String TAG = LiveWallpaperService.class.getSimpleName();
    public static final int SENSOR_RATE = 60;


    @Override
    public Engine onCreateEngine() {
        return new ParallaxEngine();
    }

    class ParallaxEngine extends GLEngine implements LiveWallpaperRenderer.Callbacks,
            SharedPreferences.OnSharedPreferenceChangeListener, RotationSensor.Callback {

        private SharedPreferences prefs;
        private SharedPreferences.Editor editor;
        private LiveWallpaperRenderer renderer;
        private RotationSensor rotationSensor;
        private BroadcastReceiver powerSaverChangeReceiver;

        private boolean pauseInSavePowerMode = false;
        private boolean savePowerMode = false;
        private boolean allowClickToChange = false;
        private boolean isSlideShowEnabled = false;
        private String currentPlaylistId = PLAYLIST_NONE;

        // TODO - time related parameters
        private long TIME_SECOND = 1000;
        private long timer = 30 * TIME_SECOND;
        private long timeStarted = 0;

        // TODO - remove and add ROOM database here
        private int mImagesArrayIndex = 0;
        private List<LocalWallpaper> playlistWallpapers = new ArrayList<>();
        private WallpaperRepository mRepository;

        private GestureDetector doubleTapDetector;

        // Runnable Threads
        private final Handler handler = new Handler();
        private final Runnable slideshow = new Runnable() {
            public void run() {
                Log.d(TAG, "run: slideshow handler called!");
                incrementWallpaper();
                changeWallpaper();
            }
        };


        @Override
        @SuppressLint("CommitPrefEdits")
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8, 8, 8, 0, 0, 0);

            Log.d(TAG, "onCreate: ");
            
            // initial Setup of LiveWallpaper
            renderer = new LiveWallpaperRenderer(LiveWallpaperService.this.getApplicationContext(), this);
            setRenderer(renderer);
            setRenderMode(RENDERMODE_WHEN_DIRTY);
            rotationSensor = new RotationSensor(LiveWallpaperService.this.getApplicationContext(),
                    this, SENSOR_RATE);


            // Shared Preferences initialization
            prefs = PreferenceManager.getDefaultSharedPreferences(LiveWallpaperService.this);
            prefs.registerOnSharedPreferenceChangeListener(this);
            editor = prefs.edit();

            // Setting initial parameters
            renderer.setBiasRange(prefs.getInt("range", 10));
            renderer.setDelay(21 - prefs.getInt("deny", 10));
            renderer.setScrollMode(prefs.getBoolean("scroll", true));
            renderer.setIsDefaultWallpaper(prefs.getBoolean("default_wallpaper", true));
            renderer.setLocalWallpaperPath(prefs.getString("local_wallpaper_path", DEFAULT_LOCAL_PATH));
            setPowerSaverEnabled(prefs.getBoolean("power_saver", true));
            setSlideShowEnabled(prefs.getBoolean("slideshow",false));
            renderer.setWallpaperType(prefs.getInt("type",TYPE_SINGLE));
            setAllowClickToChange(prefs.getBoolean("double_tap",false));
            setCurrentPlaylist(prefs.getString("current_playlist",PLAYLIST_NONE));

            // Adding touch listeners for touch feedback
            setTouchEventsEnabled(true);
            doubleTapDetector = new GestureDetector(getApplicationContext(),
                    new DoubleTapGestureListener(this));


        }

        @Override
        public void onDestroy() {
            // Unregister this as listener
            Log.d(TAG, "onDestroy: ");
            rotationSensor.unregister();
            handler.removeCallbacks(slideshow);
            if (Build.VERSION.SDK_INT >= 21) {
                unregisterReceiver(powerSaverChangeReceiver);
            }
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            // Kill renderer
            if (renderer != null) {
                // assuming yours has this method - it should!
                renderer.release();
            }
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (!pauseInSavePowerMode || !savePowerMode) {
                if (visible) {
                    rotationSensor.register();
                    renderer.startTransition();
                    if (isSlideShowEnabled){
                        if (systemTime() - timeStarted + 100 < timer) {
                            // left over timer
                            handler.postDelayed(slideshow, timer - (systemTime() - timeStarted));
                        } else {
                            // otherwise draw a new one since it's time for a new one
                            incrementWallpaper();
                            changeWallpaper();
                        }
                    }

                } else {
                    rotationSensor.unregister();
                    handler.removeCallbacks(slideshow);
                    renderer.stopTransition();
                }
            } else {
                if (visible) {
                    renderer.startTransition();
                } else {
                    renderer.stopTransition();
                }
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            this.doubleTapDetector.onTouchEvent(event);
        }


        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                                     int xPixelOffset, int yPixelOffset) {
            if (!isPreview()) {
                renderer.setOffset(xOffset, yOffset);
                renderer.setOffsetStep(xOffsetStep, yOffsetStep);
                Log.i(TAG, xOffset + ", " + yOffset + ", " + xOffsetStep + ", " + yOffsetStep);
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

        @Override
        public void requestRender() {
            super.requestRender();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d(TAG, "onSharedPreferenceChanged: " + key);
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
                case "refresh_wallpaper":
                    String localWallpaperPath = sharedPreferences.getString("local_wallpaper_path",DEFAULT_LOCAL_PATH);
                    boolean isDefault = sharedPreferences.getBoolean("default_wallpaper", true);
                    renderer.refreshWallpaper(localWallpaperPath,isDefault);
                    break;
                case "type":
                    renderer.setWallpaperType(sharedPreferences.getInt(key, TYPE_SINGLE));
                    break;
                case "slideshow":
                    setSlideShowEnabled(prefs.getBoolean("slideshow",false));
                    break;
                case "interval":
                    // TODO - time interval changes comes here
                    break;
                case "double_tap":
                    setAllowClickToChange(prefs.getBoolean("double_tap",false));
                    break;
                case "current_playlist":
                    setCurrentPlaylist(prefs.getString("current_playlist",PLAYLIST_NONE));
            }
        }



        // enable/disable power saver mode for post lollipop devices
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
                            if (pm != null) {
                                savePowerMode = pm.isPowerSaveMode();
                            }
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
                    if (pm != null) {
                        savePowerMode = pm.isPowerSaveMode();
                    }
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

        // enable/disable DoubleTap to change Wallpaper
        void setAllowClickToChange(boolean enabled){
            if (allowClickToChange == enabled) return;
            allowClickToChange = enabled;
        }
        boolean isAllowClickToChange() {
            return allowClickToChange;
        }

        // enable/disable wallpapers slideshow
        void setSlideShowEnabled(boolean enabled){
            if (isSlideShowEnabled == enabled) return;
            isSlideShowEnabled = enabled;
            if (isSlideShowEnabled){
                // Activate wallpapers Slideshow
                handler.removeCallbacks(slideshow);
                if (isVisible() && isSlideShowEnabled){
                    handler.postDelayed(slideshow, timer);
                    timeStarted = systemTime();
                }
            } else {
                handler.removeCallbacks(slideshow);
            }
        }
        boolean isSlideShowEnabled(){
            return isSlideShowEnabled;
        }


        // enable/disable playlists
        void setCurrentPlaylist(String playlistId) {
            if (currentPlaylistId.equals(playlistId)) return;
            this.currentPlaylistId = playlistId;
            if (!playlistId.equals(PLAYLIST_NONE)) {

                mRepository = new WallpaperRepository(getApplicationContext());
                mRepository.getPlaylistWallpapers(playlistId).observeForever(new Observer<List<LocalWallpaper>>() {
                    @Override
                    public void onChanged(List<LocalWallpaper> wallpaperList) {
                        Log.d(TAG, "onChanged: wallpaperList = " + wallpaperList.size());
                        mImagesArrayIndex = 0;
                        currentPlaylistId = playlistId;
                        playlistWallpapers = wallpaperList;

                        String localWallpaperPath = playlistWallpapers.get(mImagesArrayIndex).getLocalPath();
                        boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                        renderer.refreshWallpaper(localWallpaperPath, isDefault);
                        //mRepository.getPlaylistWallpapers(playlistId).removeObserver(this);
                    }
                });
            }
        }

        // Functions for wallpapers slideshow
        void incrementWallpaper(){
            // TODO - Change max Index size based on local images too
            mImagesArrayIndex++;
            if (mImagesArrayIndex >= playlistWallpapers.size()) {
                mImagesArrayIndex = 0;
            }
            Log.d(TAG, "incrementCounter: " + mImagesArrayIndex);
        }
        void changeWallpaper(){

            if (playlistWallpapers.size()>0){
                String localWallpaperPath = playlistWallpapers.get(mImagesArrayIndex).getLocalPath();
                editor.putString("local_wallpaper_path", localWallpaperPath).apply();
                boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                renderer.refreshWallpaper(localWallpaperPath, isDefault);

                handler.removeCallbacks(slideshow);
                if (isVisible()){
                    handler.postDelayed(slideshow, timer);
                    timeStarted = systemTime();
                } 
            } else {
                Log.d(TAG, "changeWallpaper: empty playlistWallpapers!");
            }
            
            
        }
        private long systemTime() {
            return System.nanoTime() / 1000000;
        }



    }

}
