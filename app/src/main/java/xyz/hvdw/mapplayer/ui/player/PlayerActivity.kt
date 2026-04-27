package xyz.hvdw.mapplayer.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.data.MusicRepository
import xyz.hvdw.mapplayer.model.Track
import xyz.hvdw.mapplayer.player.MusicService

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_URI = "extra_folder_uri"
        const val EXTRA_SHUFFLE = "extra_shuffle"
        const val EXTRA_START_INDEX = "extra_start_index"
    }

    private var service: MusicService? = null
    private var bound = false

    private lateinit var imgAlbumArt: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnExit: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as MusicService.LocalBinder
            service = b.getService()
            service?.playbackListener = playbackListener
            bound = true

            startPlaybackIfNeeded()
            updateUi()
            handler.post(updateRunnable)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
            handler.removeCallbacks(updateRunnable)
        }
    }

    private val playbackListener = object : MusicService.PlaybackListener {
        override fun onTrackChanged() {
            runOnUiThread { updateUi() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        txtTitle = findViewById(R.id.txtTitle)
        txtArtist = findViewById(R.id.txtArtist)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        seekBar = findViewById(R.id.seekBar)
        btnExit = findViewById(R.id.btnExit)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)

        btnExit.setOnClickListener { finish() }
        btnPrev.setOnClickListener { service?.skipPrevious() }
        btnNext.setOnClickListener { service?.skipNext() }
        btnPlayPause.setOnClickListener {
            service?.togglePlayPause()
            updatePlayPauseIcon()
        }

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            val intent = Intent(this, MusicService::class.java)
            intent.action = "ACTION_STOP"
            startService(intent)
            finish()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = service?.getDuration() ?: 0L
                    val newPos = (duration * progress / 1000L)
                    service?.playerSeekTo(newPos)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        service?.playbackListener = null
        if (bound) {
            unbindService(connection)
            bound = false
        }
        handler.removeCallbacks(updateRunnable)
    }

    // --------------------------------------------------------------------
    // PLAYBACK START
    // --------------------------------------------------------------------
    private fun startPlaybackIfNeeded() {
        val folderUriStr = intent.getStringExtra(EXTRA_FOLDER_URI) ?: return
        val shuffle = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        val folderUri = Uri.parse(folderUriStr)

        val tracks = MusicRepository.listTracksInFolder(
            this,
            folderUri
        ) { updatedTracks ->
            if (updatedTracks.isNotEmpty()) {
                service?.playTracks(updatedTracks, startIndex, shuffle)
                runOnUiThread { updateUi() }
            }
        }

        if (tracks.isNotEmpty()) {
            service?.playTracks(tracks, startIndex, shuffle)
        }
    }

    // --------------------------------------------------------------------
    // UI UPDATES
    // --------------------------------------------------------------------
    private fun updateUi() {
        val track = service?.getCurrentTrack() ?: return

        txtTitle.text = track.title
        txtArtist.text = track.artist ?: track.album ?: ""

        // Reset time labels for new track
        txtCurrentTime.text = "0:00"
        txtTotalTime.text = formatTime(service?.getDuration() ?: 0L)

        // Album art
        if (track.albumArt != null) {
            imgAlbumArt.setImageBitmap(track.albumArt)
        } else {
            Thread {
                val bmp = MusicRepository.loadEmbeddedAlbumArt(this, track.uri)

                if (bmp != null) {
                    track.albumArt = bmp

                    runOnUiThread {
                        imgAlbumArt.setImageBitmap(bmp)
                        service?.refreshMetadata()
                    }
                } else {
                    runOnUiThread {
                        imgAlbumArt.setImageResource(R.drawable.ic_music_note)
                    }
                }
            }.start()
        }

        updatePlayPauseIcon()
        updateProgress()
    }

    private fun updatePlayPauseIcon() {
        val playing = service?.isPlaying() ?: false
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateProgress() {
        val svc = service ?: return

        val duration = svc.getDuration()
        val position = svc.getPosition()

        // Seekbar
        if (duration > 0) {
            val progress = (position * 1000L / duration).toInt()
            seekBar.progress = progress
        } else {
            seekBar.progress = 0
        }

        // Time labels
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
