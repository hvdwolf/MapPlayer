package xyz.hvdw.mapplayer.model

import android.graphics.Bitmap
import android.net.Uri

data class FolderItem(
    val uri: Uri,
    val name: String,
    val coverArtUri: Uri?,
    val thumbnail: Bitmap? = null
)