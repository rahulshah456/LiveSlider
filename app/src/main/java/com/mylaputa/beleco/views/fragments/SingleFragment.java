package com.mylaputa.beleco.views.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
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
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.adapters.WallpapersListAdapter;
import com.mylaputa.beleco.database.models.LocalWallpaper;
import com.mylaputa.beleco.utils.Constant;
import com.mylaputa.beleco.viewmodel.PlaylistViewModel;
import com.mylaputa.beleco.viewmodel.WallpaperViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

public class SingleFragment extends Fragment {

    private static final String TAG = SingleFragment.class.getSimpleName();
    private WallpaperViewModel wallpaperViewModel;
    private PlaylistViewModel playlistViewModel;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private final int REQUEST_CHOOSE_PHOTOS = 0;
    private RecyclerView mRecyclerView;
    private WallpapersListAdapter listAdapter;
    private Context mContext;
    private LocalWallpaper defaultWallpaper;
    private ProgressDialog progressDialog;

    @SuppressLint("CommitPrefEdits")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_single, container, false);

        // View initializations
        mContext = getContext();
        mRecyclerView = view.findViewById(R.id.singleRecyclerId);
        FloatingActionButton addWallpaperFAB = view.findViewById(R.id.addWallpaperId);
        progressDialog = new ProgressDialog(mContext);
        progressDialog.setMessage("Processing...");
        prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        editor = prefs.edit();
        String localPath = Constant.ASSETS_PATH + Constant.DEFAULT_WALLPAPER;
        defaultWallpaper = new LocalWallpaper(Constant.DEFAULT,Constant.DEFAULT_WALLPAPER,
                localPath,localPath);


        if (getActivity()!=null){
            wallpaperViewModel = new ViewModelProvider(getActivity()).get(WallpaperViewModel.class);
            playlistViewModel = new ViewModelProvider(getActivity()).get(PlaylistViewModel.class);
        }
        InitRecyclerView();

        addWallpaperFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, REQUEST_CHOOSE_PHOTOS);
            }
        });


        return view;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));
    }


    private void InitRecyclerView(){

        int gridSize = ((getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)? 2:4);
        mRecyclerView.setLayoutManager(new GridLayoutManager(mContext, gridSize));
        listAdapter = new WallpapersListAdapter(mContext);
        mRecyclerView.setAdapter(listAdapter);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        listAdapter.addWallpaper(defaultWallpaper);
        listAdapter.setOnItemClickListener(new WallpapersListAdapter.OnItemClickListener() {
            @Override
            public void OnItemClick(int position) {
                Log.d(TAG, "OnItemClick: " + position);
                LocalWallpaper wallpaper = listAdapter.getItemList().get(position);
                if (wallpaper.getPlaylistId().equals(Constant.DEFAULT)){
                    editor.putBoolean("default_wallpaper",true).apply();
                } else {
                    editor.putBoolean("default_wallpaper",false).apply();
                }
                editor.putString("current_wallpaper",wallpaper.getName()).apply();
                updateSelections(position);
            }

            @Override
            public void OnItemLongClick(int position) {
                Log.d(TAG, "OnItemLongClick: " + position);
            }
        });

        wallpaperViewModel.getAllWallpapers().observe(getViewLifecycleOwner(), new Observer<List<LocalWallpaper>>() {
            @Override
            public void onChanged(List<LocalWallpaper> localWallpapers) {
                Log.d(TAG, "onChanged: " + localWallpapers.size());
                if (listAdapter.getItemCount()>0){
                    listAdapter.clearList();
                    listAdapter.addWallpaper(defaultWallpaper);
                    listAdapter.addWallpapers(localWallpapers);
                } else {
                    if (!listAdapter.getItemList().contains(defaultWallpaper)){
                        listAdapter.addWallpaper(defaultWallpaper);
                    }
                    listAdapter.addWallpapers(localWallpapers);
                }

            }
        });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHOOSE_PHOTOS) {
            if (resultCode == Activity.RESULT_OK && data!=null) {
                if (data.getData()!=null){
                    progressDialog.show();
                    new SaveSingle().execute(data.getData());
                    Log.d(TAG, "onActivityResult: getData() = " + data.getData());
                }
            }
        }
    }


    public void updateSelections(int position){
        for (int pos=0;pos<listAdapter.getItemCount();pos++){
            if (position == pos){
                mRecyclerView.findViewHolderForAdapterPosition(pos)
                        .itemView.findViewById(R.id.view_shadow).setVisibility(View.VISIBLE);
                mRecyclerView.findViewHolderForAdapterPosition(pos)
                        .itemView.findViewById(R.id.image_selection).setVisibility(View.VISIBLE);
            } else {
                mRecyclerView.findViewHolderForAdapterPosition(pos)
                        .itemView.findViewById(R.id.view_shadow).setVisibility(View.GONE);
                mRecyclerView.findViewHolderForAdapterPosition(pos)
                        .itemView.findViewById(R.id.image_selection).setVisibility(View.GONE);
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
    private class SaveSingle extends AsyncTask<Uri, Integer, Void> {

        @Override
        protected Void doInBackground(Uri... uris) {

            Uri contentURI  = uris[0];
            InputStream in = openUri(contentURI);
            if (in != null) {
                try {
                    if (getActivity() != null) {

                        String wallpaper_name = Constant.HEADER + System.currentTimeMillis() + Constant.PNG;
                        String localPath = mContext.getFilesDir() + File.separator + wallpaper_name;
                        LocalWallpaper localWallpaper = new LocalWallpaper(Constant.CUSTOM,
                                wallpaper_name,localPath,String.valueOf(contentURI));
                        FileOutputStream fos = getActivity().openFileOutput(wallpaper_name,
                                Context.MODE_PRIVATE);
                        byte[] buffer = new byte[1024];
                        int bytes;
                        while ((bytes = in.read(buffer)) > 0) {
                            fos.write(buffer, 0, bytes);
                        }
                        wallpaperViewModel.insert(localWallpaper);
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
