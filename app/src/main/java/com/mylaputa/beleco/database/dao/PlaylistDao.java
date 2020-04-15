package com.mylaputa.beleco.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.mylaputa.beleco.database.models.Playlist;
import java.util.List;

@Dao
public interface PlaylistDao {

    @Insert()
    void insertPlaylist(Playlist playlist);

    @Delete
    void deletePlaylist(Playlist playlist);

    @Update
    void updatePlaylist(Playlist playlist);

    @Query("SELECT * FROM playlist ORDER BY name DESC")
    LiveData<List<Playlist>> getAllPlaylists();

}
