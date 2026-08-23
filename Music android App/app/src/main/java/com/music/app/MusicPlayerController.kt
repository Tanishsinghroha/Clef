package com.music.app

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Singleton controller that manages a single MediaPlayer instance
 * and background playback queue auto-advance.
 */
object MusicPlayerController {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongId: String? = null
    
    // Audio waveform spectrum / beat magnitudes for 39 visualizer bars
    var audioMagnitudes by mutableStateOf(FloatArray(39) { 0f })
        private set

    private var visualizer: Visualizer? = null
    private var lastFftTime = 0L

    private val beatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val beatRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                if (System.currentTimeMillis() - lastFftTime > 200L) {
                    updateFallbackBeats()
                }
                beatHandler.postDelayed(this, 50L) // 20 FPS beat update
            }
        }
    }

    private fun startBeatTicker() {
        beatHandler.removeCallbacks(beatRunnable)
        beatHandler.post(beatRunnable)
    }

    private fun setupVisualizer(mp: MediaPlayer) {
        releaseVisualizer()
        try {
            val audioSessionId = mp.audioSessionId
            if (audioSessionId != 0) {
                appContext?.let { AudioAmplifierController.attachToSession(audioSessionId, it) }

                val viz = Visualizer(audioSessionId)
                if (viz.enabled) viz.enabled = false
                val captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
                viz.captureSize = captureSize
                viz.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null && isPlaying) {
                            lastFftTime = System.currentTimeMillis()
                            processFft(fft)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                viz.enabled = true
                visualizer = viz
            }
        } catch (_: Exception) {
            visualizer = null
        }
        startBeatTicker()
    }

    private fun processFft(fft: ByteArray) {
        val numBars = 39
        val n = fft.size
        val numBins = (n / 2) - 1
        if (numBins <= 0) return

        val newMagnitudes = FloatArray(numBars)
        val binMags = FloatArray(numBins)
        for (i in 0 until numBins) {
            val r = fft[2 * (i + 1)].toFloat()
            val im = fft[2 * (i + 1) + 1].toFloat()
            binMags[i] = (kotlin.math.hypot(r, im) / 64f).coerceIn(0f, 1f)
        }

        val current = audioMagnitudes
        for (bar in 0 until numBars) {
            val binIndex = ((bar.toFloat() / numBars) * numBins).toInt().coerceIn(0, numBins - 1)
            val rawMag = binMags[binIndex].coerceIn(0f, 1f)
            val oldMag = if (current.size == numBars) current[bar] else 0f
            val newMag = if (rawMag > oldMag) {
                rawMag
            } else {
                (oldMag * 0.7f).coerceAtLeast(0f)
            }
            newMagnitudes[bar] = newMag
        }

        audioMagnitudes = newMagnitudes
    }

    private fun updateFallbackBeats() {
        val pos = currentPosition
        val numBars = 39
        val currentMags = audioMagnitudes
        val newMags = FloatArray(numBars)

        val beatPhase = (pos % 500) / 500f
        val beatKick = kotlin.math.exp(-beatPhase * 4f)

        val snarePhase = ((pos + 250) % 500) / 500f
        val snarePulse = kotlin.math.exp(-snarePhase * 5f) * 0.7f

        for (i in 0 until numBars) {
            val centerDist = kotlin.math.abs(i - numBars / 2f) / (numBars / 2f)
            val waveFactor = kotlin.math.sin(pos * 0.012f + i * 0.35f) * 0.3f + 0.5f

            val baseBeat = if (i in 8..30) {
                (beatKick * (1f - centerDist * 0.5f) + waveFactor * 0.3f).toFloat()
            } else {
                (snarePulse + waveFactor * 0.4f).toFloat()
            }

            val targetMag = baseBeat.coerceIn(0.15f, 1.0f)
            val prev = if (currentMags.size == numBars) currentMags[i] else 0f
            newMags[i] = if (targetMag > prev) {
                targetMag
            } else {
                (prev * 0.72f).coerceAtLeast(0.05f)
            }
        }

        audioMagnitudes = newMags
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
    }

    // Mutable state so Compose UI updates automatically
    var currentSong by mutableStateOf<Song?>(null)
        private set

    var playbackQueue by mutableStateOf<List<Song>>(emptyList())
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isBuffering by mutableStateOf(false)
        private set

    var songCompletedEvent by mutableStateOf(0)
        private set

    val currentPosition: Int
        get() {
            return try {
                if (mediaPlayer != null && !isBuffering) {
                    mediaPlayer?.currentPosition ?: 0
                } else 0
            } catch (e: Exception) {
                0
            }
        }

    val duration: Int
        get() {
            return try {
                if (mediaPlayer != null && !isBuffering) {
                    val d = mediaPlayer?.duration ?: 0
                    if (d > 0) d else 0
                } else 0
            } catch (e: Exception) {
                0
            }
        }

    var onPlayNext: (() -> Unit)? = null
    var onPlayPrevious: (() -> Unit)? = null
    private var appContext: Context? = null

    fun setQueue(queue: List<Song>) {
        playbackQueue = queue
    }

    /**
     * Play a song from a Song object.
     * If the same song is already playing, this toggles pause/resume.
     * If a different song is requested, the current one stops and the new one starts.
     * Supports raw resources, local URIs, and HTTP streaming URLs.
     */
    fun play(context: Context, song: Song, queue: List<Song>? = null, forceRestart: Boolean = false) {
        if (appContext == null) appContext = context.applicationContext

        if (queue != null) {
            playbackQueue = queue
        } else if (playbackQueue.isEmpty()) {
            playbackQueue = globalAllSongs
        }

        if (!forceRestart && song.id == currentSongId && mediaPlayer != null) {
            // Same song - toggle pause/resume
            if (isBuffering) return // Do not toggle if still preparing

            try {
                if (isPlaying) {
                    pause()
                } else {
                    resume()
                }
                return
            } catch (e: Exception) {
                // Errored player, stop player only and re-create below
                stopPlayerOnly()
            }
        }

        // Different song or first play - stop previous player instance
        stopPlayerOnly()
        currentSong = song
        currentSongId = song.id

        val targetUrl = song.uriString
        val fallbackUrl = song.onlineStreamUrl

        // Determine if we should attempt local playback or online stream
        var playUrl = targetUrl

        if (playUrl != null && (playUrl.startsWith("file://") || playUrl.startsWith("/"))) {
            val file = if (playUrl.startsWith("file://")) {
                java.io.File(Uri.parse(playUrl).path ?: "")
            } else {
                java.io.File(playUrl)
            }
            if (!file.exists() || file.length() == 0L) {
                if (!fallbackUrl.isNullOrEmpty()) {
                    playUrl = fallbackUrl
                } else {
                    isBuffering = false
                    isPlaying = false
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        context.let {
                            android.widget.Toast.makeText(it, "File missing or corrupted: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    playNext()
                    return
                }
            }
        }

        val onCompletionListener = MediaPlayer.OnCompletionListener {
            this@MusicPlayerController.isPlaying = false
            songCompletedEvent++
            // Auto advance queue asynchronously on main thread to avoid native C++ release deadlock
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                playNext()
            }
        }

        if (playUrl != null && (playUrl.startsWith("http://") || playUrl.startsWith("https://"))) {
            // Online streaming URL - use async preparation
            try {
                isBuffering = true
                val player = MediaPlayer()
                try {
                    player.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
                } catch (_: Exception) {}
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
                player.setAudioAttributes(audioAttributes)
                player.setDataSource(playUrl)
                player.setOnPreparedListener { mp ->
                    this@MusicPlayerController.isBuffering = false
                    mp.start()
                    this@MusicPlayerController.isPlaying = true
                    failedTrackCount = 0
                    setupVisualizer(mp)
                    updateService()
                }
                player.setOnErrorListener { _, _, _ ->
                    this@MusicPlayerController.isBuffering = false
                    this@MusicPlayerController.isPlaying = false
                    failedTrackCount++
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        appContext?.let {
                            android.widget.Toast.makeText(it, "Error playing stream: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    playNext()
                    true
                }
                player.setOnCompletionListener(onCompletionListener)
                player.prepareAsync()
                mediaPlayer = player
                updateService()
            } catch (e: Exception) {
                isBuffering = false
                mediaPlayer = null
                isPlaying = false
                failedTrackCount++
                playNext()
            }
        } else {
            // Local resource or local URI (content://, file://, etc.)
            var newPlayer: MediaPlayer? = null
            if (playUrl != null) {
                try {
                    val uri = if (playUrl.startsWith("content://") || playUrl.startsWith("file://") || playUrl.startsWith("android.resource://")) {
                        Uri.parse(playUrl)
                    } else {
                        Uri.fromFile(java.io.File(playUrl))
                    }
                    newPlayer = MediaPlayer.create(context, uri)
                } catch (e: Exception) {
                    newPlayer = null
                }

                // Secondary fallback for content:// URIs if MediaPlayer.create returned null
                if (newPlayer == null && playUrl.startsWith("content://")) {
                    try {
                        val player = MediaPlayer()
                        try {
                            player.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                        } catch (_: Exception) {}
                        val audioAttributes = android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                        player.setAudioAttributes(audioAttributes)
                        player.setDataSource(context, Uri.parse(playUrl))
                        player.prepare()
                        newPlayer = player
                    } catch (e: Exception) {
                        newPlayer = null
                    }
                }
            } else if (song.rawResId != 0) {
                try {
                    newPlayer = MediaPlayer.create(context, song.rawResId)
                } catch (e: Exception) {
                    newPlayer = null
                }
            }

            if (newPlayer != null) {
                try {
                    newPlayer.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                } catch (_: Exception) {}
                mediaPlayer = newPlayer
                newPlayer.setOnCompletionListener(onCompletionListener)
                newPlayer.start()
                isPlaying = true
                failedTrackCount = 0
                setupVisualizer(newPlayer)
                updateService()
            } else {
                // If local playback failed and fallback online stream URL exists
                if (!fallbackUrl.isNullOrEmpty() && fallbackUrl != playUrl) {
                    val fallbackSong = song.copy(uriString = fallbackUrl)
                    play(context, fallbackSong, queue, forceRestart = true)
                } else {
                    isPlaying = false
                    isBuffering = false
                    failedTrackCount++
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Playback failed for: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    playNext()
                }
            }
        }
    }

    private var failedTrackCount = 0

    fun playNext() {
        val ctx = appContext ?: return
        val queue = if (playbackQueue.isNotEmpty()) playbackQueue else globalAllSongs
        if (queue.isNotEmpty()) {
            if (failedTrackCount >= queue.size) {
                failedTrackCount = 0
                stop()
                return
            }
            val current = currentSong
            val currentIndex = if (current != null) {
                queue.indexOfFirst { it.id == current.id }
            } else -1

            val nextIndex = if (currentIndex != -1) {
                (currentIndex + 1) % queue.size
            } else 0

            play(ctx, queue[nextIndex], queue, forceRestart = true)
        } else {
            failedTrackCount = 0
            stop()
        }
    }

    fun playPrevious() {
        val ctx = appContext ?: return
        val queue = if (playbackQueue.isNotEmpty()) playbackQueue else globalAllSongs
        if (queue.isNotEmpty()) {
            val current = currentSong
            val currentIndex = if (current != null) {
                queue.indexOfFirst { it.id == current.id }
            } else -1

            val prevIndex = if (currentIndex != -1) {
                if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
            } else 0

            play(ctx, queue[prevIndex], queue, forceRestart = true)
        } else {
            stop()
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {}
        isPlaying = false
        beatHandler.removeCallbacks(beatRunnable)
        audioMagnitudes = FloatArray(39) { 0f }
        updateService()
    }

    fun resume() {
        try {
            if (mediaPlayer != null && !isPlaying && !isBuffering) {
                mediaPlayer?.start()
                isPlaying = true
                mediaPlayer?.let { setupVisualizer(it) } ?: startBeatTicker()
                updateService()
            }
        } catch (e: Exception) {}
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else resume()
    }

    private fun stopPlayerOnly() {
        releaseVisualizer()
        beatHandler.removeCallbacks(beatRunnable)
        audioMagnitudes = FloatArray(39) { 0f }
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (_: Exception) {}
            it.release()
        }
        mediaPlayer = null
        isPlaying = false
        isBuffering = false
    }

    fun stop() {
        stopPlayerOnly()
        currentSongId = null
        currentSong = null
        updateService()
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {}
    }

    fun isCurrentSong(songId: String): Boolean {
        return currentSongId == songId
    }

    private fun updateService() {
        val context = appContext ?: return
        val intent = android.content.Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_UPDATE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

