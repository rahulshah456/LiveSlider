package com.droid2developers.liveslider.views.fragments;

import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.live_wallpaper.LiveWallpaperService;
import com.droid2developers.liveslider.views.activities.ChangeWallpaperActivity;
import com.droid2developers.liveslider.views.activities.SettingsActivity;

import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.EXTRA_IS_LOCK_MODE;

/**
 * Below API 34, WallpaperManager#getWallpaperInfo() is gated by package-visibility rules and
 * unreliably returns null for this app's own running live wallpaper (see
 * https://issuetracker.google.com/issues/247921423), so activation status can't be detected.
 * Skip detection entirely: always show Activate, Change Wallpaper, and Settings together.
 */
public class SimpleHomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_simple, container, false);

        view.findViewById(R.id.activate_button).setOnClickListener(v -> onActivateClicked());

        view.findViewById(R.id.change_wallpaper_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ChangeWallpaperActivity.class);
            intent.putExtra(EXTRA_IS_LOCK_MODE, false);
            startActivity(intent);
        });

        view.findViewById(R.id.wallpaper_settings_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), SettingsActivity.class);
            intent.putExtra(EXTRA_IS_LOCK_MODE, false);
            startActivity(intent);
        });

        return view;
    }

    private void onActivateClicked() {
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putString("local_wallpaper_path", DEFAULT_LOCAL_PATH)
                .putBoolean("default_wallpaper", true)
                .apply();
        try {
            startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            new ComponentName(requireContext(), LiveWallpaperService.class))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(getContext(), R.string.toast_failed_launch_wallpaper_chooser,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
