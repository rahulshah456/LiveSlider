package com.droid2developers.liveslider.views.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.droid2developers.liveslider.R
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsCardView : CardView, View.OnClickListener {
    private var iconImageView: ImageView? = null
    private var headerTextView: TextView? = null
    private var subHeaderTextView: TextView? = null
    private var materialSwitch: MaterialSwitch? = null

    private var onCardClickListener: OnCardClickListener? = null
    private var onSwitchChangeListener: OnSwitchChangeListener? = null

    private var hasSwitchControl = true

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        // Inflate the layout
        LayoutInflater.from(context).inflate(R.layout.component_settings_card, this, true)

        // Setup card properties
        setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        cardElevation = 0f
        isClickable = true
        setFocusable(true)

        // Properly get the selectableItemBackground from theme
        val attrsArray = intArrayOf(android.R.attr.selectableItemBackground)
        val typedArray = context.obtainStyledAttributes(attrsArray)
        try {
            val selectableItemBackgroundResource = typedArray.getResourceId(0, 0)
            if (selectableItemBackgroundResource != 0) {
                setForeground(
                    AppCompatResources.getDrawable(
                        context,
                        selectableItemBackgroundResource
                    )
                )
            }
        } finally {
            typedArray.recycle()
        }

        // Find views
        iconImageView = findViewById(R.id.settings_card_icon)
        headerTextView = findViewById(R.id.settings_card_header)
        subHeaderTextView = findViewById(R.id.settings_card_subheader)
        materialSwitch = findViewById(R.id.settings_card_switch)

        // Set click listener for the card
        setOnClickListener(this)

        // Handle custom attributes
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SettingsCardView)
            try {
                // Set icon
                val iconRes = a.getResourceId(R.styleable.SettingsCardView_cardIcon, 0)
                if (iconRes != 0) {
                    setIcon(iconRes)
                }

                // Set header text
                val headerText = a.getString(R.styleable.SettingsCardView_cardHeader)
                if (headerText != null) {
                    setHeaderText(headerText)
                }

                // Set subheader text
                val subHeaderText = a.getString(R.styleable.SettingsCardView_cardSubHeader)
                if (subHeaderText != null) {
                    setSubHeaderText(subHeaderText)
                }

                // Check if switch is needed
                hasSwitchControl = a.getBoolean(R.styleable.SettingsCardView_hasSwitch, true)
                if (!hasSwitchControl) {
                    materialSwitch?.visibility = GONE
                }

                // Set switch state
                val switchChecked = a.getBoolean(R.styleable.SettingsCardView_switchChecked, false)
                this.isSwitchChecked = switchChecked
            } finally {
                a.recycle()
            }
        }

        // Setup switch change listener
        materialSwitch!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (onSwitchChangeListener != null) {
                onSwitchChangeListener?.onSwitchChanged(this, isChecked)
            }
        })

        // Disable switch click to prevent double triggering
        materialSwitch?.isClickable = false
    }

    override fun onClick(v: View?) {
        if (onCardClickListener != null) {
            onCardClickListener!!.onCardClick(this)
        }

        // If has switch, toggle it when card is clicked
        if (hasSwitchControl) {
            materialSwitch!!.toggle()
        }
    }

    // Setter methods
    fun setIcon(@DrawableRes iconRes: Int) {
        iconImageView?.setImageResource(iconRes)
    }

    fun setHeaderText(text: String?) {
        headerTextView?.text = text
    }

    fun setHeaderText(@StringRes textRes: Int) {
        headerTextView?.setText(textRes)
    }

    fun setSubHeaderText(text: String?) {
        subHeaderTextView?.text = text
    }

    fun setSubHeaderText(@StringRes textRes: Int) {
        subHeaderTextView?.setText(textRes)
    }

    var isSwitchChecked: Boolean
        get() = hasSwitchControl && materialSwitch?.isChecked == true
        set(checked) {
            if (hasSwitchControl) {
                materialSwitch!!.setChecked(checked)
            }
        }

    fun setSwitchVisibility(visibility: Int) {
        materialSwitch?.visibility = visibility
        hasSwitchControl = visibility == VISIBLE
    }

    fun getMaterialSwitch(): MaterialSwitch {
        return materialSwitch!!
    }

    // Listener interfaces
    interface OnCardClickListener {
        fun onCardClick(cardView: SettingsCardView?)
    }

    interface OnSwitchChangeListener {
        fun onSwitchChanged(cardView: SettingsCardView?, isChecked: Boolean)
    }

    // Listener setters
    fun setOnCardClickListener(listener: OnCardClickListener?) {
        this.onCardClickListener = listener
    }

    fun setOnSwitchChangeListener(listener: OnSwitchChangeListener?) {
        this.onSwitchChangeListener = listener
    }
}
