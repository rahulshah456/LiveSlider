package com.droid2developers.liveslider.views.activities;

import android.app.WallpaperManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.views.fragments.ActivateFragment;
import com.droid2developers.liveslider.views.fragments.HomeFragment;

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
}