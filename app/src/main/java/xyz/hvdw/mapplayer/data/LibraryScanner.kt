package xyz.hvdw.mapplayer.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import java.io.File

object LibraryScanner {

    private const val TAG = "LibraryScanner"
    private const val LIBRARY_FILE = "library.json"

    data class LibraryFolder(
        val uri: String,
        val name: String,
        val parentUri: String?,
        val coverArtPath: String?
    )

    data class LibraryTrack(
        val uri: String,
        val folderUri: String,
        val title: String,
        val artist: String?,
        val album: String?
    )

    data class LibraryDb(
        val folders: List<LibraryFolder>,
        val tracks: List<LibraryTrack>
    )

    private val gson = Gson()

    fun scanLibrary(context: Context, onFinished: (() -> Unit)? = null) {
        Thread {
            try {
                val musicDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                Log.d(TAG, "Scanning: ${musicDir.absolutePath}, exists=${musicDir.exists()}, isDir=${musicDir.isDirectory}")

                val folders = mutableListOf<LibraryFolder>()
                val tracks = mutableListOf<LibraryTrack>()

                scanFolderRecursive(musicDir, null, folders, tracks)
                Log.d(TAG, "AFTER scan: folders=${folders.size}, tracks=${tracks.size}")

                val db = LibraryDb(folders, tracks)
                val json = gson.toJson(db)

                val file = File(context.filesDir, LIBRARY_FILE)
                file.writeText(json)

                MusicRepository.loadLibraryFromJson(context)

                Log.d(TAG,"Library scan finished: ${folders.size} folders, ${tracks.size} tracks"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning library", e)
            } finally {
                onFinished?.invoke()
            }
        }.start()
    }

    private fun scanFolderRecursive(
        dir: File,
        parentUri: String?,
        folders: MutableList<LibraryFolder>,
        tracks: MutableList<LibraryTrack>
    ) {
        if (!dir.isDirectory || dir.name.startsWith(".")) return
        Log.d(TAG, "Scanning folder: ${dir.absolutePath}")

        val folderUri = dir.toURI().toString()
        val cover = findCoverArtFile(dir)
        


        folders += LibraryFolder(
            uri = folderUri,
            name = dir.name,
            parentUri = parentUri,
            coverArtPath = cover?.absolutePath
        )

        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                scanFolderRecursive(f, folderUri, folders, tracks)
            } else if (isAudioFile(f.name)) {
                Log.d(TAG, "Found audio: ${f.absolutePath}")
                val trackUri = f.toURI().toString()
                tracks += LibraryTrack(
                    uri = trackUri,
                    folderUri = folderUri,
                    title = f.nameWithoutExtension,
                    artist = null,
                    album = null
                )
            }
        }
    }

    private fun findCoverArtFile(dir: File): File? {
        val names = listOf("cover.jpg", "Cover.jpg", "folder.jpg", "Folder.jpg")
        return names
            .map { File(dir, it) }
            .firstOrNull { it.exists() && it.isFile }
    }

    private fun isAudioFile(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()

        if (lower.endsWith(".m3u")) return false

        return lower.endsWith(".mp3") ||
                lower.endsWith(".flac") ||
                lower.endsWith(".m4a") ||
                lower.endsWith(".ogg") ||
                lower.endsWith(".wav")
    }

    fun loadLibrary(context: Context): LibraryDb? {
        return try {
            val file = File(context.filesDir, LIBRARY_FILE)
            if (!file.exists()) return null
            val json = file.readText()
            gson.fromJson(json, LibraryDb::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
