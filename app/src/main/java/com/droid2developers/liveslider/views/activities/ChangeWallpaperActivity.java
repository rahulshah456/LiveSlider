package com.droid2developers.liveslider.views.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import android.os.Bundle;
import android.view.View;

import com.google.android.material.tabs.TabLayout;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.adapters.TabAdapter;
import com.droid2developers.liveslider.viewmodel.PlaylistViewModel;
import com.droid2developers.liveslider.viewmodel.WallpaperViewModel;
import com.droid2developers.liveslider.views.fragments.SingleFragment;
import com.droid2developers.liveslider.views.fragments.SlideshowFragment;

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
        backButton.setOnClickListener(v -> onBackPressed());

    }
}
