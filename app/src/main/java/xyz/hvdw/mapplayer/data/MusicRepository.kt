package xyz.hvdw.mapplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import xyz.hvdw.mapplayer.model.FolderItem
import xyz.hvdw.mapplayer.model.Track
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import xyz.hvdw.mapplayer.data.LibraryScanner.LibraryFolder
import xyz.hvdw.mapplayer.data.LibraryTrack

data class SearchEntry(
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val folderUri: String?,
    val playlistUri: String? = null
)

object MusicRepository {

    private val libraryLoaded = AtomicBoolean(false)

    var folders: List<LibraryScanner.LibraryFolder> = emptyList()
        private set
    var tracks: List<LibraryTrack> = emptyList()
        private set
    private val folderCache = mutableMapOf<String, List<Track>>()
    private var playlists: List<LibraryScanner.LibraryPlaylist> = emptyList()


    fun loadLibraryFromJson(context: Context) {
        val db = LibraryScanner.loadLibrary(context) ?: return
        folders = db.folders
        tracks = db.tracks
        playlists = db.playlists.orEmpty()
        folderCache.clear()
        libraryLoaded.set(true)
    }

    fun ensureLibraryLoaded(context: Context) {
        if (!libraryLoaded.get()) {
            loadLibraryFromJson(context)
        }
    }

    fun isReady(): Boolean = libraryLoaded.get()

    // ---------- Folder listing ----------

    fun listSubfolders(context: Context, folderUri: Uri?): List<FolderItem> {
        ensureLibraryLoaded(context)

        val parent = folderUri?.toString()
        val children = folders.filter { it.parentUri == parent }
        Log.d("MusicRepository", "listSubfolders parent=$parent -> ${children.size} children")

        return children
            .map { f ->
                //FolderItem(
                //    uri = Uri.parse(f.uri),
                //    name = f.name,
                //    coverArtUri = f.coverArtPath?.let { Uri.fromFile(File(it)) }
                //)
                val bmp: Bitmap? = f.coverArtPath?.let { path ->
                    try {
                        BitmapFactory.decodeFile(path)
                    } catch (e: Exception) {
                        Log.w("MusicRepository", "Failed to decode folder cover art: $path", e)
                        null
                    }
                }

                FolderItem(
                    uri = Uri.parse(f.uri),
                    name = f.name,
                    coverArtUri = f.coverArtPath?.let { Uri.fromFile(File(it)) },
                    thumbnail = bmp
                )

            }
            .sortedBy { it.name.lowercase() }
    }

    //Helper for Android Auto onLoadChildren
    fun listRootFolders(): List<LibraryScanner.LibraryFolder> {
        return folders.filter { it.parentUri == null }
    }

    fun listPlaylists(context: Context): List<FolderItem> {
        ensureLibraryLoaded(context)
        return playlists
            .sortedBy { it.name.lowercase() }
            .map { playlist ->
                val thumbnail = playlist.trackUris.firstNotNullOfOrNull { trackUri ->
                    tracks.firstOrNull { it.uri == trackUri }?.thumbnailPath
                }?.let { BitmapFactory.decodeFile(it) }
                FolderItem(
                    uri = Uri.parse(playlist.uri),
                    name = playlist.name,
                    coverArtUri = null,
                    thumbnail = thumbnail
                )
            }
    }

    fun isPlaylistUri(uri: Uri?): Boolean {
        val key = uri?.toString() ?: return false
        return playlists.any { it.uri == key }
    }


    // ---------- Track listing (instant from memory) ----------
    fun listTracksInFolder(
        context: Context,
        folderUri: Uri,
        callback: (List<Track>) -> Unit
    ): List<Track> {
        ensureLibraryLoaded(context)

        val key = folderUri.toString()
        val playlist = playlists.firstOrNull { it.uri == key }
        if (playlist != null) {
            return listTracksInPlaylist(playlist)
                .also {
                    folderCache[key] = it
                    callback(it)
                }
        }

        val list = tracks
            .filter { t: LibraryTrack -> t.folderUri == key }
            .sortedBy { t: LibraryTrack ->
                // Sort by filename, not metadata title
                Uri.parse(t.uri).lastPathSegment?.lowercase() ?: ""
            }
            .map { t: LibraryTrack ->
                val bmp = t.thumbnailPath?.let { path ->
                    BitmapFactory.decodeFile(path)
                }

                Track(
                    //uri = Uri.parse(t.uri),
                    uri = Uri.fromFile(File(t.path)),
                    title = t.title ?: "",
                    artist = t.artist,
                    album = t.album,
                    duration = t.duration,
                    albumArt = bmp
                )
            }

        // Cache the final list
        folderCache[key] = list

        callback(list)
        return list
    }

