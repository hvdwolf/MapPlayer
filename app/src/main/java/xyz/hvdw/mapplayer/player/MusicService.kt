package xyz.hvdw.mapplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
//import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.model.Track
import xyz.hvdw.mapplayer.ui.player.PlayerActivity

class MusicService : Service() {

    interface PlaybackListener {
        fun onTrackChanged()
    }

    companion object {
        private const val CHANNEL_ID = "map_player_channel"
        private const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = LocalBinder()

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat

    private val queue: MutableList<Track> = mutableListOf()
    private var shuffledQueue: MutableList<Track> = mutableListOf()
    private var currentIndex: Int = 0
    private var shuffle: Boolean = false

    var playbackListener: PlaybackListener? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        player = ExoPlayer.Builder(this).build()

        mediaSession = MediaSessionCompat(this, "MapPlayerSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    player.playWhenReady = true
                    updatePlaybackState()
                    startForegroundWithNotification()
                }

                override fun onPause() {
                    player.playWhenReady = false
                    updatePlaybackState()
                    stopForeground(false)
                    updateNotification()
                }

                override fun onSkipToNext() {
                    skipNext()
                }

                override fun onSkipToPrevious() {
                    skipPrevious()
                }
            })
            isActive = true
        }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                updatePlaybackState()
                //if (state == Player.STATE_ENDED) {
                //    skipNext()
                //}
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------
    // PLAYBACK CONTROL (our own shuffle, our own queue)
    // ---------------------------------------------------------

    fun playTracks(tracks: List<Track>, startIndex: Int, shuffle: Boolean) {
        if (tracks.isEmpty()) return

        queue.clear()
        queue.addAll(tracks)

        this.shuffle = shuffle

        if (shuffle) {
            shuffledQueue = tracks.shuffled().toMutableList()
            currentIndex = 0
        } else {
            shuffledQueue.clear()
            currentIndex = startIndex.coerceIn(0, tracks.lastIndex)
        }

        playCurrent()
    }

    private fun playCurrent() {
        val list = if (shuffle) shuffledQueue else queue
        if (list.isEmpty()) return

        val track = list[currentIndex]

        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.playWhenReady = true

        updateMetadata(track)
        startForegroundWithNotification()

        // Notify PlayerActivity that the track changed
        playbackListener?.onTrackChanged()
    }

    fun skipNext() {
        val list = if (shuffle) shuffledQueue else queue
        if (list.isEmpty()) return

        currentIndex = (currentIndex + 1) % list.size
        playCurrent()
    }

    fun skipPrevious() {
        val list = if (shuffle) shuffledQueue else queue
        if (list.isEmpty()) return

        currentIndex = if (currentIndex - 1 < 0) list.lastIndex else currentIndex - 1
        playCurrent()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            mediaSession.controller.transportControls.pause()
        } else {
            mediaSession.controller.transportControls.play()
        }
    }

    fun getCurrentTrack(): Track? {
        val list = if (shuffle) shuffledQueue else queue
        return list.getOrNull(currentIndex)
    }

    fun getDuration(): Long = player.duration.coerceAtLeast(0L)
    fun getPosition(): Long = player.currentPosition
    fun isPlaying(): Boolean = player.isPlaying

    // ---------------------------------------------------------
    // METADATA + NOTIFICATION
    // ---------------------------------------------------------

    private fun updateMetadata(track: Track) {
        // Use embedded art if available, otherwise load placeholder
        val albumArtBitmap = track.albumArt
            ?: vectorToBitmap(R.drawable.ic_music_note_placeholder)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist ?: "Unknown artist")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album ?: "Unknown album")
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
            .build()

        mediaSession.setMetadata(metadata)
    }

    private fun updatePlaybackState() {
        val state = if (player.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, player.currentPosition, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Map Player",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val controller = mediaSession.controller
        val metadata = controller.metadata
        val description = metadata?.description

        val intent = Intent(this, PlayerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(description?.title ?: "Playing")
            .setContentText(description?.subtitle ?: "")
            .setSubText(description?.description ?: "")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)?.let {
            builder.setLargeIcon(it)
        }

        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).setAction("ACTION_PREV"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).setAction("ACTION_TOGGLE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MusicService::class.java).setAction("ACTION_NEXT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
        builder.addAction(
            if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (player.isPlaying) "Pause" else "Play",
            playIntent
        )
        builder.addAction(R.drawable.ic_skip_next, "Next", nextIntent)

        return builder.build()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PREV" -> skipPrevious()
            "ACTION_NEXT" -> skipNext()
            "ACTION_TOGGLE" -> togglePlayPause()
            "ACTION_STOP" -> stopAndRelease()
        }
        return START_STICKY
    }

    fun stopAndRelease() {
        player.stop()
        player.release()
        stopForeground(true)
        stopSelf()
    }

    private fun smartShuffle(tracks: List<Track>): MutableList<Track> {
        if (tracks.size <= 1) return tracks.toMutableList()

        val pool = tracks.shuffled().toMutableList()
        val result = mutableListOf<Track>()

        // Detect if constraints are meaningful
        val multipleArtists = pool.map { it.artist ?: "" }.distinct().size > 1
        val multipleAlbums = pool.map { it.album ?: "" }.distinct().size > 1

        // Always pick a random first track
        result.add(pool.removeAt(0))

        while (pool.isNotEmpty()) {
            val last = result.last()

            val candidate = pool.firstOrNull { t ->
                (!multipleArtists || (t.artist ?: "") != (last.artist ?: "")) &&
                (!multipleAlbums || (t.album ?: "") != (last.album ?: ""))
            }

            if (candidate != null) {
                result.add(candidate)
                pool.remove(candidate)
                continue
            }

            // No constraint‑satisfying track → fallback to any remaining
            result.add(pool.removeAt(0))
        }

        return result
    }

    private fun vectorToBitmap(drawableId: Int): Bitmap {
        val drawable = resources.getDrawable(drawableId, null)
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

}
