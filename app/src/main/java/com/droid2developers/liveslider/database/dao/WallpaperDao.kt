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

    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    fun getPlaylistWallpapers(key: String?): LiveData<List<LocalWallpaper?>?>?

    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    suspend fun getWallpapersByPlaylist(key: String?): List<LocalWallpaper?>?


    @Query("SELECT * FROM localwallpaper WHERE playlistId = :key ORDER BY name DESC")
    fun getDirectPlaylistWallpapers(key: String?): List<LocalWallpaper?>?

    @get:Query("SELECT * FROM localwallpaper WHERE playlistId = 'Custom' ORDER BY name DESC")
    val allWallpapers: LiveData<List<LocalWallpaper?>?>?
}
