package com.droid2developers.liveslider.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.IOException;

public class FileUtil {

    public static final String TAG = FileUtil.class.getSimpleName();

    private final Context mContext;

    public FileUtil(Context mContext) {
        this.mContext = mContext;
    }

    public void scanDeletedFile(String path) {
        try {
            MediaScannerConnection.scanFile(mContext, new String[] { path },
                    null, (path1, uri) -> {
                        Log.i("ExternalStorage", "Scanned " + path1 + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                        if (uri != null) {
                            mContext.getContentResolver().delete(uri, null, null);
                        } else {
                            Log.d(TAG, "scanDeletedFile: unable to delete from MediaScanner!");
                        }

                    });
        } catch (Exception e) {
            e.fillInStackTrace();
        }

    }


    public boolean deleteLocalFile(String wallpaperPath) throws IOException {
        boolean isDeleted;
        File file = new File(wallpaperPath);
        isDeleted = file.delete();
        Log.d(TAG, "deleteLocalFile: 1st Attempt = " + isDeleted);
        if(file.exists() && !isDeleted){
            isDeleted = file.getCanonicalFile().delete();
            Log.d(TAG, "deleteLocalFile: 2nd Attempt = " + isDeleted);
            if(file.exists()){
                isDeleted = mContext.deleteFile(file.getName());
                Log.d(TAG, "deleteLocalFile: Last Attempt = " + isDeleted);
            }
        }
        return isDeleted;
    }


    public File getParentDirectory() {
        String state = Environment.getExternalStorageState();

        // Make sure it's available
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            return mContext.getExternalFilesDir(null);
        } else {
            // Load another directory, probably local memory
            return mContext.getFilesDir();
        }
    }


    public File getCacheDirectory() {
        String state = Environment.getExternalStorageState();

        // Make sure it's available
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            return mContext.getExternalCacheDir();
        } else {
            // Load another directory, probably local memory
            return mContext.getCacheDir();
        }
    }

}
