package xyz.hvdw.mapplayer.ui.folder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
    private var initialized = false
    private var searchView: SearchView? = null
    private var inSearchMode: Boolean = false

    private lateinit var gestureDetector: GestureDetector

    // Broadcast receiver to refresh UI when library updates
    private val libraryUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MapPlayer", "Library updated → refreshing folder browser")
            reloadContent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_browser)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        /* toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_search -> {
                    // IMPORTANT: allow SearchView to expand
                    false
                }
                else -> false
            }
        } */


        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.setSaveEnabled(false)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (!PermissionManager.hasAllPermissions(this)) {
            PermissionManager.requestPermissions(this)
            return
        }


        // Listen for library updates
        LocalBroadcastManager.getInstance(this).registerReceiver(
            libraryUpdatedReceiver,
            IntentFilter("ACTION_LIBRARY_UPDATED")
        )

    }

    override fun onResume() {
        super.onResume()

        // If we were in search mode before going to PlayerActivity,
        // restore the normal folder/song view.
        if (inSearchMode) {
            inSearchMode = false

            if (currentFolderUri == null) {
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                )
                currentFolderUri = Uri.parse(musicDir.toURI().toString())
            }

            loadContent(currentFolderUri)
            return
        }

        // No double initialization
        if (initialized) return

        if (!PermissionManager.hasAllPermissions(this)) {
            PermissionManager.requestPermissions(this)
            return
        }

        initializeBrowser()   // <-- pas nu mag je scannen / laden
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
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
            recyclerView.adapter = null
            return
        }

        val subfolders = MusicRepository.listSubfolders(this, folderUri)

        if (subfolders.isNotEmpty()) {
            recyclerView.adapter = FolderAdapter(subfolders, this)
            return
        }

        if (folderUri != null) {
            MusicRepository.listTracksInFolder(
                this,
                folderUri
            ) { updatedTracks ->
                runOnUiThread {
                    val adapter = SongAdapter(updatedTracks, this)
                    adapter.setShuffle(false)
                    recyclerView.adapter = adapter
                }
            }

            return
        }

        recyclerView.adapter = FolderAdapter(emptyList(), this)
    }

    // Reload current folder after library update
    private fun reloadContent() {
        loadContent(currentFolderUri)
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

    override fun onDestroy() {
        super.onDestroy()
        // Clean up receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(libraryUpdatedReceiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (PermissionManager.hasAllPermissions(this)) {
            initializeBrowser()
        }
    }


    private fun initializeBrowser() {
         if (initialized) return
         initialized = true

        // If no folder was passed, default to the Music directory
        currentFolderUri = intent.getStringExtra(EXTRA_FOLDER_URI)?.let { Uri.parse(it) }
            ?: run {
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                )
                musicDir.toURI().toString().let { Uri.parse(it) }
            }

        setupGestureDetector()
        findViewById<View>(R.id.rootFolderBrowser).setOnTouchListener { _, event ->
            event?.let { gestureDetector.onTouchEvent(it) }
            false
        }

        MusicRepository.ensureLibraryLoaded(this)

        if (!MusicRepository.isReady()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
            // Listen for library updates
            LocalBroadcastManager.getInstance(this).registerReceiver(
                libraryUpdatedReceiver,
                IntentFilter("ACTION_LIBRARY_UPDATED")
            )
        } else {
            loadContent(currentFolderUri)
        }
    }


    // ---------- Search options ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView

        searchView?.queryHint = getString(R.string.search_hint)

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Ignore submit to prevent double search
                //if (!query.isNullOrEmpty()) performSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val q = newText?.trim().orEmpty()

                if (q.isEmpty()) {
                    // Query cleared → leave search mode and show folder again
                    inSearchMode = false
                    loadContent(currentFolderUri)
                    return true
                }

                inSearchMode = true
                performSearch(q)
                return true
            }

        })

        searchView?.setOnCloseListener {
            // User pressed back or closed search manually
            inSearchMode = false
            loadContent(currentFolderUri)
            false
        }
        return true
    }

    private fun performSearch(query: String) {
        val q = query.lowercase()

        val results = MusicRepository.getAllTracks().filter { track ->
            track.title.lowercase().contains(q) ||
            (track.artist ?: "").lowercase().contains(q) ||
            (track.album ?: "").lowercase().contains(q)
        }

        inSearchMode = true

        recyclerView.adapter = SearchResultsAdapter(results) { clickedTrack ->
            openPlayerFromSearch(clickedTrack)
        }
    }

    class SearchResultsAdapter(
        private val tracks: List<Track>,
        private val onClick: (Track) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.txtTitle)
            val artist: TextView = view.findViewById(R.id.txtArtist)

            init {
                view.setOnClickListener {
                    onClick(tracks[adapterPosition])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val t = tracks[position]
            holder.title.text = t.title
            holder.artist.text = t.artist
        }

        override fun getItemCount() = tracks.size
    }

    private fun openPlayer(track: Track, results: List<Track>) {
        val index = results.indexOfFirst { it.uri == track.uri }
        if (index == -1) return

        val intent = Intent(this, PlayerActivity::class.java)

        intent.putExtra(PlayerActivity.EXTRA_START_INDEX, index)
        intent.putExtra(PlayerActivity.EXTRA_SHUFFLE, false)
        startActivity(intent)
    }

    private fun openPlayerFromSearch(track: Track) {
        val folderUri = MusicRepository.getFolderUriOfTrack(track.uri)
            ?: currentFolderUri   // fallback to the folder you were in
            ?: return


        val index = MusicRepository.getIndexOfTrackInFolder(folderUri, track.uri)
        if (index == -1) return

        /*searchView?.setQuery("", false)
        searchView?.clearFocus()
        searchView?.onActionViewCollapsed() */

        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_FOLDER_URI, folderUri.toString())
        intent.putExtra(PlayerActivity.EXTRA_START_INDEX, index)
        intent.putExtra(PlayerActivity.EXTRA_SHUFFLE, false)
        startActivity(intent)
    }

}
