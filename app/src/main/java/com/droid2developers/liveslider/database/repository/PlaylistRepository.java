package com.droid2developers.liveslider.database.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import com.droid2developers.liveslider.database.LiveWallpaperDatabase;
import com.droid2developers.liveslider.database.dao.PlaylistDao;
import com.droid2developers.liveslider.database.models.Playlist;
import java.util.List;

public class PlaylistRepository {

    private PlaylistDao mPlaylistDao;
    private LiveData<List<Playlist>> mAllPlaylists;


    public PlaylistRepository(Context context) {
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
            mPlaylistDao.deletePlaylist(playlist);
        });
    }


    public void update(Playlist playlist) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute(() -> {
            mPlaylistDao.updatePlaylist(playlist);
        });
    }

}
