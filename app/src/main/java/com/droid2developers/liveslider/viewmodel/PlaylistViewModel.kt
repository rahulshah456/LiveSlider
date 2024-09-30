package com.droid2developers.liveslider.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.droid2developers.liveslider.database.models.Playlist
import com.droid2developers.liveslider.database.repository.PlaylistRepository

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val mRepository = PlaylistRepository(application)
    val allPlaylists: LiveData<List<Playlist?>?>? = mRepository.allPlaylists

    fun insert(playlist: Playlist?) {
        mRepository.insert(playlist)
    }

    fun delete(playlist: Playlist?) {
        if (playlist != null) {
            mRepository.delete(playlist)
        }
    }

    fun update(playlist: Playlist?) {
        mRepository.update(playlist)
    }
}
