package com.droid2developers.liveslider.views.activities

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.live_wallpaper.Cube
import com.droid2developers.liveslider.models.BiasChangeEvent
import com.droid2developers.liveslider.models.FaceRotationEvent
import com.droid2developers.liveslider.utils.Constant
import com.droid2developers.liveslider.views.components.SettingsCardView
import com.droid2developers.liveslider.views.components.SettingsCardView.OnCardClickListener
import com.droid2developers.liveslider.views.components.SettingsCardView.OnSwitchChangeListener
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import xyz.aprildown.hmspickerview.HmsPickerView

class SettingsActivity : AppCompatActivity(), OnCardClickListener, OnSwitchChangeListener {
    private var editor: SharedPreferences.Editor? = null
    private var prefs: SharedPreferences? = null
    private var wallpaperType = Constant.TYPE_SINGLE

    private var slideshowCard: SettingsCardView? = null
    private var intervalCard: SettingsCardView? = null
    private var doubleTapCard: SettingsCardView? = null
    private var powerSaverCard: SettingsCardView? = null
    private var backButton: CardView? = null
    private var faceText: TextView? = null
    private var seekBarRange: SeekBar? = null
    private var seekBarDelay: SeekBar? = null
    private var cube: Cube? = null

    // Calibration controls
    private var calibrationGroup: MaterialButtonToggleGroup? = null
    private var defaultCalibrationButton: Button? = null
    private var verticalCalibrationButton: Button? = null
    private var dynamicCalibrationButton: Button? = null

    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        setupSystemBarsAndCutouts()

        window.decorView.getRootView()
            .setBackgroundColor(Color.argb(153, 35, 35, 35))

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        editor = prefs?.edit()

