package com.droid2developers.liveslider.views.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.adapters.PlaylistAdapter;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.database.models.Playlist;
import com.droid2developers.liveslider.database.repository.PlaylistRepository;
import com.droid2developers.liveslider.database.repository.WallpaperRepository;
import com.droid2developers.liveslider.utils.Constant;
import com.droid2developers.liveslider.viewmodel.PlaylistViewModel;
import com.droid2developers.liveslider.viewmodel.WallpaperViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.droid2developers.liveslider.utils.Constant.PLAYLIST_NONE;

public class SlideshowFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = SlideshowFragment.class.getSimpleName();
    private WallpaperViewModel wallpaperViewModel;
    private PlaylistViewModel playlistViewModel;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private PlaylistAdapter listAdapter;
    private RecyclerView mRecyclerView;
    //private PlayListAdapter listAdapter;
    private final int REQUEST_MULTIPLE_PHOTOS = 1;
    private Context mContext;
    private ProgressDialog progressDialog;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_slideshow, container, false);

        // View initializations
        mContext = getContext();
        mRecyclerView = view.findViewById(R.id.slideshowRecyclerId);
        FloatingActionButton addPlaylistFAB = view.findViewById(R.id.addPlaylistId);
        progressDialog = new ProgressDialog(mContext);
        progressDialog.setMessage("Processing...");

        if (getActivity()!=null){
            wallpaperViewModel = new ViewModelProvider(getActivity()).get(WallpaperViewModel.class);
            playlistViewModel = new ViewModelProvider(getActivity()).get(PlaylistViewModel.class);
        }
        InitRecyclerView();

        addPlaylistFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, REQUEST_MULTIPLE_PHOTOS);
            }
        });
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



    private void InitRecyclerView(){


        int gridSize = ((getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)? 1:2);
        mRecyclerView.setLayoutManager(new GridLayoutManager(mContext, gridSize));
        listAdapter = new PlaylistAdapter(mContext,prefs.getString("current_playlist",PLAYLIST_NONE));
        mRecyclerView.setAdapter(listAdapter);
//        mRecyclerView.setNestedScrollingEnabled(false);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        listAdapter.setOnItemClickListener(new PlaylistAdapter.OnItemClickListener() {
            @Override
            public void OnItemLongClick(int position) {

                Playlist playlist = listAdapter.getItemList().get(position);
                String currentPlaylist = prefs.getString("current_playlist",PLAYLIST_NONE);

                new MaterialAlertDialogBuilder(mContext)
                        .setIcon(ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.delete_icon, null))
                        .setTitle("Delete?")
                        .setMessage("Are you sure you want to delete this wallpaper...")
                        .setCancelable(false)
                        .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with operation
                                if (playlist.getPlaylistId().equals(currentPlaylist)){
                                    Toast.makeText(mContext, "You cannot delete current activated playlist!",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    playlistViewModel.delete(playlist);
                                    wallpaperViewModel.deletePlaylistWallpapers(playlist.getPlaylistId());
                                }
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
        });

        playlistViewModel.getAllPlaylists().observe(getViewLifecycleOwner(), new Observer<List<Playlist>>() {
            @Override
            public void onChanged(List<Playlist> playlists) {
                Log.d(TAG, "onChanged: " + playlists.size());
                if (listAdapter.getItemCount()>0){
                    listAdapter.clearList();
                }
                listAdapter.addPlaylists(playlists);

            }
        });


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
            List<String> coverUrls = new ArrayList<>();
            coverUrls.add(String.valueOf(clipData[0].getItemAt(0).getUri()));
            coverUrls.add(String.valueOf(clipData[0].getItemAt(1).getUri()));
            coverUrls.add(String.valueOf(clipData[0].getItemAt(2).getUri()));

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

            Playlist playlist = new Playlist(playlistId,name,coverUrls,
                    new Date(),new Date(),clipData[0].getItemCount());
            playlistRepository.insert(playlist);

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


    @SuppressLint("CommitPrefEdits")
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (prefs == null){
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
            editor = prefs.edit();
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged: " + key);
        if (key.equals("type")){
            listAdapter.updatePlaylist();
            listAdapter.notifyDataSetChanged();
            Log.d(TAG, "onSharedPreferenceChanged: notifyDataSetChanged!");
        }
    }
}
