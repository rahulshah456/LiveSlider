package com.droid2developers.liveslider.views.activities;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.live_wallpaper.Cube;
import com.droid2developers.liveslider.live_wallpaper.LiveWallpaperRenderer;
import com.droid2developers.liveslider.utils.Constant;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import xyz.aprildown.hmspickerview.HmsPickerView;

import static com.droid2developers.liveslider.utils.Constant.DEFAULT_SLIDESHOW_TIME;
import static com.droid2developers.liveslider.utils.Constant.MINIMUM_SLIDESHOW_TIME;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SLIDESHOW;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = SettingsActivity.class.getSimpleName();

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private int wallpaperType = TYPE_SINGLE;
    private String currentPlaylist;
    private Cube cube;

    private CardView scrollCard,slideshowCard,intervalCard,doubleTapCard,powerSaverCard,backButton;
    private Switch scrollSwitch,slideshowSwitch,doubleTapSwitch,powerSaverSwitch;
    private TextView intervalText;
    private SeekBar seekBarRange,seekBarDelay;


    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));

        // Cube to see wallpaper parallax effect in realtime
        cube = findViewById(R.id.cube);

        // View initializations
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        editor = prefs.edit();
        seekBarRange = findViewById(R.id.seekBarRange);
        seekBarDelay = findViewById(R.id.seekBarDelay);
        backButton = findViewById(R.id.backButtonId);

        // Actual Settings Cards
        scrollCard = findViewById(R.id.card1ID);
        slideshowCard = findViewById(R.id.card2ID);
        intervalCard = findViewById(R.id.card3ID);
        doubleTapCard = findViewById(R.id.card4ID);
        powerSaverCard = findViewById(R.id.card5ID);

        // Actual Settings Switches
        scrollSwitch = findViewById(R.id.switch1ID);
        slideshowSwitch = findViewById(R.id.switch2ID);
        doubleTapSwitch = findViewById(R.id.switch3ID);
        powerSaverSwitch = findViewById(R.id.switch4ID);

        // Actual Settings TextViews
        intervalText = findViewById(R.id.interval_intro);

        // Introduction of the LiveWallpaper
        TextView introduction = findViewById(R.id.introduction);
        SpannableString spannableString = new SpannableString(Html.fromHtml(getResources()
                .getString(R.string.introduction2)));
        introduction.setText(spannableString);


        // Applying initial selected settings to views
        InitPreferences();
        InitListeners();
        InitOnClicks();

    }


    private void InitPreferences(){
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
        currentPlaylist = prefs.getString("current_playlist", Constant.PLAYLIST_NONE);
        wallpaperType = prefs.getInt("type", TYPE_SINGLE);

        long timeInMillis = prefs.getLong("slideshow_timer", DEFAULT_SLIDESHOW_TIME);
        intervalText.setText(Constant.getTimeText(timeInMillis));
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

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
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
                if (wallpaperType == TYPE_SLIDESHOW){
                    if (slideshowSwitch.isChecked()){
                        slideshowSwitch.setChecked(false);
                        intervalCard.setVisibility(View.GONE);
                        doubleTapCard.setVisibility(View.GONE);
                    } else {
                        slideshowSwitch.setChecked(true);
                        intervalCard.setVisibility(View.VISIBLE);
                        doubleTapCard.setVisibility(View.VISIBLE);
                    }
                } else {
                    Toast.makeText(SettingsActivity.this, R.string.select_playlist,
                            Toast.LENGTH_SHORT).show();
                }


            }
        });
        intervalCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showIntervalDialog();
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

    }


    private void showIntervalDialog(){

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.hms_picker, null);
        HmsPickerView hmsPickerView = dialogView.findViewById(R.id.hmsPickerView);

        long timeInMillis = prefs.getLong("slideshow_timer", DEFAULT_SLIDESHOW_TIME);
        hmsPickerView.setTimeInMillis(timeInMillis);

        MaterialAlertDialogBuilder dialogBuilder =
                new MaterialAlertDialogBuilder(SettingsActivity.this)
                        .setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.clock_icon, null))
                        .setTitle("Change slideshow time interval?")
                        .setView(dialogView)
                        // Because the picker is long, remove vertical insets to make sure the view not get clipped.
                        .setBackgroundInsetBottom(0)
                        .setBackgroundInsetTop(0)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton(android.R.string.cancel, null);

        AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {

                Button positive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        long timeInMillis = hmsPickerView.getTimeInMillis();
                        if (timeInMillis > MINIMUM_SLIDESHOW_TIME) {
                            intervalText.setText(Constant.getTimeText(timeInMillis));
                            editor.putLong("slideshow_timer",timeInMillis).apply();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(SettingsActivity.this,
                                    "Slideshow Time can't be empty or less than 10 Seconds!",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });


                Button negative = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                negative.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

            }
        });
        alertDialog.show();

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
