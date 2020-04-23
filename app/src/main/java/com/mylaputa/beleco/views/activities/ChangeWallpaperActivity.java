package com.mylaputa.beleco.views.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import android.os.Bundle;
import android.view.View;

import com.google.android.material.tabs.TabLayout;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.adapters.TabAdapter;
import com.mylaputa.beleco.viewmodel.PlaylistViewModel;
import com.mylaputa.beleco.viewmodel.WallpaperViewModel;
import com.mylaputa.beleco.views.fragments.SingleFragment;
import com.mylaputa.beleco.views.fragments.SlideshowFragment;

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

        CardView backButton = findViewById(R.id.backButtonId);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

    }
}
