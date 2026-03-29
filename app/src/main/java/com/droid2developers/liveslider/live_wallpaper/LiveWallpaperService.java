package com.droid2developers.liveslider.live_wallpaper;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import androidx.preference.PreferenceManager;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.database.repository.WallpaperRepository;
import net.rbgrn.android.glwallpaperservice.GLWallpaperService;
import java.util.ArrayList;
import java.util.List;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_SLIDESHOW_TIME;
import static com.droid2developers.liveslider.utils.Constant.PLAYLIST_NONE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_FADE;
import static com.droid2developers.liveslider.utils.Constant.PREF_CHANGE_ON_UNLOCK;
import static com.droid2developers.liveslider.utils.Constant.PREF_DUAL_PLAYLIST_ENABLED;
import static com.droid2developers.liveslider.utils.Constant.PREF_LOCK_PLAYLIST;
import com.droid2developers.liveslider.utils.Constant;

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
        private BroadcastReceiver screenOnReceiver;
        private BroadcastReceiver userPresentReceiver;

        private boolean pauseInSavePowerMode = false;
        private boolean changeOnUnlock = false;
        private boolean savePowerMode = false;
        private boolean allowClickToChange = false;
        private boolean isSlideShowEnabled = false;
        private String currentPlaylistId = PLAYLIST_NONE;

        // Dual-playlist (Feature 2): separate playlists for home and lock screen
        private boolean isDualPlaylistEnabled = false;
        private String lockPlaylistId = PLAYLIST_NONE;
        private int lockImagesArrayIndex = 0;
        private List<LocalWallpaper> lockPlaylistWallpapers = new ArrayList<>();
        private WallpaperRepository lockRepository;

        // TODO - time related parameters
        private long timer = DEFAULT_SLIDESHOW_TIME;
        private long timeStarted = 0;

        // TODO - remove and add ROOM database here
        private int mImagesArrayIndex = 0;
        private List<LocalWallpaper> playlistWallpapers = new ArrayList<>();
        private WallpaperRepository mRepository;

        private GestureDetector doubleTapDetector;

        // Runnable Threads
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable slideshow = () -> {
            Log.d(TAG, "run: slideshow handler called!");
            incrementWallpaper();
            changeWallpaper();
        };


        @Override
        @SuppressLint("CommitPrefEdits")
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8, 8, 8, 0, 0, 0);
            
            // Shared Preferences initialization (must happen BEFORE setRenderer so that
            // onSurfaceChanged / onDrawFrame sees the correct path and isDefault flag)
            prefs = PreferenceManager.getDefaultSharedPreferences(LiveWallpaperService.this);
            prefs.registerOnSharedPreferenceChangeListener(this);
            editor = prefs.edit();

            // initial Setup of LiveWallpaper
            renderer = new LiveWallpaperRenderer(LiveWallpaperService.this.getApplicationContext(), this);

            // Pre-populate renderer fields BEFORE setRenderer() triggers the GL thread,
            // otherwise onSurfaceChanged fires with localWallpaperPath==null and
            // isDefaultWallpaper==false, causing a failed load that falls back to the
            // built-in default wallpaper and ignores the user's saved selection.
            renderer.setIsDefaultWallpaper(prefs.getBoolean("default_wallpaper", true));
            renderer.setLocalWallpaperPath(prefs.getString("local_wallpaper_path", DEFAULT_LOCAL_PATH));
            renderer.setWallpaperType(prefs.getInt("type", TYPE_SINGLE));

            setRenderer(renderer);
            setRenderMode(RENDERMODE_WHEN_DIRTY);
            rotationSensor = new RotationSensor(LiveWallpaperService.this.getApplicationContext(),
                    this, SENSOR_RATE);

            // Setting remaining initial parameters
            renderer.setBiasRange(prefs.getInt("range", 10));
            renderer.setDelay(21 - prefs.getInt("delay", 10));
            renderer.setScrollMode(prefs.getBoolean("scroll", true));
            setPowerSaverEnabled(prefs.getBoolean("power_saver", true));
            setSlideShowEnabled(prefs.getBoolean("slideshow",false));
            renderer.setWallpaperType(prefs.getInt("type",TYPE_SINGLE));
            setAllowClickToChange(prefs.getBoolean("double_tap",false));
            setCurrentPlaylist(prefs.getString("current_playlist",PLAYLIST_NONE));
            setTimer(prefs.getLong("slideshow_timer", DEFAULT_SLIDESHOW_TIME));
            renderer.setTransitionEffect(prefs.getInt("transition_effect", TRANSITION_FADE));
            renderer.setAnimationSpeed(prefs.getInt("animation_speed", Constant.ANIMATION_SPEED_NORMAL));

            // Set initial calibration mode
            rotationSensor.setCalibrationMode(prefs.getInt("calibration_mode", 0)); // 0 = CALIBRATION_DEFAULT

            // Set initial face switch animation duration (default 400ms, or from prefs if available)
            int delayPref = prefs.getInt("delay", 10);
            rotationSensor.setFaceSwitchAnimationDurationFromDelay(delayPref);

            // Adding touch listeners for touch feedback
            setTouchEventsEnabled(true);
            doubleTapDetector = new GestureDetector(getApplicationContext(),
                    new DoubleTapGestureListener(this));

            // Change on screen wake — register ACTION_SCREEN_ON receiver
            changeOnUnlock = prefs.getBoolean(Constant.PREF_CHANGE_ON_UNLOCK, false);
            screenOnReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // Check keyguard state first: if device has no lock screen the device
                    if (isDualPlaylistEnabled && !lockPlaylistWallpapers.isEmpty()) {
                        // Feature 2: advance lock playlist and display it on screen wake
                        boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                        renderer.refreshWallpaperFresh(path, isDefault);
                        Log.d(TAG, "screenOnReceiver: dual mode — lock image " + lockImagesArrayIndex);
                    } else if (!isDualPlaylistEnabled && isSlideShowEnabled && changeOnUnlock
                            && isSetAsHomeLiveWallpaper()) {
                        // Feature 1: advance main slideshow on screen wake.
                        Log.d(TAG, "screenOnReceiver: screen on — advancing slideshow");
                        incrementWallpaper();
                        changeWallpaper();
                    }
                }
            };
            registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));

            // Dual-playlist: when user unlocks, restore home playlist image
            userPresentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    } else if (isSlideShowEnabled && changeOnUnlock) {
                        // Feature 1: advance main slideshow on screen wake
                        String path = playlistWallpapers.get(mImagesArrayIndex).getLocalPath();
                        boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                        renderer.refreshWallpaperFresh(path, isDefault);
                        Log.d(TAG, "userPresentReceiver: dual mode — home image " + mImagesArrayIndex);
                    }
                }
            };
            registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));

            // Feature 2 — initialise dual playlist from saved prefs
                    if (isDualPlaylistEnabled && !playlistWallpapers.isEmpty()) {
            // Unregister this as listener
            Log.d(TAG, "onDestroy: ");
            rotationSensor.unregister();
            handler.removeCallbacks(slideshow);
            if(powerSaverChangeReceiver != null) {
                unregisterReceiver(powerSaverChangeReceiver);
            }
            if (screenOnReceiver != null) {
                unregisterReceiver(screenOnReceiver);
            }
            if (userPresentReceiver != null) {
                unregisterReceiver(userPresentReceiver);
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
        public void onFaceChanged(int face) {
            renderer.setNewFaceRotation(face);
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
                    int delay = sharedPreferences.getInt("delay", 10);
                    renderer.setDelay(21 - delay);
                    rotationSensor.setFaceSwitchAnimationDurationFromDelay(delay);
                    break;
                case "scroll":
                    Log.d(TAG, "onSharedPreferenceChanged: " + sharedPreferences.getBoolean(key, true));
                    renderer.setScrollMode(sharedPreferences.getBoolean(key, true));
                    break;
                case "power_saver":
                    setPowerSaverEnabled(sharedPreferences.getBoolean(key, true));
                    break;
                case "refresh_wallpaper":
                    String localWallpaperPath = sharedPreferences.getString("local_wallpaper_path",DEFAULT_LOCAL_PATH);
                    boolean isDefault = sharedPreferences.getBoolean("default_wallpaper", true);
                    renderer.refreshWallpaperFresh(localWallpaperPath,isDefault);
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
                    break;
                case "slideshow_timer":
                    setTimer(prefs.getLong("slideshow_timer", DEFAULT_SLIDESHOW_TIME));
                    break;
                case "calibration_mode":
                    int calibrationMode = sharedPreferences.getInt(key, 0); // 0 = DEFAULT
                    rotationSensor.setCalibrationMode(calibrationMode);
                    Log.d(TAG, "Calibration mode changed to: " + calibrationMode);
                    break;
                case "transition_effect":
                    renderer.setTransitionEffect(sharedPreferences.getInt(key, TRANSITION_FADE));
                    break;
                case "animation_speed":
                    renderer.setAnimationSpeed(sharedPreferences.getInt(key, Constant.ANIMATION_SPEED_NORMAL));
                    break;
                case "change_on_unlock":
                    changeOnUnlock = sharedPreferences.getBoolean(key, false);
                    break;
                case PREF_DUAL_PLAYLIST_ENABLED:
                    isDualPlaylistEnabled = sharedPreferences.getBoolean(key, false);
                    break;
                case PREF_LOCK_PLAYLIST:
                    setLockPlaylist(sharedPreferences.getString(key, PLAYLIST_NONE));
                    break;
            }
        }



        // enable/disable power saver mode for post lollipop devices
        void setPowerSaverEnabled(boolean enabled) {
            if (pauseInSavePowerMode == enabled) return;
            pauseInSavePowerMode = enabled;
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pauseInSavePowerMode) {
                powerSaverChangeReceiver = new BroadcastReceiver() {
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
                mRepository.getPlaylistWallpapers(playlistId).observeForever(wallpaperList -> {
                    Log.d(TAG, "onChanged: wallpaperList = " + wallpaperList.size());
                    mImagesArrayIndex = 0;
                    currentPlaylistId = playlistId;
                    playlistWallpapers = wallpaperList;

                    String localWallpaperPath = playlistWallpapers.get(mImagesArrayIndex).getLocalPath();
                    boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                    renderer.refreshWallpaperFresh(localWallpaperPath, isDefault);
                    //mRepository.getPlaylistWallpapers(playlistId).removeObserver(this);
                });
            }
        }

        /**
         * Returns true if this live wallpaper is currently set as the active wallpaper
         * on the HOME (FLAG_SYSTEM) screen slot.
         * Uses {@link WallpaperManager#getWallpaperInfo()} — public API since level 5.
         */
        private boolean isSetAsHomeLiveWallpaper() {
            WallpaperManager wm = WallpaperManager.getInstance(getApplicationContext());
            WallpaperInfo info = wm.getWallpaperInfo();
            return info != null && info.getPackageName().equals(getPackageName());
        }

        /**
         * Returns true if this live wallpaper is currently displayed on the LOCK screen.
         *
         * API 34+ : {@link WallpaperManager#getWallpaperInfo(int)} with {@link WallpaperManager#FLAG_LOCK}
         *           is a public method in API 36 (confirmed from SDK source).  We guard with
         *           {@code >= UPSIDE_DOWN_CAKE} and wrap in try-catch in case the method is not
         *           yet present on an API 34/35 device's firmware build.
                    });
                }
            } else {
                lockPlaylistWallpapers = new ArrayList<>();
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

            if (!playlistWallpapers.isEmpty()){
                String localWallpaperPath = playlistWallpapers.get(mImagesArrayIndex).getLocalPath();
                editor.putString("local_wallpaper_path", localWallpaperPath).apply();
                boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                renderer.refreshWallpaperFresh(localWallpaperPath, isDefault);

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


        private void setTimer(long timeInMillis) {
            if (timer == timeInMillis) return;

            //set new time for slideshow
            timer = timeInMillis;
            handler.removeCallbacks(slideshow);
            handler.postDelayed(slideshow, timer);
            timeStarted = systemTime();
        }



    }

}
