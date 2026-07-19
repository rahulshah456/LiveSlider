package com.droid2developers.liveslider.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.droid2developers.liveslider.database.models.LocalWallpaper

@Dao
interface WallpaperDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWallpaper(wallpaper: LocalWallpaper)

    @Update
    suspend fun updateWallpaper(wallpaper: LocalWallpaper): Int

    @Delete
    fun deleteWallpaper(wallpaper: LocalWallpaper)

    @Query("DELETE FROM localwallpaper WHERE playlistId = :key")
    fun deletePlaylistWallpapers(key: String?)

    @Query("SELECT * FROM localwallpaper WHERE id = :key")
    fun getWallpaper(key: Int): LiveData<LocalWallpaper?>?

    // Rendering query: excludes rows still awaiting crop (localPath null) so the live
    // wallpaper engine never picks up a not-yet-processed image mid-slideshow.
    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key AND localPath IS NOT NULL ORDER BY name DESC")
    fun getPlaylistWallpapers(key: String?): LiveData<List<LocalWallpaper?>?>?

    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    suspend fun getWallpapersByPlaylist(key: String?): List<LocalWallpaper?>?


    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    fun getDirectPlaylistWallpapers(key: String?): List<LocalWallpaper?>?

    @get:Query("SELECT * FROM localwallpaper WHERE playlistId = 'Custom' ORDER BY name DESC")
    val allWallpapers: LiveData<List<LocalWallpaper?>?>?

    @Query("SELECT COUNT(*) FROM localwallpaper WHERE playlistId = :key AND localPath IS NOT NULL")
    fun getProcessedCount(key: String?): LiveData<Int>

    // Crop bias (triple-tap crop overlay). Keyed by localPath because the live
    // wallpaper engine identifies the current image by its file path, not row id.
    @Query("UPDATE localwallpaper SET cropBias = :bias WHERE localPath = :path")
    fun updateCropBias(path: String?, bias: Float)

    @Query("SELECT cropBias FROM localwallpaper WHERE localPath = :path LIMIT 1")
    fun getCropBias(path: String?): Float?
}
