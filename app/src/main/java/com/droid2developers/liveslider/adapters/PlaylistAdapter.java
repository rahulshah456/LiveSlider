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
        View viewShadow;
        ImageView thumbIv, selectionImage;
        TextView count, title, month, day;
        ProgressBar progressIndicator;

        MyViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            selectionImage = itemView.findViewById(R.id.image_selection);
            viewShadow = itemView.findViewById(R.id.view_shadow);
            thumbIv = itemView.findViewById(R.id.mainThumbnailId);
            count = itemView.findViewById(R.id.collectionCountId);
            title = itemView.findViewById(R.id.collectionTitleId);
            month = itemView.findViewById(R.id.monthHeaderId);
            day = itemView.findViewById(R.id.dayHeaderId);
            progressIndicator = itemView.findViewById(R.id.progressView);
            progressIndicator.setIndeterminate(true);

        }

        @Override
        public void onClick(View view) {
            Playlist playlist = getItemList().get(getLayoutPosition());

            new MaterialAlertDialogBuilder(mContext)
                    .setIcon(ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.error_24dp, null))
                    .setTitle("Activate Playlist?")
                    .setMessage(R.string.playlist_activation)
                    .setCancelable(false)
                    .setPositiveButton("Activate", (dialog, which) -> {
                        // Continue with operation
                        updateSelection(playlist);
                        dialog.dismiss();
                    })
//                    .setNeutralButton("Edit", (dialog, which) -> {
//                        Toast.makeText(mContext, "Work in progress!", Toast.LENGTH_SHORT).show();
//                        dialog.dismiss();
//                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Log.d(TAG, "onClick: Cancelled Delete!");
                        dialog.dismiss();
                    })
                    .create()
                    .show();
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
        int wallpaperType = prefs.getInt("type", TYPE_SINGLE);

        if (wallpaperType == TYPE_SLIDESHOW && currentPlaylist.equals(playlist.playlistId)) {
            holder.viewShadow.setVisibility(View.VISIBLE);
            holder.selectionImage.setVisibility(View.VISIBLE);
        } else {
            holder.viewShadow.setVisibility(View.GONE);
            holder.selectionImage.setVisibility(View.GONE);
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(playlist.createdAt);

        // Get the day of the month and month (in text format)
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String month = new SimpleDateFormat("MMM", Locale.getDefault()).format(playlist.createdAt);

        String itemCount = playlist.size + "+ Photos";
        holder.count.setText(itemCount);
        holder.title.setText(playlist.name);
        holder.day.setText(String.valueOf(day));
        holder.month.setText(month);


        RequestOptions options = new RequestOptions()
                .centerInside()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH);

        if (coverImage != null) {
            // Load Thumbnail from Local Storage
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
