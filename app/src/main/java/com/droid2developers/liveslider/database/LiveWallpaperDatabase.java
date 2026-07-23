package com.droid2developers.liveslider.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.droid2developers.liveslider.database.dao.PlaylistDao;
import com.droid2developers.liveslider.database.dao.WallpaperDao;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.database.models.Playlist;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.droid2developers.liveslider.utils.Constant.DB_NAME;

@Database(entities = {LocalWallpaper.class, Playlist.class}, version = 6, exportSchema = false)
public abstract class LiveWallpaperDatabase extends RoomDatabase {

    // v5: per-wallpaper horizontal crop bias (triple-tap crop overlay).
    // 0 = center crop, matching the previous behaviour for all existing rows.
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE localwallpaper ADD COLUMN cropBias REAL NOT NULL DEFAULT 0");
        }
    };

    // v6: version bump only, schema identical to v5. Nothing to do.
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // No schema change between v5 and v6.
        }
    };

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
                            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }


}
