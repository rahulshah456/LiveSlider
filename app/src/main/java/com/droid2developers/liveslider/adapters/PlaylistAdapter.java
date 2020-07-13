package com.droid2developers.liveslider.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.database.models.Playlist;
import com.droid2developers.liveslider.utils.BlurTransformation;

import java.util.ArrayList;
import java.util.List;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;
import static com.droid2developers.liveslider.utils.Constant.PLAYLIST_NONE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SLIDESHOW;
import static com.droid2developers.liveslider.utils.Constant.WALLPAPER_NONE;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.MyViewHolder> {

    private static final String TAG = PlaylistAdapter.class.getSimpleName();
    private OnItemClickListener onItemClickListener;
    private List<Playlist> mAllPlaylist =  new ArrayList<>();
    private String playlistId;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Context mContext;


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


    public class MyViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{
        View viewShadow;
        ImageView thumb_1,thumb_2,thumb_3,selectionImage;
        TextView count,title,description,month,day;

        MyViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            selectionImage = itemView.findViewById(R.id.image_selection);
            viewShadow = itemView.findViewById(R.id.view_shadow);
            thumb_1 = itemView.findViewById(R.id.mainThumbnailId);
            thumb_2 = itemView.findViewById(R.id.secThumbnailId);
            thumb_3 = itemView.findViewById(R.id.blurThumbnailId);
            count = itemView.findViewById(R.id.collectionCountId);
            title = itemView.findViewById(R.id.collectionTitleId);
            description = itemView.findViewById(R.id.descriptionId);
            month = itemView.findViewById(R.id.monthHeaderId);
            day = itemView.findViewById(R.id.dayHeaderId);


        }

        @Override
        public void onClick(View view) {
            Playlist playlist = getItemList().get(getLayoutPosition());

            new MaterialAlertDialogBuilder(mContext, R.style.MaterialAlertDialogTheme)
                    .setIcon(mContext.getResources().getDrawable(R.drawable.error_24dp))
                    .setTitle("Activate Playlist?")
                    .setMessage(R.string.playlist_activation)
                    .setCancelable(false)
                    .setPositiveButton("Activate", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Continue with operation
                            updateSelection(playlist);
                            dialog.dismiss();
                        }
                    })
                    .setNeutralButton("Edit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(mContext, "Work in progress!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "onClick: Cancelled Delete!");
                            dialog.dismiss();
                        }
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
        List<String> coverUrls = playlist.getCover_urls();


        String currentPlaylist = prefs.getString("current_playlist",PLAYLIST_NONE);
        int wallpaperType = prefs.getInt("type",TYPE_SINGLE);

        if (wallpaperType == TYPE_SLIDESHOW && currentPlaylist.equals(playlist.getPlaylistId())){
            holder.viewShadow.setVisibility(View.VISIBLE);
            holder.selectionImage.setVisibility(View.VISIBLE);
        } else {
            holder.viewShadow.setVisibility(View.GONE);
            holder.selectionImage.setVisibility(View.GONE);
        }

        String itemCount = playlist.getSize() + "+";
        holder.count.setText(itemCount);
        holder.title.setText(playlist.getName());

        RequestOptions options = new RequestOptions()
                .centerInside()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH);

        // Load Thumbnail from Local Storage
        Glide.with(mContext)
                .load(coverUrls.get(0))
                .thumbnail(0.5f)
                .transition(withCrossFade())
                .apply(options)
                .into(holder.thumb_1);
        Glide.with(mContext)
                .load(coverUrls.get(1))
                .thumbnail(0.5f)
                .transition(withCrossFade())
                .apply(options)
                .into(holder.thumb_2);

        RequestOptions options_blur = new RequestOptions()
                .centerCrop()
                .transform(new BlurTransformation(25,3))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH);
        Glide.with(mContext)
                .load(coverUrls.get(2))
                .thumbnail(0.5f)
                .transition(withCrossFade())
                .apply(options_blur)
                .into(holder.thumb_3);



    }

    @Override
    public int getItemCount() {
        return mAllPlaylist.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }


    private void updateSelection(Playlist playlist){

        if (!playlistId.equals(playlist.getPlaylistId())){

            editor.putInt("type",TYPE_SLIDESHOW);
            editor.putString("local_wallpaper_path",WALLPAPER_NONE);
            editor.putBoolean("double_tap",true);
            editor.putString("current_playlist",playlist.getPlaylistId());
            editor.putBoolean("slideshow",true);
            if (editor.commit()){
                playlistId = playlist.getPlaylistId();
                notifyDataSetChanged();
            }
        } else {
            Toast.makeText(mContext, "Playlist already activated!", Toast.LENGTH_SHORT).show();
        }

    }


    public void addPlaylists(List<Playlist> list){
        mAllPlaylist.addAll(list);
        notifyDataSetChanged();
    }

    public void updatePlaylist(){
        playlistId = prefs.getString("current_playlist",PLAYLIST_NONE);
    }

    public void clearList(){
        if (mAllPlaylist.size()>0){
            mAllPlaylist.clear();
            notifyDataSetChanged();
        }
    }

    public List<Playlist> getItemList(){
        return mAllPlaylist;
    }
}
