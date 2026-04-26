package xyz.hvdw.mapplayer.ui.folder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.data.LibraryScanner
import xyz.hvdw.mapplayer.data.MusicRepository
import xyz.hvdw.mapplayer.model.FolderItem
import xyz.hvdw.mapplayer.model.Track
import xyz.hvdw.mapplayer.permissions.PermissionManager
import xyz.hvdw.mapplayer.ui.player.PlayerActivity
import xyz.hvdw.mapplayer.ui.settings.SettingsActivity

class FolderBrowserActivity : AppCompatActivity(),
    FolderAdapter.FolderClickListener,
    SongAdapter.SongClickListener {

    companion object {
        const val EXTRA_FOLDER_URI = "extra_folder_uri"
    }

    private lateinit var recyclerView: RecyclerView
    private var currentFolderUri: Uri? = null

    private lateinit var txtScanning: TextView
    private lateinit var progressScanning: ProgressBar

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_browser)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        txtScanning = findViewById(R.id.txtScanning)
        progressScanning = findViewById(R.id.progressScanning)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.setSaveEnabled(false)
        recyclerView.layoutManager = LinearLayoutManager(this)

        currentFolderUri = intent.getStringExtra(EXTRA_FOLDER_URI)?.let { Uri.parse(it) }

        setupGestureDetector()
        findViewById<View>(R.id.rootFolderBrowser).setOnTouchListener { _, event ->
            event?.let { gestureDetector.onTouchEvent(it) }
            false
        }

        if (!PermissionManager.hasAllPermissions(this)) {
            PermissionManager.requestPermissions(this)
            return
        }

        MusicRepository.ensureLibraryLoaded(this)

        if (!MusicRepository.isReady()) {
            txtScanning.visibility = View.VISIBLE
            progressScanning.visibility = View.VISIBLE
            recyclerView.adapter = null

            LibraryScanner.scanLibrary(this) {
                runOnUiThread {
                    txtScanning.visibility = View.GONE
                    progressScanning.visibility = View.GONE
                    loadContent(null)
                }
            }
        } else {
            loadContent(currentFolderUri)
        }
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
            }
        )
    }

    private fun loadContent(folderUri: Uri?) {
        Log.d("MapPlayer", "Loading folder: $folderUri")

        if (!MusicRepository.isReady()) {
            txtScanning.visibility = View.VISIBLE
            progressScanning.visibility = View.VISIBLE
            recyclerView.adapter = null
            return
        }

        txtScanning.visibility = View.GONE
        progressScanning.visibility = View.GONE

        val subfolders = MusicRepository.listSubfolders(this, folderUri)

        if (subfolders.isNotEmpty()) {
            recyclerView.adapter = FolderAdapter(subfolders, this)
            return
        }

        if (folderUri != null) {
            val tracks = MusicRepository.listTracksInFolder(
                this,
                folderUri
            ) { updatedTracks ->
                runOnUiThread {
                    val adapter = SongAdapter(updatedTracks, this)
                    adapter.setShuffle(false)
                    recyclerView.adapter = adapter
                }
            }

            val adapter = SongAdapter(tracks, this)
            adapter.setShuffle(false)
            recyclerView.adapter = adapter
            return
        }

        recyclerView.adapter = FolderAdapter(emptyList(), this)
    }

    override fun onFolderClick(folder: FolderItem) {
        currentFolderUri = folder.uri
        loadContent(folder.uri)
    }

    override fun onFolderLongClick(view: View, folder: FolderItem) {
        // Optional
    }

    override fun onFolderPlay(folder: FolderItem, shuffle: Boolean) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_FOLDER_URI, folder.uri.toString())
        intent.putExtra(PlayerActivity.EXTRA_START_INDEX, 0)
        intent.putExtra(PlayerActivity.EXTRA_SHUFFLE, shuffle)
        startActivity(intent)
    }

    override fun onSongClick(track: Track, position: Int, shuffle: Boolean) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_FOLDER_URI, currentFolderUri.toString())
        intent.putExtra(PlayerActivity.EXTRA_START_INDEX, position)
        intent.putExtra(PlayerActivity.EXTRA_SHUFFLE, shuffle)
        startActivity(intent)
    }

    override fun onBackPressed() {
        val parent = currentFolderUri?.let { MusicRepository.getParentFolderUri(it) }

        if (parent != null) {
            currentFolderUri = parent
            loadContent(parent)
        } else {
            super.onBackPressed()
        }
    }


}
