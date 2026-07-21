package com.droid2developers.liveslider.views.fragments

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.droid2developers.liveslider.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ReleaseNotesBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ReleaseNotesBottomSheet"
        private const val RELEASES_API_URL =
            "https://api.github.com/repos/rahulshah456/LiveSlider/releases"
    }

    private data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("body") val body: String?,
        @SerializedName("published_at") val publishedAt: String?
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_release_notes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val progress = view.findViewById<ProgressBar>(R.id.releaseNotesProgressId)
        val errorText = view.findViewById<TextView>(R.id.releaseNotesErrorId)
        val scroll = view.findViewById<NestedScrollView>(R.id.releaseNotesScrollId)
        val container = view.findViewById<ViewGroup>(R.id.releaseNotesContainerId)

        viewLifecycleOwner.lifecycleScope.launch {
            val releases = withContext(Dispatchers.IO) { fetchReleases() }

            progress.visibility = View.GONE
            if (releases == null) {
                errorText.visibility = View.VISIBLE
                return@launch
            }

            releases.forEach { release -> container.addView(buildReleaseItem(release)) }
            scroll.visibility = View.VISIBLE
        }
    }

    private fun fetchReleases(): List<GitHubRelease>? {
        val request = Request.Builder().url(RELEASES_API_URL).build()
        return try {
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = response.body()?.string() ?: return null
                Gson().fromJson(json, Array<GitHubRelease>::class.java).toList()
            }
        } catch (e: Exception) {
            null
        }
    }

    private val markwon by lazy { Markwon.create(requireContext()) }

    private fun buildReleaseItem(release: GitHubRelease): View {
        val item = layoutInflater.inflate(
            R.layout.item_release_note,
            null,
            false
        )
        item.findViewById<TextView>(R.id.releaseTitleId).text =
            release.name?.takeIf { it.isNotBlank() } ?: release.tagName.orEmpty()
        item.findViewById<TextView>(R.id.releaseDateId).text = formatDate(release.publishedAt)

        val bodyView = item.findViewById<TextView>(R.id.releaseBodyId)
        markwon.setMarkdown(bodyView, release.body?.takeIf { it.isNotBlank() } ?: "")
        return item
    }

    private fun formatDate(iso: String?): String {
        if (iso == null) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val date = parser.parse(iso) ?: return ""
            DateUtils.formatDateTime(context, date.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
        } catch (e: ParseException) {
            ""
        }
    }
}
