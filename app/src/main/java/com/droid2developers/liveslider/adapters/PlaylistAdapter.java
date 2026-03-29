package com.droid2developers.liveslider.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.database.models.Playlist;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;
import static com.droid2developers.liveslider.utils.Constant.PLAYLIST_NONE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SLIDESHOW;
import static com.droid2developers.liveslider.utils.Constant.WALLPAPER_NONE;
import static com.droid2developers.liveslider.utils.Constant.PREF_DUAL_PLAYLIST_ENABLED;
import static com.droid2developers.liveslider.utils.Constant.PREF_LOCK_PLAYLIST;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.MyViewHolder> {

    private static final String TAG = PlaylistAdapter.class.getSimpleName();
    private OnItemClickListener onItemClickListener;
    private final List<Playlist> mAllPlaylist = new ArrayList<>();
    private String playlistId;
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    private final Context mContext;


    @SuppressLint("CommitPrefEdits")
    public PlaylistAdapter(Context mContext, String playlistId) {
        this.mContext = mContext;
        this.playlistId = playlistId;
        prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        editor = prefs.edit();
    }


    public interface OnItemClickListener {
        void OnItemLongClick(int position);
    }


    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }


    public class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        ImageView thumbIv;
        TextView count, title, creationDate;
        ProgressBar progressIndicator;
        RelativeLayout activeBadge;
        RelativeLayout lockBadge;

        MyViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            thumbIv = itemView.findViewById(R.id.mainThumbnailId);
            title = itemView.findViewById(R.id.collectionTitleId);
            creationDate = itemView.findViewById(R.id.creationDateId);
            activeBadge = itemView.findViewById(R.id.active_badge);
            lockBadge = itemView.findViewById(R.id.lock_badge);
            progressIndicator = itemView.findViewById(R.id.progressView);
            progressIndicator.setIndeterminate(true);
        }

        @Override
        public void onClick(View view) {
            Playlist playlist = getItemList().get(getLayoutPosition());
            boolean isDualEnabled = prefs.getBoolean(PREF_DUAL_PLAYLIST_ENABLED, false);

            if (isDualEnabled) {
                // Dual mode: let user choose Home or Lock assignment
                new MaterialAlertDialogBuilder(mContext)
                        .setIcon(ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.error_24dp, null))
                        .setTitle(R.string.dual_playlist_assign_title)
                        .setMessage(R.string.dual_playlist_assign_message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.dual_playlist_home, (dialog, which) -> {
                            updateHomeSelection(playlist);
                            dialog.dismiss();
                        })
                        .setNeutralButton(R.string.dual_playlist_lock, (dialog, which) -> {
                            updateLockSelection(playlist);
                            dialog.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                // Single mode: existing flow
                new MaterialAlertDialogBuilder(mContext)
                        .setIcon(ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.error_24dp, null))
                        .setTitle("Activate Playlist?")
                        .setMessage(R.string.playlist_activation)
                        .setCancelable(false)
                        .setPositiveButton("Activate", (dialog, which) -> {
                            updateSelection(playlist);
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            }
        }

        @Override
        public boolean onLongClick(View view) {
            onItemClickListener.OnItemLongClick(this.getLayoutPosition());
            return true;
        }
    }


    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_playlist, parent, false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {

        Playlist playlist = mAllPlaylist.get(position);
        String coverImage = playlist.coverImage;

        String currentPlaylist = prefs.getString("current_playlist", PLAYLIST_NONE);
        String currentLockPlaylist = prefs.getString(PREF_LOCK_PLAYLIST, PLAYLIST_NONE);
        boolean isDualEnabled = prefs.getBoolean(PREF_DUAL_PLAYLIST_ENABLED, false);
        int wallpaperType = prefs.getInt("type", TYPE_SINGLE);

        // Home active badge
        if (wallpaperType == TYPE_SLIDESHOW && currentPlaylist.equals(playlist.playlistId)) {
            holder.activeBadge.setVisibility(View.VISIBLE);
        } else {
            holder.activeBadge.setVisibility(View.GONE);
        }

        // Lock badge — only shown when dual mode is enabled
        if (isDualEnabled && !currentLockPlaylist.equals(PLAYLIST_NONE)
                && currentLockPlaylist.equals(playlist.playlistId)) {
            holder.lockBadge.setVisibility(View.VISIBLE);
        } else {
            holder.lockBadge.setVisibility(View.GONE);
        }

        // Format creation date for display
        Calendar calendar = Calendar.getInstance();
        if (playlist.createdAt != null) {
            calendar.setTime(playlist.createdAt);
        }
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(playlist.createdAt);
        String formattedDate = "Created on " + day + " " + month;

        // Set text content
        String itemCount = playlist.size + "+ Photos";
        holder.title.setText(itemCount);
        holder.creationDate.setText(formattedDate);

        RequestOptions options = new RequestOptions()
                .centerInside()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH);

        if (coverImage != null) {
            holder.progressIndicator.setVisibility(View.INVISIBLE);
            holder.thumbIv.setVisibility(View.VISIBLE);
            Glide.with(holder.thumbIv.getContext())
                    .load(coverImage)
                    .thumbnail(0.5f)
                    .transition(withCrossFade())
                    .apply(options)
                    .into(holder.thumbIv);
        } else {
            holder.thumbIv.setVisibility(View.INVISIBLE);
            holder.progressIndicator.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return mAllPlaylist.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }


    private void updateSelection(Playlist playlist) {
        if (!playlistId.equals(playlist.playlistId)) {
            editor.putInt("type", TYPE_SLIDESHOW);
            editor.putString("local_wallpaper_path", WALLPAPER_NONE);
            editor.putBoolean("double_tap", true);
            editor.putString("current_playlist", playlist.playlistId);
            editor.putBoolean("slideshow", true);
            if (editor.commit()) {
                playlistId = playlist.playlistId;
                notifyDataSetChanged();
            }
        } else {
            Toast.makeText(mContext, "Playlist already activated!", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dual-mode: assign playlist to Home screen slideshow (same as updateSelection). */
    private void updateHomeSelection(Playlist playlist) {
        updateSelection(playlist);
    }

    /** Dual-mode: assign playlist to Lock screen slideshow. */
    private void updateLockSelection(Playlist playlist) {
        String currentLock = prefs.getString(PREF_LOCK_PLAYLIST, PLAYLIST_NONE);
        if (!currentLock.equals(playlist.playlistId)) {
            editor.putString(PREF_LOCK_PLAYLIST, playlist.playlistId);
            if (editor.commit()) {
                notifyDataSetChanged();
            }
        } else {
            Toast.makeText(mContext, mContext.getString(R.string.dual_playlist_already_lock), Toast.LENGTH_SHORT).show();
        }
    }

    public void addPlaylists(List<Playlist> list) {
        mAllPlaylist.addAll(list);
        notifyDataSetChanged();
    }

    public void updatePlaylist() {
        playlistId = prefs.getString("current_playlist", PLAYLIST_NONE);
    }

    public void clearList() {
        if (!mAllPlaylist.isEmpty()) {
            mAllPlaylist.clear();
            notifyDataSetChanged();
        }
    }

    public List<Playlist> getItemList() {
        return mAllPlaylist;
    }
}
