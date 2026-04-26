package xyz.hvdw.mapplayer.model

import android.graphics.Bitmap
import android.net.Uri

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    var albumArt: Bitmap?
)
