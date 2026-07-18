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
    private TextView activateError;

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
        activateError = view.findViewById(R.id.activateErrorText);

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
        boolean isActive;
        boolean wrongScreenActive;

        if (isLockMode) {
            activateTitle.setText(R.string.nav_lock);
            activateHint.setText(R.string.activate_hint_lock);
            isActive = isServiceActiveOnLock(wm, LockLiveWallpaperService.class);
            // The system picker applies whichever component we launched with (LockLiveWallpaperService)
            // to whichever slot the user picked — so "wrong screen" means it landed on Home instead.
            wrongScreenActive = !isActive && isServiceActiveOnHome(wm, LockLiveWallpaperService.class);
        } else {
            activateTitle.setText(R.string.nav_home);
            activateHint.setText(R.string.activate_hint_default);
            isActive = isServiceActiveOnHome(wm, LiveWallpaperService.class);
            wrongScreenActive = !isActive && isServiceActiveOnLock(wm, LiveWallpaperService.class);
        }

        if (isActive) {
            activeControls.setVisibility(View.VISIBLE);
            inactiveControls.setVisibility(View.GONE);
        } else {
            activeControls.setVisibility(View.GONE);
            inactiveControls.setVisibility(View.VISIBLE);
        }

        if (activateError == null) return;
        if (!isActive && wrongScreenActive) {
            activateError.setText(isLockMode
                    ? R.string.activated_wrong_screen_lock
                    : R.string.activated_wrong_screen_home);
            activateError.setVisibility(View.VISIBLE);
        } else {
            activateError.setVisibility(View.GONE);
        }
    }

    private boolean isServiceActiveOnHome(WallpaperManager wm, Class<?> serviceClass) {
        WallpaperInfo info = wm.getWallpaperInfo();
        return isService(info, serviceClass);
    }

    private boolean isServiceActiveOnLock(WallpaperManager wm, Class<?> serviceClass) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                return isService(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK), serviceClass);
            } catch (Throwable t) {
                return false;
            }
        }
        // Pre-API 34 there's no separate lock slot: lock is considered active whenever
        // ANY of this app's live wallpaper services is the current system wallpaper.
        WallpaperInfo homeInfo = wm.getWallpaperInfo();
        return homeInfo != null && homeInfo.getPackageName().equals(requireContext().getPackageName());
    }

    private boolean isService(@Nullable WallpaperInfo info, Class<?> serviceClass) {
        return info != null && info.getPackageName().equals(requireContext().getPackageName())
                && serviceClass.getName().equals(info.getServiceName());
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
