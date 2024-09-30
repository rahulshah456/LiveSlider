package com.droid2developers.liveslider.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.droid2developers.liveslider.database.models.Playlist

@Dao
interface PlaylistDao {

    @get:Query("SELECT * FROM playlist ORDER BY name DESC")
    val allPlaylists: LiveData<List<Playlist?>?>?

    @Insert
    fun insertPlaylist(playlist: Playlist)

    @Delete
    fun deletePlaylist(playlist: Playlist)

    @Update
    fun updatePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlist WHERE playlistId == :playlistId")
    suspend fun getPlaylist(playlistId: String?): Playlist?
}
