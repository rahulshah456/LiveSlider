package com.droid2developers.liveslider.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.droid2developers.liveslider.utils.TimestampConverter
import java.util.Date

@Entity
class Playlist(

    @JvmField
    var playlistId: String? = null,

    @JvmField
    var name: String? = null,

    @JvmField
    var coverImage: String? = null,

    @JvmField
    @field:TypeConverters(TimestampConverter::class)
    var createdAt: Date? = null,

    @JvmField
    @field:TypeConverters(TimestampConverter::class)
    var modifiedAt: Date? = null,

    @JvmField
    var size: Int = 0,

    @JvmField
    var isProcessed: Boolean = false

) {
    @JvmField
    @field:PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
