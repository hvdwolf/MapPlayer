package xyz.hvdw.mapplayer.ui.folder

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.model.Track

class SongAdapter(
    private val tracks: List<Track>,
    private val listener: SongClickListener
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var shuffleMode: Boolean = false

    fun setShuffle(shuffle: Boolean) {
        shuffleMode = shuffle
    }

    interface SongClickListener {
        fun onSongClick(track: Track, position: Int, shuffle: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun getItemCount(): Int = tracks.size

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(track, holder.itemView.context)

        holder.itemView.setOnClickListener {
            listener.onSongClick(track, position, shuffleMode)
        }
    }

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val albumArt: ImageView = itemView.findViewById(R.id.songAlbumArt)
        private val title: TextView = itemView.findViewById(R.id.songTitle)
        private val artistAlbum: TextView = itemView.findViewById(R.id.songArtistAlbum)

        fun bind(track: Track, context: Context) {
            title.text = track.title

            val ctx = itemView.context
            val artist = track.artist ?: ctx.getString(R.string.unknown_artist)
            val album = track.album ?: ctx.getString(R.string.unknown_album)
            artistAlbum.text = ctx.getString(R.string.song_artist_album, artist, album)


            // 1. Thumbnail direct laden als bestand
            if (track.albumArt != null) {
                albumArt.setImageBitmap(track.albumArt)
            } else {
                albumArt.setImageResource(R.drawable.ic_music_note)
            }

        }
    }
}
