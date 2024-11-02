package com.droid2developers.liveslider.views.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.droid2developers.liveslider.R
import com.droid2developers.liveslider.adapters.WallpapersListAdapter
import com.droid2developers.liveslider.database.models.LocalWallpaper
import com.droid2developers.liveslider.utils.Constant
import com.droid2developers.liveslider.viewmodel.PlaylistViewModel
import com.droid2developers.liveslider.viewmodel.WallpaperViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class SingleFragment : Fragment(), OnSharedPreferenceChangeListener {

    companion object {
        private val TAG: String = SingleFragment::class.java.simpleName
    }

    private var editor: SharedPreferences.Editor? = null
    private var prefs: SharedPreferences? = null
    private var mRecyclerView: RecyclerView? = null
    private var listAdapter: WallpapersListAdapter? = null
    private var defaultWallpaper: LocalWallpaper? = null
    private var mFabButton: FloatingActionButton? = null

    private var progressIndicator: LinearProgressIndicator? = null
    private var wallpaperViewModel: WallpaperViewModel? = null
    private var playlistViewModel: PlaylistViewModel? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_single, container, false)

        // View initializations
        mRecyclerView = view.findViewById(R.id.singleRecyclerId)
        mFabButton = view.findViewById(R.id.addWallpaperId)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        progressIndicator?.hide()

        // Creating default wallpaper data
        val localPath = Constant.DEFAULT_LOCAL_PATH
        defaultWallpaper = LocalWallpaper(
            Constant.DEFAULT, Constant.DEFAULT_WALLPAPER_NAME,
            localPath, localPath
        )

        if (activity != null) {
            wallpaperViewModel = ViewModelProvider(requireActivity())[WallpaperViewModel::class.java]
            playlistViewModel = ViewModelProvider(requireActivity())[PlaylistViewModel::class.java]
        }

        return view
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.rootView.setBackgroundColor(Color.argb(153, 35, 35, 35))

        initRv()

        mFabButton?.setOnClickListener { v: View? ->
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                Log.d(TAG, "onActivityResult: getData() = $uri")
                saveSingleTask(uri)
            }
        }
    }


    private fun initRv() {
        val gridSize =
            (if ((resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)) 2 else 4)
        mRecyclerView?.layoutManager = GridLayoutManager(requireContext(), gridSize)
        listAdapter = WallpapersListAdapter(
            requireContext(),
            prefs?.getString("local_wallpaper_path", Constant.DEFAULT_LOCAL_PATH)
        )
        mRecyclerView?.adapter = listAdapter
        mRecyclerView?.itemAnimator = DefaultItemAnimator()
        listAdapter?.addWallpaper(defaultWallpaper)
        listAdapter?.setOnItemClickListener { position: Int ->
            val wallpaper = listAdapter!!.itemList[position]
            val localWallpaperPath =
                prefs?.getString("local_wallpaper_path", Constant.DEFAULT_LOCAL_PATH)
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
                .setPositiveButton("Confirm") { dialog: DialogInterface, which: Int ->
                    // Continue with operation
                    if (wallpaper.name == Constant.DEFAULT_WALLPAPER_NAME) {
                        Toast.makeText(
                            requireContext(), "This is the default wallpaper and can't be deleted!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        if (wallpaper.name == localWallpaperPath) {
                            Toast.makeText(
                                requireContext(), "You cannot delete current Wallpaper",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            wallpaperViewModel?.delete(wallpaper)
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
                    Log.d(TAG, "onClick: Cancelled Delete!")
                    dialog.dismiss()
                }
                .create()
                .show()
        }

        wallpaperViewModel?.allWallpapers?.observe(viewLifecycleOwner) { localWallpapers: List<LocalWallpaper?> ->
            Log.d(TAG, "onChanged: " + localWallpapers.size)
            if (listAdapter?.itemCount!! > 0) {
                listAdapter?.clearList()
                listAdapter?.addWallpaper(defaultWallpaper)
                listAdapter?.addWallpapers(localWallpapers)
            } else {
                if (!listAdapter?.itemList?.contains(defaultWallpaper)!!) {
                    listAdapter?.addWallpaper(defaultWallpaper)
                }
                listAdapter?.addWallpapers(localWallpapers)
            }
        }
    }


    private fun openUri(uri: Uri?): InputStream? {
        if (uri == null) return null
        try {
            return requireContext().contentResolver?.openInputStream(uri)
        } catch (e: Exception) {
            e.fillInStackTrace()
            return null
        }
    }


    private suspend fun saveSingleTask(contentURI: Uri?) = withContext(Dispatchers.IO) {
        val inputStream = openUri(contentURI)
        if (inputStream != null && contentURI != null)  {
            try {
                withContext(Dispatchers.Main) {
                    progressIndicator?.show()
                }
                val wallpaperName = Constant.HEADER + System.currentTimeMillis() + Constant.PNG
                val localPath = requireContext().getExternalFilesDir(null).toString() + File.separator + wallpaperName
                val localWallpaper = LocalWallpaper(
                    Constant.CUSTOM,
                    wallpaperName,
                    localPath,
                    contentURI.toString()
                )

                FileOutputStream(localPath).use { fos ->
                    // Saving file to: $localPath
                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    // Reading and writing in chunks
                    while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                        fos.write(buffer, 0, bytesRead)
                    }

                    // Insert into ViewModel once the file is saved
                    wallpaperViewModel?.insert(localWallpaper)

                    // Flush and close the output stream
                    fos.flush()
                }

                // Close input stream
                inputStream.close()
            } catch (e: IOException) {
                e.fillInStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressIndicator?.hide()
                }
            }
        } else {
            Log.e(TAG, "Input stream is null. Could not open URI: $contentURI")
        }
    }


    @SuppressLint("CommitPrefEdits")
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context)
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
            listAdapter?.updateLocalWallpaper()
            listAdapter?.notifyDataSetChanged()
        }
    }
}
