package com.droid2developers.liveslider.views.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.views.activities.ChangeWallpaperActivity;
import com.droid2developers.liveslider.views.activities.SettingsActivity;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        view.findViewById(R.id.change_wallpaper_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ChangeWallpaperActivity.class);
            startActivity(intent);
        });


        view.findViewById(R.id.wallpaper_settings_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), SettingsActivity.class);
            startActivity(intent);
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));
    }
}
