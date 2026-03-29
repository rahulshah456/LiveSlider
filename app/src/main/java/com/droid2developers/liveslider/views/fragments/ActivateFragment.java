package com.droid2developers.liveslider.views.fragments;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.droid2developers.liveslider.live_wallpaper.LiveWallpaperService;
import com.droid2developers.liveslider.R;
import com.google.android.material.card.MaterialCardView;

public class ActivateFragment extends Fragment {

    private static final String TAG = ActivateFragment.class.getSimpleName();

    private TextView homeStatusText;
    private TextView lockStatusText;
    private MaterialCardView homeStatusCard;
    private MaterialCardView lockStatusCard;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activate, container, false);

        homeStatusText = view.findViewById(R.id.homeStatusText);
        lockStatusText = view.findViewById(R.id.lockStatusText);
        homeStatusCard = view.findViewById(R.id.homeStatusCard);
        lockStatusCard = view.findViewById(R.id.lockStatusCard);

        view.findViewById(R.id.activate_button).setOnClickListener(v -> {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                new ComponentName(requireContext(), LiveWallpaperService.class))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "onClick: ", e);
                try {
                    startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException e2) {
                    Log.d(TAG, "onClick: ", e2);
                    Toast.makeText(getContext(), R.string
                            .toast_failed_launch_wallpaper_chooser, Toast.LENGTH_LONG).show();
                }
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        view.getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshWallpaperStatus();
    }

    /**
     * Queries WallpaperManager to show which surfaces this live wallpaper is active on.
     *
     * Home slot (FLAG_SYSTEM):
     *   {@code getWallpaperInfo()} — public API since level 5, no permissions needed.
     *
     * Lock slot (FLAG_LOCK):
     *   {@code getWallpaperInfo(FLAG_LOCK)} is a PUBLIC method in API 36
     *   (confirmed from SDK source — no @SystemApi / @hide annotation).
     *   Guarded with >= API 34 and try-catch in case it is absent on API 34/35 firmware.
     *   Return values:
     *     • our WallpaperInfo  → explicitly assigned to lock screen      → 🔒 ✓ (blue)
     *     • null + home active → lock inherits home; our wallpaper shown  → 🔒 ↕ (lighter blue)
     *     • null + home inactive / different pkg → not shown on lock      → 🔒 ✗ (neutral)
     *
     *   Pre-API 34: live wallpapers always occupy BOTH surfaces when set
     *               → mirror home result.
     */
    private void refreshWallpaperStatus() {
        if (getContext() == null || homeStatusText == null) return;

        WallpaperManager wm = WallpaperManager.getInstance(requireContext());
        String pkg = requireContext().getPackageName();

        // ── Home chip ─────────────────────────────────────────────────────────
        WallpaperInfo homeInfo = wm.getWallpaperInfo();
        boolean isActiveOnHome = homeInfo != null && homeInfo.getPackageName().equals(pkg);

        if (isActiveOnHome) {
            homeStatusText.setText(R.string.wallpaper_status_home_active);
            homeStatusCard.setCardBackgroundColor(Color.argb(80, 76, 175, 80));    // green
        } else {
            homeStatusText.setText(R.string.wallpaper_status_home_inactive);
            homeStatusCard.setCardBackgroundColor(Color.argb(50, 255, 255, 255));  // neutral
        }

        // ── Lock chip ─────────────────────────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                WallpaperInfo lockInfo = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK);

                if (lockInfo != null && lockInfo.getPackageName().equals(pkg)) {
                    // Explicitly assigned to the lock slot
                    lockStatusText.setText(R.string.wallpaper_status_lock_active);
                    lockStatusCard.setCardBackgroundColor(Color.argb(80, 33, 150, 243)); // blue

                } else if (lockInfo == null && isActiveOnHome) {
                    // Lock slot not separately configured → inherits home wallpaper.
                    // Our live wallpaper IS rendered on the lock screen.
                    lockStatusText.setText(R.string.wallpaper_status_lock_same_as_home);
                    lockStatusCard.setCardBackgroundColor(Color.argb(60, 33, 150, 243)); // lighter blue

                } else {
                    // Lock has a different wallpaper (static or another live wp), or home
                    // is also not ours — we are NOT shown on the lock screen.
                    lockStatusText.setText(R.string.wallpaper_status_lock_inactive);
                    lockStatusCard.setCardBackgroundColor(Color.argb(50, 255, 255, 255)); // neutral
                }

            } catch (Throwable t) {
                // getWallpaperInfo(FLAG_LOCK) not present on this firmware (API 34/35 edge case)
                // Fall back to same-as-home behaviour
                lockStatusText.setText(isActiveOnHome
                        ? R.string.wallpaper_status_lock_same_as_home
                        : R.string.wallpaper_status_lock_inactive);
                lockStatusCard.setCardBackgroundColor(isActiveOnHome
                        ? Color.argb(60, 33, 150, 243)
                        : Color.argb(50, 255, 255, 255));
            }

        } else {
            // Pre-API 34: live wallpapers always occupy BOTH surfaces when set
            if (isActiveOnHome) {
                lockStatusText.setText(R.string.wallpaper_status_lock_active);
                lockStatusCard.setCardBackgroundColor(Color.argb(80, 33, 150, 243));
            } else {
                lockStatusText.setText(R.string.wallpaper_status_lock_inactive);
                lockStatusCard.setCardBackgroundColor(Color.argb(50, 255, 255, 255));
            }
        }
    }
}
