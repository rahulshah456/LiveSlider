package com.droid2developers.liveslider.live_wallpaper;

import android.annotation.SuppressLint;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import androidx.preference.PreferenceManager;
import com.droid2developers.liveslider.database.LiveWallpaperDatabase;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.database.repository.WallpaperRepository;
import net.rbgrn.android.glwallpaperservice.GLWallpaperService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_SLIDESHOW_TIME;
import static com.droid2developers.liveslider.utils.Constant.PLAYLIST_NONE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TRANSITION_FADE;
import static com.droid2developers.liveslider.utils.Constant.PREF_CHANGE_ON_UNLOCK;
import static com.droid2developers.liveslider.utils.Constant.PREF_SHUFFLE_PLAYLIST;
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

    /**
     * Override in subclasses to use a different SharedPreferences file.
     */
    protected SharedPreferences getEnginePrefs() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    /**
     * Override in subclasses to provide a different fallback wallpaper asset path.
     */
    protected String getDefaultWallpaperPath() {
        return DEFAULT_LOCAL_PATH;
    }

    /**
     * Returns true when this service is running as the lock-screen wallpaper.
     * Overridden in {@link LockLiveWallpaperService}.
     * Used to route screen-wake advances correctly: lock advances on ACTION_SCREEN_ON,
     * home advances on ACTION_USER_PRESENT so it fires only when home is visible.
     */
    protected boolean isLockScreenService() {
        return false;
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
        private boolean shufflePlaylist = false;
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

        private MultiTapDetector tapDetector;

        // Triple-tap crop-adjust mode (home screen only, phase 1 — nothing persisted)
        private boolean cropMode = false;

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
            prefs = getEnginePrefs();
            prefs.registerOnSharedPreferenceChangeListener(this);
            editor = prefs.edit();

            // initial Setup of LiveWallpaper
            renderer = new LiveWallpaperRenderer(LiveWallpaperService.this.getApplicationContext(), this,
                    getDefaultWallpaperPath());

            // Pre-populate renderer fields BEFORE setRenderer() triggers the GL thread,
            // otherwise onSurfaceChanged fires with localWallpaperPath==null and
            // isDefaultWallpaper==false, causing a failed load that falls back to the
            // built-in default wallpaper and ignores the user's saved selection.
            renderer.setIsDefaultWallpaper(prefs.getBoolean("default_wallpaper", true));
            renderer.setLocalWallpaperPath(prefs.getString("local_wallpaper_path", getDefaultWallpaperPath()));
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
            shufflePlaylist = prefs.getBoolean(PREF_SHUFFLE_PLAYLIST, false);
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
            tapDetector = new MultiTapDetector(getApplicationContext(),
                    new MultiTapDetector.Listener() {
                        @Override
                        public void onDoubleTap() {
                            if (isAllowClickToChange() && isSlideShowEnabled()) {
                                incrementWallpaper();
                                changeWallpaper();
                            }
                        }

                        @Override
                        public void onTripleTap() {
                            if (!isLockScreenService()) {
                                cropMode = true;
                                updateOverlayPlaylistInfo();
                                renderer.showCropOverlay();
                            }
                        }
                    });

            // Change on screen wake — register ACTION_SCREEN_ON receiver
            changeOnUnlock = prefs.getBoolean(Constant.PREF_CHANGE_ON_UNLOCK, false);
            screenOnReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (isLockScreenService()) {
                        // Lock service: screen turning ON = lock screen appears = advance now,
                        // but only if the user opted into changing on screen wake.
                        if (isSlideShowEnabled && changeOnUnlock && !playlistWallpapers.isEmpty()) {
                            incrementWallpaper();
                            changeWallpaper();
                            Log.d(TAG, "screenOnReceiver: lock service — advanced to " + mImagesArrayIndex);
                        }
                    }
                    // Home service: do NOT advance here. ACTION_SCREEN_ON fires before the home
                    // screen is even visible (lock screen may still be showing). We advance in
                    // userPresentReceiver (ACTION_USER_PRESENT) which fires only when home is live.
                }
            };
            registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));

            // Dual-playlist: when user unlocks, restore home playlist image
            userPresentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (isLockScreenService()) {
                        // Home screen is now live — lock service has nothing to do.
                        return;
                    }
                    // Home service: user just unlocked → home screen is now visible.
                    // Advance the slideshow here (not in screenOnReceiver) so the change
                    // is always seen by the user and is never double-counted. Unlike the
                    // lock screen, this isn't gated by a setting — "Change On Screen Wake"
                    // is a lock-screen-only control (see updateSlideshowCardsVisibility).
                    if (isSlideShowEnabled && !playlistWallpapers.isEmpty()) {
                        incrementWallpaper();
                        changeWallpaper();
                        Log.d(TAG, "userPresentReceiver: home service — advanced to " + mImagesArrayIndex);
                    }
                }
            };
            registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));

            // Feature 2 — initialise dual playlist from saved prefs
            isDualPlaylistEnabled = prefs.getBoolean(PREF_DUAL_PLAYLIST_ENABLED, false);
            setLockPlaylist(prefs.getString(PREF_LOCK_PLAYLIST, PLAYLIST_NONE));
        }

        @Override
        public void onDestroy() {
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
                    if (isSlideShowEnabled) {
                        // Home always advances via userPresentReceiver on unlock; lock only
                        // does so when "Change On Screen Wake" is enabled (see screenOnReceiver).
                        boolean advancesOnWake = !isLockScreenService() || changeOnUnlock;
                        if (advancesOnWake) {
                            // Advances are driven by screen-on / user-present receivers.
                            // Here we only (re)start the periodic slideshow timer so that
                            // timed changes still work normally while the screen is on.
                            handler.removeCallbacks(slideshow);
                            handler.postDelayed(slideshow, timer);
                            timeStarted = systemTime();
                        } else if (systemTime() - timeStarted + 100 < timer) {
                            // Resume with whatever time was left before screen went off.
                            handler.postDelayed(slideshow, timer - (systemTime() - timeStarted));
                        } else {
                            // Timer already expired while screen was off — advance now.
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
            if (cropMode) {
                // Overlay is up — taps go to its buttons, never to the tap detector
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    android.graphics.Rect frame = getSurfaceHolder().getSurfaceFrame();
                    int hit = CropOverlay.hitTest(event.getX(), event.getY(),
                            frame.width(), frame.height(), isPlaylistActive());
                    if (hit == CropOverlay.HIT_LEFT) {
                        renderer.nudgeCrop(1);
                    } else if (hit == CropOverlay.HIT_RIGHT) {
                        renderer.nudgeCrop(-1);
                    } else if (hit == CropOverlay.HIT_DONE) {
                        cropMode = false;
                        renderer.hideCropOverlay();
                        saveCropBias();
                    } else if (hit == CropOverlay.HIT_PREV || hit == CropOverlay.HIT_NEXT) {
                        if (isPlaylistActive()) {
                            // Keep the edits for the wallpaper we're leaving, then step
                            saveCropBias();
                            if (hit == CropOverlay.HIT_NEXT) {
                                incrementWallpaper();
                            } else {
                                decrementWallpaper();
                            }
                            doChangeWallpaper();
                            updateOverlayPlaylistInfo();
                        }
                    }
                }
                return;
            }
            tapDetector.onTouchEvent(event);
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
                    String localWallpaperPath = sharedPreferences.getString("local_wallpaper_path", getDefaultWallpaperPath());
                    boolean isDefault = sharedPreferences.getBoolean("default_wallpaper", true);
                    if (isDefault) {
                        renderer.refreshWallpaperFresh(localWallpaperPath, true, 0f);
                    } else {
                        // Saved crop lives in Room — fetch off the main thread, then refresh
                        LiveWallpaperDatabase.databaseWriteExecutor.execute(() ->
                                renderer.refreshWallpaperFresh(localWallpaperPath, false,
                                        getRepository().getCropBiasSync(localWallpaperPath)));
                    }
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
                case PREF_SHUFFLE_PLAYLIST:
                    shufflePlaylist = sharedPreferences.getBoolean(key, false);
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


        private WallpaperRepository getRepository() {
            if (mRepository == null) {
                mRepository = new WallpaperRepository(getApplicationContext());
            }
            return mRepository;
        }

        // Persist the chosen crop for the wallpaper currently on screen.
        // The built-in default asset has no DB row — its center crop stays implicit.
        private void saveCropBias() {
            String path = renderer.getCurrentWallpaperPath();
            if (path != null && !path.equals(getDefaultWallpaperPath())) {
                getRepository().updateCropBias(path, renderer.getCropBias());
                Log.d(TAG, "saveCropBias: " + renderer.getCropBias() + " for " + path);
            }
        }

        // enable/disable playlists
        void setCurrentPlaylist(String playlistId) {
            if (currentPlaylistId.equals(playlistId)) return;
            this.currentPlaylistId = playlistId;
            if (!playlistId.equals(PLAYLIST_NONE)) {

                getRepository().getPlaylistWallpapers(playlistId).observeForever(wallpaperList -> {
                    Log.d(TAG, "onChanged: wallpaperList = " + wallpaperList.size());
                    // Room re-runs this query (and re-emits) on ANY write to the localwallpaper
                    // table, including PlaylistWorker processing an unrelated playlist. Only
                    // reset position when the playlist itself just changed, and only touch the
                    // GL surface if the currently-shown path actually changed — otherwise every
                    // unrelated wallpaper crop resets the slideshow position and flickers here.
                    boolean isPlaylistSwitch = !playlistId.equals(currentPlaylistId);
                    String previousPath = !playlistWallpapers.isEmpty()
                            ? playlistWallpapers.get(mImagesArrayIndex).getLocalPath() : null;

                    currentPlaylistId = playlistId;
                    playlistWallpapers = wallpaperList;
                    if (isPlaylistSwitch) {
                        mImagesArrayIndex = 0;
                    } else if (mImagesArrayIndex >= playlistWallpapers.size()) {
                        mImagesArrayIndex = 0;
                    }

                    if (playlistWallpapers.isEmpty()) {
                        // Playlist has no processed wallpapers yet (e.g. still cropping); wait for the next update.
                        return;
                    }
                    LocalWallpaper currentWallpaper = playlistWallpapers.get(mImagesArrayIndex);
                    String localWallpaperPath = currentWallpaper.getLocalPath();
                    if (!isPlaylistSwitch && localWallpaperPath != null && localWallpaperPath.equals(previousPath)) {
                        return;
                    }
                    boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                    renderer.refreshWallpaperFresh(localWallpaperPath, isDefault,
                            currentWallpaper.getCropBias());
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
         * API 34+: uses WallpaperManager#getWallpaperInfo(FLAG_LOCK) if available.
         */
        private boolean isSetAsLockLiveWallpaper() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    WallpaperInfo lockInfo = WallpaperManager.getInstance(getApplicationContext())
                            .getWallpaperInfo(WallpaperManager.FLAG_LOCK);
                    if (lockInfo != null && lockInfo.getPackageName().equals(getPackageName())) return true;
                    return lockInfo == null && isSetAsHomeLiveWallpaper();
                } catch (Throwable t) {
                    return isSetAsHomeLiveWallpaper();
                }
            }
            return isSetAsHomeLiveWallpaper();
        }

        void setLockPlaylist(String playlistId) {
            if (lockPlaylistId.equals(playlistId)) return;
            this.lockPlaylistId = playlistId;
            if (!playlistId.equals(PLAYLIST_NONE)) {
                lockRepository = new WallpaperRepository(getApplicationContext());
                lockRepository.getPlaylistWallpapers(playlistId).observeForever(wallpaperList -> {
                    Log.d(TAG, "setLockPlaylist: " + wallpaperList.size());
                    lockImagesArrayIndex = 0;
                    lockPlaylistWallpapers = wallpaperList;
                });
            } else {
                lockPlaylistWallpapers = new ArrayList<>();
            }
        }

        // True when the slideshow is running off a non-empty playlist — the only
        // state where the overlay's prev/next pill makes sense.
        private boolean isPlaylistActive() {
            return isSlideShowEnabled && !playlistWallpapers.isEmpty();
        }

        // Feeds the overlay's "7/10" pill; (0, 0) hides it (static wallpaper).
        private void updateOverlayPlaylistInfo() {
            if (isPlaylistActive()) {
                renderer.setPlaylistInfo(mImagesArrayIndex + 1, playlistWallpapers.size());
            } else {
                renderer.setPlaylistInfo(0, 0);
            }
        }

        // Functions for wallpapers slideshow
        void incrementWallpaper(){
            // TODO - Change max Index size based on local images too
            mImagesArrayIndex++;
            if (mImagesArrayIndex >= playlistWallpapers.size()) {
                mImagesArrayIndex = 0;
                if (shufflePlaylist && playlistWallpapers.size() > 1) {
                    Collections.shuffle(playlistWallpapers);
                }
            }
            Log.d(TAG, "incrementCounter: " + mImagesArrayIndex);
        }
        void decrementWallpaper(){
            if (playlistWallpapers.isEmpty()) return;
            mImagesArrayIndex = (mImagesArrayIndex - 1 + playlistWallpapers.size())
                    % playlistWallpapers.size();
            Log.d(TAG, "decrementCounter: " + mImagesArrayIndex);
        }
        void changeWallpaper(){
            // Never swap the wallpaper while the crop overlay is being used — covers
            // every change source (slideshow timer, unlock advance, double tap).
            // Reschedule so the slideshow resumes on its own after editing is done.
            // (The overlay's own prev/next buttons call doChangeWallpaper directly.)
            if (cropMode) {
                Log.d(TAG, "changeWallpaper: skipped, crop overlay is open");
                handler.removeCallbacks(slideshow);
                handler.postDelayed(slideshow, timer);
                return;
            }
            doChangeWallpaper();
        }
        private void doChangeWallpaper(){

            if (!playlistWallpapers.isEmpty()){
                LocalWallpaper nextWallpaper = playlistWallpapers.get(mImagesArrayIndex);
                String localWallpaperPath = nextWallpaper.getLocalPath();
                editor.putString("local_wallpaper_path", localWallpaperPath).apply();
                boolean isDefault = prefs.getBoolean("default_wallpaper", true);
                renderer.refreshWallpaperFresh(localWallpaperPath, isDefault,
                        nextWallpaper.getCropBias());

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
