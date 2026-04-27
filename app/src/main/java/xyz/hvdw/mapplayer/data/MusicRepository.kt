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

object MusicRepository {

    private val libraryLoaded = AtomicBoolean(false)

    private var folders: List<LibraryScanner.LibraryFolder> = emptyList()
    private var tracks: List<LibraryScanner.LibraryTrack> = emptyList()

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
            .filter { it.folderUri == key }
            .sortedBy { it.title.lowercase() }
            .map { t ->
                val trackUri = Uri.parse(t.uri)

                // Load embedded art thumbnail for each track
                val art = loadEmbeddedAlbumArtThumbnail(context, trackUri)

                Track(
                    uri = trackUri,
                    title = t.title,
                    artist = t.artist,
                    album = t.album,
                    albumArt = art
                )
            }

        callback(list)
        return list
    }


    fun getTrackCount(context: Context, folderUri: Uri): Int {
        ensureLibraryLoaded(context)
        val key = folderUri.toString()
        return tracks.count { it.folderUri == key }
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

    private fun loadEmbeddedAlbumArtThumbnail(context: Context, uri: Uri): Bitmap? {
        val full = loadEmbeddedAlbumArt(context, uri) ?: return null
        return Bitmap.createScaledBitmap(full, 256, 256, true)
    }


}
