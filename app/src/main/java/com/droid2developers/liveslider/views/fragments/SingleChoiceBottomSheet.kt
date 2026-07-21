package com.droid2developers.liveslider.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.droid2developers.liveslider.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Generic single-choice picker bottom sheet — replaces the repeated
 * MaterialAlertDialogBuilder.setSingleChoiceItems pattern used for Transition
 * Effect / Animation Speed / Wallpaper Quality.
 */
class SingleChoiceBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "SingleChoiceBottomSheet"
        private const val ARG_TITLE = "title"
        private const val ARG_LABELS = "labels"
        private const val ARG_CHECKED_ITEM = "checked_item"

        fun newInstance(title: String, labels: Array<String>, checkedItem: Int) =
            SingleChoiceBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putStringArray(ARG_LABELS, labels)
                    putInt(ARG_CHECKED_ITEM, checkedItem)
                }
            }
    }

    fun interface Listener {
        fun onItemSelected(which: Int)
    }

    var listener: Listener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_single_choice, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = requireArguments().getString(ARG_TITLE)
        val labels = requireArguments().getStringArray(ARG_LABELS) ?: emptyArray()
        val checkedItem = requireArguments().getInt(ARG_CHECKED_ITEM)

        view.findViewById<TextView>(R.id.sheetTitleId).text = title

        val optionsContainer = view.findViewById<LinearLayout>(R.id.optionsContainerId)
        val radioButtons = labels.map { label ->
            buildOptionRow(optionsContainer, label)
        }

        radioButtons.getOrNull(checkedItem)?.isChecked = true

        radioButtons.forEachIndexed { index, radioButton ->
            radioButton.setOnClickListener {
                radioButtons.forEach { it.isChecked = it === radioButton }
                listener?.onItemSelected(index)
                dismiss()
            }
        }
    }

    private fun buildOptionRow(container: LinearLayout, label: String): RadioButton {
        val row = layoutInflater.inflate(R.layout.item_single_choice, container, false) as RadioButton
        row.text = label
        container.addView(row)
        return row
    }
}
