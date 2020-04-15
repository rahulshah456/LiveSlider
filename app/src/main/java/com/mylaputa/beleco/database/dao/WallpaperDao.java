package com.mylaputa.beleco.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.mylaputa.beleco.database.models.LocalWallpaper;
import java.util.List;

public interface WallpaperDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWallpaper(LocalWallpaper wallpaper);

    @Delete
    void deleteWallpaper(LocalWallpaper wallpaper);

    @Query("DELETE FROM localwallpaper WHERE playlistId = :key")
    LiveData<LocalWallpaper> deletePlaylistWallpapers(String key);

    @Query("SELECT * FROM localwallpaper WHERE id = :key")
    LiveData<LocalWallpaper> getWallpaper(int key);

    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    LiveData<List<LocalWallpaper>> getPlaylistWallpapers(String key);

    @Query("SELECT * FROM localwallpaper ORDER BY name DESC")
    LiveData<List<LocalWallpaper>> getAllWallpapers();

}
