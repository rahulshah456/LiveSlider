package com.droid2developers.liveslider.database.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import com.droid2developers.liveslider.database.LiveWallpaperDatabase
import com.droid2developers.liveslider.database.dao.PlaylistDao
import com.droid2developers.liveslider.database.models.Playlist
import java.io.File
import java.io.IOException

class PlaylistRepository(private val mContext: Context) {
    private val mPlaylistDao: PlaylistDao

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    val allPlaylists: LiveData<List<Playlist?>?>?


    init {
        val database = LiveWallpaperDatabase.getDatabase(mContext)
        mPlaylistDao = database.playlistDao()
        allPlaylists = mPlaylistDao.allPlaylists
    }


    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    fun insert(playlist: Playlist?) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute {
            if (playlist != null) {
                mPlaylistDao.insertPlaylist(playlist)
            }
        }
    }

    suspend fun getPlaylist(playlistId: String): Playlist? {
        return mPlaylistDao.getPlaylist(playlistId)
    }


    fun delete(playlist: Playlist) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute {
            val repository = WallpaperRepository(mContext)
            val wallpaperList =
                repository.getDirectPlaylistWallpapers(playlist.playlistId)
            if (wallpaperList?.isNotEmpty() == true) {
                for (wallpaper in wallpaperList) {
                    try {
                        if (wallpaper?.localPath != null) {
                            val isDeleted = deleteLocalFile(wallpaper.localPath)
                            if (isDeleted) scanDeletedFile(wallpaper.localPath)
                            Log.d(
                                TAG, "delete: " +
                                        wallpaper.localPath + " :: status = " + isDeleted
                            )
                        }
                    } catch (e: IOException) {
                        e.fillInStackTrace()
                    }
                }
            }
            mPlaylistDao.deletePlaylist(playlist)
        }
    }


    fun update(playlist: Playlist?) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute {
            if (playlist != null) {
                mPlaylistDao.updatePlaylist(playlist)
            }
        }
    }


    private fun scanDeletedFile(path: String) {
        try {
            MediaScannerConnection.scanFile(
                mContext, arrayOf(path),
                null
            ) { path1: String, uri: Uri ->
                Log.i("ExternalStorage", "Scanned $path1:")
                Log.i("ExternalStorage", "-> uri=$uri")
                mContext.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            e.fillInStackTrace()
        }
    }


    @Throws(IOException::class)
    private fun deleteLocalFile(wallpaperPath: String): Boolean {
        var isDeleted: Boolean
        val file = File(wallpaperPath)
        isDeleted = file.delete()
        Log.d(TAG, "deleteLocalFile: 1st Attempt = $isDeleted")
        if (file.exists() && !isDeleted) {
            isDeleted = file.canonicalFile.delete()
            Log.d(TAG, "deleteLocalFile: 2nd Attempt = $isDeleted")
            if (file.exists()) {
                isDeleted = mContext.deleteFile(file.name)
                Log.d(TAG, "deleteLocalFile: Last Attempt = $isDeleted")
            }
        }
        return isDeleted
    }

    companion object {
        val TAG: String = PlaylistRepository::class.java.name
    }
}
