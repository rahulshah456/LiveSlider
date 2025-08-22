package com.droid2developers.liveslider.views.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;
import com.droid2developers.liveslider.R;
import com.droid2developers.liveslider.live_wallpaper.Cube;
import com.droid2developers.liveslider.models.BiasChangeEvent;
import com.droid2developers.liveslider.models.FaceRotationEvent;
import com.droid2developers.liveslider.utils.Constant;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import xyz.aprildown.hmspickerview.HmsPickerView;
import static com.droid2developers.liveslider.utils.Constant.DEFAULT_SLIDESHOW_TIME;
import static com.droid2developers.liveslider.utils.Constant.MINIMUM_SLIDESHOW_TIME;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SINGLE;
import static com.droid2developers.liveslider.utils.Constant.TYPE_SLIDESHOW;

public class SettingsActivity extends AppCompatActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private SharedPreferences.Editor editor;
    private SharedPreferences prefs;
    private int wallpaperType = TYPE_SINGLE;

    private CardView scrollCard, slideshowCard, intervalCard, doubleTapCard, powerSaverCard, backButton;
    private MaterialSwitch scrollSwitch, slideshowSwitch, doubleTapSwitch, powerSaverSwitch;
    private TextView intervalText, faceText;
    private SeekBar seekBarRange, seekBarDelay;
    private Cube cube;

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.argb(153, 35, 35, 35));

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        editor = prefs.edit();

        bindViews();
        setupInitialState();
        setupListeners();
    }

    private void bindViews() {
        cube = findViewById(R.id.cube);
        seekBarRange = findViewById(R.id.seekBarRange);
        seekBarDelay = findViewById(R.id.seekBarDelay);
        backButton = findViewById(R.id.backButtonId);

        scrollCard = findViewById(R.id.card1ID);
        slideshowCard = findViewById(R.id.card2ID);
        intervalCard = findViewById(R.id.card3ID);
        doubleTapCard = findViewById(R.id.card4ID);
        powerSaverCard = findViewById(R.id.card5ID);

        scrollSwitch = findViewById(R.id.switch1ID);
        slideshowSwitch = findViewById(R.id.switch2ID);
        doubleTapSwitch = findViewById(R.id.switch3ID);
        powerSaverSwitch = findViewById(R.id.switch4ID);

        intervalText = findViewById(R.id.interval_intro);
        faceText = findViewById(R.id.face);

        TextView introduction = findViewById(R.id.introduction);
        CharSequence intro = Html.fromHtml(getResources().getString(R.string.introduction2),
                Html.FROM_HTML_MODE_LEGACY
        );
        introduction.setText(intro);
    }

    private void setupInitialState() {
        seekBarRange.setProgress(prefs.getInt("range", 10));
        seekBarDelay.setProgress(prefs.getInt("delay", 10));
        scrollSwitch.setChecked(prefs.getBoolean("scroll", true));
        slideshowSwitch.setChecked(prefs.getBoolean("slideshow", false));
        doubleTapSwitch.setChecked(prefs.getBoolean("double_tap", false));
        powerSaverSwitch.setChecked(prefs.getBoolean("power_saver", true));

        wallpaperType = prefs.getInt("type", TYPE_SINGLE);
        long timeInMillis = prefs.getLong("slideshow_timer", DEFAULT_SLIDESHOW_TIME);
        intervalText.setText(Constant.getTimeText(timeInMillis));

        updateSlideshowCardsVisibility();
    }

    private void setupListeners() {
        backButton.setOnClickListener(this);
        scrollCard.setOnClickListener(this);
        slideshowCard.setOnClickListener(this);
        intervalCard.setOnClickListener(this);
        doubleTapCard.setOnClickListener(this);
        powerSaverCard.setOnClickListener(this);

        scrollSwitch.setOnCheckedChangeListener(this);
        slideshowSwitch.setOnCheckedChangeListener(this);
        doubleTapSwitch.setOnCheckedChangeListener(this);
        powerSaverSwitch.setOnCheckedChangeListener(this);
        
        setupSeekBarListener(seekBarRange, "range");
        setupSeekBarListener(seekBarDelay, "delay");
    }
    
    private void setupSeekBarListener(SeekBar seekBar, final String key) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt(key, progress).apply();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        if (id == R.id.backButtonId) {
            getOnBackPressedDispatcher().onBackPressed();
        } else if (id == R.id.card1ID) {
            scrollSwitch.toggle();
        } else if (id == R.id.card2ID) {
            handleSlideshowClick();
        } else if (id == R.id.card3ID) {
            showIntervalDialog();
        } else if (id == R.id.card4ID) {
            doubleTapSwitch.toggle();
        } else if (id == R.id.card5ID) {
            powerSaverSwitch.toggle();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final int id = buttonView.getId();
        if (id == R.id.switch1ID) {
            editor.putBoolean("scroll", isChecked);
        } else if (id == R.id.switch2ID) {
            editor.putBoolean("slideshow", isChecked);
            updateSlideshowCardsVisibility();
        } else if (id == R.id.switch3ID) {
            editor.putBoolean("double_tap", isChecked);
        } else if (id == R.id.switch4ID) {
            editor.putBoolean("power_saver", isChecked);
        }
        editor.apply();
    }

    private void handleSlideshowClick() {
        if (wallpaperType == TYPE_SLIDESHOW) {
            slideshowSwitch.toggle();
        } else {
            Toast.makeText(this, R.string.select_playlist, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSlideshowCardsVisibility() {
        int visibility = slideshowSwitch.isChecked() ? View.VISIBLE : View.GONE;
        intervalCard.setVisibility(visibility);
        doubleTapCard.setVisibility(visibility);
    }

    private void showIntervalDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.hms_picker, null);
        HmsPickerView hmsPickerView = dialogView.findViewById(R.id.hmsPickerView);
        TextView errorTextView = dialogView.findViewById(R.id.errorTextView);
        hmsPickerView.setTimeInMillis(prefs.getLong("slideshow_timer", DEFAULT_SLIDESHOW_TIME));

        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                .setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.clock_icon, null))
                .setTitle("Change slideshow time interval?")
                .setView(dialogView)
                .setBackgroundInsetBottom(0)
                .setBackgroundInsetTop(0)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .create();

        alertDialog.setOnShowListener(dialog -> {
            Button positive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                long timeInMillis = hmsPickerView.getTimeInMillis();
                if (timeInMillis > MINIMUM_SLIDESHOW_TIME) {
                    intervalText.setText(Constant.getTimeText(timeInMillis));
                    editor.putLong("slideshow_timer", timeInMillis).apply();
                    errorTextView.setVisibility(View.GONE);
                    alertDialog.dismiss();
                } else {
                    errorTextView.setVisibility(View.VISIBLE);
                }
            });
        });
        alertDialog.show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(BiasChangeEvent event) {
        cube.setRotation(event.getY(), event.getX());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFaceRotationEvent(FaceRotationEvent event) {
        faceText.setText(event.getReadableFaceName());
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
