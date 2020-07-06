package com.mylaputa.beleco.utils;

import android.os.Environment;

public class Constant {

    public static String DB_NAME = "db_live_wallpapers";

    public static final String HEADER = "wallpaper_";
    public static final String DEFAULT_WALLPAPER_NAME = "wallpaper_default.jpg";
    private static final String ASSETS_PATH = "file:///android_asset/";
    public static final String DEFAULT_LOCAL_PATH = Constant.ASSETS_PATH + Constant.DEFAULT_WALLPAPER_NAME;


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

    public static final String PLAYLIST_NONE = "none";
    public static final String WALLPAPER_NONE = "none";



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
}
