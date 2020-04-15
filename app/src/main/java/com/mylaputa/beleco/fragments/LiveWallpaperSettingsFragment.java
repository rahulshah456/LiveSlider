package com.mylaputa.beleco.fragments;

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

import com.mylaputa.beleco.live_wallpaper.Cube;
import com.mylaputa.beleco.live_wallpaper.LiveWallpaperRenderer;
import com.mylaputa.beleco.R;
import com.mylaputa.beleco.utils.Constant;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by dklap on 1/22/2017.
 */

public class LiveWallpaperSettingsFragment extends Fragment {

    private static final String TAG = LiveWallpaperSettingsFragment.class.getSimpleName();
    private final int REQUEST_CHOOSE_PHOTOS = 0;

    private int oldPicture = 0;
    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
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
        doubleTapSwitch.setChecked(prefs.getBoolean("double_tap",false));
        powerSaverSwitch.setChecked(prefs.getBoolean("power_saver", true));
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
                if (slideshowSwitch.isChecked()){
                    slideshowSwitch.setChecked(false);
                } else {
                    slideshowSwitch.setChecked(true);
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

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, REQUEST_CHOOSE_PHOTOS);

//                Intent intent = new Intent();
//                intent.setType("image/*");
//                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
//                intent.setAction(Intent.ACTION_GET_CONTENT);
//                startActivityForResult(Intent.createChooser(intent,"Select Wallpapers"), REQUEST_CHOOSE_PHOTOS);
            }
        });

    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHOOSE_PHOTOS) {
            if (resultCode == Activity.RESULT_OK && data!=null) {
                if (data.getData()!=null){
                    Log.d(TAG, "onActivityResult: getData() = " + data.getData());
                } else if (data.getClipData()!=null){
                    Log.d(TAG, "onActivityResult: getClipData() = " + data.getClipData().getItemCount());
                }
            }
        }
    }

    private InputStream openUri(Uri uri) {
        if (uri == null) return null;
        try {
            return ((getActivity()!=null)? getActivity().getContentResolver().openInputStream(uri):null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private class SaveSelections extends AsyncTask<ClipData, Integer, Void> {

        private ProgressDialog dialog;

        public SaveSelections(Context mContext) {
            dialog = new ProgressDialog(mContext);
        }

        @Override
        protected Void doInBackground(ClipData... clipData) {

            for(int index=0; index<clipData[0].getItemCount(); index++){

                Uri contentURI  = clipData[0].getItemAt(index).getUri();
                InputStream in = openUri(contentURI);
                if (in != null) {
                    try {
                        if (getActivity() != null) {

                            String wallpaper_name = Constant.HEADER + System.currentTimeMillis();

                            FileOutputStream fos = getActivity().openFileOutput(wallpaper_name, Context.MODE_PRIVATE);
                            byte[] buffer = new byte[1024];
                            int bytes;
                            while ((bytes = in.read(buffer)) > 0) {
                                fos.write(buffer, 0, bytes);
                            }
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



            // do background work here
//

            return null;
        }

        @Override
        protected void onPreExecute() {
            dialog.setMessage("Processing selections...");
            dialog.show();
        }

        @Override
        protected void onPostExecute(Void result) {
            // do UI work here
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
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
