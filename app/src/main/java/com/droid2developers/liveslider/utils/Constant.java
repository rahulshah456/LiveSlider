package com.droid2developers.liveslider.utils;

import android.os.Environment;

import java.util.concurrent.TimeUnit;

public class Constant {

    public static String DB_NAME = "db_live_wallpapers";

    public static final String HEADER = "wallpaper_";
    public static final String DEFAULT_WALLPAPER_NAME = "wallpaper_default.jpg";
    private static final String ASSETS_PATH = "file:///android_asset/";
    public static final String DEFAULT_LOCAL_PATH = Constant.ASSETS_PATH + Constant.DEFAULT_WALLPAPER_NAME;

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

    public static final String PLAYLIST_NONE = "none";
    public static final String WALLPAPER_NONE = "none";

    public static final String WORKER_KEY_PLAYLIST_ID = "playlist_id";



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
