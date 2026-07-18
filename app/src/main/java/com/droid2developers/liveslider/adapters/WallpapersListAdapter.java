package com.droid2developers.liveslider.adapters;

import android.annotation.SuppressLint;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.utils.Constant;

import java.util.ArrayList;
import java.util.List;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;
import static com.droid2developers.liveslider.utils.Constant.PLAYLIST_NONE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SLIDESHOW;
import static com.droid2developers.liveslider.utils.Constant.PREFS_LOCK;

public class WallpapersListAdapter extends RecyclerView.Adapter<WallpapersListAdapter.MyViewHolder> {

    private static final String TAG = WallpapersListAdapter.class.getSimpleName();
    private OnItemClickListener onItemClickListener;
    private List<LocalWallpaper> mWallpapersList =  new ArrayList<>();
    private String localWallpaperPath;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Context mContext;
    private boolean isLockMode;


    @SuppressLint("CommitPrefEdits")
    public WallpapersListAdapter(Context mContext, String localWallpaperPath, boolean isLockMode) {
        this.mContext = mContext;
        this.localWallpaperPath = localWallpaperPath;
        this.isLockMode = isLockMode;
        if (isLockMode) {
            prefs = mContext.getSharedPreferences(Constant.PREFS_LOCK, Context.MODE_PRIVATE);
        } else {
            prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        }
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
            boolean isSlideShow = prefs.getBoolean("slideshow", false);
            int wallpaperType = prefs.getInt("type", TYPE_SINGLE);

            // If currently in slideshow mode, warn before switching to single
            if (isSlideShow || wallpaperType == TYPE_SLIDESHOW) {
                new MaterialAlertDialogBuilder(mContext)
                        .setIcon(ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.error_24dp, null))
                        .setTitle("Change Wallpaper?")
                        .setMessage(R.string.single_wallpaper_alert)
                        .setCancelable(false)
                        .setPositiveButton("Confirm", (dialog, which) -> {
                            dispatchActivation(wallpaper);
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                dispatchActivation(wallpaper);
            }
        }

        /** Checks which surfaces are live, then activates on the correct surface(s). */
        private void dispatchActivation(LocalWallpaper wallpaper) {
            WallpaperManager wm = WallpaperManager.getInstance(mContext);
            String pkg = mContext.getPackageName();

            if (isLockMode) {
                // If in Lock Mode, only check for Lock Screen service
                boolean isActiveOnLock;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        WallpaperInfo lockInfo = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
                        isActiveOnLock = lockInfo != null && lockInfo.getPackageName().equals(pkg);
                    } catch (Throwable t) {
                        isActiveOnLock = false;
                    }
                } else {
                    isActiveOnLock = wm.getWallpaperInfo() != null && wm.getWallpaperInfo().getPackageName().equals(pkg);
                }

                if (!isActiveOnLock) {
                    showActivationDialog();
                    return;
                }
                updateLockSelection(wallpaper);

            } else {
                // If in Home Mode, only check for Home Screen service
                WallpaperInfo homeInfo = wm.getWallpaperInfo();
                boolean isActiveOnHome = homeInfo != null && homeInfo.getPackageName().equals(pkg);

                if (!isActiveOnHome) {
                    showActivationDialog();
                    return;
                }
                updateSelection(wallpaper);
            }
        }

        private void showActivationDialog() {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(R.string.activate_not_live_title)
                    .setMessage(R.string.activate_not_live_msg)
                    .setPositiveButton(android.R.string.ok, null)
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
        final View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_wallpaper_thumb, parent, false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder holder, int position) {
        LocalWallpaper wallpaper = mWallpapersList.get(position);

        String imageUrl = wallpaper.getLocalPath();

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
                if (isLockMode) {
                    Toast.makeText(mContext, "Lock screen wallpaper updated!", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(mContext, "Wallpaper already selected!", Toast.LENGTH_SHORT).show();
        }
    }

    /** Assigns this single wallpaper to the lock screen slot (LockLiveWallpaperService). */
    private void updateLockSelection(LocalWallpaper wallpaper) {
        // This is now redundant if isLockMode is true, as updateSelection will use the correct editor.
        // But we'll keep it for cases where we might want to update lock from home (though user said remove it).
        updateSelection(wallpaper);
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
