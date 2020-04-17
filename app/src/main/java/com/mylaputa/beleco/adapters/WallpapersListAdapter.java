package com.mylaputa.beleco.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.database.models.LocalWallpaper;
import com.mylaputa.beleco.utils.Constant;

import java.util.ArrayList;
import java.util.List;
import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

public class WallpapersListAdapter extends RecyclerView.Adapter<WallpapersListAdapter.MyViewHolder> {

    private static final String TAG = WallpapersListAdapter.class.getSimpleName();
    private OnItemClickListener onItemClickListener;
    private List<LocalWallpaper> mWallpapersList =  new ArrayList<>();
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Context mContext;


    @SuppressLint("CommitPrefEdits")
    public WallpapersListAdapter(Context mContext) {
        this.mContext = mContext;
        prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        editor = prefs.edit();
    }


    public interface OnItemClickListener {
        void OnItemClick(int position);
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
            onItemClickListener.OnItemClick(this.getLayoutPosition());
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
        String currentWallpaper = prefs.getString("current_wallpaper", Constant.DEFAULT_WALLPAPER);

        if (!isSlideShow & currentWallpaper.equals(wallpaper.getName())){
            holder.viewShadow.setVisibility(View.VISIBLE);
            holder.selectionImage.setVisibility(View.VISIBLE);
        }

        RequestOptions options = new RequestOptions()
                .centerInside()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH);

        if (wallpaper.getPlaylistId().equals(Constant.DEFAULT)){
            Glide.with(mContext)
                    .load(Uri.parse(imageUrl))
                    .thumbnail(0.5f)
                    .transition(withCrossFade())
                    .apply(options)
                    .into(holder.thumbnail);
        } else {
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

    public List<LocalWallpaper> getItemList(){
        return mWallpapersList;
    }

}
