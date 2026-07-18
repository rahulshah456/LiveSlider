package com.droid2developers.liveslider.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.droid2developers.liveslider.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlaylistActionsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "PlaylistActionsBottomSheet"
        private const val ARG_PLAYLIST_ID = "playlist_id"
        private const val ARG_PLAYLIST_NAME = "playlist_name"
        private const val ARG_IS_PROCESSED = "is_processed"

        fun newInstance(playlistId: String, name: String?, isProcessed: Boolean) =
            PlaylistActionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PLAYLIST_ID, playlistId)
                    putString(ARG_PLAYLIST_NAME, name)
                    putBoolean(ARG_IS_PROCESSED, isProcessed)
                }
            }
    }

    interface Listener {
        fun onActivate(playlistId: String)
        fun onReprocess(playlistId: String)
        fun onAddImages(playlistId: String)
        fun onDelete(playlistId: String)
    }

    var listener: Listener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_playlist_actions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val playlistId = requireArguments().getString(ARG_PLAYLIST_ID) ?: return
        val name = requireArguments().getString(ARG_PLAYLIST_NAME)
        val isProcessed = requireArguments().getBoolean(ARG_IS_PROCESSED)

        view.findViewById<TextView>(R.id.sheetPlaylistTitleId).text = name

        val activateButton = view.findViewById<View>(R.id.actionActivateId)
        activateButton.isEnabled = isProcessed
        activateButton.alpha = if (isProcessed) 1f else 0.5f
        activateButton.setOnClickListener {
            listener?.onActivate(playlistId)
            dismiss()
        }

        val reprocessButton = view.findViewById<View>(R.id.actionReprocessId)
        reprocessButton.isEnabled = !isProcessed
        reprocessButton.alpha = if (isProcessed) 0.5f else 1f
        reprocessButton.setOnClickListener {
            listener?.onReprocess(playlistId)
            dismiss()
        }

        view.findViewById<View>(R.id.actionAddImagesId).setOnClickListener {
            listener?.onAddImages(playlistId)
            dismiss()
        }

        view.findViewById<View>(R.id.actionDeleteId).setOnClickListener {
            listener?.onDelete(playlistId)
            dismiss()
        }
    }
}
