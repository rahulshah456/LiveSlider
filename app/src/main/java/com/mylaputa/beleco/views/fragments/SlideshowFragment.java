package com.mylaputa.beleco.views.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.mylaputa.beleco.R;
import com.mylaputa.beleco.database.models.LocalWallpaper;
import com.mylaputa.beleco.database.models.Playlist;
import com.mylaputa.beleco.database.repository.PlaylistRepository;
import com.mylaputa.beleco.database.repository.WallpaperRepository;
import com.mylaputa.beleco.utils.Constant;
import com.mylaputa.beleco.viewmodel.PlaylistViewModel;
import com.mylaputa.beleco.viewmodel.WallpaperViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static com.mylaputa.beleco.utils.Constant.PLAYLIST_SLIDESHOW;

public class SlideshowFragment extends Fragment {

    private static final String TAG = SlideshowFragment.class.getSimpleName();
    private WallpaperViewModel wallpaperViewModel;
    private PlaylistViewModel playlistViewModel;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private final int REQUEST_MULTIPLE_PHOTOS = 1;
    private Context mContext;
    private ProgressDialog progressDialog;

    @SuppressLint("CommitPrefEdits")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_slideshow, container, false);

        // View initializations
        mContext = getContext();
        progressDialog = new ProgressDialog(mContext);
        progressDialog.setMessage("Processing...");
        prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        editor = prefs.edit();

        if (getActivity()!=null){
            wallpaperViewModel = new ViewModelProvider(getActivity()).get(WallpaperViewModel.class);
            playlistViewModel = new ViewModelProvider(getActivity()).get(PlaylistViewModel.class);
        }
        return view;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MULTIPLE_PHOTOS) {
            if (resultCode == Activity.RESULT_OK && data!=null) {
                if (data.getClipData()!=null){
                    progressDialog.show();
                    Log.d(TAG, "onActivityResult: getClipData() = " + data.getClipData().getItemCount());
                    new SaveSelections().execute(data.getClipData());
                }
            }
        }
    }


    private InputStream openUri(Uri uri) {
        if (uri == null) return null;
        try {
            return mContext.getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SaveSelections extends AsyncTask<ClipData, Integer, Void> {

        @Override
        protected Void doInBackground(ClipData... clipData) {

            PlaylistRepository playlistRepository = new PlaylistRepository(getContext());
            WallpaperRepository wallpaperRepository = new WallpaperRepository(getContext());

            String playlistId = String.valueOf(System.currentTimeMillis());
            String name = "Playlist_" + playlistId;
            String desc = "This is basic wallpapers playlist and uses room as it's local database";
            Playlist playlist = new Playlist(playlistId,name,desc,new Date(),new Date(),clipData[0].getItemCount());
            playlistRepository.insert(playlist);

            for(int index=0; index<clipData[0].getItemCount(); index++){

                Uri contentURI  = clipData[0].getItemAt(index).getUri();
                InputStream in = openUri(contentURI);
                if (in != null) {
                    try {
                        if (getActivity() != null) {

                            String wallpaper_name = Constant.HEADER + System.currentTimeMillis() + Constant.PNG;
                            String localPath = mContext.getFilesDir() + File.separator + wallpaper_name;
                            LocalWallpaper localWallpaper = new LocalWallpaper(playlistId,
                                    wallpaper_name,localPath,String.valueOf(contentURI));
                            FileOutputStream fos = getActivity().openFileOutput(wallpaper_name,
                                    Context.MODE_PRIVATE);
                            byte[] buffer = new byte[1024];
                            int bytes;
                            while ((bytes = in.read(buffer)) > 0) {
                                fos.write(buffer, 0, bytes);
                            }
                            wallpaperRepository.insert(localWallpaper);
                            in.close();
                            fos.flush();
                            fos.close();

                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), R.string.toast_failed_set_picture,
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getActivity(), R.string.toast_invalid_pic_path,
                            Toast.LENGTH_LONG).show();
                }

            }

            // updating current playlist
            editor.putString("current_playlist",playlistId);
            editor.apply();
            editor.putInt("type",PLAYLIST_SLIDESHOW);
            editor.apply();

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // do UI work here
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }
}
