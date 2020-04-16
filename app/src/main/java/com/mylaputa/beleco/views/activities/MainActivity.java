package com.mylaputa.beleco.views.activities;

import android.app.WallpaperManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mylaputa.beleco.R;
import com.mylaputa.beleco.views.fragments.ActivateFragment;
import com.mylaputa.beleco.views.fragments.SettingsFragment;


public class MainActivity extends AppCompatActivity {
    boolean intro;
    WallpaperManager manager;

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
                        .replace(R.id.container, new SettingsFragment())
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
        if (intro && manager.getWallpaperInfo() != null && manager.getWallpaperInfo().getPackageName()
                .equals(this.getPackageName())) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                    .replace(R.id.container, new SettingsFragment())
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