package com.droid2developers.liveslider.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.droid2developers.liveslider.database.models.LocalWallpaper
import com.droid2developers.liveslider.database.repository.PlaylistRepository
import com.droid2developers.liveslider.database.repository.WallpaperRepository
import com.droid2developers.liveslider.utils.Constant.WORKER_KEY_PLAYLIST_ID
import com.droid2developers.liveslider.utils.DeviceMetrics
import com.droid2developers.liveslider.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.shouheng.compress.Compress
import me.shouheng.compress.RequestBuilder
import me.shouheng.compress.strategy.Strategies
import me.shouheng.compress.strategy.compress.Compressor
import me.shouheng.compress.strategy.config.ScaleMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

class PlaylistWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {


    companion object {
        val TAG: String? = Companion::class.java.simpleName
    }

    private var playlistRepository: PlaylistRepository? = null
    private var wallpaperRepository: WallpaperRepository? = null


    override suspend fun doWork(): Result {

        playlistRepository = PlaylistRepository(applicationContext)
        wallpaperRepository = WallpaperRepository(applicationContext)

        Log.d(TAG, "doWork: Start")

        // finding the playlist to process its wallpapers
        val playlistId = inputData.getString(WORKER_KEY_PLAYLIST_ID)

        if (playlistId != null) {
            val playlist = playlistRepository?.getPlaylist(playlistId)
            val wallpapers = wallpaperRepository?.getWallpapers(playlistId)
            val covers = mutableListOf<String?>()
            if (wallpapers?.isNotEmpty() == true) {
                for (wall in wallpapers) {
                    val localPath = processWallpaper(wall)
                    wall?.localPath = localPath
                    wallpaperRepository?.updateWallpaper(wall)
                    covers.add(localPath)
                }
                val coverImage = createCoverImage(covers, playlistId)
                playlist?.isProcessed = true
                playlist?.coverImage = coverImage
                playlistRepository?.update(playlist)
            }
        }

        Log.d(TAG, "doWork: Done")

        return Result.success()
    }

    private suspend fun processWallpaper(wall: LocalWallpaper?): String? {

        val originalPath = wall?.originalPath ?: return null

        val windowManager =
            applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val height: Int = DeviceMetrics.getRealDisplayHeight(windowManager)
        val width: Int = DeviceMetrics.getRealDisplayWidth(windowManager)
        val targetDirectory: File = FileUtil(applicationContext).getParentDirectory()

        val localPath = suspendCancellableCoroutine<String> { continuation ->
            Glide.with(applicationContext)
                .asBitmap()
                .load(originalPath)
                .into(object : CustomTarget<Bitmap?>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap?>?
                    ) {
                        val compressor: Compressor =
                            Compress.Companion.with(applicationContext, resource)
                                .setQuality(80)
                                .setTargetDir(targetDirectory.absolutePath)
                                .strategy(Strategies.compressor())
                                .setIgnoreIfSmaller(true)
                                .setMaxHeight(max(height, width).toFloat())
                                .setMaxWidth(min(height, width).toFloat())
                                .setScaleMode(ScaleMode.SCALE_SMALLER)

                        compressor.launch()
                        compressor.setCompressListener(object : RequestBuilder.Callback<File> {
                            override fun onError(throwable: Throwable) {
                                System.gc()
                                continuation.resumeWithException(throwable)
                            }

                            override fun onStart() {}

                            override fun onSuccess(result: File) {
                                System.gc()
                                continuation.resume(result.absolutePath)
                            }

                        })
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {

                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        Log.d(TAG, "onLoadFailed: ")
                    }
                })
        }

        return localPath
    }


    private suspend fun createCoverImage(
        covers: MutableList<String?>?,
        playlistId: String
    ): String {
        val firstThree = covers?.slice(0..3)
        val cover = createCompositeImage(firstThree)
        val file = File(applicationContext.filesDir, "${playlistId}-cover.png")
        val localPath = suspendCancellableCoroutine<String> { continuation ->
            try {
                val stream = FileOutputStream(file)
                cover?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                stream.flush()
                stream.close()
                continuation.resume(file.absolutePath)
            } catch (e: IOException) {
                e.fillInStackTrace()
                continuation.resumeWithException(e)
            }
        }
        return localPath
    }

    private suspend fun createCompositeImage(contentUris: List<String?>?): Bitmap? {
        return withContext(Dispatchers.IO) {
            if (contentUris?.isEmpty() == true) return@withContext null

            // Define target dimensions
            val compositeWidth = 1600
            val compositeHeight = 900

            // Load the images with down-sampling
            val leftImage =
                decodeSampledBitmapFromUri(
                    Uri.fromFile(contentUris?.get(0)?.let { File(it) }),
                    compositeWidth / 2,
                    compositeHeight
                )
                    ?: return@withContext null
            val topRightImage =
                decodeSampledBitmapFromUri(
                    Uri.fromFile(contentUris?.get(1)?.let { File(it) }),
                    compositeWidth / 2,
                    compositeHeight / 2
                )
                    ?: return@withContext null
            val bottomRightImage =
                decodeSampledBitmapFromUri(
                    Uri.fromFile(contentUris?.get(2)?.let { File(it) }),
                    compositeWidth / 2,
                    compositeHeight / 2
                )
                    ?: return@withContext null

            // Create a new bitmap for the composite image
            val compositeBitmap =
                Bitmap.createBitmap(compositeWidth, compositeHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(compositeBitmap)
            val paint = Paint()

            // Draw the left image
            canvas.drawBitmap(leftImage, 0f, 0f, paint)

            // Draw the top right image
            canvas.drawBitmap(topRightImage, compositeWidth / 2.toFloat(), 0f, paint)

            // Draw the bottom right image
            canvas.drawBitmap(
                bottomRightImage,
                compositeWidth / 2.toFloat(),
                compositeHeight / 2.toFloat(),
                paint
            )

            // Recycle bitmaps to free up memory
            leftImage.recycle()
            topRightImage.recycle()
            bottomRightImage.recycle()

            compositeBitmap
        }
    }

    // Function to decode bitmap with inSampleSize for down-sampling
    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(
            applicationContext.contentResolver.openInputStream(uri),
            null,
            options
        )

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeStream(
            applicationContext.contentResolver.openInputStream(
                uri
            ), null, options
        )
    }

    // Function to calculate inSampleSize
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

}