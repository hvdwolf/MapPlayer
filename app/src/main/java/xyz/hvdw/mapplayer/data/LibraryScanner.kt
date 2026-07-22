package xyz.hvdw.mapplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.security.MessageDigest
import android.net.Uri
import java.io.FileOutputStream
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import java.io.File
import xyz.hvdw.mapplayer.data.LibraryTrack


object LibraryScanner {

    private const val TAG = "LibraryScanner"
    private const val LIBRARY_FILE = "library.json"
    @Volatile
    var errorLogWritten: Boolean = false


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


    fun scanLibrary(
        context: Context,
        listener: ScanProgressListener? = null,
        onFinished: (() -> Unit)? = null
    ) {
        Thread {
            try {
                errorLogWritten = false
                //appendErrorLog(context, "TEST: scanLibrary gestart", null)

                val musicDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

                Log.d(TAG, "Scanning root: ${musicDir.absolutePath}")

                val folders = mutableListOf<LibraryFolder>()
                val tracks = mutableListOf<LibraryTrack>()

                val totalFiles = countAudioFiles(musicDir)
                val currentFileRef = intArrayOf(0)

                if (musicDir.exists() && musicDir.isDirectory) {
                    scanFolderRecursive(
                        context = context,
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
        context: Context,
        dir: File,
        parentUri: String?,
        folders: MutableList<LibraryFolder>,
        tracks: MutableList<LibraryTrack>,
        listener: ScanProgressListener?,
        totalFiles: Int,
        currentFileRef: IntArray
    ) {
        try {
            if (!dir.isDirectory || dir.name.startsWith(".")) return

            val folderUri = dir.toURI().toString()

            // 1. Try cover.jpg / folder.jpg
            var finalCoverArtPath: String? = null
            try {
                finalCoverArtPath = findCoverArtFile(dir)?.absolutePath
            } catch (e: Exception) {
                val msg = "Failed to check cover art in folder ${dir.absolutePath}: ${e.message}"
                Log.w(TAG, msg)
                appendErrorLog(context, msg, e)
            }

            // 2. Collect audio files in this folder
            val tracksInThisFolder = try {
                dir.listFiles()?.filter { f ->
                    !f.isDirectory && isAudioFile(f.name)
                } ?: emptyList()
            } catch (e: Exception) {
                val msg = "Failed to list files in folder ${dir.absolutePath}: ${e.message}"
                Log.w(TAG, msg)
                appendErrorLog(context, msg, e)
                emptyList()
            }

            // 3. If no cover.jpg found → try embedded art from first track
            if (finalCoverArtPath == null && tracksInThisFolder.isNotEmpty()) {
                try {
                    val firstTrack = tracksInThisFolder.first()
                    val meta = extractMetadataWithArt(context, firstTrack)
                    if (meta.art != null) {
                        val prefs = context.getSharedPreferences("mapplayer_settings", Context.MODE_PRIVATE)
                        val size = prefs.getInt("pref_thumbnail_size", 256)
                        val thumb = Bitmap.createScaledBitmap(meta.art, size, size, true)

                        val outFile = File(context.cacheDir, "thumb_${thumb.hashCode()}.jpg")
                        FileOutputStream(outFile).use { os ->
                            thumb.compress(Bitmap.CompressFormat.JPEG, 85, os)
                        }
                        finalCoverArtPath = outFile.absolutePath
                    }
                } catch (e: Exception) {
                    val msg = "Failed to extract embedded art for folder ${dir.absolutePath}: ${e.message}"
                    Log.w(TAG, msg)
                    appendErrorLog(context, msg, e)
                } catch (e: Error) {
                    val msg = "NATIVE CRASH while extracting embedded art for ${dir.absolutePath}: ${e.message}"
                    Log.e(TAG, msg)
                    appendErrorLog(context, msg, e)
                }
            }

            // 4. Store folder entry
            folders += LibraryFolder(
                uri = folderUri,
                name = dir.name,
                parentUri = parentUri,
                coverArtPath = finalCoverArtPath
            )

            // 5. Scan children
            val children = try {
                dir.listFiles() ?: emptyArray()
            } catch (e: Exception) {
                val msg = "Failed to list directory ${dir.absolutePath}: ${e.message}"
                Log.w(TAG, msg)
                appendErrorLog(context, msg, e)
                emptyArray()
            }

            for (f in children) {
                if (f.isDirectory) {
                    scanFolderRecursive(
                        context = context,
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

                    try {
                        //val trackUri = f.toURI().toString()
                        val trackUri = f.absolutePath
                        val meta = extractMetadataWithArt(context, f)

                        val thumbnailPath = if (meta.art != null) {
                            val key = sha1(f.absolutePath)
                            saveThumbnail(context, meta.art, key)
                        } else null

                        tracks += LibraryTrack(
                            uri = trackUri,
                            folderUri = folderUri,
                            title = meta.title ?: f.nameWithoutExtension,
                            artist = meta.artist,
                            album = meta.album,
                            duration = meta.duration,
                            thumbnailPath = thumbnailPath,
                            metadataLoaded = true,
                            path = f.absolutePath
                        )

                    } catch (e: Exception) {
                        val msg = "Failed to process audio file ${f.absolutePath}: ${e.message}"
                        Log.w(TAG, msg)
                        appendErrorLog(context, msg, e)
                    } catch (e: Error) {
                        val msg = "NATIVE CRASH while processing ${f.absolutePath}: ${e.message}"
                        Log.e(TAG, msg)
                        appendErrorLog(context, msg, e)
                    }
                }
            }

        } catch (e: Exception) {
            val msg = "Unexpected exception in scanFolderRecursive for ${dir.absolutePath}: ${e.message}"
            Log.e(TAG, msg)
            appendErrorLog(context, msg, e)

        } catch (e: Error) {
            val msg = "NATIVE CRASH in scanFolderRecursive for ${dir.absolutePath}: ${e.message}"
            Log.e(TAG, msg)
            appendErrorLog(context, msg, e)
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
        val candidates = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png")
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

    // ** Nieuw ** //
    data class MetaResult(
        val title: String?,
        val artist: String?,
        val album: String?,
        val art: Bitmap?,
        val duration: Long
    )

    fun extractMetadataWithArt(context: Context, file: File): MetaResult {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var art: Bitmap? = null
        var duration = 0L

        val mmr = MediaMetadataRetriever()

        try {
            // Dit is de meest crashgevoelige call
            try {
                mmr.setDataSource(file.absolutePath)
            } catch (e: Exception) {
                val msg = "setDataSource() failed for ${file.absolutePath}"
                Log.w(TAG, msg, e)
                appendErrorLog(context, msg, e)
                return MetaResult(file.nameWithoutExtension, null, null, null, 0L)
            } catch (e: Error) {
                val msg = "NATIVE CRASH in setDataSource() for ${file.absolutePath}"
                Log.e(TAG, msg, e)
                appendErrorLog(context, msg, e)
                return MetaResult(file.nameWithoutExtension, null, null, null, 0L)
            }

            fun safeGet(block: () -> String?): String? =
                try { block() } catch (e: Exception) {
                    val msg = "Metadata read failed for ${file.absolutePath}: ${e.message}"
                    Log.w(TAG, msg)
                    appendErrorLog(context, msg, e)
                    null
                }

            fun safeArt(): Bitmap? =
                try {
                    mmr.embeddedPicture?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } catch (e: Exception) {
                    val msg = "Embedded art decode failed for ${file.absolutePath}: ${e.message}"
                    Log.w(TAG, msg)
                    appendErrorLog(context, msg, e)
                    null
                }

            title = safeGet { mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) }
            artist = safeGet { mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) }
            album = safeGet { mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) }

            duration = safeGet { mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) }
                ?.toLongOrNull() ?: 0L

            art = safeArt()

        } catch (e: Exception) {
            val msg = "Unexpected metadata exception for ${file.absolutePath}: ${e.message}"
            Log.w(TAG, msg)
            appendErrorLog(context, msg, e)

        } catch (e: Error) {
            val msg = "NATIVE CRASH in metadata for ${file.absolutePath}: ${e.message}"
            Log.e(TAG, msg)
            appendErrorLog(context, msg, e)

        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        if (title.isNullOrBlank()) title = file.nameWithoutExtension

        return MetaResult(title, artist, album, art, duration)
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

    private fun saveThumbnail(context: Context, bmp: Bitmap, key: String): String {
        val dir = File(context.filesDir, "thumbnails")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "$key.jpg")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        return file.absolutePath
    }

    private fun sha1(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun appendErrorLog(context: Context, message: String, throwable: Throwable? = null) {
        try {
            errorLogWritten = true

            val baseDir = context.getExternalFilesDir(null)  // /storage/emulated/0/Android/data/…/files
            val logDir = File(baseDir, "_MapPlayer_Log")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, "error.log")

            logFile.appendText(
                buildString {
                    append("=== ERROR ===\n")
                    append(message)
                    append("\n")
                    if (throwable != null) {
                        append(throwable.toString())
                        append("\n")
                        throwable.stackTrace.forEach { append("    at $it\n") }
                    }
                    append("\n")
                }
            )

        } catch (e: Exception) {
            Log.e("LibraryScanner", "Failed to write error.log", e)
        }
    }

}
