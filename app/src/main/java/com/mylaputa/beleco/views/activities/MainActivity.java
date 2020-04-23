package com.mylaputa.beleco.views.activities;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.app.usage.StorageStatsManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.views.fragments.ActivateFragment;
import com.mylaputa.beleco.views.fragments.HomeFragment;

import java.io.File;
import java.io.IOException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.UUID;

import static android.os.storage.StorageManager.ACTION_MANAGE_STORAGE;


public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();
    private boolean intro;
    private WallpaperManager manager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = WallpaperManager.getInstance(this);
        if (savedInstanceState == null) {
            if (manager.getWallpaperInfo() == null ||
                    !manager.getWallpaperInfo().getPackageName().equals(this.getPackageName())) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, new ActivateFragment())
                        .commit();
                intro = true;
            } else {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, new HomeFragment())
                        .commit();
                intro = false;
            }
        }
    }


    public class CheckStorage extends AsyncTask<Void,Void,Void>{

        @Override
        protected Void doInBackground(Void... voids) {

            // App needs 10 MB within internal storage.
            final long NUM_BYTES_NEEDED_FOR_MY_APP = 1024 * 1024 * 10L;

            try {

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    StorageManager storageManager = getApplicationContext().getSystemService(StorageManager.class);
                    if (storageManager!=null){
                        UUID appSpecificInternalDirUuid = storageManager.getUuidForPath(getFilesDir());
                        long availableBytes = storageManager.getAllocatableBytes(appSpecificInternalDirUuid);
                        if (availableBytes >= NUM_BYTES_NEEDED_FOR_MY_APP) {
                            storageManager.allocateBytes(appSpecificInternalDirUuid, NUM_BYTES_NEEDED_FOR_MY_APP);
                        } else {
                            // Display prompt to user, requesting that they choose files to remove.
                            Intent storageIntent = new Intent();
                            storageIntent.setAction(ACTION_MANAGE_STORAGE);
                        }
                    }
                }

            } catch (IOException e){
                e.printStackTrace();
            }

            return null;
        }
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("intro", intro);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        intro = savedInstanceState.getBoolean("intro");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (intro && manager.getWallpaperInfo() != null &&
                manager.getWallpaperInfo().getPackageName().equals(this.getPackageName())) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                    .replace(R.id.container, new HomeFragment())
                    .commit();
            intro = false;
        } else if (!intro && (manager.getWallpaperInfo() == null ||
                !manager.getWallpaperInfo().getPackageName().equals(this.getPackageName()))) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new ActivateFragment())
                    .commit();
            intro = true;
        }
    }



    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }
}