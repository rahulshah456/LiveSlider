package com.mylaputa.beleco.views.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import com.mylaputa.beleco.R;
import com.mylaputa.beleco.viewmodel.PlaylistViewModel;
import com.mylaputa.beleco.viewmodel.WallpaperViewModel;
import com.mylaputa.beleco.views.activities.ChangeWallpaperActivity;

import java.io.InputStream;

public class SingleFragment extends Fragment {

    private static final String TAG = SingleFragment.class.getSimpleName();
    private WallpaperViewModel wallpaperViewModel;
    private PlaylistViewModel playlistViewModel;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private final int REQUEST_CHOOSE_PHOTOS = 0;
    private Context mContext;
    private ProgressDialog progressDialog;

    @SuppressLint("CommitPrefEdits")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_single, container, false);

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
