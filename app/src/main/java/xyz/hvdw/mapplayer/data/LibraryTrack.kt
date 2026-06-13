package xyz.hvdw.mapplayer.data

data class LibraryTrack(
    val uri: String,
    val folderUri: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long = 0L,
    val thumbnailPath: String? = null,
    val metadataLoaded: Boolean = false,
    val path: String
)
