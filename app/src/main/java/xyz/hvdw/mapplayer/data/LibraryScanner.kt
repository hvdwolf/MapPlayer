package xyz.hvdw.mapplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import java.io.File

object LibraryScanner {

    private const val TAG = "LibraryScanner"
    private const val LIBRARY_FILE = "library.json"

    interface ScanProgressListener {
        fun onProgress(current: Int, total: Int)
    }

    data class LibraryDb(
        val folders: List<LibraryFolder>,
        val tracks: List<LibraryTrack>
    )

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

    fun scanLibrary(
        context: Context,
        listener: ScanProgressListener? = null,
        onFinished: (() -> Unit)? = null
    ) {
        Thread {
            try {
                val musicDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

                Log.d(TAG, "Scanning root: ${musicDir.absolutePath}")

                val folders = mutableListOf<LibraryFolder>()
                val tracks = mutableListOf<LibraryTrack>()

                val totalFiles = countAudioFiles(musicDir)
                val currentFileRef = intArrayOf(0)

                if (musicDir.exists() && musicDir.isDirectory) {
                    scanFolderRecursive(
                        dir = musicDir,
                        parentUri = null,
                        folders = folders,
                        tracks = tracks,
                        listener = listener,
                        totalFiles = totalFiles,
                        currentFileRef = currentFileRef
                    )
                }

                val db = LibraryDb(folders = folders, tracks = tracks)
                val json = Gson().toJson(db)

                val outFile = File(context.filesDir, LIBRARY_FILE)
                outFile.writeText(json)

                Log.d(TAG, "Library JSON written to: ${outFile.absolutePath}")

                MusicRepository.loadLibraryFromJson(context)

            } catch (e: Exception) {
                Log.e(TAG, "Error scanning library", e)
            } finally {
                onFinished?.invoke()
            }
        }.start()
    }

    private fun countAudioFiles(dir: File): Int {
        if (!dir.isDirectory) return 0
        var count = 0
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                count += countAudioFiles(f)
            } else if (isAudioFile(f.name)) {
                count++
            }
        }
        return count
    }

    private fun scanFolderRecursive(
        dir: File,
        parentUri: String?,
        folders: MutableList<LibraryFolder>,
        tracks: MutableList<LibraryTrack>,
        listener: ScanProgressListener?,
        totalFiles: Int,
        currentFileRef: IntArray
    ) {
        if (!dir.isDirectory || dir.name.startsWith(".")) return

        val folderUri = dir.toURI().toString()
        val coverArtPath = findCoverArtFile(dir)?.absolutePath

        folders += LibraryFolder(
            uri = folderUri,
            name = dir.name,
            parentUri = parentUri,
            coverArtPath = coverArtPath
        )

        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                scanFolderRecursive(
                    dir = f,
                    parentUri = folderUri,
                    folders = folders,
                    tracks = tracks,
                    listener = listener,
                    totalFiles = totalFiles,
                    currentFileRef = currentFileRef
                )
            } else if (isAudioFile(f.name)) {

                // Update progress
                currentFileRef[0]++
                listener?.onProgress(currentFileRef[0], totalFiles)

                val trackUri = f.toURI().toString()
                val (title, artist, album) = extractMetadata(f)

                tracks += LibraryTrack(
                    uri = trackUri,
                    folderUri = folderUri,
                    title = title,
                    artist = artist,
                    album = album
                )
            }
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") ||
                lower.endsWith(".m4a") ||
                lower.endsWith(".aac") ||
                lower.endsWith(".flac") ||
                lower.endsWith(".wav") ||
                lower.endsWith(".ogg")
    }

    private fun findCoverArtFile(dir: File): File? {
        val candidates = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png")
        return dir.listFiles()?.firstOrNull { f ->
            !f.isDirectory && candidates.any { cand ->
                f.name.equals(cand, ignoreCase = true)
            }
        }
    }

    private fun extractMetadata(file: File): Triple<String, String?, String?> {
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read metadata for ${file.absolutePath}", e)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        val finalTitle = title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        val finalArtist = artist?.takeIf { it.isNotBlank() }
        val finalAlbum = album?.takeIf { it.isNotBlank() }

        return Triple(finalTitle, finalArtist, finalAlbum)
    }

    fun loadLibrary(context: Context): LibraryDb? {
        return try {
            val file = File(context.filesDir, LIBRARY_FILE)
            if (!file.exists()) return null
            val json = file.readText()
            Gson().fromJson(json, LibraryDb::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load library.json", e)
            null
        }
    }
}
