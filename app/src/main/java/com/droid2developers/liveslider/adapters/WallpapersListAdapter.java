package com.droid2developers.liveslider.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.utils.Constant;

import java.util.ArrayList;
import java.util.List;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.PLAYLIST_NONE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SLIDESHOW;

public class WallpapersListAdapter extends RecyclerView.Adapter<WallpapersListAdapter.MyViewHolder> {

    private static final String TAG = WallpapersListAdapter.class.getSimpleName();
    private OnItemClickListener onItemClickListener;
    private List<LocalWallpaper> mWallpapersList =  new ArrayList<>();
    private String localWallpaperPath;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Context mContext;


    @SuppressLint("CommitPrefEdits")
    public WallpapersListAdapter(Context mContext, String localWallpaperPath) {
        this.mContext = mContext;
        this.localWallpaperPath = localWallpaperPath;
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
        ImageView thumbnail,selectionImage;
        View viewShadow;

        MyViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            thumbnail = itemView.findViewById(R.id.image_thumbnail);
            selectionImage = itemView.findViewById(R.id.image_selection);
            viewShadow = itemView.findViewById(R.id.view_shadow);
        }

        @Override
        public void onClick(View view) {

            LocalWallpaper wallpaper = getItemList().get(getLayoutPosition());
            boolean isSlideShow = prefs.getBoolean("slideshow",false);
            int wallpaperType = prefs.getInt("type",TYPE_SINGLE);

            if (isSlideShow || wallpaperType == TYPE_SLIDESHOW){
                new MaterialAlertDialogBuilder(mContext,R.style.MaterialAlertDialogTheme)
                        .setIcon(mContext.getResources().getDrawable(R.drawable.error_24dp))
                        .setTitle("Change Wallpaper?")
                        .setMessage(R.string.single_wallpaper_alert)
                        .setCancelable(false)
                        .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with operation
                                updateSelection(wallpaper);
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
            } else {
                updateSelection(wallpaper);
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
        final View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_wallpaper_thumb, parent, false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder holder, int position) {
        LocalWallpaper wallpaper = mWallpapersList.get(position);

        String imageUrl = wallpaper.getLocalPath();
        Log.d(TAG, "onBindViewHolder: " + imageUrl);

        boolean isSlideShow = prefs.getBoolean("slideshow",false);
        int wallpaperType = prefs.getInt("type",TYPE_SINGLE);

        if (wallpaperType == TYPE_SINGLE && localWallpaperPath.equals(wallpaper.getLocalPath())){
            holder.viewShadow.setVisibility(View.VISIBLE);
            holder.selectionImage.setVisibility(View.VISIBLE);
        } else {
            holder.viewShadow.setVisibility(View.GONE);
            holder.selectionImage.setVisibility(View.GONE);
        }

        RequestOptions options = new RequestOptions()
                .centerInside()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH);

        if (wallpaper.getPlaylistId().equals(Constant.DEFAULT)){
            // Load Thumbnail from Assets Folder
            Glide.with(mContext)
                    .load(Uri.parse(imageUrl))
                    .thumbnail(0.5f)
                    .transition(withCrossFade())
                    .apply(options)
                    .into(holder.thumbnail);
        } else {
            // Load Thumbnail from Local Storage
            Glide.with(mContext)
                    .load(imageUrl)
                    .thumbnail(0.5f)
                    .transition(withCrossFade())
                    .apply(options)
                    .into(holder.thumbnail);
        }

    }


    @Override
    public int getItemCount() {
        return mWallpapersList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }


    private void updateSelection(LocalWallpaper wallpaper){
        if (!localWallpaperPath.equals(wallpaper.getLocalPath())){

            boolean isDefaultWallpaper = wallpaper.getPlaylistId().equals(Constant.DEFAULT);
            editor.putBoolean("slideshow",false);
            editor.putInt("type",TYPE_SINGLE);
            editor.putString("current_playlist",PLAYLIST_NONE);
            editor.putBoolean("default_wallpaper",isDefaultWallpaper);
            editor.putString("local_wallpaper_path",wallpaper.getLocalPath());
            editor.putString("refresh_wallpaper",String.valueOf(System.currentTimeMillis()));
            if (editor.commit()){
                localWallpaperPath = wallpaper.getLocalPath();
                notifyDataSetChanged();
            }
        } else {
            Toast.makeText(mContext, "Wallpaper already selected!", Toast.LENGTH_SHORT).show();
        }
    }

    public void addWallpapers(List<LocalWallpaper> list){
        mWallpapersList.addAll(list);
        notifyDataSetChanged();
    }

    public void addWallpaper(LocalWallpaper wallpaper){
        mWallpapersList.add(wallpaper);
        notifyDataSetChanged();
    }

    public void clearList(){
        if (mWallpapersList.size()>0){
            mWallpapersList.clear();
            notifyDataSetChanged();
        }
    }

    public void updateLocalWallpaper(){
        localWallpaperPath = prefs.getString("local_wallpaper_path",DEFAULT_LOCAL_PATH);
    }

    public List<LocalWallpaper> getItemList(){
        return mWallpapersList;
    }

}
