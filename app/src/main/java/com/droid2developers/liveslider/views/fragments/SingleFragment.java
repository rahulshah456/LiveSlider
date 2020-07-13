package com.droid2developers.liveslider.views.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
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
import com.droid2developers.liveslider.adapters.WallpapersListAdapter;
import com.droid2developers.liveslider.database.models.LocalWallpaper;
import com.droid2developers.liveslider.utils.Constant;
import com.droid2developers.liveslider.viewmodel.PlaylistViewModel;
import com.droid2developers.liveslider.viewmodel.WallpaperViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.droid2developers.liveslider.utils.Constant.DEFAULT_LOCAL_PATH;

public class SingleFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

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

        // Creating default wallpaper data
        String localPath = DEFAULT_LOCAL_PATH;
        defaultWallpaper = new LocalWallpaper(Constant.DEFAULT,Constant.DEFAULT_WALLPAPER_NAME,
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
        listAdapter = new WallpapersListAdapter(mContext,prefs.getString("local_wallpaper_path",DEFAULT_LOCAL_PATH));
        mRecyclerView.setAdapter(listAdapter);
//        mRecyclerView.setNestedScrollingEnabled(false);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        listAdapter.addWallpaper(defaultWallpaper);
        listAdapter.setOnItemClickListener(new WallpapersListAdapter.OnItemClickListener() {

            @Override
            public void OnItemLongClick(int position) {
                LocalWallpaper wallpaper = listAdapter.getItemList().get(position);
                String localWallpaperPath = prefs.getString("local_wallpaper_path",DEFAULT_LOCAL_PATH);

                new MaterialAlertDialogBuilder(mContext,R.style.MaterialAlertDialogTheme)
                        .setIcon(getResources().getDrawable(R.drawable.delete_icon))
                        .setTitle("Delete?")
                        .setMessage("Are you sure you want to delete this wallpaper...")
                        .setCancelable(false)
                        .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with operation
                                if (wallpaper.getName().equals(Constant.DEFAULT_WALLPAPER_NAME)){
                                    Toast.makeText(mContext, "This is the default wallpaper and can't be deleted!",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    if (wallpaper.getName().equals(localWallpaperPath)){
                                        Toast.makeText(mContext, "You cannot delete current Wallpaper",
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        wallpaperViewModel.delete(wallpaper);
                                    }
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
                        String localPath = mContext.getExternalFilesDir(null) + File.separator + wallpaper_name;
                        LocalWallpaper localWallpaper = new LocalWallpaper(Constant.CUSTOM,
                                wallpaper_name,localPath,String.valueOf(contentURI));
                        FileOutputStream fos = new FileOutputStream(localPath);
                        //FileOutputStream fos = getActivity().openFileOutput(wallpaper_name, Context.MODE_PRIVATE);
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


    @SuppressLint("CommitPrefEdits")
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (prefs==null){
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
            listAdapter.updateLocalWallpaper();
            listAdapter.notifyDataSetChanged();
            Log.d(TAG, "onSharedPreferenceChanged: notifyDataSetChanged!");
        }
    }

}