        bindViews()
        setupInitialState()
        setupListeners()
    }

    /**
     * Sets up proper handling of system bars and display cutouts/notches
     * Following Android's modern edge-to-edge guidelines
     */
    private fun setupSystemBarsAndCutouts() {
        val rootView = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            // Apply padding to avoid system bars and cutouts
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )

            // Return the insets to continue propagation
            windowInsets
        }
    }

    private fun bindViews() {
        cube = findViewById(R.id.cube)
        seekBarRange = findViewById(R.id.seekBarRange)
        seekBarDelay = findViewById(R.id.seekBarDelay)
        backButton = findViewById(R.id.backButtonId)

        powerSaverCard = findViewById(R.id.card5ID)
        slideshowCard = findViewById(R.id.card2ID)
        intervalCard = findViewById(R.id.card3ID)
        doubleTapCard = findViewById(R.id.card4ID)

        faceText = findViewById<TextView>(R.id.face)

        // Calibration controls
        calibrationGroup = findViewById(R.id.calibrationGroup)
        defaultCalibrationButton = findViewById(R.id.defaultCalibration)
        verticalCalibrationButton = findViewById(R.id.button2)
        dynamicCalibrationButton = findViewById(R.id.dynamicCalibration)

        // Help button
        val helpButton = findViewById<CardView>(R.id.helpButtonId)
        helpButton?.setOnClickListener { showHelpDialog() }
    }

    private fun setupInitialState() {
        val rangeProgress = prefs?.getInt("range", 10) ?: 10
        val delayProgress = prefs?.getInt("delay", 10) ?: 10

        seekBarRange?.progress = rangeProgress
        seekBarDelay?.progress = delayProgress

        slideshowCard?.isSwitchChecked = prefs?.getBoolean("slideshow", false) ?: false
        doubleTapCard?.isSwitchChecked = prefs?.getBoolean("double_tap", false) ?: false
        powerSaverCard?.isSwitchChecked = prefs?.getBoolean("power_saver", true) ?: true

        wallpaperType = prefs?.getInt("type", Constant.TYPE_SINGLE) ?: Constant.TYPE_SINGLE
        val timeInMillis = prefs?.getLong("slideshow_timer",
            Constant.DEFAULT_SLIDESHOW_TIME) ?: Constant.DEFAULT_SLIDESHOW_TIME
        updateIntervalText(timeInMillis)
        updateSlideshowCardsVisibility()

        // Setup initial calibration mode
        setupInitialCalibrationMode()
    }

    private fun setupInitialCalibrationMode() {
        val currentCalibrationMode = prefs?.getInt("calibration_mode",
            Constant.CALIBRATION_DEFAULT) ?: Constant.CALIBRATION_DEFAULT

        when (currentCalibrationMode) {
            Constant.CALIBRATION_DEFAULT -> {
                calibrationGroup?.check(R.id.defaultCalibration)
            }
            Constant.CALIBRATION_VERTICAL -> {
                calibrationGroup?.check(R.id.button2)
            }
            Constant.CALIBRATION_DYNAMIC -> {
                calibrationGroup?.check(R.id.dynamicCalibration)
            }
        }
    }

    private fun setupListeners() {
        backButton?.setOnClickListener { v: View? -> onBackPressedDispatcher.onBackPressed() }

        powerSaverCard?.setOnCardClickListener(this)
        powerSaverCard?.setOnSwitchChangeListener(this)

        slideshowCard?.setOnCardClickListener(this)
        slideshowCard?.setOnSwitchChangeListener(this)

        intervalCard?.setOnCardClickListener(this)

        doubleTapCard?.setOnCardClickListener(this)
        doubleTapCard?.setOnSwitchChangeListener(this)

        seekBarRange?.let { setupSeekBarListener(it, "range") }
        seekBarDelay?.let { setupSeekBarListener(it, "delay") }

        // Calibration controls
        calibrationGroup?.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.defaultCalibration -> {
                        editor?.putInt("calibration_mode", Constant.CALIBRATION_DEFAULT)
                        editor?.apply()
                    }
                    R.id.button2 -> {
                        editor?.putInt("calibration_mode", Constant.CALIBRATION_VERTICAL)
                        editor?.apply()
                    }
                    R.id.dynamicCalibration -> {
                        editor?.putInt("calibration_mode", Constant.CALIBRATION_DYNAMIC)
                        editor?.apply()
                    }
                }
            }
        }
    }

    private fun setupSeekBarListener(seekBar: SeekBar, key: String?) {
        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    editor?.putInt(key, progress)?.apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onCardClick(cardView: SettingsCardView?) {
        val id = cardView?.id
        if (id == R.id.card2ID) {
            handleSlideshowClick()
        } else if (id == R.id.card3ID) {
            showIntervalDialog()
        }
    }

    override fun onSwitchChanged(
        cardView: SettingsCardView?,
        isChecked: Boolean
    ) {
        val id = cardView?.id
        if (id == R.id.card5ID) {
            editor?.putBoolean("power_saver", isChecked)
        } else if (id == R.id.card2ID) {
            if (wallpaperType == Constant.TYPE_SLIDESHOW || !isChecked) {
                editor?.putBoolean("slideshow", isChecked)
                updateSlideshowCardsVisibility()
            } else {
                slideshowCard?.isSwitchChecked = false
                Toast.makeText(this, R.string.select_playlist, Toast.LENGTH_SHORT).show()
            }
        } else if (id == R.id.card4ID) {
            editor?.putBoolean("double_tap", isChecked)
        }
        editor?.apply()
    }

    private fun handleSlideshowClick() {
        if (wallpaperType != Constant.TYPE_SLIDESHOW) {
            // This will be handled in onSwitchChanged when switch gets toggled
            // Just show toast here, don't modify switch state
            // TODO: Show dialog to navigate to playlist selection screen
        }
    }

    private fun updateSlideshowCardsVisibility() {
        val visibility = if (slideshowCard?.isSwitchChecked == true) View.VISIBLE else View.GONE
        intervalCard?.visibility = visibility
        doubleTapCard?.visibility = visibility
    }

    private fun updateIntervalText(timeInMillis: Long) {
        val timeText = Constant.getTimeText(timeInMillis)
        intervalCard?.setSubHeaderText(timeText)
    }

    private fun showIntervalDialog() {
        val dialogView = layoutInflater.inflate(R.layout.hms_picker, null)
        val hmsPickerView = dialogView.findViewById<HmsPickerView>(R.id.hmsPickerView)
        val errorTextView = dialogView.findViewById<TextView>(R.id.errorTextView)
        val time = prefs?.getLong(
            "slideshow_timer",
            Constant.DEFAULT_SLIDESHOW_TIME
        ) ?: Constant.DEFAULT_SLIDESHOW_TIME
        hmsPickerView.setTimeInMillis(time)

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.clock_icon, null))
            .setTitle("Change slideshow time interval?")
            .setView(dialogView)
            .setBackgroundInsetBottom(0)
            .setBackgroundInsetTop(0)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog: DialogInterface?, which: Int -> dialog?.dismiss() }
            .create()

        alertDialog.setOnShowListener { dialog: DialogInterface? ->
            val positive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener { v: View? ->
                val timeInMillis = hmsPickerView.getTimeInMillis()
                if (timeInMillis > Constant.MINIMUM_SLIDESHOW_TIME) {
                    updateIntervalText(timeInMillis)
                    editor?.putLong("slideshow_timer", timeInMillis)?.apply()
                    errorTextView.visibility = View.GONE
                    alertDialog.dismiss()
                } else {
                    errorTextView.visibility = View.VISIBLE
                }
            }
        }
        alertDialog.show()
    }

    /**
     * Shows a help dialog with information about settings controls and calibration modes
     */
    private fun showHelpDialog() {
        val helpMessage = buildString {
            // Introduction
            append(getString(R.string.introduction1))
            append("\n\n")
            append(Html.fromHtml(getString(R.string.introduction2), Html.FROM_HTML_MODE_LEGACY))
            append("\n\n")

            // Settings Controls
            append(getString(R.string.help_settings_controls_title))
            append("\n\n")
            append(getString(R.string.help_range_info))
            append("\n\n")
            append(getString(R.string.help_speed_info))
            append("\n\n")
            append(getString(R.string.help_power_saver_info))
            append("\n\n")
            append(getString(R.string.help_slideshow_info))
            append("\n\n")
            append(getString(R.string.help_double_tap_info))
            append("\n\n")

            // Calibration Modes
            append(getString(R.string.help_calibration_modes_title))
            append("\n\n")
            append(getString(R.string.help_default_calibration))
            append("\n\n")
            append(getString(R.string.help_vertical_calibration))
            append("\n\n")
            append(getString(R.string.help_dynamic_calibration))
            append("\n\n")
            append(getString(R.string.help_cube_helper))
        }

        MaterialAlertDialogBuilder(this)
            .setIcon(ResourcesCompat.getDrawable(resources, R.drawable.help_24dp, null))
            .setTitle(getString(R.string.help_dialog_title))
            .setMessage(helpMessage)
            .setPositiveButton(getString(R.string.help_got_it)) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: BiasChangeEvent) {
        cube?.setRotation(event.getY(), event.getX())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFaceRotationEvent(event: FaceRotationEvent) {
        faceText?.text = event.readableFaceName
    }

    public override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    public override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }
}
