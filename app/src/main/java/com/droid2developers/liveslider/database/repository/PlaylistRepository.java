package com.droid2developers.liveslider.database.repository;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.droid2developers.liveslider.database.LiveWallpaperDatabase;
import com.droid2developers.liveslider.database.dao.PlaylistDao;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.database.models.Playlist;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PlaylistRepository {

    public static final String TAG = PlaylistRepository.class.getName();
    private PlaylistDao mPlaylistDao;
    private Context mContext;
    private LiveData<List<Playlist>> mAllPlaylists;


    public PlaylistRepository(Context context) {
        this.mContext = context;
        LiveWallpaperDatabase database = LiveWallpaperDatabase.getDatabase(context);
        mPlaylistDao = database.playlistDao();
        mAllPlaylists = mPlaylistDao.getAllPlaylists();
    }


    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<Playlist>> getAllPlaylists(){
        return mAllPlaylists;
    }


    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(Playlist playlist) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute(() -> {
            mPlaylistDao.insertPlaylist(playlist);
        });
    }


    public void delete(Playlist playlist) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute(() -> {

            WallpaperRepository repository = new WallpaperRepository(mContext);
            List<LocalWallpaper> wallpaperList =
                    repository.getDirectPlaylistWallpapers(playlist.getPlaylistId());
            if (wallpaperList.size() > 0) {
                for (LocalWallpaper wallpaper: wallpaperList) {
                    try {
                        boolean isDeleted = deleteLocalFile(wallpaper.getLocalPath());
                        if (isDeleted) scanDeletedFile(wallpaper.getLocalPath());
                        Log.d(TAG, "delete: " +
                                wallpaper.getLocalPath() + " :: status = " + isDeleted);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            mPlaylistDao.deletePlaylist(playlist);
        });
    }


    public void update(Playlist playlist) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute(() -> {
            mPlaylistDao.updatePlaylist(playlist);
        });
    }


    private void scanDeletedFile(String path) {
        try {
            MediaScannerConnection.scanFile(mContext, new String[] { path },
                    null, (path1, uri) -> {
                        Log.i("ExternalStorage", "Scanned " + path1 + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                        mContext.getContentResolver().delete(uri, null, null);
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

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
