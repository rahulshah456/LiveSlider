package com.droid2developers.liveslider.views.fragments

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.adapters.PlaylistAdapter
import com.droid2developers.liveslider.database.models.LocalWallpaper
import com.droid2developers.liveslider.database.models.Playlist
import com.droid2developers.liveslider.database.repository.PlaylistRepository
import com.droid2developers.liveslider.database.repository.WallpaperRepository
import com.droid2developers.liveslider.utils.Constant
import com.droid2developers.liveslider.utils.SpacingItemDecoration
import com.droid2developers.liveslider.utils.processPlaylistWorker
import com.droid2developers.liveslider.viewmodel.PlaylistViewModel
import com.droid2developers.liveslider.viewmodel.WallpaperViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

class SlideshowFragment : Fragment(), OnSharedPreferenceChangeListener {

    companion object {
        private val TAG: String = SlideshowFragment::class.java.simpleName

        fun newInstance(isLockMode: Boolean): SlideshowFragment {
            val fragment = SlideshowFragment()
            val args = Bundle()
            args.putBoolean(Constant.EXTRA_IS_LOCK_MODE, isLockMode)
            fragment.arguments = args
            return fragment
        }
    }

    private var isLockMode = false
    private var editor: SharedPreferences.Editor? = null
    private var prefs: SharedPreferences? = null
    private var listAdapter: PlaylistAdapter? = null
    private var mRecyclerView: RecyclerView? = null
    private var mFabButton: FloatingActionButton? = null

