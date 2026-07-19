package com.droid2developers.liveslider.utils;

import android.os.Build;
import android.os.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Constant {

    /**
     * Independent lock-screen live wallpapers only exist from API 34 onward, and even then
     * many OEM skins silently ignore FLAG_LOCK and fall back to a static lock image. There's
     * no public API to query real OEM support (isLockscreenLiveWallpaperEnabled() is a hidden
     * SystemApi) — this SDK gate is the only usable pre-check; actual success is confirmed
     * after activation via WallpaperManager#getWallpaperInfo(FLAG_LOCK).
     */
    public static boolean supportsIndependentLockWallpaper() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    public static String DB_NAME = "db_live_wallpapers";

    public static final String HEADER = "wallpaper_";
    public static final String DEFAULT_WALLPAPER_NAME = "wallpaper_default.jpg";
    public static final String DEFAULT_LOCK_WALLPAPER_NAME = "wallpaper_default_lock.jpg";
    private static final String ASSETS_PATH = "file:///android_asset/";
    public static final String DEFAULT_LOCAL_PATH = Constant.ASSETS_PATH + Constant.DEFAULT_WALLPAPER_NAME;
    public static final String DEFAULT_LOCK_LOCAL_PATH = Constant.ASSETS_PATH + Constant.DEFAULT_LOCK_WALLPAPER_NAME;

    // Tracks which surface the user intends to activate (stored briefly before launching chooser)
    public static final String PREF_ACTIVATE_TARGET = "activate_target";
    public static final String ACTIVATE_TARGET_HOME = "home";
    public static final String ACTIVATE_TARGET_LOCK = "lock";

    //slideshow timer constants
    public static final long DEFAULT_SLIDESHOW_TIME = 15 * 60 * 1000;
    public static final long MINIMUM_SLIDESHOW_TIME = 2 * 1000;


    // Image file formats
    public static final String PNG = ".png";
    public static final String JPG = ".jpg";

    /** Wallpaper PlaylistId Types
     * if DEFAULT - default assets wallpaper
     * if CUSTOM - single local wallpapers
     * id PlaylistId - wallpaper from a playlist
     */
    public static final String DEFAULT = "Default";
    public static final String CUSTOM = "Custom";

    // LiveWallpaper Types to enable/disable different formats
    public static final int TYPE_SINGLE = 0;
    public static final int TYPE_AUTO = 1;
    public static final int TYPE_SLIDESHOW = 2;

    // Calibration Mode Constants
    public static final int CALIBRATION_DEFAULT = 0;
    public static final int CALIBRATION_VERTICAL = 1;
    public static final int CALIBRATION_DYNAMIC = 2;

    // Transition Effect Constants  (stored in prefs as "transition_effect")
    public static final int TRANSITION_FADE     = 0;
    public static final int TRANSITION_DISSOLVE = 1;
    public static final int TRANSITION_PIXELATE = 2;
    public static final int TRANSITION_WIPE     = 3; // horizontal left→right sweep
    public static final int TRANSITION_BLUR     = 4; // Gaussian blur crossfade
    public static final int TRANSITION_ZOOM     = 5; // grow from screen centre

    // Animation Speed Constants (stored in prefs as "animation_speed")
    public static final int ANIMATION_SPEED_QUARTER = 4; // 0.25x — 2.4s
    public static final int ANIMATION_SPEED_HALF    = 0; // 0.5x  — 1.2s
    public static final int ANIMATION_SPEED_NORMAL  = 1; // 1x    — 0.6s (default)
    public static final int ANIMATION_SPEED_DOUBLE  = 2; // 2x    — 0.3s
    public static final int ANIMATION_SPEED_TRIPLE  = 3; // 3x    — 0.15s
    public static final int ANIMATION_SPEED_STUPID  = 5; // 4x    — 0.05s

    public static final String PLAYLIST_NONE = "none";
    public static final String WALLPAPER_NONE = "none";

    public static final String WORKER_KEY_PLAYLIST_ID = "playlist_id";
    public static final String PREF_CHANGE_ON_UNLOCK  = "change_on_unlock";
    public static final String PREF_SHUFFLE_PLAYLIST  = "shuffle_playlist";
    // --- Shader effects registry -------------------------------------------------
    // Exactly one shader (or none) can be active at a time. PREF_ACTIVE_SHADER
    // stores the ShaderDef.id of the chosen one ("none" = no overlay shader).
    // Per-shader parameters are looked up by ShaderDef.id + ShaderParam.key to
    // build the SharedPreferences key ("shader_<id>_<key>") — one generic path
    // instead of hand-declared PREF_* constants per parameter per shader.
    public static final String PREF_ACTIVE_SHADER = "active_shader";
    public static final String SHADER_NONE = "none";

    public static String shaderPrefKey(String shaderId, String paramKey) {
        return "shader_" + shaderId + "_" + paramKey;
    }

    public enum ShaderParamType { SLIDER, TOGGLE }

    /** One configurable value on a shader: a 0-100 slider or a boolean toggle.
     *  min/max/defaultValue are in the shader's own float unit space (sliders
     *  are stored as 0-100 progress in prefs and mapped to [min,max] at read
     *  time); defaultValue for a TOGGLE is 0f/1f. */
    public static final class ShaderParam {
        public final String key;
        public final String label;
        public final ShaderParamType type;
        public final float min;
        public final float max;
        public final float defaultValue;

        public ShaderParam(String key, String label, ShaderParamType type,
                            float min, float max, float defaultValue) {
            this.key = key;
            this.label = label;
            this.type = type;
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }

        /** Default value expressed as a 0-100 SeekBar progress (SLIDER only). */
        public int defaultProgress() {
            return Math.round((defaultValue - min) / (max - min) * 100f);
        }

        /** Maps a 0-100 SeekBar progress to this param's [min,max] float range. */
        public float progressToValue(int progress) {
            return min + (progress / 100f) * (max - min);
        }
    }

    /** One selectable overlay shader and its configurable parameters. */
    public static final class ShaderDef {
        public final String id;
        public final String displayName;
        public final List<ShaderParam> params;

        public ShaderDef(String id, String displayName, ShaderParam... params) {
            this.id = id;
            this.displayName = displayName;
            this.params = Arrays.asList(params);
        }
    }

    public static final String SHADER_RAIN = "rain";
    public static final String SHADER_RIPPLE = "ripple";
    public static final String SHADER_SNOW = "snow";

    public static final ShaderDef SHADER_DEF_RAIN = new ShaderDef(SHADER_RAIN, "Rain",
            new ShaderParam("intensity", "Intensity", ShaderParamType.SLIDER, 0f, 1f, 0.5f),
            new ShaderParam("speed", "Speed", ShaderParamType.SLIDER, 0f, 2f, 1.0f),
            new ShaderParam("brightness", "Brightness", ShaderParamType.SLIDER, 0f, 2f, 1.0f),
            new ShaderParam("lightning", "Lightning", ShaderParamType.TOGGLE, 0f, 1f, 0f)
    );

    public static final ShaderDef SHADER_DEF_RIPPLE = new ShaderDef(SHADER_RIPPLE, "Ripple",
            new ShaderParam("speed", "Speed", ShaderParamType.SLIDER, 0f, 2f, 1.0f),
            new ShaderParam("cellSize", "Ripple Size", ShaderParamType.SLIDER, 4f, 20f, 10f),
            new ShaderParam("strength", "Strength", ShaderParamType.SLIDER, 0f, 2f, 1.0f),
            new ShaderParam("touchOnly", "Touch-Only Ripples", ShaderParamType.TOGGLE, 0f, 1f, 0f),
            new ShaderParam("rainLines", "Rain Lines", ShaderParamType.TOGGLE, 0f, 1f, 0f),
            new ShaderParam("rainLinesStrength", "Rain Lines Opacity", ShaderParamType.SLIDER, 0f, 2f, 0.7f),
            new ShaderParam("rainLinesSpeed", "Rain Lines Speed", ShaderParamType.SLIDER, 0f, 2f, 1.0f),
            new ShaderParam("rainLinesAngle", "Rain Lines Direction", ShaderParamType.SLIDER, -0.6f, 0.6f, 0.22f)
    );

    public static final ShaderDef SHADER_DEF_SNOW = new ShaderDef(SHADER_SNOW, "Snow",
            new ShaderParam("speed", "Speed", ShaderParamType.SLIDER, 0f, 2f, 1.0f),
            new ShaderParam("density", "Density", ShaderParamType.SLIDER, 0f, 2f, 1.0f),
            new ShaderParam("flakeSize", "Flake Size", ShaderParamType.SLIDER, 0.5f, 3f, 1.0f),
            new ShaderParam("opacity", "Opacity", ShaderParamType.SLIDER, 0f, 1f, 0.85f)
    );

    public static final List<ShaderDef> SHADERS =
            Arrays.asList(SHADER_DEF_RAIN, SHADER_DEF_RIPPLE, SHADER_DEF_SNOW);

    public static ShaderDef findShaderDef(String id) {
        for (ShaderDef def : SHADERS) {
            if (def.id.equals(id)) return def;
        }
        return null;
    }

    // Dual playlist (separate home / lock screen playlists)
    public static final String PREF_DUAL_PLAYLIST_ENABLED = "dual_playlist_enabled";
    public static final String PREF_LOCK_PLAYLIST          = "lock_playlist";

    /** SharedPreferences file name used exclusively by LockLiveWallpaperService. */
    public static final String PREFS_LOCK = "prefs_lock";

    public static final String EXTRA_IS_LOCK_MODE = "extra_is_lock_mode";



    public enum StorageState {NOT_AVAILABLE, WRITEABLE, READ_ONLY}
    public StorageState getExternalStorageState() {
        StorageState result = StorageState.NOT_AVAILABLE;
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return StorageState.WRITEABLE;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return StorageState.READ_ONLY;
        }
        return result;
    }


    public static String getTimeText(long timeInMillis) {
        String timeString = "Wallpaper changes in every ";
        long hours = TimeUnit.MILLISECONDS.toHours(timeInMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis) -
                TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeInMillis) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeInMillis));

        if (hours > 0) timeString = timeString + hours + " hours ";
        if (minutes > 0) timeString = timeString + minutes + " minutes ";
        if (seconds > 0) timeString = timeString + seconds + " seconds ";
        return timeString;
    }

    // Phone face orientation constants
    public static final int FACE_UNKNOWN = -1;
    public static final int FACE_PORTRAIT_UP = 0;      // Normal holding
    public static final int FACE_LANDSCAPE_LEFT = 1;   // Rotated left
    public static final int FACE_LANDSCAPE_RIGHT = 2;  // Rotated right
    public static final int FACE_PORTRAIT_DOWN = 3;    // Upside down
    public static final int FACE_FLAT_UP = 4;          // Lying flat, screen up
    public static final int FACE_FLAT_DOWN = 5;        // Lying flat, screen down

    public static String getFaceName(int face) {
        switch (face) {
            case FACE_PORTRAIT_UP: return "PORTRAIT_UP";
            case FACE_LANDSCAPE_LEFT: return "LANDSCAPE_LEFT";
            case FACE_LANDSCAPE_RIGHT: return "LANDSCAPE_RIGHT";
            case FACE_PORTRAIT_DOWN: return "PORTRAIT_DOWN";
            case FACE_FLAT_UP: return "FLAT_UP";
            case FACE_FLAT_DOWN: return "FLAT_DOWN";
            default: return "UNKNOWN";
        }
    }

    public static String getFaceNameReadable(int face) {
        switch (face) {
            case FACE_PORTRAIT_UP: return "FACING TOP";
            case FACE_LANDSCAPE_LEFT: return "FACING LEFT";
            case FACE_LANDSCAPE_RIGHT: return "FACING RIGHT";
            case FACE_PORTRAIT_DOWN: return "FACING BOTTOM";
            case FACE_FLAT_UP: return "DEFAULT";
            case FACE_FLAT_DOWN: return "INVERTED";
            default: return "UNKNOWN";
        }
    }
}
