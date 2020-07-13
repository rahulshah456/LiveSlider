package com.droid2developers.liveslider.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.database.repository.WallpaperRepository;

import java.util.List;

public class WallpaperViewModel extends AndroidViewModel {

    private static final String TAG = WallpaperViewModel.class.getSimpleName();
    private WallpaperRepository mRepository;
    private String playlistId = null;
    private LiveData<List<LocalWallpaper>> playlistWallpapers;
    private LiveData<List<LocalWallpaper>> allWallpapers;

    public WallpaperViewModel(@NonNull Application application) {
        super(application);
        Log.d(TAG, "WallpaperViewModel: init");
        mRepository = new WallpaperRepository(application);
        allWallpapers = mRepository.getAllWallpapers();
    }


    public LiveData<List<LocalWallpaper>> getAllWallpapers() {
        return allWallpapers;
    }

    public LiveData<List<LocalWallpaper>> getPlaylistWallpapers(String playlistId){
        if (this.playlistId.equals(playlistId) && playlistWallpapers!=null){
            return playlistWallpapers;
        } else {
            this.playlistId = playlistId;
            playlistWallpapers = mRepository.getPlaylistWallpapers(playlistId);
            return playlistWallpapers;
        }
    }

    public void insert(LocalWallpaper wallpaper) {
        mRepository.insert(wallpaper);
    }

    public void delete(LocalWallpaper wallpaper){
        mRepository.delete(wallpaper);
    }

    public void deletePlaylistWallpapers(String playlistId){
        mRepository.deletePlaylistWallpapers(playlistId);
    }


    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared: ");
    }
}
