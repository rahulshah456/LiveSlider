package com.droid2developers.liveslider.views.activities

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.utils.Constant
import com.droid2developers.liveslider.views.components.SettingsCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Single settings page for whichever overlay shader is active. The top card
 * picks the active shader (None / Rain / Ripple / …) via a choice dialog —
 * same pattern as Transition Effect on the main settings page — and the rows
 * below it are built at runtime from that shader's Constant.ShaderDef.params
 * list, so adding a new shader/parameter needs no new layout or activity code.
 */
class ShaderSettingsActivity : AppCompatActivity() {
    private var editor: SharedPreferences.Editor? = null
    private var prefs: SharedPreferences? = null

    private var backButton: CardView? = null
    private var activeShaderCard: SettingsCardView? = null
    private var paramsContainer: LinearLayout? = null

    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_shader_settings)
        setupSystemBarsAndCutouts()

        window.decorView.rootView.setBackgroundColor(Color.argb(153, 35, 35, 35))

        val isLockMode = intent.getBooleanExtra(Constant.EXTRA_IS_LOCK_MODE, false)
        prefs = if (isLockMode) {
            getSharedPreferences(Constant.PREFS_LOCK, MODE_PRIVATE)
        } else {
            PreferenceManager.getDefaultSharedPreferences(this)
        }
        editor = prefs?.edit()

        backButton = findViewById(R.id.backButtonId)
        activeShaderCard = findViewById(R.id.activeShaderCard)
        paramsContainer = findViewById(R.id.paramsContainer)

        backButton?.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        activeShaderCard?.setOnClickListener { showShaderPickerDialog() }

        refreshActiveShaderCard()
        rebuildParamRows()
    }

    private fun setupSystemBarsAndCutouts() {
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
            windowInsets
        }
    }

    private fun activeShaderId(): String =
        prefs?.getString(Constant.PREF_ACTIVE_SHADER, Constant.SHADER_NONE) ?: Constant.SHADER_NONE

    private fun shaderDisplayName(id: String): String =
        Constant.findShaderDef(id)?.displayName ?: getString(R.string.shader_none)

    private fun refreshActiveShaderCard() {
        activeShaderCard?.setSubHeaderText(shaderDisplayName(activeShaderId()))
    }

    private fun showShaderPickerDialog() {
        val ids = listOf(Constant.SHADER_NONE) + Constant.SHADERS.map { it.id }
        val labels = ids.map { if (it == Constant.SHADER_NONE) getString(R.string.shader_none) else shaderDisplayName(it) }
        val current = activeShaderId()
        val checkedItem = ids.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.shader_active))
            .setSingleChoiceItems(labels.toTypedArray(), checkedItem) { dialog, which ->
                val chosen = ids[which]
                editor?.putString(Constant.PREF_ACTIVE_SHADER, chosen)?.apply()
                refreshActiveShaderCard()
                rebuildParamRows()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /** Tears down and rebuilds paramsContainer's rows for the currently active shader. */
    private fun rebuildParamRows() {
        val container = paramsContainer ?: return
        container.removeAllViews()

        val def = Constant.findShaderDef(activeShaderId()) ?: return
        for (param in def.params) {
            container.addView(buildParamRow(def, param))
        }
    }

    private fun buildParamRow(def: Constant.ShaderDef, param: Constant.ShaderParam): View {
        val prefKey = Constant.shaderPrefKey(def.id, param.key)
        return if (param.type == Constant.ShaderParamType.TOGGLE) {
            SettingsCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.settings_item_height) * 2
                )
                setHeaderText(param.label)
                isSwitchChecked = prefs?.getBoolean(prefKey, param.defaultValue >= 0.5f) ?: false
                setOnSwitchChangeListener(object : SettingsCardView.OnSwitchChangeListener {
                    override fun onSwitchChanged(cardView: SettingsCardView?, isChecked: Boolean) {
                        editor?.putBoolean(prefKey, isChecked)?.apply()
                    }
                })
            }
        } else {
            buildSliderRow(prefKey, param)
        }
    }

    private fun buildSliderRow(prefKey: String, param: Constant.ShaderParam): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                resources.getDimensionPixelSize(R.dimen.settings_item_horizontal_margin), 0,
                resources.getDimensionPixelSize(R.dimen.settings_item_horizontal_margin), 0
            )
        }
        val label = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                resources.getDimensionPixelSize(R.dimen.settings_item_height)
            ).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            text = param.label
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                resources.getDimensionPixelSize(R.dimen.settings_item_height),
                1f
            )
            max = 100
            progress = prefs?.getInt(prefKey, param.defaultProgress()) ?: param.defaultProgress()
            setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) editor?.putInt(prefKey, progress)?.apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        row.addView(label)
        row.addView(seekBar)
        return row
    }
}
