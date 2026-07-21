package com.droid2developers.liveslider.views.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.utils.Constant
import com.droid2developers.liveslider.utils.resetOnLongPress
import com.droid2developers.liveslider.views.components.SettingsCardView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider

/**
 * Bottom sheet to enable/disable the overlay shader and pick which one (Rain /
 * Ripple / …) is active, tuning its parameters right away — replaces the old
 * standalone ShaderSettingsActivity.
 */
class ShaderSettingsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ShaderSettingsBottomSheet"
        private const val ARG_IS_LOCK_MODE = "is_lock_mode"

        fun newInstance(isLockMode: Boolean) =
            ShaderSettingsBottomSheet().apply {
                arguments = Bundle().apply { putBoolean(ARG_IS_LOCK_MODE, isLockMode) }
            }
    }

    private var editor: SharedPreferences.Editor? = null
    private var prefs: SharedPreferences? = null

    private var shaderEnabledCard: SettingsCardView? = null
    private var activeShaderGroup: MaterialButtonToggleGroup? = null
    private var paramsContainer: LinearLayout? = null

    /** Real, selectable shaders — no "None" entry; that's the enable switch instead. */
    private val shaderIds = Constant.SHADERS.map { it.id }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_shader_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isLockMode = requireArguments().getBoolean(ARG_IS_LOCK_MODE)
        prefs = if (isLockMode) {
            requireContext().getSharedPreferences(Constant.PREFS_LOCK, android.content.Context.MODE_PRIVATE)
        } else {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        }
        editor = prefs?.edit()

        shaderEnabledCard = view.findViewById(R.id.shaderEnabledCard)
        activeShaderGroup = view.findViewById(R.id.activeShaderGroup)
        paramsContainer = view.findViewById(R.id.paramsContainer)

        setupShaderEnabledSwitch()
        setupShaderGroup()
        refreshShaderGroupEnabled()
        rebuildParamRows()
    }

    private fun activeShaderId(): String =
        prefs?.getString(Constant.PREF_ACTIVE_SHADER, Constant.SHADER_NONE) ?: Constant.SHADER_NONE

    private fun lastShaderId(): String =
        prefs?.getString(Constant.PREF_LAST_SHADER, shaderIds.first()) ?: shaderIds.first()

    private fun shaderDisplayName(id: String): String =
        Constant.findShaderDef(id)?.displayName ?: id

    private fun setupShaderEnabledSwitch() {
        val card = shaderEnabledCard ?: return
        card.isSwitchChecked = activeShaderId() != Constant.SHADER_NONE
        card.setOnSwitchChangeListener(object : SettingsCardView.OnSwitchChangeListener {
            override fun onSwitchChanged(cardView: SettingsCardView?, isChecked: Boolean) {
                if (isChecked) {
                    editor?.putString(Constant.PREF_ACTIVE_SHADER, lastShaderId())?.apply()
                } else {
                    editor?.putString(Constant.PREF_LAST_SHADER, activeShaderId())?.apply()
                    editor?.putString(Constant.PREF_ACTIVE_SHADER, Constant.SHADER_NONE)?.apply()
                }
                refreshShaderGroupEnabled()
                rebuildParamRows()
            }
        })
    }

    private fun refreshShaderGroupEnabled() {
        val enabled = shaderEnabledCard?.isSwitchChecked == true
        activeShaderGroup?.let { group ->
            group.alpha = if (enabled) 1f else 0.5f
            for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = enabled
        }
    }

    private fun setupShaderGroup() {
        val group = activeShaderGroup ?: return
        shaderIds.forEachIndexed { index, id ->
            val button = layoutInflater.inflate(R.layout.item_toggle_button, group, false) as Button
            button.id = index
            button.text = shaderDisplayName(id)
            button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                width = 0
                weight = 1f
            }
            group.addView(button)
        }
        val currentId = activeShaderId().takeIf { it != Constant.SHADER_NONE } ?: lastShaderId()
        group.check(shaderIds.indexOf(currentId).coerceAtLeast(0))

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val chosen = shaderIds[checkedId]
                editor?.putString(Constant.PREF_LAST_SHADER, chosen)?.apply()
                if (shaderEnabledCard?.isSwitchChecked == true) {
                    editor?.putString(Constant.PREF_ACTIVE_SHADER, chosen)?.apply()
                }
                rebuildParamRows()
            }
        }
    }

    /** Tears down and rebuilds paramsContainer's rows for the currently active shader.
     *  Shows nothing while the effect is disabled — no point tuning a shader that isn't drawing. */
    private fun rebuildParamRows() {
        val container = paramsContainer ?: return
        container.removeAllViews()

        if (shaderEnabledCard?.isSwitchChecked != true) return
        val def = Constant.findShaderDef(activeShaderId()) ?: return

        // Keys of TOGGLE params that have at least one dependent — the group starts
        // with that toggle itself, so the "new group" divider goes right before it.
        val groupStartKeys = def.params.mapNotNull { it.dependsOnKey }.toSet()

        // key -> the views nested under that TOGGLE param, filled in as we build them below.
        // A plain MutableList so the toggle row's listener (created before its dependents
        // exist, since dependents come later in def.params) sees later additions by reference.
        val dependentsByToggleKey = mutableMapOf<String, MutableList<View>>()
        groupStartKeys.forEach { dependentsByToggleKey[it] = mutableListOf() }

        for (param in def.params) {
            if (param.key in groupStartKeys) {
                // This toggle starts a nested group (e.g. Rain Lines) — mark it off
                // from the settings above as a visually distinct section.
                container.addView(buildSubDivider())
            }
            val dependsOn = param.dependsOnKey
            val row = buildParamRow(def, param, dependentsByToggleKey[param.key])
            if (dependsOn != null) {
                row.visibility = if (isToggleParamChecked(def, dependsOn)) View.VISIBLE else View.GONE
                dependentsByToggleKey.getValue(dependsOn).add(row)
            }
            container.addView(row)
        }
    }

    private fun isToggleParamChecked(def: Constant.ShaderDef, paramKey: String): Boolean {
        val toggleParam = def.params.first { it.key == paramKey }
        return prefs?.getBoolean(Constant.shaderPrefKey(def.id, paramKey), toggleParam.defaultValue >= 0.5f) ?: false
    }

    private fun buildSubDivider(): View = View(requireContext()).apply {
        setBackgroundResource(R.color.color_divider)
        val sideMargin = resources.getDimensionPixelSize(R.dimen.settings_item_horizontal_margin)
        // More breathing room than the standard divider_vertical_margin (8dp) — this one
        // separates a whole nested settings group, not just two adjacent rows.
        val verticalMargin = resources.getDimensionPixelSize(R.dimen.divider_vertical_margin) * 2
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = sideMargin
            marginEnd = sideMargin
            topMargin = verticalMargin
            bottomMargin = verticalMargin
        }
    }

    /** Toggle param key -> a short blurb shown as the card's subheader, since these read
     *  as bare on/off switches otherwise ("Rain Lines" alone doesn't say what it does). */
    private fun toggleDescription(paramKey: String): String? = when (paramKey) {
        "touchOnly" -> "Ripples react to your touches only"
        "rainLines" -> "Adds a virtual rain effect alongside the ripples"
        "lightning" -> "Flickers the screen brightness to mimic lightning"
        else -> null
    }

    /** [dependents], when this param is a TOGGLE, are the rows only shown while it's checked.
     *  The list is mutated after this row is built (dependents are added as they're built later),
     *  so the listener below always reads its current contents rather than a stale snapshot. */
    private fun buildParamRow(def: Constant.ShaderDef, param: Constant.ShaderParam, dependents: List<View>?): View {
        val prefKey = Constant.shaderPrefKey(def.id, param.key)
        return if (param.type == Constant.ShaderParamType.TOGGLE) {
            SettingsCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.settings_item_height)
                ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.divider_vertical_margin) }
                setHeaderText(param.label)
                toggleDescription(param.key)?.let { setSubHeaderText(it) }
                val checked = prefs?.getBoolean(prefKey, param.defaultValue >= 0.5f) ?: false
                isSwitchChecked = checked
                setOnSwitchChangeListener(object : SettingsCardView.OnSwitchChangeListener {
                    override fun onSwitchChanged(cardView: SettingsCardView?, isChecked: Boolean) {
                        editor?.putBoolean(prefKey, isChecked)?.apply()
                        dependents?.forEach { it.visibility = if (isChecked) View.VISIBLE else View.GONE }
                    }
                })
            }
        } else {
            buildSliderRow(prefKey, param)
        }
    }

    private fun buildSliderRow(prefKey: String, param: Constant.ShaderParam): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                resources.getDimensionPixelSize(R.dimen.settings_item_horizontal_margin), 0,
                resources.getDimensionPixelSize(R.dimen.settings_item_horizontal_margin), 0
            )
        }
        val label = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = param.label
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val defaultProgress = param.defaultProgress().toFloat()
        val slider = Slider(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            valueFrom = 0f
            valueTo = 100f
            value = (prefs?.getInt(prefKey, param.defaultProgress()) ?: param.defaultProgress()).toFloat()
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) editor?.putInt(prefKey, value.toInt())?.apply()
            }
            resetOnLongPress(defaultProgress)
        }
        row.addView(label)
        row.addView(slider)
        return row
    }
}
