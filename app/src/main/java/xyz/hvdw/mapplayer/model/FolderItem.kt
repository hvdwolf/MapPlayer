package xyz.hvdw.mapplayer.model

import android.net.Uri

data class FolderItem(
    val uri: Uri,
    val name: String,
    val coverArtUri: Uri?
)
