package com.mylaputa.beleco.views.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.adapters.TabAdapter;
import com.mylaputa.beleco.database.models.LocalWallpaper;
import com.mylaputa.beleco.database.models.Playlist;
import com.mylaputa.beleco.database.repository.PlaylistRepository;
import com.mylaputa.beleco.database.repository.WallpaperRepository;
import com.mylaputa.beleco.utils.Constant;
import com.mylaputa.beleco.viewmodel.PlaylistViewModel;
import com.mylaputa.beleco.viewmodel.WallpaperViewModel;
import com.mylaputa.beleco.views.fragments.SingleFragment;
import com.mylaputa.beleco.views.fragments.SlideshowFragment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static com.mylaputa.beleco.utils.Constant.PLAYLIST_SLIDESHOW;

public class ChangeWallpaperActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_wallpaper);

        // Initializing ViewModels
        WallpaperViewModel wallpaperViewModel =
                new ViewModelProvider(this).get(WallpaperViewModel.class);
        PlaylistViewModel playlistViewModel =
                new ViewModelProvider(this).get(PlaylistViewModel.class);

        // Initializing ViewPager
        ViewPager viewPager = findViewById(R.id.viewPagerId);
        TabAdapter tabAdapter = new TabAdapter(getSupportFragmentManager());
        // Adding required fragments
        TabLayout tabLayout = findViewById(R.id.tabLayoutId);
        tabAdapter.addFragment(new SingleFragment(),"Single");
        tabAdapter.addFragment(new SlideshowFragment(),"Slideshow");
        // Setting up final preview
        viewPager.setAdapter(tabAdapter);
        tabLayout.setupWithViewPager(viewPager);

    }
}
