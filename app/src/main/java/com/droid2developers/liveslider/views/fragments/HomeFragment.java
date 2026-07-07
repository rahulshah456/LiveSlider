package com.droid2developers.liveslider.views.fragments;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.live_wallpaper.LiveWallpaperService;
import com.droid2developers.liveslider.live_wallpaper.LockLiveWallpaperService;
import com.droid2developers.liveslider.views.activities.ChangeWallpaperActivity;
import com.droid2developers.liveslider.views.activities.SettingsActivity;

import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCK_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.EXTRA_IS_LOCK_MODE;
import static com.droid2developers.liveslider.utils.Constant.PREF_DUAL_PLAYLIST_ENABLED;

public class HomeFragment extends Fragment {

    private static final String TAG = HomeFragment.class.getSimpleName();
    private boolean isLockMode = false;

    private View activeControls;
    private View inactiveControls;
    private TextView activateTitle;
    private TextView activateHint;

    public static HomeFragment newInstance(boolean isLockMode) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putBoolean(EXTRA_IS_LOCK_MODE, isLockMode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isLockMode = getArguments().getBoolean(EXTRA_IS_LOCK_MODE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        activeControls = view.findViewById(R.id.active_controls);
        inactiveControls = view.findViewById(R.id.inactive_controls);
        activateTitle = view.findViewById(R.id.activate_title);
        activateHint = view.findViewById(R.id.activateHintText);

        view.findViewById(R.id.change_wallpaper_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ChangeWallpaperActivity.class);
            intent.putExtra(EXTRA_IS_LOCK_MODE, isLockMode);
            startActivity(intent);
        });

        view.findViewById(R.id.wallpaper_settings_button).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), SettingsActivity.class);
            intent.putExtra(EXTRA_IS_LOCK_MODE, isLockMode);
            startActivity(intent);
        });

        view.findViewById(R.id.activate_button).setOnClickListener(v -> onActivateClicked());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        if (getContext() == null) return;

        WallpaperManager wm = WallpaperManager.getInstance(requireContext());
        String pkg = requireContext().getPackageName();
        boolean isActive;

        if (isLockMode) {
            activateTitle.setText(R.string.nav_lock);
            activateHint.setText(R.string.activate_hint_lock);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    WallpaperInfo lockInfo = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
                    isActive = lockInfo != null && lockInfo.getPackageName().equals(pkg)
                            && LockLiveWallpaperService.class.getName().equals(lockInfo.getServiceName());
                } catch (Throwable t) {
                    isActive = false;
                }
            } else {
                // Pre-API 34, if home is active, lock is active too
                WallpaperInfo homeInfo = wm.getWallpaperInfo();
                isActive = homeInfo != null && homeInfo.getPackageName().equals(pkg);
            }
        } else {
            activateTitle.setText(R.string.nav_home);
            activateHint.setText(R.string.activate_hint_default);
            WallpaperInfo homeInfo = wm.getWallpaperInfo();
            isActive = homeInfo != null && homeInfo.getPackageName().equals(pkg)
                    && LiveWallpaperService.class.getName().equals(homeInfo.getServiceName());
        }

        if (isActive) {
            activeControls.setVisibility(View.VISIBLE);
            inactiveControls.setVisibility(View.GONE);
        } else {
            activeControls.setVisibility(View.GONE);
            inactiveControls.setVisibility(View.VISIBLE);
        }
    }

    private void onActivateClicked() {
        if (isLockMode) {
            SharedPreferences lockPrefs = requireContext()
                    .getSharedPreferences("prefs_lock", Context.MODE_PRIVATE);
            if (!lockPrefs.contains("local_wallpaper_path")) {
                lockPrefs.edit()
                        .putString("local_wallpaper_path", DEFAULT_LOCK_LOCAL_PATH)
                        .putBoolean("default_wallpaper", true)
                        .apply();
            }
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putBoolean(PREF_DUAL_PLAYLIST_ENABLED, true).apply();
            launchWallpaperChooser(LockLiveWallpaperService.class);
        } else {
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putString("local_wallpaper_path", DEFAULT_LOCAL_PATH)
                    .putBoolean("default_wallpaper", true)
                    .apply();
            launchWallpaperChooser(LiveWallpaperService.class);
        }
    }

    private void launchWallpaperChooser(Class<?> serviceClass) {
        try {
            startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            new ComponentName(requireContext(), serviceClass))
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));
    }
}
