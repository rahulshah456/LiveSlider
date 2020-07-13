package com.droid2developers.liveslider.database.repository;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import com.droid2developers.liveslider.database.LiveWallpaperDatabase;
import com.droid2developers.liveslider.database.dao.WallpaperDao;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class WallpaperRepository {

    public static final String TAG = WallpaperRepository.class.getSimpleName();
    private Context mContext;
    private WallpaperDao mWallpaperDao;
    private LiveData<List<LocalWallpaper>> mLocalWallpapers;


    public WallpaperRepository(Context mContext) {
        Log.d(TAG, "WallpaperRepository: init");
        this.mContext = mContext;
        LiveWallpaperDatabase database = LiveWallpaperDatabase.getDatabase(mContext);
        mWallpaperDao = database.wallpaperDao();
        mLocalWallpapers = mWallpaperDao.getAllWallpapers();
    }


    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<LocalWallpaper>> getAllWallpapers(){
        return mLocalWallpapers;
    }



    public LiveData<List<LocalWallpaper>> getPlaylistWallpapers(String playlistId){
        return mWallpaperDao.getPlaylistWallpapers(playlistId);
    }


    public List<LocalWallpaper> getDirectPlaylistWallpapers(String playlistId){
        return mWallpaperDao.getDirectPlaylistWallpapers(playlistId);
    }


    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(LocalWallpaper wallpaper) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute(() -> {
            mWallpaperDao.insertWallpaper(wallpaper);
        });
    }


    public void delete(LocalWallpaper wallpaper) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute(() -> {
            try {
                boolean isDeleted = deleteLocalFile(wallpaper.getLocalPath());
                Log.d(TAG, "delete: final Status = " + isDeleted);
                if (isDeleted){
                    mWallpaperDao.deleteWallpaper(wallpaper);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }


    public void deletePlaylistWallpapers(String playlistId) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute(() -> {
            mWallpaperDao.deletePlaylistWallpapers(playlistId);
        });
    }


    private boolean deleteLocalFile(String wallpaperPath) throws IOException {
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


}
