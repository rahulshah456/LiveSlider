package com.droid2developers.liveslider.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.droid2developers.liveslider.database.dao.PlaylistDao;
import com.droid2developers.liveslider.database.dao.WallpaperDao;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.database.models.Playlist;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.droid2developers.liveslider.utils.Constant.DB_NAME;

@Database(entities = {LocalWallpaper.class, Playlist.class}, version = 2, exportSchema = false)
public abstract class LiveWallpaperDatabase extends RoomDatabase {

    public abstract WallpaperDao wallpaperDao();
    public abstract PlaylistDao playlistDao();

    private static volatile LiveWallpaperDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static LiveWallpaperDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (LiveWallpaperDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            LiveWallpaperDatabase.class, DB_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }


}
