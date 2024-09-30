package com.droid2developers.liveslider.utils

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkRequest
import com.droid2developers.liveslider.background.PlaylistWorker
import com.droid2developers.liveslider.utils.Constant.WORKER_KEY_PLAYLIST_ID
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

fun createInputDataForWorker(inputMap: Map<String, Any?>): Data {
    val builder = Data.Builder()
    for ((key, value) in inputMap) {
        when (value) {
            is Int -> builder.putInt(key, value)
            is String -> builder.putString(key, value)
            is Boolean -> builder.putBoolean(key, value)
        }
    }
    return builder.build()
}



fun processPlaylistWorker(playlistId: String, name: String): OneTimeWorkRequest {
    // Create charging constraint
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()

    //create input data for worker
    val data = createInputDataForWorker(mapOf(
        WORKER_KEY_PLAYLIST_ID to playlistId,
    ))

    //worker to detect faces in the images
    return OneTimeWorkRequest.Builder(PlaylistWorker::class.java)
        .setInputData(data)
        .addTag(name)
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.LINEAR,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()
}