    //private PlayListAdapter listAdapter;
    private var showProgress = MutableLiveData(false)
    private var workManager: WorkManager? = null
    private var wallpaperViewModel: WallpaperViewModel? = null
    private var playlistViewModel: PlaylistViewModel? = null
    private var pendingAddImagesPlaylistId: String? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_slideshow, container, false)

        isLockMode = arguments?.getBoolean(Constant.EXTRA_IS_LOCK_MODE) ?: false

        // View initializations
        workManager = WorkManager.getInstance(requireContext())
        mRecyclerView = view.findViewById(R.id.slideshowRecyclerId)
        mFabButton = view.findViewById(R.id.addPlaylistId)

        if (activity != null) {
            wallpaperViewModel =
                ViewModelProvider(requireActivity())[WallpaperViewModel::class.java]
            playlistViewModel = ViewModelProvider(requireActivity())[PlaylistViewModel::class.java]
        }
        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.rootView.setBackgroundColor(Color.argb(153, 35, 35, 35))

        initRv()

        mFabButton?.setOnClickListener {
            pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // In your Activity or Fragment
        showProgress.observe(viewLifecycleOwner) { isProgressVisible ->
            if (isProgressVisible) {
                // Show progress indicator (e.g., ProgressBar)
                Log.d(TAG, "onCreateView: inProgress")
            } else {
                // Hide progress indicator
                Log.d(TAG, "onCreateView: Completed")
            }
        }
    }


    private val pickMultipleMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(99)) { uris ->
            if (uris.isNotEmpty()) {
                showProgress.value = true
                lifecycleScope.launch {
                    try {
                        createPlaylist(uris)
                    } catch (e: Exception) {
                        Log.d(TAG, "Error: ", e)
                    } finally {
                        delay(1000)
                        showProgress.value = false
                    }
                }
            }
        }

    private val pickMoreImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(99)) { uris ->
            val playlistId = pendingAddImagesPlaylistId
            pendingAddImagesPlaylistId = null
            if (uris.isNotEmpty() && playlistId != null) {
                showProgress.value = true
                lifecycleScope.launch {
                    try {
                        addImagesToPlaylist(playlistId, uris)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding images to playlist $playlistId", e)
                    } finally {
                        delay(1000)
                        showProgress.value = false
                    }
                }
            }
        }


    private fun initRv() {
        val gridSize =
            (if ((resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)) 1 else 2)
        mRecyclerView?.layoutManager = GridLayoutManager(requireContext(), gridSize)

        // Add 12dp spacing between items
        val spacingInPixels = resources.getDimensionPixelSize(R.dimen.item_spacing_12dp)
        mRecyclerView?.addItemDecoration(SpacingItemDecoration(spacingInPixels))

        listAdapter =
            PlaylistAdapter(
                requireContext(),
                prefs?.getString("current_playlist", Constant.PLAYLIST_NONE),
                isLockMode
            )
        mRecyclerView?.adapter = listAdapter
        mRecyclerView?.itemAnimator = DefaultItemAnimator()
        listAdapter?.setOnItemClickListener(object : PlaylistAdapter.OnItemClickListener {
            override fun OnItemLongClick(position: Int) {
                confirmDeletePlaylist(listAdapter?.itemList?.get(position))
            }

            override fun onItemClick(position: Int) {
                val playlist = listAdapter?.itemList?.get(position) ?: return
                val sheet = PlaylistActionsBottomSheet.newInstance(
                    playlist.playlistId!!, playlist.name, playlist.isProcessed
                )
                sheet.listener = object : PlaylistActionsBottomSheet.Listener {
                    override fun onActivate(playlistId: String) {
                        listAdapter?.activatePlaylist(playlist)
                    }

                    override fun onReprocess(playlistId: String) {
                        reprocessPlaylist(playlistId, playlist.name)
                    }

                    override fun onAddImages(playlistId: String) {
                        pendingAddImagesPlaylistId = playlistId
                        pickMoreImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }

                    override fun onDelete(playlistId: String) {
                        confirmDeletePlaylist(playlist)
                    }
                }
                sheet.show(childFragmentManager, PlaylistActionsBottomSheet.TAG)
            }
        })

        playlistViewModel?.allPlaylists?.observe(viewLifecycleOwner) { playlists: List<Playlist?>? ->
            Log.d(TAG, "onChanged: " + playlists?.size)
            if (listAdapter?.itemCount!! > 0) {
                listAdapter?.clearList()
            }
            listAdapter?.addPlaylists(playlists)

            playlists?.forEach { playlist ->
                val id = playlist?.playlistId ?: return@forEach
                wallpaperViewModel?.getProcessedCount(id)?.observe(viewLifecycleOwner) { processed ->
                    listAdapter?.setProcessedCount(id, processed ?: 0, playlist.size)
                }
            }
        }
    }

    private fun createPlaylist(clipData: List<@JvmSuppressWildcards Uri>) {

        val playlistRepository = PlaylistRepository(requireContext())
        val wallpaperRepository = WallpaperRepository(requireContext())

        val playlistId = System.currentTimeMillis().toString()
        val name = "Playlist $playlistId"

        for (contentURI in clipData) {

            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(contentURI, flag)
            Log.d(TAG, "PhotoPicker URL :$contentURI")

            val wallpaperName =
                Constant.HEADER + System.currentTimeMillis() + Constant.PNG
            val localWallpaper = LocalWallpaper(
                playlistId,
                wallpaperName, null, contentURI.toString()
            )
            wallpaperRepository.insert(localWallpaper)
        }

        val playlist = Playlist(
            playlistId, name, null,
            Date(), Date(), clipData.size, false
        )
        playlistRepository.insert(playlist)


        val processWorkRequest: OneTimeWorkRequest =
            processPlaylistWorker(playlistId, name)
        workManager?.enqueueUniqueWork(
            playlistId,
            ExistingWorkPolicy.REPLACE, processWorkRequest
        )
    }

    private suspend fun addImagesToPlaylist(playlistId: String, clipData: List<Uri>) {
        val playlistRepository = PlaylistRepository(requireContext())
        val wallpaperRepository = WallpaperRepository(requireContext())

        for (contentURI in clipData) {
            val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(contentURI, flag)

            val wallpaperName = Constant.HEADER + System.currentTimeMillis() + Constant.PNG
            val localWallpaper = LocalWallpaper(
                playlistId,
                wallpaperName, null, contentURI.toString()
            )
            wallpaperRepository.insert(localWallpaper)
        }

        val playlist = playlistRepository.getPlaylist(playlistId) ?: return
        playlist.size += clipData.size
        playlist.isProcessed = false
        playlistRepository.update(playlist)

        reprocessPlaylist(playlistId, playlist.name)
    }

    private fun reprocessPlaylist(playlistId: String, name: String?) {
        val processWorkRequest: OneTimeWorkRequest =
            processPlaylistWorker(playlistId, name ?: playlistId)
        workManager?.enqueueUniqueWork(
            playlistId,
            ExistingWorkPolicy.REPLACE, processWorkRequest
        )
    }

    private fun confirmDeletePlaylist(playlist: Playlist?) {
        playlist ?: return
        val currentPlaylist = prefs?.getString("current_playlist", Constant.PLAYLIST_NONE)
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(
                ResourcesCompat.getDrawable(
                    requireContext().resources,
                    R.drawable.delete_icon,
                    null
                )
            )
            .setTitle("Delete?")
            .setMessage("Are you sure you want to delete this wallpaper...")
            .setCancelable(false)
            .setPositiveButton("Confirm") { dialog: DialogInterface, _: Int ->
                if (playlist.playlistId == currentPlaylist) {
                    Toast.makeText(
                        requireContext(), "You cannot delete current activated playlist!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    playlistViewModel?.delete(playlist)
                    wallpaperViewModel?.deletePlaylistWallpapers(playlist.playlistId)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog: DialogInterface, _: Int ->
                Log.d(TAG, "onClick: Cancelled Delete!")
                dialog.dismiss()
            }
            .create()
            .show()
    }

    override fun onResume() {
        super.onResume()
    }

    @SuppressLint("CommitPrefEdits")
    override fun onAttach(context: Context) {
        super.onAttach(context)
        isLockMode = arguments?.getBoolean(Constant.EXTRA_IS_LOCK_MODE) ?: false
        if (prefs == null) {
            prefs = if (isLockMode) {
                context.getSharedPreferences(Constant.PREFS_LOCK, Context.MODE_PRIVATE)
            } else {
                PreferenceManager.getDefaultSharedPreferences(context)
            }
            editor = prefs?.edit()
        }
        prefs?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        Log.d(TAG, "onSharedPreferenceChanged: $key")
        if (key == "type") {
            Log.d(TAG, "onSharedPreferenceChanged: notifyDataSetChanged!")
            listAdapter?.updatePlaylist()
            listAdapter?.notifyDataSetChanged()
        }
    }
}