    private fun listTracksInPlaylist(playlist: LibraryScanner.LibraryPlaylist): List<Track> {
        val tracksByUri = tracks.associateBy { it.uri }
        return playlist.trackUris.mapNotNull { trackUri ->
            tracksByUri[trackUri]?.let { track ->
                Track(
                    uri = Uri.fromFile(File(track.path)),
                    title = track.title ?: "",
                    artist = track.artist,
                    album = track.album,
                    duration = track.duration,
                    albumArt = track.thumbnailPath?.let(BitmapFactory::decodeFile)
                )
            }
        }
    }

    fun getCachedTracksInFolder(folderUri: Uri): List<Track>? {
        return folderCache[folderUri.toString()]
    }


    fun getTrackCount(context: Context, folderUri: Uri): Int {
        ensureLibraryLoaded(context)
        val key = folderUri.toString()
        playlists.firstOrNull { it.uri == key }?.let { return it.trackUris.size }
        return tracks.count { t: LibraryTrack -> t.folderUri == key }
    }

    // ---------- Folder thumbnail (cover.jpg only) ----------

    fun getFolderThumbnail(context: Context, folderUri: Uri): Bitmap? {
        ensureLibraryLoaded(context)
        val key = folderUri.toString()
        playlists.firstOrNull { it.uri == key }?.let { playlist ->
            val thumbnailPath = playlist.trackUris.asSequence()
                .mapNotNull { uri -> tracks.firstOrNull { it.uri == uri }?.thumbnailPath }
                .firstOrNull()
            return thumbnailPath?.let { BitmapFactory.decodeFile(it) }
        }
        val folder = folders.firstOrNull { it.uri == key } ?: return null
        val path = folder.coverArtPath ?: return null

        return try {
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) {
            null
        }
    }

    // ---------- Embedded album art for PlayerActivity ----------

    fun loadEmbeddedAlbumArt(context: Context, uri: Uri): Bitmap? {
        return try {
            val mmr = MediaMetadataRetriever()
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                pfd.use { mmr.setDataSource(it.fileDescriptor) }
                val art = mmr.embeddedPicture
                mmr.release()
                if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
            } else {
                mmr.release()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getParentFolderUri(uri: Uri): Uri? {
        val key = uri.toString()
        val folder = folders.firstOrNull { it.uri == key } ?: return null
        return folder.parentUri?.let { Uri.parse(it) }
    }

    fun getAllTracks(): List<Track> {
        return tracks.map { t ->
            Track(
                //uri = Uri.parse(t.uri),
                uri = Uri.fromFile(File(t.path)),
                title = t.title ?: "",
                artist = t.artist,
                album = t.album,
                duration = t.duration,
                albumArt = t.thumbnailPath?.let { path ->
                    BitmapFactory.decodeFile(path)
                }
            )
        }
    }

    fun getAllTracksForSearch(): List<SearchEntry> {
        return tracks.map { t ->
            SearchEntry(
                uri = Uri.fromFile(File(t.path)).toString(),
                        title = t.title ?: "",
                        artist = t.artist,
                        album = t.album,
                        folderUri = t.folderUri
            )
        }
    }

    fun getAllPlaylistTracksForSearch(): List<SearchEntry> {
        val tracksByUri = tracks.associateBy { it.uri }
        val seenTrackUris = mutableSetOf<String>()
        return playlists.flatMap { playlist ->
            playlist.trackUris.mapNotNull { trackUri ->
                val track = tracksByUri[trackUri] ?: return@mapNotNull null
                if (!seenTrackUris.add(trackUri)) return@mapNotNull null
                SearchEntry(
                    uri = Uri.fromFile(File(track.path)).toString(),
                    title = track.title ?: "",
                    artist = track.artist,
                    album = track.album,
                    folderUri = track.folderUri,
                    playlistUri = playlist.uri
                )
            }
        }
    }

    fun getTrackByUri(uri: String): Track? {
        return getAllTracks().firstOrNull { it.uri.toString() == uri }
    }

    fun getFolderUriOfTrack(uri: Uri): Uri? {
        val key = uri.toString()
        val t = tracks.firstOrNull { it.uri == key } ?: return null
        return t.folderUri?.let { Uri.parse(it) }
    }

    fun getTracksInFolder(folderUri: Uri): List<Track> {
        val key = folderUri.toString()
        return tracks
            .filter { it.folderUri == key }
            .sortedBy { Uri.parse(it.uri).lastPathSegment?.lowercase() ?: "" }
            .map { t ->
                Track(
                    //uri = Uri.parse(t.uri),
                    uri = Uri.fromFile(File(t.path)),
                    title = t.title ?: "",
                    artist = t.artist,
                    album = t.album,
                    duration = t.duration,
                    albumArt = null
                )
            }
    }

    fun getIndexOfTrackInFolder(folderUri: Uri, trackUri: Uri): Int {
        val key = folderUri.toString()
        val trackKey = trackUri.toString()

        val list = tracks
            .filter { it.folderUri == key }
            .sortedBy { Uri.parse(it.uri).lastPathSegment?.lowercase() ?: "" }

        return list.indexOfFirst { it.uri == trackKey }
    }

}
