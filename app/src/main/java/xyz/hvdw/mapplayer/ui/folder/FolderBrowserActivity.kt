package xyz.hvdw.mapplayer.ui.folder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.data.MusicRepository
import xyz.hvdw.mapplayer.model.FolderItem
import xyz.hvdw.mapplayer.model.Track
import xyz.hvdw.mapplayer.permissions.PermissionManager
import xyz.hvdw.mapplayer.ui.player.PlayerActivity
import xyz.hvdw.mapplayer.ui.settings.SettingsActivity
import xyz.hvdw.mapplayer.data.SearchEntry

class FolderBrowserActivity : AppCompatActivity(),
    FolderAdapter.FolderClickListener,
    SongAdapter.SongClickListener,
    FolderGalleryAdapter.FolderClickListener {

    companion object {
        const val EXTRA_FOLDER_URI = "extra_folder_uri"
        private const val PREF_GALLERY_VIEW = "pref_gallery_view"
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

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.setSaveEnabled(false)

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

        if (initialized) return

        if (!PermissionManager.hasAllPermissions(this)) {
            PermissionManager.requestPermissions(this)
            return
        }

        initializeBrowser()
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

        findViewById<View>(R.id.rootFolderBrowser).setOnTouchListener { _, event ->
            event?.let { gestureDetector.onTouchEvent(it) }
            false
        }
    }

    private fun isGalleryMode(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(PREF_GALLERY_VIEW, false)
    }

    private fun autoSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        // Roughly 120dp per tile → adjust as you like
        return (screenWidthDp / 120f).toInt().coerceAtLeast(2)
    }

    private fun loadContent(folderUri: Uri?) {
        Log.d("MapPlayer", "Loading folder: $folderUri")

        if (!MusicRepository.isReady()) {
            recyclerView.adapter = null
            return
        }

        val subfolders = MusicRepository.listSubfolders(this, folderUri)

        if (subfolders.isNotEmpty()) {
            if (isGalleryMode()) {
                recyclerView.layoutManager = GridLayoutManager(this, autoSpanCount())
                recyclerView.adapter = FolderGalleryAdapter(subfolders, this)
            } else {
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = FolderAdapter(subfolders, this)
            }
            return
        }

        if (folderUri != null) {
            MusicRepository.listTracksInFolder(
                this,
                folderUri
            ) { updatedTracks ->
                runOnUiThread {
                    recyclerView.layoutManager = LinearLayoutManager(this)
                    val adapter = SongAdapter(updatedTracks, this)
                    adapter.setShuffle(false)
                    recyclerView.adapter = adapter
                }
            }
            return
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = FolderAdapter(emptyList(), this)
    }

    private fun reloadContent() {
        loadContent(currentFolderUri)
    }

    // ---------- Folder clicks ----------

    override fun onFolderClick(folder: FolderItem) {
        currentFolderUri = folder.uri
        loadContent(folder.uri)
    }

    override fun onFolderLongClick(view: View, folder: FolderItem) {
        showFolderPopupMenu(view, folder)
    }

    override fun onFolderPlay(folder: FolderItem, shuffle: Boolean) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_FOLDER_URI, folder.uri.toString())
        intent.putExtra(PlayerActivity.EXTRA_START_INDEX, 0)
        intent.putExtra(PlayerActivity.EXTRA_SHUFFLE, shuffle)
        startActivity(intent)
    }

    // Gallery adapter callbacks
    /*override fun onPlay(folder: FolderItem) {
        onFolderPlay(folder, shuffle = false)
    }

    override fun onShuffle(folder: FolderItem) {
        onFolderPlay(folder, shuffle = true)
    }*/

    override fun onOpen(folder: FolderItem) {
        onFolderClick(folder)
    }

    override fun onLongPress(view: View, folder: FolderItem) {
        showFolderPopupMenu(view, folder)
    }

    private fun showFolderPopupMenu(anchor: View, folder: FolderItem) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_folder, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_play -> {
                    onFolderPlay(folder, shuffle = false)
                    true
                }
                R.id.action_play_random -> {
                    onFolderPlay(folder, shuffle = true)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }


    // ---------- Song clicks ----------

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

        currentFolderUri = intent.getStringExtra(EXTRA_FOLDER_URI)?.let { Uri.parse(it) }
            ?: run {
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                )
                Uri.parse(musicDir.toURI().toString())
            }

        setupGestureDetector()

        MusicRepository.ensureLibraryLoaded(this)

        if (!MusicRepository.isReady()) {
            startActivity(Intent(this, SettingsActivity::class.java))
            LocalBroadcastManager.getInstance(this).registerReceiver(
                libraryUpdatedReceiver,
                IntentFilter("ACTION_LIBRARY_UPDATED")
            )
        } else {
            loadContent(currentFolderUri)
        }
    }

    // ---------- Search ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView

        searchView?.queryHint = getString(R.string.search_hint)

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val q = newText?.trim().orEmpty()

                if (q.isEmpty()) {
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
            inSearchMode = false
            loadContent(currentFolderUri)
            false
        }
        return true
    }


    private fun performSearch(query: String) {
        val q = query.lowercase()

        val results = MusicRepository.getAllTracksForSearch().filter { t ->
            t.title.lowercase().contains(q) ||
            (t.artist ?: "").lowercase().contains(q) ||
            (t.album ?: "").lowercase().contains(q)
        }

        inSearchMode = true

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = SearchResultsAdapter(results) { entry ->
            openPlayerFromSearchEntry(entry)
        }
    }

    class SearchResultsAdapter(
        private val tracks: List<SearchEntry>,
        private val onClick: (SearchEntry) -> Unit
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
            holder.artist.text = t.artist ?: ""
        }

        override fun getItemCount() = tracks.size
    }

    private fun openPlayerFromSearchEntry(entry: SearchEntry) {
        val folderUri = entry.folderUri?.let { Uri.parse(it) }
            ?: currentFolderUri
            ?: return

        val cached = MusicRepository.getCachedTracksInFolder(folderUri)
        if (cached != null) {
            val index = cached.indexOfFirst { it.uri.toString() == entry.uri }
            if (index != -1) {
                openPlayerWithCachedList(folderUri, cached, index)
                return
            }
        }

        MusicRepository.listTracksInFolder(this, folderUri) { tracks ->
            if (tracks.isEmpty()) return@listTracksInFolder

            val index = tracks.indexOfFirst { it.uri.toString() == entry.uri }
            if (index == -1) return@listTracksInFolder

            runOnUiThread {
                openPlayerWithCachedList(folderUri, tracks, index)
            }
        }
    }

    private fun openPlayerWithCachedList(folderUri: Uri, tracks: List<Track>, index: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_FOLDER_URI, folderUri.toString())
        intent.putExtra(PlayerActivity.EXTRA_START_INDEX, index)
        intent.putExtra(PlayerActivity.EXTRA_SHUFFLE, false)
        startActivity(intent)
    }
}
