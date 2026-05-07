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
import xyz.hvdw.mapplayer.data.LibraryTrack

data class SearchEntry(
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val folderUri: String?
)

object MusicRepository {

    private val libraryLoaded = AtomicBoolean(false)

    private var folders: List<LibraryScanner.LibraryFolder> = emptyList()
    private var tracks: List<LibraryTrack> = emptyList()
    private val folderCache = mutableMapOf<String, List<Track>>()


    fun loadLibraryFromJson(context: Context) {
        val db = LibraryScanner.loadLibrary(context) ?: return
        folders = db.folders
        tracks = db.tracks
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
                FolderItem(
                    uri = Uri.parse(f.uri),
                    name = f.name,
                    coverArtUri = f.coverArtPath?.let { Uri.fromFile(File(it)) }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // ---------- Track listing (instant from memory) ----------
    fun listTracksInFolder(
        context: Context,
        folderUri: Uri,
        callback: (List<Track>) -> Unit
    ): List<Track> {
        ensureLibraryLoaded(context)

        val key = folderUri.toString()

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
                    uri = Uri.parse(t.uri),
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

    fun getCachedTracksInFolder(folderUri: Uri): List<Track>? {
        return folderCache[folderUri.toString()]
    }


    fun getTrackCount(context: Context, folderUri: Uri): Int {
        ensureLibraryLoaded(context)
        val key = folderUri.toString()
        return tracks.count { t: LibraryTrack -> t.folderUri == key }
    }

    // ---------- Folder thumbnail (cover.jpg only) ----------

    fun getFolderThumbnail(context: Context, folderUri: Uri): Bitmap? {
        ensureLibraryLoaded(context)
        val key = folderUri.toString()
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
                uri = Uri.parse(t.uri),
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
                uri = t.uri,
                title = t.title ?: "",
                artist = t.artist,
                album = t.album,
                folderUri = t.folderUri
            )
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
                    uri = Uri.parse(t.uri),
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
