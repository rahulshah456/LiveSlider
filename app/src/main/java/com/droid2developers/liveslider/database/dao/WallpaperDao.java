package com.droid2developers.liveslider.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import java.util.List;

@Dao
public interface WallpaperDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWallpaper(LocalWallpaper wallpaper);

    @Delete
    void deleteWallpaper(LocalWallpaper wallpaper);

    @Query("DELETE FROM localwallpaper WHERE playlistId = :key")
    void deletePlaylistWallpapers(String key);

    @Query("SELECT * FROM localwallpaper WHERE id = :key")
    LiveData<LocalWallpaper> getWallpaper(int key);

    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    LiveData<List<LocalWallpaper>> getPlaylistWallpapers(String key);

    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    List<LocalWallpaper> getDirectPlaylistWallpapers(String key);

    @Query("SELECT * FROM localwallpaper WHERE playlistId = 'Custom' ORDER BY name DESC")
    LiveData<List<LocalWallpaper>> getAllWallpapers();

}
