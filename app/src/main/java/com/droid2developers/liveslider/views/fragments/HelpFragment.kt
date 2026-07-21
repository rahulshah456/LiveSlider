package com.droid2developers.liveslider.views.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RatingBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.droid2developers.liveslider.BuildConfig
import com.droid2developers.liveslider.R

class HelpFragment : Fragment(R.layout.fragment_help) {

    companion object {
        private const val ISSUES_URL = "https://github.com/rahulshah456/LiveSlider/issues"
        private const val PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=com.droid2developers.liveslider"
        private const val SUPPORT_EMAIL = "droid2developers@gmail.com"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.helpVersionId).text =
            getString(R.string.help_sheet_version, BuildConfig.VERSION_NAME)

        view.findViewById<View>(R.id.helpReleaseNotesId).setOnClickListener {
            ReleaseNotesBottomSheet().show(childFragmentManager, ReleaseNotesBottomSheet.TAG)
        }
        view.findViewById<View>(R.id.helpReportIssueId).setOnClickListener {
            openUrl(ISSUES_URL)
        }
        view.findViewById<View>(R.id.helpEmailId).setOnClickListener {
            sendSupportEmail()
        }
        view.findViewById<RatingBar>(R.id.helpRatingStarsId).setOnClickListener {
            openUrl(PLAY_STORE_URL)
        }
        view.findViewById<View>(R.id.helpRateId).setOnClickListener {
            openUrl(PLAY_STORE_URL)
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun sendSupportEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.help_sheet_email_subject))
        }
        startActivity(intent)
    }
}
