package com.droid2developers.liveslider.database.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import com.droid2developers.liveslider.database.LiveWallpaperDatabase
import com.droid2developers.liveslider.database.dao.WallpaperDao
import com.droid2developers.liveslider.database.models.LocalWallpaper
import java.io.File
import java.io.IOException

class WallpaperRepository(mContext: Context) {
    private val mContext: Context
    private val mWallpaperDao: WallpaperDao

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    val allWallpapers: LiveData<List<LocalWallpaper?>?>?


    init {
        Log.d(TAG, "WallpaperRepository: init")
        this.mContext = mContext
        val database = LiveWallpaperDatabase.getDatabase(mContext)
        mWallpaperDao = database.wallpaperDao()
        allWallpapers = mWallpaperDao.allWallpapers
    }


    fun getPlaylistWallpapers(playlistId: String?): LiveData<List<LocalWallpaper?>?>? {
        return mWallpaperDao.getPlaylistWallpapers(playlistId)
    }

    suspend fun getWallpapers(playlistId: String?): List<LocalWallpaper?>? {
        return mWallpaperDao.getWallpapersByPlaylist(playlistId)
    }


    fun getDirectPlaylistWallpapers(playlistId: String?): List<LocalWallpaper?>? {
        return mWallpaperDao.getDirectPlaylistWallpapers(playlistId)
    }


    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    fun insert(wallpaper: LocalWallpaper?) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute {
            if (wallpaper != null) {
                mWallpaperDao.insertWallpaper(wallpaper)
            }
        }
    }

    suspend fun updateWallpaper(wallpaper: LocalWallpaper?) {
        if (wallpaper != null) {
            mWallpaperDao.updateWallpaper(wallpaper)
        }
    }


    fun delete(wallpaper: LocalWallpaper) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute {
            try {
                val isDeleted = deleteLocalFile(wallpaper.localPath)
                Log.d(TAG, "delete: final Status = $isDeleted")
                if (isDeleted) {
                    scanDeletedFile(wallpaper.localPath)
                    mWallpaperDao.deleteWallpaper(wallpaper)
                } else {
                    Toast.makeText(mContext, "Error in deleting wallpaper!", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: IOException) {
                e.fillInStackTrace()
            }
        }
    }


    fun deletePlaylistWallpapers(playlistId: String?) {
        LiveWallpaperDatabase.databaseWriteExecutor.execute {
            mWallpaperDao.deletePlaylistWallpapers(playlistId)
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
        val TAG: String = WallpaperRepository::class.java.simpleName
    }
}
