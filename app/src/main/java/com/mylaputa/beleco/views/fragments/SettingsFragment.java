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
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import android.text.Html;
import android.text.SpannableString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mylaputa.beleco.database.models.LocalWallpaper;
import com.mylaputa.beleco.database.models.Playlist;
import com.mylaputa.beleco.database.repository.PlaylistRepository;
import com.mylaputa.beleco.database.repository.WallpaperRepository;
import com.mylaputa.beleco.live_wallpaper.Cube;
import com.mylaputa.beleco.live_wallpaper.LiveWallpaperRenderer;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.utils.Constant;
import com.mylaputa.beleco.views.activities.ChangeWallpaperActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import static com.mylaputa.beleco.utils.Constant.PLAYLIST_DEFAULT;
import static com.mylaputa.beleco.utils.Constant.PLAYLIST_SLIDESHOW;

/**
 * Created by dklap on 1/22/2017.
 */

public class SettingsFragment extends Fragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();


    private int oldPicture = 0;

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private int wallpaperType = PLAYLIST_DEFAULT;
    private String currentPlaylist;
    private Cube cube;

    private CardView scrollCard,slideshowCard,intervalCard,doubleTapCard,powerSaverCard,selectionsCard;
    private Switch scrollSwitch,slideshowSwitch,doubleTapSwitch,powerSaverSwitch;
    private TextView selectionsCount,intervalText;
    private SeekBar seekBarRange,seekBarDelay;


    @Nullable
    @Override
    @SuppressLint("CommitPrefEdits")
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // View initializations
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        editor = prefs.edit();
        seekBarRange = view.findViewById(R.id.seekBarRange);
        seekBarDelay = view.findViewById(R.id.seekBarDelay);

        // Actual Settings Cards
        scrollCard = view.findViewById(R.id.card1ID);
        slideshowCard = view.findViewById(R.id.card2ID);
        intervalCard = view.findViewById(R.id.card3ID);
        doubleTapCard = view.findViewById(R.id.card4ID);
        powerSaverCard = view.findViewById(R.id.card5ID);
        selectionsCard = view.findViewById(R.id.card6ID);

        // Actual Settings Switches
        scrollSwitch = view.findViewById(R.id.switch1ID);
        slideshowSwitch = view.findViewById(R.id.switch2ID);
        doubleTapSwitch = view.findViewById(R.id.switch3ID);
        powerSaverSwitch = view.findViewById(R.id.switch4ID);

        // Actual Settings TextViews
        selectionsCount = view.findViewById(R.id.selections_count);
        intervalText = view.findViewById(R.id.interval_intro);

        // Introduction of the LiveWallpaper
        TextView introduction = view.findViewById(R.id.introduction);
        SpannableString spannableString = new SpannableString(Html.fromHtml(getResources()
                .getString(R.string.introduction2)));
        introduction.setText(spannableString);


        // Applying initial selected settings to views
        InitPreferences();
        InitListeners();
        InitOnClicks();


        // Cube to see wallpaper parallax effect in realtime
        cube = view.findViewById(R.id.cube);

        return view;
    }

    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        view.getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));
    }


    private void InitPreferences(){
        oldPicture = prefs.getInt("default_picture", 0);
        seekBarRange.setProgress(prefs.getInt("range", 10));
        seekBarDelay.setProgress(prefs.getInt("delay", 10));
        scrollSwitch.setChecked(prefs.getBoolean("scroll", true));
        slideshowSwitch.setChecked(prefs.getBoolean("slideshow",false));
        if (slideshowSwitch.isChecked()){
            intervalCard.setVisibility(View.VISIBLE);
            doubleTapCard.setVisibility(View.VISIBLE);
        }
        doubleTapSwitch.setChecked(prefs.getBoolean("double_tap",false));
        powerSaverSwitch.setChecked(prefs.getBoolean("power_saver", true));
        currentPlaylist = prefs.getString("current_playlist",null);
        wallpaperType = prefs.getInt("type",PLAYLIST_DEFAULT);
    }
    private void InitListeners(){
        seekBarRange.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt("range", progress);
                    editor.apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        seekBarDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt("delay", progress);
                    editor.apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        scrollSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean("scroll", isChecked);
                editor.apply();
            }
        });
        slideshowSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean("slideshow", isChecked);
                editor.apply();
            }
        });
        powerSaverSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean("power_saver", isChecked);
                editor.apply();
            }
        });
        doubleTapSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean("double_tap", isChecked);
                editor.apply();
            }
        });
    }
    private void InitOnClicks(){

        scrollCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (scrollSwitch.isChecked()){
                    scrollSwitch.setChecked(false);
                } else {
                    scrollSwitch.setChecked(true);
                }
            }
        });
        slideshowCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wallpaperType == PLAYLIST_SLIDESHOW){
                    if (slideshowSwitch.isChecked()){
                        slideshowSwitch.setChecked(false);
                        intervalCard.setVisibility(View.GONE);
                        doubleTapCard.setVisibility(View.GONE);
                    } else {
                        slideshowSwitch.setChecked(true);
                        intervalCard.setVisibility(View.GONE);
                        doubleTapCard.setVisibility(View.GONE);
                    }
                } else {
                    Toast.makeText(getContext(), R.string.select_playlist,
                            Toast.LENGTH_SHORT).show();
                }


            }
        });
        intervalCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), "Work in progress!", Toast.LENGTH_SHORT).show();
            }
        });
        doubleTapCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (doubleTapSwitch.isChecked()){
                    doubleTapSwitch.setChecked(false);
                } else {
                    doubleTapSwitch.setChecked(true);
                }
            }
        });
        powerSaverCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (powerSaverSwitch.isChecked()){
                    powerSaverSwitch.setChecked(false);
                } else {
                    powerSaverSwitch.setChecked(true);
                }
            }
        });
        selectionsCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent =  new Intent(getContext(), ChangeWallpaperActivity.class);
                startActivity(intent);
//                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
//                intent.setType("image/*");
//                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
//                intent.addCategory(Intent.CATEGORY_OPENABLE);
//                startActivityForResult(intent, REQUEST_CHOOSE_PHOTOS);

            }
        });

    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }



    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(LiveWallpaperRenderer.BiasChangeEvent event) {
        cube.setRotation(event.getY(), event.getX());
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }
}
