package xyz.hvdw.mapplayer.ui.player

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat

import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.data.MusicRepository
import xyz.hvdw.mapplayer.player.MusicService

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_URI = "extra_folder_uri"
        const val EXTRA_SHUFFLE = "extra_shuffle"
        const val EXTRA_START_INDEX = "extra_start_index"
    }

    private lateinit var imgAlbumArt: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtAlbum: TextView
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnExit: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var txtShuffle: TextView

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    // --------------------------------------------------------------------
    // MEDIA BROWSER CONNECTION
    // --------------------------------------------------------------------
    private val browserConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val token = mediaBrowser.sessionToken
            mediaController = MediaControllerCompat(this@PlayerActivity, token).apply {
                registerCallback(controllerCallback)
            }
            MediaControllerCompat.setMediaController(this@PlayerActivity, mediaController)

            startPlaybackIfNeeded()
            updateUi()
            handler.post(updateRunnable)
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            updateUi()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            updatePlayPauseIcon()
            updateProgress()
        }
    }

    // --------------------------------------------------------------------
    // LIFECYCLE
    // --------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        txtTitle = findViewById(R.id.txtTitle)
        txtArtist = findViewById(R.id.txtArtist)
        txtAlbum = findViewById(R.id.txtAlbum)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        seekBar = findViewById(R.id.seekBar)
        btnExit = findViewById(R.id.btnExit)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        txtShuffle = findViewById(R.id.txtShuffle)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MusicService::class.java),
            browserConnectionCallback,
            null
        )

        btnExit.setOnClickListener {
            val intent = Intent(this, MusicService::class.java)
            intent.action = MusicService.ACTION_STOP
            startService(intent)
            finish()
        }

        btnPrev.setOnClickListener {
            mediaController?.transportControls?.skipToPrevious()
        }

        btnNext.setOnClickListener {
            mediaController?.transportControls?.skipToNext()
        }

        btnPlayPause.setOnClickListener {
            val playing = mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
            if (playing) mediaController?.transportControls?.pause()
            else mediaController?.transportControls?.play()
        }

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            val intent = Intent(this, MusicService::class.java)
            intent.action = MusicService.ACTION_STOP
            startService(intent)
            finish()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val metadata = mediaController?.metadata ?: return
                    val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                    val newPos = (duration * progress / 1000L)
                    mediaController?.transportControls?.seekTo(newPos)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
        handler.removeCallbacks(updateRunnable)
    }

    // --------------------------------------------------------------------
    // PLAYBACK START
    // --------------------------------------------------------------------
    private fun startPlaybackIfNeeded() {
        val folderUriStr = intent.getStringExtra(EXTRA_FOLDER_URI) ?: return
        val shuffle = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        val extras = Bundle().apply {
            putString("folderUri", folderUriStr)
            putBoolean("shuffle", shuffle)
            putInt("startIndex", startIndex)
        }

        mediaController?.transportControls?.playFromMediaId("folder:$folderUriStr", extras)
    }

    // --------------------------------------------------------------------
    // UI UPDATES
    // --------------------------------------------------------------------
    private fun updateUi() {
        val metadata = mediaController?.metadata ?: return

        txtTitle.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
        txtArtist.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        txtAlbum.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: ""

        val art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
        if (art != null) {
            imgAlbumArt.setImageBitmap(art)
        } else {
            imgAlbumArt.setImageResource(R.drawable.ic_music_note)
        }

        updatePlayPauseIcon()
        updateShuffleTxt()
        updateProgress()
    }

    private fun updatePlayPauseIcon() {
        val playing = mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateShuffleTxt() {
        val shuffle = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
        txtShuffle.visibility = if (shuffle) View.VISIBLE else View.GONE
    }

    private fun updateProgress() {
        val metadata = mediaController?.metadata ?: return
        val state = mediaController?.playbackState ?: return

        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        val position = state.position

        if (duration > 0) {
            val progress = (position * 1000L / duration).toInt()
            seekBar.progress = progress
        } else {
            seekBar.progress = 0
        }

        txtCurrentTime.text = formatTime(position)
        txtTotalTime.text = formatTime(duration)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
