package com.mylaputa.beleco.views.activities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.ViewPager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.adapters.TabAdapter;
import com.mylaputa.beleco.database.models.LocalWallpaper;
import com.mylaputa.beleco.database.models.Playlist;
import com.mylaputa.beleco.database.repository.PlaylistRepository;
import com.mylaputa.beleco.database.repository.WallpaperRepository;
import com.mylaputa.beleco.utils.Constant;
import com.mylaputa.beleco.views.fragments.SingleFragment;
import com.mylaputa.beleco.views.fragments.SlideshowFragment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static com.mylaputa.beleco.utils.Constant.PLAYLIST_SLIDESHOW;

public class ChangeWallpaperActivity extends AppCompatActivity {

    public static final String TAG = ChangeWallpaperActivity.class.getSimpleName();
    private final int REQUEST_CHOOSE_PHOTOS = 0;
    private ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_wallpaper);

        dialog = new ProgressDialog(this);
        dialog.setMessage("Processing...");

        ViewPager viewPager = findViewById(R.id.viewPagerId);
        TabAdapter tabAdapter = new TabAdapter(getSupportFragmentManager());
        TabLayout tabLayout = findViewById(R.id.tabLayoutId);

        tabAdapter.addFragment(new SingleFragment(),"Single");
        tabAdapter.addFragment(new SlideshowFragment(),"Slideshow");

        viewPager.setAdapter(tabAdapter);
        tabLayout.setupWithViewPager(viewPager);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHOOSE_PHOTOS) {
            if (resultCode == Activity.RESULT_OK && data!=null) {
                if (data.getData()!=null){
                    dialog.show();
                    //new SaveSingle().execute(data.getData());
                    Log.d(TAG, "onActivityResult: getData() = " + data.getData());
                } else if (data.getClipData()!=null){
                    dialog.show();
                    Log.d(TAG, "onActivityResult: getClipData() = " + data.getClipData().getItemCount());
                    //new SaveSelections().execute(data.getClipData());
                }
            }
        }
    }


    private InputStream openUri(Uri uri) {
        if (uri == null) return null;
        try {
            return getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


//    @SuppressLint("StaticFieldLeak")
//    private class SaveSingle extends AsyncTask<Uri, Integer, Void> {
//
//        @Override
//        protected Void doInBackground(Uri... uris) {
//            return null;
//        }
//
//
//        @Override
//        protected void onPostExecute(Void result) {
//            // do UI work here
//            if (dialog.isShowing()) {
//                dialog.dismiss();
//            }
//        }
//    }
//    @SuppressLint("StaticFieldLeak")
//    private class SaveSelections extends AsyncTask<ClipData, Integer, Void> {
//
//        @Override
//        protected Void doInBackground(ClipData... clipData) {
//
//            PlaylistRepository playlistRepository = new PlaylistRepository(getContext());
//            WallpaperRepository wallpaperRepository = new WallpaperRepository(getContext());
//
//            String playlistId = String.valueOf(System.currentTimeMillis());
//            String name = "Playlist_" + playlistId;
//            String desc = "This is basic wallpapers playlist and uses room as it's local database";
//            Playlist playlist = new Playlist(playlistId,name,desc,new Date(),new Date());
//            playlistRepository.insert(playlist);
//
//            for(int index=0; index<clipData[0].getItemCount(); index++){
//
//                Uri contentURI  = clipData[0].getItemAt(index).getUri();
//                InputStream in = openUri(contentURI);
//                if (in != null) {
//                    try {
//                        if (getActivity() != null) {
//
//                            String wallpaper_name = Constant.HEADER + System.currentTimeMillis();
//                            LocalWallpaper localWallpaper = new LocalWallpaper(playlistId,
//                                    wallpaper_name,String.valueOf(contentURI));
//                            FileOutputStream fos = getActivity().openFileOutput(wallpaper_name,
//                                    Context.MODE_PRIVATE);
//                            byte[] buffer = new byte[1024];
//                            int bytes;
//                            while ((bytes = in.read(buffer)) > 0) {
//                                fos.write(buffer, 0, bytes);
//                            }
//                            wallpaperRepository.insert(localWallpaper);
//                            in.close();
//                            fos.flush();
//                            fos.close();
//
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                        Toast.makeText(getActivity(), R.string.toast_failed_set_picture,
//                                Toast.LENGTH_LONG).show();
//                    }
//                } else {
//                    Toast.makeText(getActivity(), R.string.toast_invalid_pic_path,
//                            Toast.LENGTH_LONG).show();
//                }
//
//            }
//
//            // updating current playlist
//            editor.putString("current_playlist",playlistId);
//            editor.apply();
//            editor.putInt("type",PLAYLIST_SLIDESHOW);
//            editor.apply();
//
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(Void result) {
//            // do UI work here
//            if (dialog.isShowing()) {
//                dialog.dismiss();
//            }
//        }
//    }
}
