package com.music.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private val channelId = "music_playback_channel"

    // SupervisorJob: child failures/cancellations don't kill the scope
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var imageLoadJob: Job? = null

    // Generation counter — incremented each time a new song triggers updateNotification.
    // Prevents stale image fetches from overwriting the current song's notification.
    private var notificationGeneration = 0

    // Track whether startForeground has been called at least once
    private var isForegroundStarted = false

    // Singleton ImageLoader for Coil (fallback only)
    private val imageLoader by lazy {
        ImageLoader.Builder(applicationContext)
            .allowHardware(false)
            .build()
    }

    // Bitmap LRU cache keyed by image URL
    private val bitmapCache = object : LruCache<String, Bitmap>(50) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    companion object {
        private const val TAG = "MusicService"
        const val ACTION_PLAY_PAUSE = "com.music.app.PLAY_PAUSE"
        const val ACTION_NEXT = "com.music.app.NEXT"
        const val ACTION_PREVIOUS = "com.music.app.PREVIOUS"
        const val ACTION_UPDATE = "UPDATE_NOTIFICATION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MusicService")
        mediaSession.isActive = true

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                MusicPlayerController.resume()
            }
            override fun onPause() {
                MusicPlayerController.pause()
            }
            override fun onSkipToNext() {
                MusicPlayerController.playNext()
            }
            override fun onSkipToPrevious() {
                MusicPlayerController.playPrevious()
            }
            override fun onSeekTo(pos: Long) {
                MusicPlayerController.seekTo(pos.toInt())
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                MusicPlayerController.togglePlayPause()
                updateNotification()
            }
            ACTION_NEXT -> {
                MusicPlayerController.playNext()
            }
            ACTION_PREVIOUS -> {
                MusicPlayerController.playPrevious()
            }
            ACTION_UPDATE -> {
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getScaledBitmap(bitmap: Bitmap, maxDimension: Int = 512): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val aspectRatio = width.toFloat() / height.toFloat()
        val (targetWidth, targetHeight) = if (width > height) {
            maxDimension to (maxDimension / aspectRatio).toInt()
        } else {
            (maxDimension * aspectRatio).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1), true)
    }

    /**
     * Download bitmap via HttpURLConnection with manual redirect handling.
     */
    private fun downloadBitmapDirectly(urlString: String): Bitmap? {
        var currentUrl = urlString
        var redirects = 0
        while (redirects < 5) {
            try {
                val url = URL(currentUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                conn.doInput = true
                conn.connect()

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308) {
                    val newUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!newUrl.isNullOrEmpty()) {
                        currentUrl = if (newUrl.startsWith("http")) newUrl else URL(url, newUrl).toString()
                        redirects++
                        continue
                    }
                }

                if (status in 200..299) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    conn.disconnect()
                    if (bytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            Log.d(TAG, "downloadBitmapDirectly SUCCESS: $urlString (${bmp.width}x${bmp.height})")
                            return bmp
                        }
                    }
                } else {
                    Log.w(TAG, "downloadBitmapDirectly: HTTP $status for $urlString")
                    conn.disconnect()
                }
                break
            } catch (e: Exception) {
                Log.e(TAG, "downloadBitmapDirectly error: ${e.message}")
                break
            }
        }
        return null
    }

    /**
     * Fetch bitmap from various URL types (http/https, file://, content://).
     * For HTTP: tries direct HttpURLConnection first, then Coil as fallback.
     */
    private suspend fun fetchBitmapFromUrl(urlString: String): Bitmap? {
        val cleanUrl = urlString.trim()
        if (cleanUrl.isEmpty()) return null

        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            val secureUrl = if (cleanUrl.startsWith("http://")) cleanUrl.replaceFirst("http://", "https://") else cleanUrl

            // 1. Primary: Direct HttpURLConnection
            try {
                val directBmp = withContext(Dispatchers.IO) { downloadBitmapDirectly(secureUrl) }
                if (directBmp != null) return directBmp
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "Direct download failed: ${e.message}")
            }

            // 2. Fallback: Coil with caching disabled
            try {
                val request = ImageRequest.Builder(this@MusicService)
                    .data(secureUrl)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    Log.d(TAG, "Coil fallback SUCCESS for: $secureUrl")
                    return drawableToBitmap(result.drawable)
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.e(TAG, "Coil fallback error: ${e.message}")
            }
        } else if (cleanUrl.startsWith("file://") || cleanUrl.startsWith("/")) {
            val path = if (cleanUrl.startsWith("file://")) Uri.parse(cleanUrl).path else cleanUrl
            if (path != null && File(path).exists()) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val art = retriever.embeddedPicture
                    retriever.release()
                    if (art != null && art.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                        if (bmp != null) return bmp
                    }
                } catch (_: Exception) {}

                val lower = path.lowercase()
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
                    try {
                        val bmp = BitmapFactory.decodeFile(path)
                        if (bmp != null) return bmp
                    } catch (_: Exception) {}
                }
            }
        } else if (cleanUrl.startsWith("content://")) {
            val uri = Uri.parse(cleanUrl)
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(this@MusicService, uri)
                val art = retriever.embeddedPicture
                retriever.release()
                if (art != null && art.isNotEmpty()) {
                    val bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                    if (bmp != null) return bmp
                }
            } catch (_: Exception) {}

            try {
                val mimeType = contentResolver.getType(uri)
                if (mimeType != null && mimeType.startsWith("image/")) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) return bmp
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Build and post the notification with the given bitmap.
     * Only calls startForeground() on the very first invocation;
     * all subsequent updates use NotificationManager.notify() to avoid
     * Android rate-limiting/caching issues with repeated startForeground().
     */
    private fun postNotification(targetSong: Song, rawBitmap: Bitmap) {
        val bitmap = getScaledBitmap(rawBitmap, 512)
        val isPlaying = MusicPlayerController.isPlaying

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val playPauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePending = PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPending = PendingIntent.getService(this, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(this, 3, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // First: clear metadata then set new — forces SystemUI on OEM skins (Vivo, Samsung, etc.) to refresh artwork
        mediaSession.setMetadata(MediaMetadataCompat.Builder().build())

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, targetSong.id)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, targetSong.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, targetSong.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, targetSong.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, targetSong.artist)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, MusicPlayerController.duration.toLong())

        if (!targetSong.imageUrl.isNullOrEmpty()) {
            val secureUrl = if (targetSong.imageUrl.startsWith("http://")) targetSong.imageUrl.replaceFirst("http://", "https://") else targetSong.imageUrl
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, secureUrl)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, secureUrl)
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, secureUrl)
        }

        mediaSession.setMetadata(metadataBuilder.build())

        // Second: update playback state with proper timestamp
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                MusicPlayerController.currentPosition.toLong(),
                1.0f,
                SystemClock.elapsedRealtime()
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        // Third: build notification
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(targetSong.title)
            .setContentText(targetSong.artist)
            .setLargeIcon(bitmap)
            .setContentIntent(openAppPending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(R.drawable.ic_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(R.drawable.ic_next, "Next", nextPending)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )

        val notification = builder.build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!isForegroundStarted) {
            // First time: must call startForeground to satisfy Android's foreground service requirement
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
            isForegroundStarted = true
            Log.d(TAG, "postNotification: startForeground for '${targetSong.title}', bitmap=${bitmap.width}x${bitmap.height}")
        } else {
            // Subsequent: use NotificationManager.notify to avoid rate-limiting on Android 12+
            notificationManager.notify(1, notification)
            Log.d(TAG, "postNotification: notify() for '${targetSong.title}', bitmap=${bitmap.width}x${bitmap.height}")
        }
    }

    private fun updateNotification() {
        val song = MusicPlayerController.currentSong
        if (song == null) {
            Log.d(TAG, "updateNotification: no current song, removing")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForegroundStarted = false
            return
        }

        // Increment generation — any in-flight fetch from a previous generation will be ignored
        val currentGen = ++notificationGeneration
        Log.d(TAG, "updateNotification [gen=$currentGen]: song='${song.title}', imageUrl='${song.imageUrl}'")

        // 1. Check cache by imageUrl (more stable key than stream URL)
        val cacheKey = song.imageUrl?.ifEmpty { null } ?: song.id
        val cachedBmp = bitmapCache.get(cacheKey)
        if (cachedBmp != null) {
            Log.d(TAG, "updateNotification [gen=$currentGen]: cache HIT")
            imageLoadJob?.cancel()
            postNotification(song, cachedBmp)
            return
        }

        // 2. Show placeholder immediately
        val defaultRes = if (song.imageRes != 0) song.imageRes else R.drawable.pic_4
        val defaultBitmap = try {
            BitmapFactory.decodeResource(resources, defaultRes)
        } catch (_: Exception) { null } ?: Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        postNotification(song, defaultBitmap)

        // 3. Cancel previous fetch and start a new one
        imageLoadJob?.cancel()
        val targetSong = song
        val artworkSource = song.imageUrl?.ifEmpty { null } ?: song.uriString?.ifEmpty { null }

        if (artworkSource.isNullOrEmpty()) {
            Log.w(TAG, "updateNotification [gen=$currentGen]: no artwork source")
            return
        }

        Log.d(TAG, "updateNotification [gen=$currentGen]: fetching from '$artworkSource'")

        imageLoadJob = serviceScope.launch {
            try {
                val fetchedBitmap = fetchBitmapFromUrl(artworkSource)
                ensureActive()

                // Check generation — if a newer updateNotification was called, skip this result
                if (notificationGeneration != currentGen) {
                    Log.d(TAG, "updateNotification [gen=$currentGen]: stale (current=$notificationGeneration), skipping")
                    return@launch
                }

                if (fetchedBitmap != null) {
                    bitmapCache.put(cacheKey, fetchedBitmap)
                    Log.d(TAG, "updateNotification [gen=$currentGen]: image fetched OK, posting")
                    if (MusicPlayerController.currentSong?.id == targetSong.id) {
                        withContext(Dispatchers.Main) {
                            postNotification(targetSong, fetchedBitmap)
                        }
                    }
                } else {
                    Log.w(TAG, "updateNotification [gen=$currentGen]: fetchBitmapFromUrl returned null")
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "updateNotification [gen=$currentGen]: fetch cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "updateNotification [gen=$currentGen]: error: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the current playing song"
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageLoadJob?.cancel()
        serviceScope.cancel()
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
