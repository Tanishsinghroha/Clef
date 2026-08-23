package com.music.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.music.app.api.UserFeedManager
import com.music.app.api.SpotifyPlaylistImporter
import com.music.app.api.MusicDownloader
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.app.ui.theme.MusicAppTheme
import com.music.app.api.JioSaavnApi
import com.music.app.api.SaavnTrack
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.statusBarsPadding
import com.music.app.api.NearbyShareController
import com.music.app.ui.NearbyShareTopPopup
import com.music.app.ui.NearbyRadarScreen
import com.music.app.ui.NearbySendSelectionScreen

// Helper to format milliseconds to mm:ss
fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

// Data class to represent a song
data class Song(
    val title: String,
    val artist: String,
    val imageRes: Int,
    val rawResId: Int = 0, // 0 means no audio file linked
    val uriString: String? = null,
    val imageUrl: String? = null, // URL for online album art
    val onlineStreamUrl: String? = null // Backup online stream URL for downloaded songs
) {
    val id: String get() = when {
        rawResId != 0 -> rawResId.toString()
        !uriString.isNullOrEmpty() -> uriString!!
        !onlineStreamUrl.isNullOrEmpty() -> onlineStreamUrl!!
        else -> "${title}_${artist}"
    }
}

// Data class to represent a playlist
data class Playlist(
    val name: String,
    val songs: SnapshotStateList<Song>,
    val coverImageRes: Int,
    val backgroundColor: Color,
    val textColor: Color,
    val subTextColor: Color
)

// Global list of all songs
val globalAllSongs = mutableStateListOf<Song>()

val globalPlaylists = mutableStateListOf(
    Playlist(
        name = "Favs",
        songs = mutableStateListOf(),
        coverImageRes = R.drawable.pic_4,
        backgroundColor = Color(0xFFE5DED5),
        textColor = Color.Black,
        subTextColor = Color.DarkGray
    ),
    Playlist(
        name = "Gym",
        songs = mutableStateListOf(),
        coverImageRes = R.drawable.pic_5,
        backgroundColor = Color(0xFF4B5A69),
        textColor = Color(0xFFE5DED5),
        subTextColor = Color(0xFFA0AAB5)
    ),
    Playlist(
        name = "Chill",
        songs = mutableStateListOf(),
        coverImageRes = R.drawable.pic_1,
        backgroundColor = Color(0xFF2E3A46),
        textColor = Color(0xFFE5DED5),
        subTextColor = Color(0xFFA0AAB5)
    ),
    Playlist(
        name = "Party",
        songs = mutableStateListOf(),
        coverImageRes = R.drawable.pic_3,
        backgroundColor = Color(0xFF1C1F26),
        textColor = Color(0xFFE5DED5),
        subTextColor = Color(0xFFA0AAB5)
    )
)

fun savePlaylists(context: android.content.Context) {
    val prefs = context.getSharedPreferences("music_app_prefs", android.content.Context.MODE_PRIVATE)

    // Save rich playlists data so both online & local songs in playlists are preserved
    val richPlaylistsData = globalPlaylists.joinToString("###") { playlist ->
        val songsData = playlist.songs.joinToString("@@@") { song ->
            "${song.title}:::${song.artist}:::${song.imageRes}:::${song.uriString ?: ""}:::${song.imageUrl ?: ""}:::${song.onlineStreamUrl ?: ""}"
        }
        "${playlist.name}===$songsData"
    }

    // Only save truly local / downloaded songs in custom_songs (Downloads page)
    val customSongs = globalAllSongs.filter {
        val uri = it.uriString
        uri != null && (uri.startsWith("file:") || uri.startsWith("content:") || (!uri.startsWith("http://") && !uri.startsWith("https://")))
    }
    val customSerialized = customSongs.joinToString(";;") { song ->
        "${song.title}::${song.artist}::${song.imageRes}::${song.uriString}::${song.imageUrl ?: ""}::${song.onlineStreamUrl ?: ""}"
    }

    prefs.edit()
        .putString("playlists_rich_data", richPlaylistsData)
        .putString("custom_songs", customSerialized)
        .apply()
}

fun loadPlaylists(context: android.content.Context) {
    val prefs = context.getSharedPreferences("music_app_prefs", android.content.Context.MODE_PRIVATE)

    // Load local custom songs only into Downloads / globalAllSongs
    val customData = prefs.getString("custom_songs", null)
    if (!customData.isNullOrEmpty()) {
        val customSongsList = customData.split(";;")
        for (songStr in customSongsList) {
            val parts = songStr.split("::")
            if (parts.size >= 4) {
                val uri = parts[3]
                // Only load if local file or content uri (ignore raw http streams)
                if (uri.isNotBlank() && (uri.startsWith("file:") || uri.startsWith("content:") || (!uri.startsWith("http://") && !uri.startsWith("https://")))) {
                    if (globalAllSongs.none { it.uriString == uri }) {
                        val imgUrl = if (parts.size >= 5 && parts[4].isNotBlank()) parts[4].replace("http://", "https://") else null
                        val fallbackStreamUrl = if (parts.size >= 6 && parts[5].isNotBlank()) parts[5] else null
                        globalAllSongs.add(
                            Song(
                                title = parts[0],
                                artist = parts[1],
                                imageRes = parts[2].toIntOrNull() ?: R.drawable.pic_4,
                                uriString = uri,
                                imageUrl = imgUrl,
                                onlineStreamUrl = fallbackStreamUrl
                            )
                        )
                    }
                }
            }
        }
    }

    // Load playlists from rich data
    val richData = prefs.getString("playlists_rich_data", null)
    if (!richData.isNullOrEmpty()) {
        val pParts = richData.split("###")
        for (i in pParts.indices) {
            if (i < globalPlaylists.size) {
                val split = pParts[i].split("===")
                if (split.isNotEmpty()) {
                    var pName = split[0]
                    if (i == 0 && pName == "Favorites") pName = "Favs"
                    if (i == 1 && pName == "Workout") pName = "Gym"
                    globalPlaylists[i] = globalPlaylists[i].copy(name = pName)
                    globalPlaylists[i].songs.clear()
                    if (split.size >= 2 && split[1].isNotEmpty()) {
                        val songEntries = split[1].split("@@@")
                        for (entry in songEntries) {
                            val parts = entry.split(":::")
                            if (parts.size >= 3) {
                                val uri = if (parts.size >= 4 && parts[3].isNotEmpty()) parts[3] else null
                                val img = if (parts.size >= 5 && parts[4].isNotEmpty()) parts[4] else null
                                val stream = if (parts.size >= 6 && parts[5].isNotEmpty()) parts[5] else null
                                globalPlaylists[i].songs.add(
                                    Song(
                                        title = parts[0],
                                        artist = parts[1],
                                        imageRes = parts[2].toIntOrNull() ?: R.drawable.pic_4,
                                        uriString = uri,
                                        imageUrl = img,
                                        onlineStreamUrl = stream
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Fallback to legacy loader if first run
        val data = prefs.getString("playlists_data", null)
        if (!data.isNullOrEmpty()) {
            val parts = data.split(";")
            if (parts.size == globalPlaylists.size) {
                for (i in parts.indices) {
                    val playlistData = parts[i].split("|")
                    if (playlistData.isNotEmpty()) {
                        var loadedName = playlistData[0]
                        if (i == 0 && loadedName == "Favorites") loadedName = "Favs"
                        if (i == 1 && loadedName == "Workout") loadedName = "Gym"
                        globalPlaylists[i] = globalPlaylists[i].copy(name = loadedName)
                    }
                    if (playlistData.size >= 2) {
                        val songIds = playlistData[1].split(",").filter { it.isNotEmpty() }
                        globalPlaylists[i].songs.clear()
                        for (idStr in songIds) {
                            val song = globalAllSongs.find { it.id == idStr }
                            if (song != null) {
                                globalPlaylists[i].songs.add(song)
                            }
                        }
                    } else {
                        globalPlaylists[i].songs.clear()
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    name: String,
    songCount: String,
    imageRes: Int,
    imageUrl: String? = null,
    backgroundColor: Color,
    textColor: Color,
    subTextColor: Color,
    imageOnRight: Boolean,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pillHeight = 120.dp
    val imageSize = 110.dp
    val cardWidth = 250.dp 
    // Tweak this value to move the picture further left or right!
    val imageHorizontalOffset = 5.dp 
    // Tweak this value to move the pill background itself left or right!
    val pillHorizontalOffset = 40.dp
    // Tweak this value to move the text left or right!
    val textHorizontalOffset = -18.dp

    Box(
        modifier = modifier
            .width(300.dp) // Total width to accommodate pill and overlapping image
            // We lock the height to the tallest visible element (pill or image) 
            // so the invisible shadow layers don't force the playlists apart!
            .height(if (pillHeight > imageSize) pillHeight else imageSize)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Pill body
        Card(
            shape = RoundedCornerShape(percent = 50),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .width(cardWidth)
                .height(pillHeight)
                .align(if (imageOnRight) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = if (imageOnRight) pillHorizontalOffset else -pillHorizontalOffset)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(if (imageOnRight) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(
                            start = if (imageOnRight) 24.dp else 0.dp,
                            end = if (!imageOnRight) 24.dp else 0.dp
                        )
                        .offset(x = if (imageOnRight) textHorizontalOffset else -textHorizontalOffset)
                        .fillMaxHeight()
                        .width(cardWidth - 110.dp), // Space for text
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = name, 
                        fontSize = 32.sp, 
                        color = textColor, 
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = songCount, 
                        fontSize = 15.sp, 
                        color = subTextColor
                    )
                }
            }
        }

        // Circular Image with manual shadow (centered)
        Box(
            modifier = Modifier
                .size(imageSize + 40.dp) // Extra room for the blur spread
                .align(if (imageOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                .offset(x = if (imageOnRight) imageHorizontalOffset else -imageHorizontalOffset)
        ) {
            // Blur layer 3 (outermost, lightest)
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(imageSize + 20.dp)
                    .align(Alignment.Center)
            ) {}

            // Blur layer 2 (middle)
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.30f),
                modifier = Modifier
                    .size(imageSize + 10.dp)
                    .align(Alignment.Center)
            ) {}

            // Blur layer 1 (innermost, darkest)
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(imageSize)
                    .align(Alignment.Center)
            ) {}

            // Actual image (first song photo if available, else playlist default)
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(imageSize)
                    .align(Alignment.Center)
            ) {
                if (imageUrl != null) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .placeholder(imageRes)
                            .error(imageRes)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// Song row item for All Songs and Playlist pages
@Composable
fun SongRow(
    song: Song,
    onPlayClick: () -> Unit = {},
    isAdded: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 4.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 2.dp,
    playButtonOffsetX: androidx.compose.ui.unit.Dp = 4.dp,
    titleToArtistGap: androidx.compose.ui.unit.Dp = (-4).dp,
    thumbnailSize: androidx.compose.ui.unit.Dp = 54.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onLongClick() })
                    }
                } else Modifier
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Song thumbnail (supports both local resources and online URLs)
        val imageModel: Any = song.imageUrl ?: song.uriString ?: song.imageRes
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(imageModel)
                .crossfade(true)
                .error(song.imageRes)
                .placeholder(song.imageRes)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(thumbnailSize)
                .clip(RoundedCornerShape(8.dp))
        )

        // Song title and artist
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(titleToArtistGap)
        ) {
            Text(
                text = song.title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Text(
                text = song.artist,
                fontSize = 13.5.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play button
        val isCurrentPlaying = MusicPlayerController.isPlaying && MusicPlayerController.isCurrentSong(song.id)
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier.offset(x = playButtonOffsetX)
        ) {
            if (isAdded) {
                Image(
                    painter = painterResource(id = R.drawable.ic_tick),
                    contentDescription = "Added",
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = if (isCurrentPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Now Playing bar at the bottom
@Composable
fun NowPlayingBar(
    song: Song,
    onClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCurrentPlaying = MusicPlayerController.isPlaying && MusicPlayerController.isCurrentSong(song.id)
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(isCurrentPlaying) {
        val updateState = {
            val current = MusicPlayerController.currentPosition
            val duration = MusicPlayerController.duration
            progress = if (duration > 0) current.toFloat() / duration.toFloat() else 0f
        }
        if (isCurrentPlaying) {
            while (true) {
                updateState()
                delay(100) // Update UI frequently for smooth progress
            }
        } else {
            updateState()
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Song thumbnail
                val imageModel: Any = song.imageUrl ?: song.uriString ?: song.imageRes
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(imageModel)
                        .crossfade(true)
                        .error(song.imageRes)
                        .placeholder(song.imageRes)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                // Song title and artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = song.artist,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play button
                val isCurrentPlaying = MusicPlayerController.isPlaying && MusicPlayerController.isCurrentSong(song.id)
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Image(
                        painter = painterResource(id = if (isCurrentPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            // Progress bar (starts from left, aligned exactly at the bottom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(15.dp) // Thicker box for easier tapping
                    .pointerInput(song) {
                        detectTapGestures { offset ->
                            val duration = MusicPlayerController.duration
                            if (duration > 0 && MusicPlayerController.isCurrentSong(song.id)) {
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val newPos = (fraction * duration).toInt()
                                MusicPlayerController.seekTo(newPos)
                                progress = fraction
                            }
                        }
                    },
                contentAlignment = Alignment.BottomStart
            ) {
                // The filled white portion of the progress bar
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth(if (progress > 0f) progress else 0.001f) // Valid fraction
                        .height(3.dp)
                ) {}
            }
        }
    }
}

// Audio waveform visualization at the top of the music player
@Composable
fun WaveformVisualization(
    isPlaying: Boolean,
    amplitudeScale: Float = 2.5f, // Adjustable amplitude scale multiplier
    modifier: Modifier = Modifier
) {
    val barHeights = remember {
        floatArrayOf(
            8f, 12f, 18f, 12f, 24f, 36f, 24f, 18f, 32f, 50f, 70f, 90f, 110f, 95f, 70f,
            50f, 30f, 45f, 60f, 75f, 60f, 45f, 30f, 20f, 40f, 55f, 45f, 35f, 20f,
            30f, 45f, 35f, 25f, 15f, 10f, 8f, 6f, 4f, 4f
        )
    }

    val audioMagnitudes = MusicPlayerController.audioMagnitudes

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(horizontal = 24.dp)
    ) {
        val totalBars = barHeights.size
        val barWidth = 4.dp.toPx()
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
        val availableWidth = size.width - (barWidth * totalBars)
        val gap = if (totalBars > 1) availableWidth / (totalBars - 1) else 0f
        val centerY = size.height / 2

        for (i in 0 until totalBars) {
            val baseHeight = barHeights[i]
            val mag = if (i < audioMagnitudes.size) audioMagnitudes[i] else 0f
            val targetScale = if (isPlaying) (0.2f + (mag * 1.4f)) * amplitudeScale else 0.1f
            val scale = targetScale.coerceIn(0.1f, 2.0f)
            val barHeightPx = (baseHeight * scale).coerceIn(3f, 120f).dp.toPx()

            val left = i * (barWidth + gap)
            val top = centerY - (barHeightPx / 2)

            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeightPx),
                cornerRadius = cornerRadius
            )
        }
    }
}

// Music Player Page
@Composable
fun MusicPlayerPage(
    song: Song,
    isPlaying: Boolean = false,
    onBackClick: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentImageIndex by remember { mutableStateOf((1..25).random()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(7000)
            currentImageIndex = (currentImageIndex % 25) + 1
        }
    }

    var progress by remember { mutableStateOf(0f) }
    var currentStr by remember { mutableStateOf("00:00") }
    var durationStr by remember { mutableStateOf("00:00") }
    var showAmplifierPopup by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, song) {
        val updateState = {
            val current = MusicPlayerController.currentPosition
            val duration = MusicPlayerController.duration
            progress = if (duration > 0) current.toFloat() / duration.toFloat() else 0f
            currentStr = formatDuration(current)
            durationStr = formatDuration(duration)
        }
        if (isPlaying && MusicPlayerController.isCurrentSong(song.id)) {
            while (true) {
                updateState()
                delay(100)
            }
        } else {
            updateState()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tweak these values to adjust positions!
        val backIconOffsetX = 0.dp
        val backIconOffsetY = 24.dp
        val waveformOffsetY = 45.dp
        // Tweak this to push the large song image UP or DOWN!
        val largeImageOffsetY = 40.dp
        // Tweak this to push the bottom section (song info, timeline, controls) UP!
        val bottomSectionPadding = 80.dp

        // Background
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button + Waveform area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                // Interactive Waveform visualization at the top - click to open Amplifier & Audio FX
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .offset(y = waveformOffsetY)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAmplifierPopup = true },
                    contentAlignment = Alignment.Center
                ) {
                    WaveformVisualization(
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Back button at top-left (rotated downwards to collapse player)
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = backIconOffsetX, y = backIconOffsetY)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Collapse",
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(-90f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Large album art
            val currentDrawableRes = remember(currentImageIndex) {
                context.resources.getIdentifier("slideshow_$currentImageIndex", "drawable", context.packageName)
            }
            Crossfade(
                targetState = currentDrawableRes,
                animationSpec = androidx.compose.animation.core.tween(2000),
                label = "slideshow",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .offset(y = largeImageOffsetY)
                    .clip(RoundedCornerShape(20.dp))
            ) { resId ->
                if (resId == 0 && song.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(song.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = if (resId != 0) resId else song.imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Song info: thumbnail + title + artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Small thumbnail
                val imageModel: Any = song.imageUrl ?: song.uriString ?: song.imageRes
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .error(song.imageRes)
                        .placeholder(song.imageRes)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Song name and artist
                Column {
                    Text(
                        text = song.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = song.artist,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(24.dp) // Thicker box for easier tapping
                    .pointerInput(song) {
                        detectTapGestures { offset ->
                            val duration = MusicPlayerController.duration
                            if (duration > 0 && MusicPlayerController.isCurrentSong(song.id)) {
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val newPos = (fraction * duration).toInt()
                                MusicPlayerController.seekTo(newPos)
                                progress = fraction
                                currentStr = formatDuration(newPos)
                            }
                        }
                    }
            ) {
                // Background track
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.Center)
                ) {}
                // Filled progress
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth(if (progress > 0f) progress else 0.001f)
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                ) {}
            }

            // Time stamps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentStr,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = durationStr,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Previous
                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_previous),
                        contentDescription = "Previous",
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Pause / Play (large)
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp)
                ) {
                    Image(
                        painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(23.dp)
                    )
                }
                // Next
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_next),
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp)
                    )
                }

            }

            Spacer(modifier = Modifier.height(bottomSectionPadding))
        }

        // Amplifier & Audio FX Popup Dialog
        if (showAmplifierPopup) {
            AmplifierPopup(
                onDismiss = { showAmplifierPopup = false }
            )
        }
    }
}

// Inside Playlist Page
@Composable
fun InsidePlaylistPage(
    playlist: Playlist,
    activeBarSong: Song? = null,
    onNameChange: (String) -> Unit = {},
    onPlaySong: (Song, List<Song>?) -> Unit = { _, _ -> },
    onAddFromLocalClick: () -> Unit = {},
    onAddFromOnlineClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNowPlayingClick: (Song) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isActionsExpanded by remember { mutableStateOf(false) }
    var showSpotifyDialog by remember { mutableStateOf(false) }
    var spotifyUrlInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importProgressText by remember { mutableStateOf("") }
    var importCurrent by remember { mutableIntStateOf(0) }
    var importTotal by remember { mutableIntStateOf(0) }
    var importError by remember { mutableStateOf<String?>(null) }
    var shouldReplaceExisting by remember { mutableStateOf(false) }

    var isDownloadingPlaylist by remember { mutableStateOf(false) }

    val triggerDownloadPlaylist = {
        if (!isDownloadingPlaylist) {
            // First check all songs against downloaded cache/disk to immediately resolve any that are already downloaded
            var alreadyDownloadedCount = 0
            val songsToDownload = mutableListOf<Song>()

            for (song in playlist.songs) {
                val existing = MusicDownloader.findExistingDownloadedSong(context, song, globalAllSongs)
                if (existing != null) {
                    val idx = playlist.songs.indexOfFirst { it.title == song.title && it.artist == song.artist }
                    if (idx != -1) {
                        playlist.songs[idx] = existing
                    }
                    if (globalAllSongs.none { it.uriString == existing.uriString }) {
                        globalAllSongs.add(existing)
                    }
                    alreadyDownloadedCount++
                } else {
                    songsToDownload.add(song)
                }
            }
            savePlaylists(context)

            if (songsToDownload.isEmpty()) {
                android.widget.Toast.makeText(
                    context,
                    "All ${playlist.songs.size} songs are already downloaded!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                isDownloadingPlaylist = true
                coroutineScope.launch {
                    var downloadedCount = 0
                    for (i in songsToDownload.indices) {
                        val song = songsToDownload[i]
                        val downloaded = MusicDownloader.downloadSong(context, song, globalAllSongs)
                        if (downloaded != null) {
                            val idx = playlist.songs.indexOfFirst { it.title == song.title && it.artist == song.artist }
                            if (idx != -1) {
                                playlist.songs[idx] = downloaded
                            }
                            if (globalAllSongs.none { it.uriString == downloaded.uriString }) {
                                globalAllSongs.add(downloaded)
                            }
                            downloadedCount++
                            savePlaylists(context)
                        }
                    }
                    isDownloadingPlaylist = false
                    val message = if (alreadyDownloadedCount > 0) {
                        "Downloaded $downloadedCount new songs ($alreadyDownloadedCount already downloaded)!"
                    } else {
                        "Downloaded $downloadedCount songs for offline play!"
                    }
                    android.widget.Toast.makeText(
                        context,
                        message,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    if (showSpotifyDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    showSpotifyDialog = false
                    importError = null
                }
            },
            title = {
                Text(
                    text = "Link Playlist",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Paste any public playlist link (e.g. Spotify) to import its tracks directly into ${playlist.name}.",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (spotifyUrlInput.isEmpty()) {
                            Text(
                                text = "https://open.spotify.com/playlist/...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = spotifyUrlInput,
                            onValueChange = {
                                spotifyUrlInput = it
                                importError = null
                            },
                            singleLine = true,
                            enabled = !isImporting,
                            textStyle = TextStyle(
                                fontSize = 13.5.sp,
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (importError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = importError!!,
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp
                        )
                    }

                    if (isImporting) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (importTotal > 0) importCurrent.toFloat() / importTotal.toFloat() else 0f
                            },
                            color = Color.White,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = importProgressText,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (spotifyUrlInput.isNotBlank() && !isImporting) {
                            isImporting = true
                            importError = null
                            coroutineScope.launch {
                                try {
                                    val result = SpotifyPlaylistImporter.importSpotifyPlaylist(
                                        context = context,
                                        playlistUrlOrId = spotifyUrlInput,
                                        onProgress = { current, total, trackName ->
                                            importCurrent = current
                                            importTotal = total
                                            importProgressText = "Matching track $current of $total: $trackName"
                                        }
                                    )

                                    if (result.importedSongs.isNotEmpty()) {
                                        if (shouldReplaceExisting) {
                                            playlist.songs.clear()
                                        }
                                        for (song in result.importedSongs) {
                                            if (playlist.songs.none { it.title == song.title && it.artist == song.artist }) {
                                                playlist.songs.add(song)
                                            }
                                        }
                                        savePlaylists(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Linked ${result.importedSongs.size} tracks into ${playlist.name}!",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        showSpotifyDialog = false
                                        spotifyUrlInput = ""
                                    } else {
                                        importError = "No playable tracks could be matched from this playlist."
                                    }
                                } catch (e: Exception) {
                                    importError = e.message ?: "Failed to import playlist. Make sure the link is public."
                                } finally {
                                    isImporting = false
                                }
                            }
                        }
                    },
                    enabled = spotifyUrlInput.isNotBlank() && !isImporting
                ) {
                    Text(
                        text = if (isImporting) "Linking..." else "Link",
                        color = if (spotifyUrlInput.isNotBlank() && !isImporting) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                if (!isImporting) {
                    TextButton(onClick = { showSpotifyDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF242424),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tweak this value to move the top bar (back arrow, +, title) up or down!
        val topBarVerticalOffset = 15.dp
        // Tweak this value to move the Now Playing bar up or down!
        val nowPlayingVerticalOffset = -15.dp

        // Background
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: Back arrow, playlist name, add button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .offset(y = topBarVerticalOffset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back arrow
                IconButton(onClick = onBackClick) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Playlist name
                BasicTextField(
                    value = playlist.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                // Action icons (Files, Cloud, Plus)
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .animateContentSize(animationSpec = spring()),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = isActionsExpanded,
                            enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                            exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        isActionsExpanded = false
                                        onAddFromLocalClick()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_files),
                                        contentDescription = "Add from Downloads",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isActionsExpanded = false
                                        onAddFromOnlineClick()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_cloud),
                                        contentDescription = "Add from Online",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Plus / Close Button
                        IconButton(
                            onClick = { isActionsExpanded = !isActionsExpanded },
                            modifier = Modifier.size(48.dp)
                        ) {
                            val rotation by animateFloatAsState(
                                targetValue = if (isActionsExpanded) 45f else 0f,
                                label = "playlist_plus_rotation"
                            )
                            Image(
                                painter = painterResource(id = R.drawable.ic_plus),
                                contentDescription = "Toggle Add Options",
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(rotation)
                            )
                        }
                    }
                }
            }

            // Large cover image (displays first song photo or playlist default)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                val firstSong = playlist.songs.firstOrNull()
                val context = androidx.compose.ui.platform.LocalContext.current
                if (firstSong?.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(firstSong.imageUrl)
                            .placeholder(firstSong.imageRes)
                            .error(firstSong.imageRes)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Image(
                        painter = painterResource(id = firstSong?.imageRes ?: playlist.coverImageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            // Action row under the cover image: Song count, Download button, Link button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${playlist.songs.size} Songs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Download Playlist Button
                    IconButton(
                        onClick = { triggerDownloadPlaylist() },
                        enabled = !isDownloadingPlaylist,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isDownloadingPlaylist) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            val allLocal = playlist.songs.isNotEmpty() && playlist.songs.all {
                                MusicDownloader.findExistingDownloadedSong(context, it, globalAllSongs) != null ||
                                (it.uriString != null && !it.uriString.startsWith("http://") && !it.uriString.startsWith("https://"))
                            }
                            Image(
                                painter = painterResource(id = if (allLocal) R.drawable.ic_downloaded else R.drawable.ic_download),
                                contentDescription = "Download Playlist",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Link Button
                    IconButton(
                        onClick = { showSpotifyDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_link),
                            contentDescription = "Link Playlist",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Song list
            val context = androidx.compose.ui.platform.LocalContext.current
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(playlist.songs) { song ->
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Remove from Playlist") },
                            text = { Text("Are you sure you want to remove '${song.title}' from this playlist?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteDialog = false
                                    playlist.songs.remove(song)
                                    savePlaylists(context)
                                }) {
                                    Text("Remove", color = Color.Red)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Cancel", color = Color.White)
                                }
                            },
                            containerColor = Color(0xFF2C2C2C),
                            titleContentColor = Color.White,
                            textContentColor = Color.LightGray
                        )
                    }

                    SongRow(
                        song = song,
                        onLongClick = { showDeleteDialog = true },
                        onPlayClick = { onPlaySong(song, playlist.songs) }
                    )
                }
            }

            // Now Playing bar at bottom - clicking opens music player
            if (activeBarSong != null) {
                NowPlayingBar(
                    song = activeBarSong,
                    onClick = { onNowPlayingClick(activeBarSong) },
                    onPlayPauseClick = { onPlaySong(activeBarSong, null) },
                    modifier = Modifier.offset(y = nowPlayingVerticalOffset)
                )
            }
        }
    }
}

@Composable
fun Page1(
    activeBarSong: Song? = null,
    onPlaylistClick: (Playlist) -> Unit = {},
    onSwipeToAllSongs: () -> Unit = {},
    onSwipeToOnlineSongs: () -> Unit = {},
    onNowPlayingClick: (Song) -> Unit = {},
    onPlaySong: (Song, List<Song>?) -> Unit = { _, _ -> },
    onOpenNearbyShare: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playlistToClear by remember { mutableStateOf<Playlist?>(null) }

    if (playlistToClear != null) {
        val targetPlaylist = playlistToClear!!
        AlertDialog(
            onDismissRequest = { playlistToClear = null },
            title = {
                Text(
                    text = "Clear '${targetPlaylist.name}'?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove all ${targetPlaylist.songs.size} songs from '${targetPlaylist.name}'? The playlist itself will not be deleted.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = targetPlaylist.songs.size
                        targetPlaylist.songs.clear()
                        savePlaylists(context)
                        android.widget.Toast.makeText(
                            context,
                            "Cleared $count songs from '${targetPlaylist.name}'",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        playlistToClear = null
                    }
                ) {
                    Text("Clear", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToClear = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF2C2C2C),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Tweak this value to increase or decrease the space between playlists
    val playlistGap = 8.dp

    val playlists = globalPlaylists
    Box(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (delta > 20) {
                        onOpenNearbyShare()
                    }
                }
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val p0FirstSong = playlists.getOrNull(0)?.songs?.firstOrNull()
            PlaylistCard(
                name = playlists[0].name,
                songCount = "${playlists[0].songs.size} Songs",
                imageRes = p0FirstSong?.imageRes ?: playlists[0].coverImageRes,
                imageUrl = p0FirstSong?.imageUrl,
                backgroundColor = playlists[0].backgroundColor,
                textColor = playlists[0].textColor,
                subTextColor = playlists[0].subTextColor,
                imageOnRight = true,
                onClick = { onPlaylistClick(playlists[0]) },
                onLongClick = { playlistToClear = playlists[0] },
                modifier = Modifier.offset(x = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(playlistGap))
            
            val p1FirstSong = playlists.getOrNull(1)?.songs?.firstOrNull()
            PlaylistCard(
                name = playlists[1].name,
                songCount = "${playlists[1].songs.size} Songs",
                imageRes = p1FirstSong?.imageRes ?: playlists[1].coverImageRes,
                imageUrl = p1FirstSong?.imageUrl,
                backgroundColor = playlists[1].backgroundColor,
                textColor = playlists[1].textColor,
                subTextColor = playlists[1].subTextColor,
                imageOnRight = false,
                onClick = { onPlaylistClick(playlists[1]) },
                onLongClick = { playlistToClear = playlists[1] },
                modifier = Modifier.offset(x = -16.dp)
            )
            
            Spacer(modifier = Modifier.height(playlistGap))
            
            val p2FirstSong = playlists.getOrNull(2)?.songs?.firstOrNull()
            PlaylistCard(
                name = playlists[2].name,
                songCount = "${playlists[2].songs.size} Songs",
                imageRes = p2FirstSong?.imageRes ?: playlists[2].coverImageRes,
                imageUrl = p2FirstSong?.imageUrl,
                backgroundColor = playlists[2].backgroundColor,
                textColor = playlists[2].textColor,
                subTextColor = playlists[2].subTextColor,
                imageOnRight = true,
                onClick = { onPlaylistClick(playlists[2]) },
                onLongClick = { playlistToClear = playlists[2] },
                modifier = Modifier.offset(x = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(playlistGap))
            
            val p3FirstSong = playlists.getOrNull(3)?.songs?.firstOrNull()
            PlaylistCard(
                name = playlists[3].name,
                songCount = "${playlists[3].songs.size} Songs",
                imageRes = p3FirstSong?.imageRes ?: playlists[3].coverImageRes,
                imageUrl = p3FirstSong?.imageUrl,
                backgroundColor = playlists[3].backgroundColor,
                textColor = playlists[3].textColor,
                subTextColor = playlists[3].subTextColor,
                imageOnRight = false,
                onClick = { onPlaylistClick(playlists[3]) },
                onLongClick = { playlistToClear = playlists[3] },
                modifier = Modifier.offset(x = -16.dp)
            )
        }

        // Now Playing bar at bottom
        if (activeBarSong != null) {
            NowPlayingBar(
                song = activeBarSong,
                onClick = { onNowPlayingClick(activeBarSong) },
                onPlayPauseClick = { onPlaySong(activeBarSong, null) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-15).dp)
            )
        }
    }
}

// All Songs Page
@Composable
fun AllSongsPage(
    activeBarSong: Song? = null,
    addingToPlaylist: Playlist? = null,
    onPlaylistModified: () -> Unit = {},
    onPlaySong: (Song, List<Song>?) -> Unit = { _, _ -> },
    onBackSwipe: () -> Unit = {},
    onNowPlayingClick: (Song) -> Unit = {},
    onSongAdded: (Song) -> Unit = {},
    onCloudClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {}

                var fileName = "Unknown"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex) ?: "Unknown"
                        }
                    }
                }
                if (fileName.lastIndexOf('.') > 0) {
                    fileName = fileName.substring(0, fileName.lastIndexOf('.'))
                }

                var title = "Unknown Title"
                var artist = "Unknown Artist"
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName
                    artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    retriever.release()
                } catch (e: Exception) {
                    title = fileName
                }
                
                val randomIndex = (1..25).random()
                val imageResId = context.resources.getIdentifier("slideshow_$randomIndex", "drawable", context.packageName)
                val newSong = Song(
                    title = title,
                    artist = artist,
                    imageRes = if (imageResId != 0) imageResId else R.drawable.pic_4,
                    uriString = uri.toString()
                )
                onSongAdded(newSong)
            }
        }
    )

    // State to track if action icons are expanded
    var isActionsExpanded by remember { mutableStateOf(false) }

    // State for search query
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // =========================================================================
            // Adjust these values to easily fine-tune margins, offsets, and gaps!
            // =========================================================================
            val screenTopPadding = 38.dp             // Top padding to shift everything down below status bar / camera notch
            val nowPlayingVerticalOffset = -15.dp
            val titleOffsetX = 0.dp                  // Horizontal offset for Downloads title & count
            val titleOffsetY = 8.dp                  // Vertical offset for Downloads title & count
            val titleToCountGap = (-4).dp            // Vertical gap between Downloads title and count
            val plusButtonOffsetX = 0.dp             // Horizontal offset for plus / action button
            val plusButtonOffsetY = 8.dp             // Vertical offset for plus / action button
            val downloadsHeaderToSearchBarGap = 24.dp // Gap between Downloads title and search bar
            val downloadsSearchBarOffsetX = 0.dp     // Horizontal offset for search bar
            val downloadsSearchBarOffsetY = 0.dp     // Vertical offset for search bar
            val downloadsSearchBarHeight = 50.dp     // Height of search bar
            val downloadsSearchBarMarginBottom = 16.dp // Bottom margin under search bar

            // Tweak row horizontal margin (left arrow) & play button offset (right arrow)
            val allSongsRowHorizontalPadding = 4.dp  // [Left Arrow] Margin between left edge & thumbnail
            val allSongsRowVerticalPadding = 2.dp    // Vertical padding between song rows
            val allSongsPlayButtonOffsetX = 4.dp     // [Right Arrow] Offset for play button to right edge
            val allSongsTitleToArtistGap = (-4).dp   // Vertical gap between Song Title and Artist text
            val allSongsThumbnailSize = 54.dp        // Size of song thumbnail
            // =========================================================================

            Spacer(modifier = Modifier.height(screenTopPadding))

            // Top bar: Title + icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and count
                Column(
                    modifier = Modifier.offset(x = titleOffsetX, y = titleOffsetY),
                    verticalArrangement = Arrangement.spacedBy(titleToCountGap)
                ) {
                    Text(
                        text = if (addingToPlaylist != null) "Add to ${addingToPlaylist.name}" else "Downloads",
                        fontSize = if (addingToPlaylist != null) 22.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Total ${globalAllSongs.size}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // Action icons (Files, Cloud, Plus)
                Box(
                    modifier = Modifier
                        .offset(x = plusButtonOffsetX, y = plusButtonOffsetY)
                        .height(48.dp)
                        .animateContentSize(animationSpec = spring()),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (addingToPlaylist != null) {
                            // Done Button in Selection Mode
                            IconButton(
                                onClick = onBackSwipe,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_done),
                                    contentDescription = "Done",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            AnimatedVisibility(
                                visible = isActionsExpanded,
                                enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                                exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { launcher.launch(arrayOf("audio/*")) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_files),
                                            contentDescription = "Files",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onCloudClick,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_cloud),
                                            contentDescription = "Cloud",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Plus / Close Button
                            IconButton(
                                onClick = { isActionsExpanded = !isActionsExpanded },
                                modifier = Modifier.size(48.dp)
                            ) {
                                val rotation by animateFloatAsState(
                                    targetValue = if (isActionsExpanded) 45f else 0f,
                                    label = "plus_rotation"
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.ic_plus),
                                    contentDescription = "Toggle Actions",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(rotation)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(downloadsHeaderToSearchBarGap))

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = downloadsSearchBarOffsetX, y = downloadsSearchBarOffsetY)
                    .padding(bottom = downloadsSearchBarMarginBottom)
                    .height(downloadsSearchBarHeight)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color(0xFF2C2C2C))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search songs or artists...",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Song list
            val filteredSongs = remember(searchQuery, globalAllSongs.size, globalAllSongs.toList()) {
                if (searchQuery.isEmpty()) {
                    globalAllSongs
                } else {
                    globalAllSongs.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(filteredSongs) { song ->
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Remove Song") },
                            text = { Text("Are you sure you want to remove '${song.title}' from your library?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDeleteDialog = false
                                    if (MusicPlayerController.isCurrentSong(song.id)) {
                                        MusicPlayerController.stop()
                                    }
                                    globalAllSongs.remove(song)
                                    globalPlaylists.forEach { it.songs.remove(song) }
                                    onPlaylistModified()
                                }) {
                                    Text("Remove", color = Color.Red)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Cancel", color = Color.White)
                                }
                            },
                            containerColor = Color(0xFF2C2C2C),
                            titleContentColor = Color.White,
                            textContentColor = Color.LightGray
                        )
                    }

                    val isAdded = addingToPlaylist?.songs?.contains(song) == true
                    SongRow(
                        song = song,
                        isAdded = isAdded,
                        onLongClick = if (song.uriString != null) { { showDeleteDialog = true } } else null,
                        horizontalPadding = allSongsRowHorizontalPadding,
                        verticalPadding = allSongsRowVerticalPadding,
                        playButtonOffsetX = allSongsPlayButtonOffsetX,
                        titleToArtistGap = allSongsTitleToArtistGap,
                        thumbnailSize = allSongsThumbnailSize,
                        onPlayClick = {
                            if (addingToPlaylist != null) {
                                if (isAdded) {
                                    addingToPlaylist.songs.remove(song)
                                } else {
                                    addingToPlaylist.songs.add(song)
                                }
                                onPlaylistModified()
                            } else {
                                onPlaySong(song, filteredSongs)
                            }
                        }
                    )
                }
            }

            
            // Now Playing bar at bottom
            if (activeBarSong != null) {
                NowPlayingBar(
                    song = activeBarSong,
                    onClick = { onNowPlayingClick(activeBarSong) },
                    onPlayPauseClick = { onPlaySong(activeBarSong, null) },
                    modifier = Modifier.offset(y = nowPlayingVerticalOffset)
                )
            }
        }


    }
}

// Online Songs Page - Browse and stream songs from JioSaavn
// Data class for Trending Indian Artist Albums
data class TrendingArtistAlbum(
    val title: String,
    val artist: String,
    val searchQuery: String,
    val imageRes: Int,
    val imageUrl: String? = null,
    val gradientColors: List<Color>
)

val trendingIndianAlbums = listOf(
    TrendingArtistAlbum(
        title = "Arijit Singh Hits",
        artist = "Arijit Singh",
        searchQuery = "Arijit Singh",
        imageRes = R.drawable.artist_arijit_singh,
        imageUrl = "https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg",
        gradientColors = listOf(Color(0xFF2B5876), Color(0xFF4E4376))
    ),
    TrendingArtistAlbum(
        title = "A.R. Rahman Classics",
        artist = "A.R. Rahman",
        searchQuery = "A.R. Rahman",
        imageRes = R.drawable.artist_ar_rahman,
        imageUrl = "https://c.saavncdn.com/artists/AR_Rahman_002_20210120084455_500x500.jpg",
        gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    ),
    TrendingArtistAlbum(
        title = "Anirudh Blockbusters",
        artist = "Anirudh Ravichander",
        searchQuery = "Anirudh Ravichander",
        imageRes = R.drawable.artist_anirudh,
        imageUrl = "https://c.saavncdn.com/artists/Anirudh_Ravichander_003_20260121134149_500x500.jpg",
        gradientColors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
    ),
    TrendingArtistAlbum(
        title = "Pritam Romance",
        artist = "Pritam",
        searchQuery = "Pritam",
        imageRes = R.drawable.artist_pritam,
        imageUrl = "https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg",
        gradientColors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
    ),
    TrendingArtistAlbum(
        title = "Shreya Melodies",
        artist = "Shreya Ghoshal",
        searchQuery = "Shreya Ghoshal",
        imageRes = R.drawable.artist_shreya_ghoshal,
        imageUrl = "https://c.saavncdn.com/artists/Shreya_Ghoshal_007_20241101074144_500x500.jpg",
        gradientColors = listOf(Color(0xFFF857A6), Color(0xFFFF5858))
    ),
    TrendingArtistAlbum(
        title = "Diljit Punjabi Hits",
        artist = "Diljit Dosanjh",
        searchQuery = "Diljit Dosanjh",
        imageRes = R.drawable.artist_diljit_dosanjh,
        imageUrl = "https://c.saavncdn.com/artists/Diljit_Dosanjh_005_20231025073054_500x500.jpg",
        gradientColors = listOf(Color(0xFFF2994A), Color(0xFFF2C94C))
    ),
    TrendingArtistAlbum(
        title = "Jubin Hits",
        artist = "Jubin Nautiyal",
        searchQuery = "Jubin Nautiyal",
        imageRes = R.drawable.artist_jubin_nautiyal,
        imageUrl = "https://c.saavncdn.com/artists/Jubin_Nautiyal_003_20231130204020_500x500.jpg",
        gradientColors = listOf(Color(0xFF1FA2FF), Color(0xFF12D8FA))
    ),
    TrendingArtistAlbum(
        title = "Karan Aujla Top",
        artist = "Karan Aujla",
        searchQuery = "Karan Aujla",
        imageRes = R.drawable.artist_karan_aujla,
        imageUrl = "https://c.saavncdn.com/artists/Karan_Aujla_004_20260810121947_500x500.jpg",
        gradientColors = listOf(Color(0xFF3A1C71), Color(0xFFD76D77))
    ),
    TrendingArtistAlbum(
        title = "Badshah Hits",
        artist = "Badshah",
        searchQuery = "Badshah",
        imageRes = R.drawable.artist_badshah,
        imageUrl = "https://c.saavncdn.com/artists/Badshah_006_20241118064015_500x500.jpg",
        gradientColors = listOf(Color(0xFFB06AB3), Color(0xFF4568DC))
    ),
    TrendingArtistAlbum(
        title = "Sonu Nigam Classics",
        artist = "Sonu Nigam",
        searchQuery = "Sonu Nigam",
        imageRes = R.drawable.artist_sonu_nigam,
        imageUrl = "https://c.saavncdn.com/artists/Sonu_Nigam_003_20260813182013_500x500.jpg",
        gradientColors = listOf(Color(0xFF134E5E), Color(0xFF71B280))
    ),
    TrendingArtistAlbum(
        title = "Sid Sriram Magic",
        artist = "Sid Sriram",
        searchQuery = "Sid Sriram",
        imageRes = R.drawable.artist_sid_sriram,
        imageUrl = "https://c.saavncdn.com/artists/Sid_Sriram_005_20240425180600_500x500.jpg",
        gradientColors = listOf(Color(0xFF5F2C82), Color(0xFF49A09D))
    ),
    TrendingArtistAlbum(
        title = "Honey Singh Grooves",
        artist = "Yo Yo Honey Singh",
        searchQuery = "Yo Yo Honey Singh",
        imageRes = R.drawable.artist_honey_singh,
        imageUrl = "https://c.saavncdn.com/artists/Yo_Yo_Honey_Singh_004_20260811095253_500x500.jpg",
        gradientColors = listOf(Color(0xFFD31027), Color(0xFFEA384D))
    ),
    TrendingArtistAlbum(
        title = "Neha Kakkar Beats",
        artist = "Neha Kakkar",
        searchQuery = "Neha Kakkar",
        imageRes = R.drawable.artist_neha_kakkar,
        imageUrl = "https://c.saavncdn.com/artists/Neha_Kakkar_007_20241212115832_500x500.jpg",
        gradientColors = listOf(Color(0xFFFC5C7D), Color(0xFF6A82FB))
    ),
    TrendingArtistAlbum(
        title = "Armaan Malik Vibes",
        artist = "Armaan Malik",
        searchQuery = "Armaan Malik",
        imageRes = R.drawable.artist_armaan_malik,
        imageUrl = "https://c.saavncdn.com/artists/Armaan_Malik_006_20260813132832_500x500.jpg",
        gradientColors = listOf(Color(0xFF4776E6), Color(0xFF8E54E9))
    ),
    TrendingArtistAlbum(
        title = "Sidhu Moose Wala",
        artist = "Sidhu Moose Wala",
        searchQuery = "Sidhu Moose Wala",
        imageRes = R.drawable.pic_4,
        imageUrl = "https://c.saavncdn.com/026/Moosetape-Punjabi-2021-20210514201300-500x500.jpg",
        gradientColors = listOf(Color(0xFF800000), Color(0xFFB22222))
    ),
    TrendingArtistAlbum(
        title = "AP Dhillon Hits",
        artist = "AP Dhillon",
        searchQuery = "AP Dhillon",
        imageRes = R.drawable.pic_4,
        imageUrl = "https://c.saavncdn.com/391/With-You-Punjabi-2023-20230811053427-500x500.jpg",
        gradientColors = listOf(Color(0xFF1E3C72), Color(0xFF2A5298))
    ),
    TrendingArtistAlbum(
        title = "Shubh Top Hits",
        artist = "Shubh",
        searchQuery = "Shubh",
        imageRes = R.drawable.pic_4,
        imageUrl = "https://c.saavncdn.com/337/Still-Rollin-Punjabi-2023-20230519124018-500x500.jpg",
        gradientColors = listOf(Color(0xFF3E5151), Color(0xFFDECBA4))
    ),
    TrendingArtistAlbum(
        title = "Sunidhi Melodies",
        artist = "Sunidhi Chauhan",
        searchQuery = "Sunidhi Chauhan",
        imageRes = R.drawable.artist_sunidhi_chauhan,
        imageUrl = "https://c.saavncdn.com/artists/Sunidhi_Chauhan_005_20250515061617_500x500.jpg",
        gradientColors = listOf(Color(0xFFEB3349), Color(0xFFF45C43))
    )
)

// Data class for Categories (Apple Music Style)
data class MusicCategory(
    val id: String,
    val name: String,
    val searchQuery: String,
    val accentColor: Color,
    val imageRes: Int = R.drawable.pic_4,
    val imageUrl: String? = null
)

val musicCategories = listOf(
    MusicCategory(
        id = "bollywood",
        name = "Bollywood\nHits",
        searchQuery = "bollywood top hits",
        accentColor = Color(0xFFE03131),
        imageRes = R.drawable.artist_arijit_singh,
        imageUrl = "https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg"
    ),
    MusicCategory(
        id = "pop",
        name = "Pop",
        searchQuery = "hindi pop songs",
        accentColor = Color(0xFFE64980),
        imageRes = R.drawable.artist_armaan_malik,
        imageUrl = "https://c.saavncdn.com/artists/Armaan_Malik_006_20260813132832_500x500.jpg"
    ),
    MusicCategory(
        id = "hits",
        name = "Hits",
        searchQuery = "top hindi songs",
        accentColor = Color(0xFFFAB005),
        imageRes = R.drawable.artist_diljit_dosanjh,
        imageUrl = "https://c.saavncdn.com/artists/Diljit_Dosanjh_005_20231025073054_500x500.jpg"
    ),
    MusicCategory(
        id = "rock",
        name = "Rock & Indie",
        searchQuery = "indian rock songs hindi",
        accentColor = Color(0xFFFD7E14),
        imageRes = R.drawable.artist_anirudh,
        imageUrl = "https://c.saavncdn.com/artists/Anirudh_Ravichander_003_20260121134149_500x500.jpg"
    ),
    MusicCategory(
        id = "romance",
        name = "Romance",
        searchQuery = "romantic hindi songs hits",
        accentColor = Color(0xFFF03E3E),
        imageRes = R.drawable.artist_shreya_ghoshal,
        imageUrl = "https://c.saavncdn.com/artists/Shreya_Ghoshal_007_20241101074144_500x500.jpg"
    ),
    MusicCategory(
        id = "holiday",
        name = "Chill &\nUnplugged",
        searchQuery = "chill acoustic hindi songs",
        accentColor = Color(0xFF845EF7),
        imageRes = R.drawable.artist_ar_rahman,
        imageUrl = "https://c.saavncdn.com/artists/AR_Rahman_002_20210120084455_500x500.jpg"
    ),
    MusicCategory(
        id = "bestof2025",
        name = "Best of\n2025",
        searchQuery = "best hindi songs 2025 hits",
        accentColor = Color(0xFFBE4BDB),
        imageRes = R.drawable.artist_pritam,
        imageUrl = "https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg"
    ),
    MusicCategory(
        id = "radio",
        name = "Radio\nHits",
        searchQuery = "superhit hindi songs",
        accentColor = Color(0xFFE03131),
        imageRes = R.drawable.artist_neha_kakkar,
        imageUrl = "https://c.saavncdn.com/artists/Neha_Kakkar_007_20241212115832_500x500.jpg"
    ),
    MusicCategory(
        id = "devotional",
        name = "Devotional",
        searchQuery = "bhakti devotional songs hindi",
        accentColor = Color(0xFFFF922B),
        imageRes = R.drawable.artist_sonu_nigam,
        imageUrl = "https://c.saavncdn.com/artists/Sonu_Nigam_003_20260813182013_500x500.jpg"
    ),
    MusicCategory(
        id = "hiphop",
        name = "Hip-Hop",
        searchQuery = "hindi rap hip hop songs",
        accentColor = Color(0xFF228BE6),
        imageRes = R.drawable.artist_badshah,
        imageUrl = "https://c.saavncdn.com/artists/Badshah_006_20241118064015_500x500.jpg"
    ),
    MusicCategory(
        id = "punjabi",
        name = "Punjabi\nBeats",
        searchQuery = "top punjabi songs hits",
        accentColor = Color(0xFFFF6B6B),
        imageRes = R.drawable.artist_karan_aujla,
        imageUrl = "https://c.saavncdn.com/artists/Karan_Aujla_004_20260810121947_500x500.jpg"
    ),
    MusicCategory(
        id = "party",
        name = "Party\n& Dance",
        searchQuery = "party dance club hindi songs",
        accentColor = Color(0xFF12B886),
        imageRes = R.drawable.artist_honey_singh,
        imageUrl = "https://c.saavncdn.com/artists/Yo_Yo_Honey_Singh_004_20260811095253_500x500.jpg"
    )
)

// Data class for Recent Searches
data class RecentSearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val imageRes: Int = R.drawable.pic_4,
    val streamUrl: String? = null,
    val isArtist: Boolean = false,
    val searchQuery: String = ""
)

fun saveRecentSearches(context: android.content.Context, list: List<RecentSearchItem>) {
    val prefs = context.getSharedPreferences("music_app_prefs", android.content.Context.MODE_PRIVATE)
    val serialized = list.take(10).joinToString("###") { item ->
        val safeTitle = item.title.replace("###", "").replace("@@@", "")
        val safeSub = item.subtitle.replace("###", "").replace("@@@", "")
        val safeQuery = item.searchQuery.replace("###", "").replace("@@@", "")
        "${item.id}@@@$safeTitle@@@$safeSub@@@${item.imageUrl ?: ""}@@@${item.imageRes}@@@${item.streamUrl ?: ""}@@@${item.isArtist}@@@$safeQuery"
    }
    prefs.edit().putString("recent_searches_data", serialized).apply()
}

fun loadRecentSearches(context: android.content.Context): List<RecentSearchItem> {
    val prefs = context.getSharedPreferences("music_app_prefs", android.content.Context.MODE_PRIVATE)
    val raw = prefs.getString("recent_searches_data", null) ?: return emptyList()
    if (raw.isBlank()) return emptyList()
    return try {
        raw.split("###").mapNotNull { entry ->
            val parts = entry.split("@@@")
            if (parts.size >= 8) {
                RecentSearchItem(
                    id = parts[0],
                    title = parts[1],
                    subtitle = parts[2],
                    imageUrl = parts[3].ifEmpty { null }?.replace("http://", "https://"),
                    imageRes = parts[4].toIntOrNull() ?: R.drawable.pic_4,
                    streamUrl = parts[5].ifEmpty { null }?.replace("http://", "https://"),
                    isArtist = parts[6].toBooleanStrictOrNull() ?: false,
                    searchQuery = parts[7]
                )
            } else null
        }.take(25)
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
private fun RecentSearchRowItem(
    item: RecentSearchItem,
    context: android.content.Context,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
    titleToSubtitleGap: androidx.compose.ui.unit.Dp = (-3).dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.isArtist) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.imageUrl ?: item.imageRes)
                    .placeholder(item.imageRes)
                    .error(item.imageRes)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.imageUrl ?: item.imageRes)
                    .placeholder(item.imageRes)
                    .error(item.imageRes)
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(titleToSubtitleGap)
        ) {
            Text(
                text = item.title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle,
                fontSize = 13.5.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onRemoveClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun OnlineSongRowItem(
    track: SaavnTrack,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isAdded: Boolean = false,
    onAddToggle: (() -> Unit)? = null,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
    titleToArtistGap: androidx.compose.ui.unit.Dp = (-3).dp
) {
    val imageUrl = JioSaavnApi.getHighResImageUrl(track)
    val streamUrl = JioSaavnApi.getStreamUrl(track)
    val title = track.cleanTitle
    val artist = track.cleanArtist

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (onAddToggle != null) {
                    onAddToggle()
                } else {
                    onPlayClick()
                }
            }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(titleToArtistGap)
        ) {
            Text(
                text = title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            val durationText = if (track.duration != "0" && track.duration.isNotBlank()) {
                " \u00B7 ${formatDuration(track.duration.toIntOrNull()?.times(1000) ?: 0)}"
            } else ""
            Text(
                text = "$artist$durationText",
                fontSize = 13.5.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val isBuffering = MusicPlayerController.isBuffering && MusicPlayerController.isCurrentSong(streamUrl)
        val isCurrentPlaying = MusicPlayerController.isPlaying && MusicPlayerController.isCurrentSong(streamUrl)

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(32.dp)
            ) {
                Image(
                    painter = painterResource(
                        id = if (isCurrentPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (onAddToggle != null) {
            IconButton(
                onClick = onAddToggle,
                modifier = Modifier.size(36.dp)
            ) {
                if (isAdded) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_tick),
                        contentDescription = "Added",
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = "Add to Playlist",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(36.dp)
            ) {
                when {
                    isDownloaded -> {
                        Image(
                            painter = painterResource(id = R.drawable.ic_downloaded),
                            contentDescription = "Downloaded",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    isDownloading -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {
                        Image(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = "Download",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

data class OnlineCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val searchQuery: String,
    val imageUrl: String?,
    val imageRes: Int,
    val accentColor: Color = Color(0xFF2C2C2C),
    val isArtistAlbum: Boolean
)

@Composable
fun OnlinePlaylistDetailPage(
    collection: OnlineCollection,
    activeBarSong: Song? = null,
    addingToPlaylist: Playlist? = null,
    onPlaylistModified: () -> Unit = {},
    onPlaySong: (Song, List<Song>?) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {},
    onNowPlayingClick: (Song) -> Unit = {},
    downloadedSongIds: Set<String> = emptySet(),
    downloadingSongIds: Set<String> = emptySet(),
    triggerDownload: (SaavnTrack) -> Unit = {},
    addRecentSearch: (RecentSearchItem) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<SaavnTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(collection.searchQuery) {
        isLoading = true
        errorMessage = null
        try {
            val response = withContext(Dispatchers.IO) {
                JioSaavnApi.service.searchSongs(query = collection.searchQuery, limit = 30)
            }
            val valid = response.results.filter { it.disabled != "true" && it.encryptedMediaUrl.isNotEmpty() }
            tracks = if (valid.isNotEmpty()) valid else response.results.filter { it.disabled != "true" }
            if (tracks.isEmpty()) {
                errorMessage = "No songs found for '${collection.title}'"
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load songs. Check your internet connection."
            tracks = emptyList()
        } finally {
            isLoading = false
        }
    }

    val queue = remember(tracks) {
        tracks.mapNotNull { track ->
            val streamUrl = JioSaavnApi.getStreamUrl(track)
            if (streamUrl.isNotEmpty()) {
                Song(
                    title = track.cleanTitle,
                    artist = track.cleanArtist,
                    imageRes = R.drawable.pic_4,
                    uriString = streamUrl,
                    imageUrl = JioSaavnApi.getHighResImageUrl(track)
                )
            } else null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val topBarVerticalOffset = 15.dp
        val nowPlayingVerticalOffset = -15.dp

        // Background
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .offset(y = topBarVerticalOffset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = collection.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Large Cover Image Header (Apple Music / Playlist Style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                val firstTrack = tracks.firstOrNull()
                val coverImageUrl = if (firstTrack != null) {
                    JioSaavnApi.getHighResImageUrl(firstTrack)
                } else {
                    collection.imageUrl
                }
                val coverImageRes = collection.imageRes

                if (collection.isArtistAlbum) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .shadow(elevation = 12.dp, shape = RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverImageUrl ?: coverImageRes)
                                .placeholder(coverImageRes)
                                .error(coverImageRes)
                                .crossfade(true)
                                .build(),
                            contentDescription = collection.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.4f)
                                        )
                                    )
                                )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .shadow(elevation = 12.dp, shape = RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(collection.accentColor)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(coverImageUrl ?: coverImageRes)
                                .placeholder(coverImageRes)
                                .error(coverImageRes)
                                .crossfade(true)
                                .build(),
                            contentDescription = collection.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            collection.accentColor.copy(alpha = 0.45f),
                                            Color.Black.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp)
                        ) {
                            Text(
                                text = collection.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = collection.subtitle,
                                fontSize = 13.sp,
                                color = Color.LightGray.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-header stats & "Play All" Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (collection.isArtistAlbum) collection.subtitle else "${tracks.size} Songs",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )

                if (queue.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2C2C2C))
                            .clickable {
                                val firstSong = queue.first()
                                onPlaySong(firstSong, queue)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_play),
                            contentDescription = "Play All",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Play All",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Songs List Content Area
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                errorMessage != null && tracks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage!!,
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            val resp = withContext(Dispatchers.IO) {
                                                JioSaavnApi.service.searchSongs(query = collection.searchQuery, limit = 30)
                                            }
                                            val valid = resp.results.filter { it.disabled != "true" && it.encryptedMediaUrl.isNotEmpty() }
                                            tracks = if (valid.isNotEmpty()) valid else resp.results.filter { it.disabled != "true" }
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to load songs. Check your internet connection."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        items(tracks) { track ->
                            val streamUrl = JioSaavnApi.getStreamUrl(track)
                            val title = track.cleanTitle
                            val artist = track.cleanArtist
                            val imageUrl = JioSaavnApi.getHighResImageUrl(track)

                            val isTrackAdded = addingToPlaylist?.songs?.any {
                                (it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)) ||
                                (streamUrl.isNotEmpty() && it.uriString == streamUrl)
                            } == true

                            OnlineSongRowItem(
                                track = track,
                                context = context,
                                coroutineScope = coroutineScope,
                                isDownloaded = downloadedSongIds.contains(track.id),
                                isDownloading = downloadingSongIds.contains(track.id),
                                isAdded = isTrackAdded,
                                onAddToggle = if (addingToPlaylist != null) {
                                    {
                                        val existing = addingToPlaylist.songs.firstOrNull {
                                            (it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)) ||
                                            (streamUrl.isNotEmpty() && it.uriString == streamUrl)
                                        }
                                        if (existing != null) {
                                            addingToPlaylist.songs.remove(existing)
                                            android.widget.Toast.makeText(context, "Removed '$title' from ${addingToPlaylist.name}", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            val onlineSong = Song(
                                                title = title,
                                                artist = artist,
                                                imageRes = R.drawable.pic_4,
                                                uriString = streamUrl,
                                                imageUrl = imageUrl,
                                                onlineStreamUrl = streamUrl
                                            )
                                            addingToPlaylist.songs.add(onlineSong)
                                            android.widget.Toast.makeText(context, "Added '$title' to ${addingToPlaylist.name}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        onPlaylistModified()
                                    }
                                } else null,
                                horizontalPadding = 0.dp,
                                verticalPadding = 3.5.dp,
                                titleToArtistGap = (-3).dp,
                                onPlayClick = {
                                    if (streamUrl.isNotEmpty()) {
                                        val onlineSong = Song(
                                            title = title,
                                            artist = artist,
                                            imageRes = R.drawable.pic_4,
                                            uriString = streamUrl,
                                            imageUrl = imageUrl
                                        )
                                        addRecentSearch(
                                            RecentSearchItem(
                                                id = track.id.ifEmpty { "${title}_${artist}" },
                                                title = title,
                                                subtitle = "Song \u00B7 $artist",
                                                imageUrl = imageUrl,
                                                streamUrl = streamUrl,
                                                isArtist = false,
                                                searchQuery = title
                                            )
                                        )
                                        onPlaySong(onlineSong, queue)
                                    }
                                },
                                onDownloadClick = { triggerDownload(track) }
                            )
                        }
                    }
                }
            }

            // Now Playing bar at bottom
            if (activeBarSong != null) {
                NowPlayingBar(
                    song = activeBarSong,
                    onClick = { onNowPlayingClick(activeBarSong) },
                    onPlayPauseClick = { onPlaySong(activeBarSong, null) },
                    modifier = Modifier.offset(y = nowPlayingVerticalOffset)
                )
            }
        }
    }
}

@Composable
fun OnlineSongsPage(
    activeBarSong: Song? = null,
    addingToPlaylist: Playlist? = null,
    onPlaylistModified: () -> Unit = {},
    onPlaySong: (Song, List<Song>?) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {},
    onNowPlayingClick: (Song) -> Unit = {},
    onDownloadSong: (Song) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SaavnTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var recentSearches by remember { mutableStateOf<List<RecentSearchItem>>(emptyList()) }
    var showSeeAllTrending by remember { mutableStateOf(false) }
    var showSeeAllRecent by remember { mutableStateOf(false) }
    var selectedOnlineCollection by remember { mutableStateOf<OnlineCollection?>(null) }

    val isTrackAdded = { track: SaavnTrack ->
        if (addingToPlaylist != null) {
            val streamUrl = JioSaavnApi.getStreamUrl(track)
            addingToPlaylist.songs.any {
                (it.title.equals(track.cleanTitle, ignoreCase = true) && it.artist.equals(track.cleanArtist, ignoreCase = true)) ||
                (streamUrl.isNotEmpty() && it.uriString == streamUrl)
            }
        } else false
    }

    val toggleTrackInPlaylist = { track: SaavnTrack ->
        if (addingToPlaylist != null) {
            val streamUrl = JioSaavnApi.getStreamUrl(track)
            val title = track.cleanTitle
            val artist = track.cleanArtist
            val imageUrl = JioSaavnApi.getHighResImageUrl(track)
            val existing = addingToPlaylist.songs.firstOrNull {
                (it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)) ||
                (streamUrl.isNotEmpty() && it.uriString == streamUrl)
            }
            if (existing != null) {
                addingToPlaylist.songs.remove(existing)
                android.widget.Toast.makeText(context, "Removed '$title' from ${addingToPlaylist.name}", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val onlineSong = Song(
                    title = title,
                    artist = artist,
                    imageRes = R.drawable.pic_4,
                    uriString = streamUrl,
                    imageUrl = imageUrl,
                    onlineStreamUrl = streamUrl
                )
                addingToPlaylist.songs.add(onlineSong)
                android.widget.Toast.makeText(context, "Added '$title' to ${addingToPlaylist.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
            onPlaylistModified()
        }
    }

    var recentSongs by remember { mutableStateOf<List<SaavnTrack>>(emptyList()) }
    var isRecentLoading by remember { mutableStateOf(true) }
    var recentError by remember { mutableStateOf<String?>(null) }

    // Personalized Feed System state
    var feedSongs by remember { mutableStateOf<List<SaavnTrack>>(emptyList()) }
    var isFeedLoading by remember { mutableStateOf(true) }
    var dynamicAlbums by remember { mutableStateOf(UserFeedManager.getPersonalizedAlbums(context, trendingIndianAlbums)) }

    var downloadingSongIds by remember { mutableStateOf(setOf<String>()) }
    var downloadedSongIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        recentSearches = loadRecentSearches(context)
    }

    val addRecentSearch: (RecentSearchItem) -> Unit = { item ->
        val updated = listOf(item) + recentSearches.filterNot { it.id == item.id || (it.title == item.title && it.subtitle == item.subtitle) }
        recentSearches = updated.take(25)
        saveRecentSearches(context, recentSearches)
    }

    val removeRecentSearch: (RecentSearchItem) -> Unit = { item ->
        val updated = recentSearches.filterNot { it.id == item.id }
        recentSearches = updated
        saveRecentSearches(context, recentSearches)
    }

    if (selectedOnlineCollection != null) {
        BackHandler { selectedOnlineCollection = null }
    } else if (showSeeAllTrending) {
        BackHandler { showSeeAllTrending = false }
    } else if (showSeeAllRecent) {
        BackHandler { showSeeAllRecent = false }
    }

    val triggerDownload: (SaavnTrack) -> Unit = { track ->
        val streamUrl = JioSaavnApi.getStreamUrl(track)
        val title = track.cleanTitle
        val artist = track.cleanArtist
        val imageUrl = JioSaavnApi.getHighResImageUrl(track)
        val isDownloaded = downloadedSongIds.contains(track.id)
        val isDownloading = downloadingSongIds.contains(track.id)

        if (!isDownloaded && !isDownloading && streamUrl.isNotEmpty()) {
            downloadingSongIds = downloadingSongIds + track.id

            coroutineScope.launch(Dispatchers.IO) {
                val safeTitle = title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifEmpty { "Song_${track.id}" }
                val fileName = "${safeTitle}_${track.id}.mp3"

                val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: context.filesDir
                val clefFolder = java.io.File(musicDir, "Clef")
                if (!clefFolder.exists()) {
                    clefFolder.mkdirs()
                }

                val targetFile = java.io.File(clefFolder, fileName)
                val tempFile = java.io.File(clefFolder, "$fileName.tmp")

                var downloadSuccess = false
                try {
                    val url = java.net.URL(streamUrl)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.connect()

                    if (connection.responseCode in 200..299) {
                        connection.inputStream.use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            if (targetFile.exists()) targetFile.delete()
                            tempFile.renameTo(targetFile)
                            downloadSuccess = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }

                withContext(Dispatchers.Main) {
                    downloadingSongIds = downloadingSongIds - track.id
                    if (downloadSuccess && targetFile.exists() && targetFile.length() > 0) {
                        downloadedSongIds = downloadedSongIds + track.id
                        val localUri = android.net.Uri.fromFile(targetFile).toString()
                        val savedSong = Song(
                            title = title,
                            artist = artist,
                            imageRes = R.drawable.pic_4,
                            uriString = localUri,
                            imageUrl = imageUrl,
                            onlineStreamUrl = streamUrl
                        )
                        onDownloadSong(savedSong)
                        android.widget.Toast.makeText(
                            context,
                            "Downloaded '$title' for offline play",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Download failed for '$title'",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Load initial trending backup songs
    LaunchedEffect(Unit) {
        isRecentLoading = true
        recentError = null
        try {
            val songs = withContext(Dispatchers.IO) {
                JioSaavnApi.fetchRecentSongs()
            }
            recentSongs = songs
        } catch (e: Exception) {
            recentError = "Failed to load songs. Check your internet connection."
        } finally {
            isRecentLoading = false
        }
    }

    // Dynamic personalized feed loader based on analyzed user taste
    LaunchedEffect(activeBarSong) {
        val sortedAlbums = UserFeedManager.getPersonalizedAlbums(context, trendingIndianAlbums)
        dynamicAlbums = sortedAlbums

        coroutineScope.launch {
            val liveAlbums = UserFeedManager.resolveAllAlbumImages(context, sortedAlbums)
            dynamicAlbums = liveAlbums
        }

        isFeedLoading = true
        try {
            val fetched = withContext(Dispatchers.IO) {
                UserFeedManager.fetchForYouFeed(context)
            }
            feedSongs = fetched
        } catch (_: Exception) {
            feedSongs = recentSongs
        } finally {
            isFeedLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(500)
            isLoading = true
            errorMessage = null
            try {
                val response = withContext(Dispatchers.IO) {
                    JioSaavnApi.service.searchSongs(query = searchQuery, limit = 20)
                }
                searchResults = response.results.filter { it.disabled != "true" }
                if (searchResults.isEmpty() && response.results.isNotEmpty()) {
                    searchResults = response.results
                }
            } catch (e: Exception) {
                errorMessage = "Search failed. Check your internet connection."
                searchResults = emptyList()
            }
            isLoading = false
        } else if (searchQuery.isEmpty()) {
            searchResults = emptyList()
        }
    }

    if (selectedOnlineCollection != null) {
        OnlinePlaylistDetailPage(
            collection = selectedOnlineCollection!!,
            activeBarSong = activeBarSong,
            addingToPlaylist = addingToPlaylist,
            onPlaylistModified = onPlaylistModified,
            onPlaySong = onPlaySong,
            onBackClick = { selectedOnlineCollection = null },
            onNowPlayingClick = onNowPlayingClick,
            downloadedSongIds = downloadedSongIds,
            downloadingSongIds = downloadingSongIds,
            triggerDownload = triggerDownload,
            addRecentSearch = addRecentSearch
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // =========================================================================
                // Adjust these values to easily fine-tune margins, offsets, and alignment!
                // =========================================================================
                val screenTopPadding = 38.dp             // Top padding below status bar / camera notch
                val onlineTitleOffsetX = 0.dp            // Horizontal offset for Online & Stream & Download title
                val onlineTitleOffsetY = 15.dp            // Vertical offset for Online & Stream & Download title
                val onlineHeaderAlignment = Alignment.CenterHorizontally // Text column alignment: Alignment.CenterHorizontally, Alignment.Start, Alignment.End
                val onlineHeaderArrangement = Arrangement.Center        // Header row arrangement: Arrangement.Center, Arrangement.Start, Arrangement.End
                val onlineTitleToSubtitleGap = (-2).dp   // Vertical gap between "Online" and "Stream & Download"
                val onlineHeaderToSearchBarGap = 24.dp   // Gap between header title and search bar
                val onlineSearchBarOffsetX = 0.dp        // Horizontal offset for search bar
                val onlineSearchBarOffsetY = 0.dp        // Vertical offset for search bar
                val onlineSearchBarHeight = 42.dp        // Height of the search bar
                val onlineSearchBarMarginBottom = 14.dp // Bottom spacing between search bar and searched songs / feed
                val recentToTrendingGap = 1.dp
                val trendingHeaderToSongsGap = 2.dp
                val songTitleToArtistGap = (-3).dp
                val trendingToArtistAlbumsGap = 8.dp
                val onlineRowPaddingHorizontal = 0.dp
                val onlineRowPaddingVertical = 3.5.dp
                val onlineDoneButtonSize = 48.dp         // Size of the Done checkmark button in selection mode
                val nowPlayingVerticalOffset = -15.dp
                // =========================================================================

                Spacer(modifier = Modifier.height(screenTopPadding))

                val activeFeedList = if (feedSongs.isNotEmpty()) feedSongs else recentSongs

                val isSelectionMode = addingToPlaylist != null
                val isDetailView = showSeeAllTrending || showSeeAllRecent
                val hasBackButton = isDetailView || isSelectionMode

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = onlineTitleOffsetX, y = onlineTitleOffsetY),
                    horizontalArrangement = if (hasBackButton) Arrangement.SpaceBetween else onlineHeaderArrangement,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasBackButton) {
                        IconButton(onClick = {
                            if (showSeeAllTrending || showSeeAllRecent) {
                                showSeeAllTrending = false
                                showSeeAllRecent = false
                            } else {
                                onBackClick()
                            }
                        }) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_back),
                                contentDescription = "Back",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(
                        modifier = if (hasBackButton) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        horizontalAlignment = if (hasBackButton) Alignment.CenterHorizontally else onlineHeaderAlignment,
                        verticalArrangement = Arrangement.spacedBy(onlineTitleToSubtitleGap)
                    ) {
                        Text(
                            text = when {
                                addingToPlaylist != null -> "Add to ${addingToPlaylist.name}"
                                showSeeAllTrending -> "For U"
                                showSeeAllRecent -> "Recent"
                                else -> "Online"
                            },
                            fontSize = if (addingToPlaylist != null) 20.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = when {
                                addingToPlaylist != null -> "${addingToPlaylist.songs.size} Songs in playlist"
                                showSeeAllTrending -> "${activeFeedList.size} Tracks"
                                showSeeAllRecent -> "${recentSearches.size} Tracks"
                                else -> "Stream & Download"
                            },
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (hasBackButton) {
                        if (addingToPlaylist != null) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.size(onlineDoneButtonSize)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_done),
                                    contentDescription = "Done",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(onlineHeaderToSearchBarGap))

                if (!isDetailView) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = onlineSearchBarOffsetX, y = onlineSearchBarOffsetY)
                            .padding(bottom = onlineSearchBarMarginBottom)
                            .height(onlineSearchBarHeight)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Color(0xFF2C2C2C))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Music, Artists, and Podcasts Search",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 14.5.sp,
                                        color = Color.White
                                    ),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                when {
                    showSeeAllTrending -> {
                        val seeAllQueue = remember(activeFeedList) {
                            activeFeedList.mapNotNull { track ->
                                val streamUrl = JioSaavnApi.getStreamUrl(track)
                                if (streamUrl.isNotEmpty()) {
                                    Song(
                                        title = track.cleanTitle,
                                        artist = track.cleanArtist,
                                        imageRes = R.drawable.pic_4,
                                        uriString = streamUrl,
                                        imageUrl = JioSaavnApi.getHighResImageUrl(track)
                                    )
                                } else null
                            }
                        }

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(activeFeedList) { track ->
                                val streamUrl = JioSaavnApi.getStreamUrl(track)
                                val title = track.cleanTitle
                                val artist = track.cleanArtist
                                val imageUrl = JioSaavnApi.getHighResImageUrl(track)

                                OnlineSongRowItem(
                                    track = track,
                                    context = context,
                                    coroutineScope = coroutineScope,
                                    isDownloaded = downloadedSongIds.contains(track.id),
                                    isDownloading = downloadingSongIds.contains(track.id),
                                    isAdded = isTrackAdded(track),
                                    onAddToggle = if (addingToPlaylist != null) { { toggleTrackInPlaylist(track) } } else null,
                                    horizontalPadding = onlineRowPaddingHorizontal,
                                    verticalPadding = onlineRowPaddingVertical,
                                    titleToArtistGap = songTitleToArtistGap,
                                    onPlayClick = {
                                        if (streamUrl.isNotEmpty()) {
                                            val onlineSong = Song(
                                                title = title,
                                                artist = artist,
                                                imageRes = R.drawable.pic_4,
                                                uriString = streamUrl,
                                                imageUrl = imageUrl
                                            )
                                            addRecentSearch(
                                                RecentSearchItem(
                                                    id = track.id.ifEmpty { "${title}_${artist}" },
                                                    title = title,
                                                    subtitle = "Song \u00B7 $artist",
                                                    imageUrl = imageUrl,
                                                    streamUrl = streamUrl,
                                                    isArtist = false,
                                                    searchQuery = title
                                                )
                                            )
                                            onPlaySong(onlineSong, seeAllQueue)
                                        }
                                    },
                                    onDownloadClick = { triggerDownload(track) }
                                )
                            }
                        }
                    }

                    showSeeAllRecent -> {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(recentSearches) { item ->
                                RecentSearchRowItem(
                                    item = item,
                                    context = context,
                                    horizontalPadding = onlineRowPaddingHorizontal,
                                    verticalPadding = onlineRowPaddingVertical,
                                    titleToSubtitleGap = songTitleToArtistGap,
                                    onClick = {
                                        if (item.streamUrl != null && item.streamUrl.isNotEmpty()) {
                                            val onlineSong = Song(
                                                title = item.title,
                                                artist = item.subtitle.removePrefix("Song \u00B7 ").removePrefix("Song • "),
                                                imageRes = item.imageRes,
                                                uriString = item.streamUrl,
                                                imageUrl = item.imageUrl
                                            )
                                            onPlaySong(onlineSong, null)
                                        } else if (item.isArtist || item.subtitle == "Artist") {
                                            selectedOnlineCollection = OnlineCollection(
                                                id = item.id,
                                                title = item.title,
                                                subtitle = "Album \u00B7 ${item.title}",
                                                searchQuery = item.searchQuery.ifEmpty { item.title },
                                                imageUrl = item.imageUrl,
                                                imageRes = item.imageRes,
                                                isArtistAlbum = true
                                            )
                                        } else if (item.subtitle == "Category") {
                                            selectedOnlineCollection = OnlineCollection(
                                                id = item.id,
                                                title = item.title,
                                                subtitle = "Category",
                                                searchQuery = item.searchQuery.ifEmpty { item.title },
                                                imageUrl = item.imageUrl,
                                                imageRes = item.imageRes,
                                                isArtistAlbum = false
                                            )
                                        } else if (item.searchQuery.isNotEmpty()) {
                                            selectedOnlineCollection = OnlineCollection(
                                                id = item.id,
                                                title = item.title,
                                                subtitle = "Playlist",
                                                searchQuery = item.searchQuery,
                                                imageUrl = item.imageUrl,
                                                imageRes = item.imageRes,
                                                isArtistAlbum = false
                                            )
                                        }
                                    },
                                    onRemoveClick = {
                                        removeRecentSearch(item)
                                    }
                                )
                            }
                        }
                    }

                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    searchQuery.isEmpty() -> {
                        Column(modifier = Modifier.weight(1f)) {
                            val currentDisplaySongs = if (feedSongs.isNotEmpty()) feedSongs else recentSongs
                            val currentFeedQueue = remember(currentDisplaySongs) {
                                currentDisplaySongs.mapNotNull { track ->
                                    val streamUrl = JioSaavnApi.getStreamUrl(track)
                                    if (streamUrl.isNotEmpty()) {
                                        Song(
                                            title = track.cleanTitle,
                                            artist = track.cleanArtist,
                                            imageRes = R.drawable.pic_4,
                                            uriString = streamUrl,
                                            imageUrl = JioSaavnApi.getHighResImageUrl(track)
                                        )
                                    } else null
                                }
                            }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                if (recentSearches.isNotEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Recent",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            TextButton(onClick = { showSeeAllRecent = true }) {
                                                Text(
                                                    text = "See All",
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    items(recentSearches.take(3)) { item ->
                                        RecentSearchRowItem(
                                            item = item,
                                            context = context,
                                            horizontalPadding = onlineRowPaddingHorizontal,
                                            verticalPadding = onlineRowPaddingVertical,
                                            titleToSubtitleGap = songTitleToArtistGap,
                                            onClick = {
                                                if (item.streamUrl != null && item.streamUrl.isNotEmpty()) {
                                                    val onlineSong = Song(
                                                        title = item.title,
                                                        artist = item.subtitle.removePrefix("Song \u00B7 ").removePrefix("Song • "),
                                                        imageRes = item.imageRes,
                                                        uriString = item.streamUrl,
                                                        imageUrl = item.imageUrl
                                                    )
                                                    onPlaySong(onlineSong, null)
                                                } else if (item.isArtist || item.subtitle == "Artist") {
                                                    selectedOnlineCollection = OnlineCollection(
                                                        id = item.id,
                                                        title = item.title,
                                                        subtitle = "Album \u00B7 ${item.title}",
                                                        searchQuery = item.searchQuery.ifEmpty { item.title },
                                                        imageUrl = item.imageUrl,
                                                        imageRes = item.imageRes,
                                                        isArtistAlbum = true
                                                    )
                                                } else if (item.subtitle == "Category") {
                                                    selectedOnlineCollection = OnlineCollection(
                                                        id = item.id,
                                                        title = item.title,
                                                        subtitle = "Category",
                                                        searchQuery = item.searchQuery.ifEmpty { item.title },
                                                        imageUrl = item.imageUrl,
                                                        imageRes = item.imageRes,
                                                        isArtistAlbum = false
                                                    )
                                                } else if (item.searchQuery.isNotEmpty()) {
                                                    selectedOnlineCollection = OnlineCollection(
                                                        id = item.id,
                                                        title = item.title,
                                                        subtitle = "Playlist",
                                                        searchQuery = item.searchQuery,
                                                        imageUrl = item.imageUrl,
                                                        imageRes = item.imageRes,
                                                        isArtistAlbum = false
                                                    )
                                                }
                                            },
                                            onRemoveClick = {
                                                removeRecentSearch(item)
                                            }
                                        )
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 4.dp,
                                                end = 4.dp,
                                                top = if (recentSearches.isNotEmpty()) recentToTrendingGap else 4.dp,
                                                bottom = trendingHeaderToSongsGap
                                            ),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "For U",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Based on your music taste",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        TextButton(onClick = { showSeeAllTrending = true }) {
                                            Text(
                                                text = "See All",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                if (isFeedLoading && currentDisplaySongs.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                } else {
                                    items(currentDisplaySongs.take(4)) { track ->
                                        val streamUrl = JioSaavnApi.getStreamUrl(track)
                                        val title = track.cleanTitle
                                        val artist = track.cleanArtist
                                        val imageUrl = JioSaavnApi.getHighResImageUrl(track)

                                        OnlineSongRowItem(
                                            track = track,
                                            context = context,
                                            coroutineScope = coroutineScope,
                                            isDownloaded = downloadedSongIds.contains(track.id),
                                            isDownloading = downloadingSongIds.contains(track.id),
                                            isAdded = isTrackAdded(track),
                                            onAddToggle = if (addingToPlaylist != null) { { toggleTrackInPlaylist(track) } } else null,
                                            horizontalPadding = onlineRowPaddingHorizontal,
                                            verticalPadding = onlineRowPaddingVertical,
                                            titleToArtistGap = songTitleToArtistGap,
                                            onPlayClick = {
                                                if (streamUrl.isNotEmpty()) {
                                                    val onlineSong = Song(
                                                        title = title,
                                                        artist = artist,
                                                        imageRes = R.drawable.pic_4,
                                                        uriString = streamUrl,
                                                        imageUrl = imageUrl
                                                    )
                                                    addRecentSearch(
                                                        RecentSearchItem(
                                                            id = track.id.ifEmpty { "${title}_${artist}" },
                                                            title = title,
                                                            subtitle = "Song \u00B7 $artist",
                                                            imageUrl = imageUrl,
                                                            streamUrl = streamUrl,
                                                            isArtist = false,
                                                            searchQuery = title
                                                        )
                                                    )
                                                    onPlaySong(onlineSong, currentFeedQueue)
                                                }
                                            },
                                            onDownloadClick = { triggerDownload(track) }
                                        )
                                    }
                                }

                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 4.dp, top = trendingToArtistAlbumsGap, end = 4.dp, bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "Albums",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Top artists & hit compilations",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.padding(bottom = 16.dp, top = 4.dp)
                                    ) {
                                        items(dynamicAlbums) { album ->
                                                    Column(
                                                        modifier = Modifier
                                                            .width(106.dp)
                                                            .clickable {
                                                                addRecentSearch(
                                                                    RecentSearchItem(
                                                                        id = "artist_${album.artist}",
                                                                        title = album.artist,
                                                                        subtitle = "Artist",
                                                                        imageUrl = album.imageUrl,
                                                                        imageRes = album.imageRes,
                                                                        isArtist = true,
                                                                        searchQuery = album.searchQuery
                                                                    )
                                                                )
                                                                selectedOnlineCollection = OnlineCollection(
                                                                    id = "artist_${album.artist}",
                                                                    title = album.title,
                                                                    subtitle = "Album \u00B7 ${album.artist}",
                                                                    searchQuery = album.searchQuery,
                                                                    imageUrl = album.imageUrl,
                                                                    imageRes = album.imageRes,
                                                                    accentColor = album.gradientColors.firstOrNull() ?: Color(0xFF2B5876),
                                                                    isArtistAlbum = true
                                                                )
                                                            },
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(100.dp)
                                                                .shadow(elevation = 8.dp, shape = CircleShape)
                                                                .clip(CircleShape)
                                                                .background(Color.Black),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .data(album.imageUrl ?: album.imageRes)
                                                                    .placeholder(album.imageRes)
                                                                    .error(album.imageRes)
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = album.artist,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )

                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .border(
                                                                        width = 1.dp,
                                                                        color = Color.White.copy(alpha = 0.2f),
                                                                        shape = CircleShape
                                                                    )
                                                            )

                                                            Box(
                                                                modifier = Modifier
                                                                    .size(30.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color.Black.copy(alpha = 0.45f))
                                                                    .border(
                                                                        width = 1.5.dp,
                                                                        color = Color.White.copy(alpha = 0.55f),
                                                                        shape = CircleShape
                                                                    ),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(13.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFF141414))
                                                                        .border(
                                                                            width = 1.dp,
                                                                            color = Color.White.copy(alpha = 0.65f),
                                                                            shape = CircleShape
                                                                        )
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        Text(
                                                            text = album.title,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )

                                                        Spacer(modifier = Modifier.height(2.dp))

                                                        Text(
                                                            text = album.artist,
                                                            fontSize = 11.5.sp,
                                                            color = Color.Gray,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 4.dp, top = 16.dp, end = 4.dp, bottom = 8.dp)
                                            ) {
                                                Text(
                                                    text = "Categories",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Explore by genre, mood & vibe",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        items(musicCategories.chunked(2)) { rowItems ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 5.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                for (category in rowItems) {
                                                    Card(
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(92.dp)
                                                            .clickable {
                                                                addRecentSearch(
                                                                    RecentSearchItem(
                                                                        id = "cat_${category.id}",
                                                                        title = category.name.replace("\n", " "),
                                                                        subtitle = "Category",
                                                                        imageUrl = category.imageUrl,
                                                                        imageRes = category.imageRes,
                                                                        isArtist = false,
                                                                        searchQuery = category.searchQuery
                                                                    )
                                                                )
                                                                selectedOnlineCollection = OnlineCollection(
                                                                    id = "cat_${category.id}",
                                                                    title = category.name.replace("\n", " "),
                                                                    subtitle = "Category",
                                                                    searchQuery = category.searchQuery,
                                                                    imageUrl = category.imageUrl,
                                                                    imageRes = category.imageRes,
                                                                    accentColor = category.accentColor,
                                                                    isArtistAlbum = false
                                                                )
                                                            },
                                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(category.accentColor)
                                                        ) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(context)
                                                                    .data(category.imageUrl ?: category.imageRes)
                                                                    .placeholder(category.imageRes)
                                                                    .error(category.imageRes)
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = category.name,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize()
                                                            )

                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .background(
                                                                        brush = Brush.verticalGradient(
                                                                            colors = listOf(
                                                                                category.accentColor.copy(alpha = 0.42f),
                                                                                Color.Black.copy(alpha = 0.65f)
                                                                            )
                                                                        )
                                                                    )
                                                            )

                                                            Text(
                                                                text = category.name,
                                                                fontSize = 15.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                lineHeight = 18.sp,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomStart)
                                                                    .padding(start = 12.dp, bottom = 10.dp, end = 10.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (rowItems.size == 1) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }

                                        item {
                                            Spacer(modifier = Modifier.height(20.dp))
                                        }
                                    }
                                }
                            }
                    searchResults.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results found for '$searchQuery'",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        val onlineSongsQueue = remember(searchResults) {
                            searchResults.mapNotNull { track ->
                                val streamUrl = JioSaavnApi.getStreamUrl(track)
                                if (streamUrl.isNotEmpty()) {
                                    Song(
                                        title = track.cleanTitle,
                                        artist = track.cleanArtist,
                                        imageRes = R.drawable.pic_4,
                                        uriString = streamUrl,
                                        imageUrl = JioSaavnApi.getHighResImageUrl(track)
                                    )
                                } else null
                            }
                        }

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(searchResults) { track ->
                                val streamUrl = JioSaavnApi.getStreamUrl(track)
                                val title = track.cleanTitle
                                val artist = track.cleanArtist
                                val imageUrl = JioSaavnApi.getHighResImageUrl(track)

                                OnlineSongRowItem(
                                    track = track,
                                    context = context,
                                    coroutineScope = coroutineScope,
                                    isDownloaded = downloadedSongIds.contains(track.id),
                                    isDownloading = downloadingSongIds.contains(track.id),
                                    isAdded = isTrackAdded(track),
                                    onAddToggle = if (addingToPlaylist != null) { { toggleTrackInPlaylist(track) } } else null,
                                    horizontalPadding = onlineRowPaddingHorizontal,
                                    verticalPadding = onlineRowPaddingVertical,
                                    titleToArtistGap = songTitleToArtistGap,
                                    onPlayClick = {
                                        if (streamUrl.isNotEmpty()) {
                                            val onlineSong = Song(
                                                title = title,
                                                artist = artist,
                                                imageRes = R.drawable.pic_4,
                                                uriString = streamUrl,
                                                imageUrl = imageUrl
                                            )
                                            addRecentSearch(
                                                RecentSearchItem(
                                                    id = track.id.ifEmpty { "${title}_${artist}" },
                                                    title = title,
                                                    subtitle = "Song \u00B7 $artist",
                                                    imageUrl = imageUrl,
                                                    streamUrl = streamUrl,
                                                    isArtist = false,
                                                    searchQuery = title
                                                )
                                            )
                                            onPlaySong(onlineSong, onlineSongsQueue)
                                        }
                                    },
                                    onDownloadClick = { triggerDownload(track) }
                                )
                            }
                        }
                    }
                }

                if (activeBarSong != null) {
                    NowPlayingBar(
                        song = activeBarSong,
                        onClick = { onNowPlayingClick(activeBarSong) },
                        onPlayPauseClick = { onPlaySong(activeBarSong, null) },
                        modifier = Modifier.offset(y = nowPlayingVerticalOffset)
                    )
                }
            }
        }
    }
}

@Composable
fun Page2() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun Page3() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun Page4() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MusicApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        loadPlaylists(context)
    }

    // Track which page to show
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var nowPlayingSong by remember { mutableStateOf<Song?>(null) }
    var addingToPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var addingOnlineToPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var activePlaybackQueue by remember { mutableStateOf<List<Song>>(globalAllSongs) }
    
    // Track the song to show in the small bottom bar
    var activeBarSong by remember { mutableStateOf<Song?>(null) }

    // Helper to play a song and update the bar
    val playSong: (Song, List<Song>?) -> Unit = { song, queue ->
        val targetQueue = queue ?: activePlaybackQueue
        activePlaybackQueue = targetQueue
        activeBarSong = song
        if (nowPlayingSong != null) {
            nowPlayingSong = song
        }
        UserFeedManager.recordPlayedSong(context, song)
        MusicPlayerController.play(context, song, targetQueue)
    }

    // Queue navigation helpers
    val playNextSong = {
        MusicPlayerController.playNext()
    }

    val playPreviousSong = {
        MusicPlayerController.playPrevious()
    }

    // Connect the background service's next/prev buttons to our queue
    MusicPlayerController.onPlayNext = { playNextSong() }
    MusicPlayerController.onPlayPrevious = { playPreviousSong() }

    // Keep UI state synchronized whenever background auto-play changes the active song
    LaunchedEffect(MusicPlayerController.currentSong) {
        val current = MusicPlayerController.currentSong
        if (current != null) {
            activeBarSong = current
            if (nowPlayingSong != null) {
                nowPlayingSong = current
            }
            UserFeedManager.recordPlayedSong(context, current)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 3 }
    )

    // Offline Nearby Share UI States
    var showNearbyTopPopup by remember { mutableStateOf(false) }
    var showNearbySendSelection by remember { mutableStateOf(false) }
    var showNearbyRadar by remember { mutableStateOf(false) }
    var pendingShareAction by remember { mutableStateOf<String?>(null) } // "SEND" or "RECEIVE"

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted || NearbyShareController.hasRequiredPermissions(context)) {
            if (pendingShareAction == "RECEIVE") {
                showNearbyTopPopup = false
                NearbyShareController.startReceiving(context)
                showNearbyRadar = true
            } else if (pendingShareAction == "SEND") {
                showNearbyTopPopup = false
                showNearbySendSelection = true
            }
        } else {
            android.widget.Toast.makeText(
                context,
                "Bluetooth & Location permissions are required for offline sharing",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        pendingShareAction = null
    }

    val launchNearbyShareAction: (String) -> Unit = { action ->
        if (NearbyShareController.hasRequiredPermissions(context)) {
            if (action == "RECEIVE") {
                showNearbyTopPopup = false
                NearbyShareController.startReceiving(context)
                showNearbyRadar = true
            } else {
                showNearbyTopPopup = false
                showNearbySendSelection = true
            }
        } else {
            pendingShareAction = action
            nearbyPermissionLauncher.launch(NearbyShareController.getRequiredPermissions().toTypedArray())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Base Content Layer (Pages, Playlists, Add Selection)
        if (addingOnlineToPlaylist != null) {
            BackHandler { addingOnlineToPlaylist = null }
            OnlineSongsPage(
                activeBarSong = activeBarSong,
                addingToPlaylist = addingOnlineToPlaylist,
                onPlaylistModified = { savePlaylists(context) },
                onPlaySong = playSong,
                onBackClick = { addingOnlineToPlaylist = null },
                onNowPlayingClick = { song -> nowPlayingSong = song },
                onDownloadSong = { song ->
                    if (globalAllSongs.none { it.uriString == song.uriString }) {
                        globalAllSongs.add(song)
                        savePlaylists(context)
                    }
                }
            )
        } else if (addingToPlaylist != null) {
            BackHandler { addingToPlaylist = null }
            // Show all songs page in selection mode
            AllSongsPage(
                activeBarSong = activeBarSong,
                addingToPlaylist = addingToPlaylist,
                onPlaylistModified = { savePlaylists(context) },
                onPlaySong = playSong,
                onBackSwipe = { addingToPlaylist = null },
                onNowPlayingClick = { song -> nowPlayingSong = song },
                onSongAdded = { song ->
                    globalAllSongs.add(song)
                    savePlaylists(context)
                }
            )
        } else if (selectedPlaylist != null) {
            BackHandler { selectedPlaylist = null }
            // Show the inside playlist page
            InsidePlaylistPage(
                playlist = selectedPlaylist!!,
                activeBarSong = activeBarSong,
                onNameChange = { newName ->
                    val index = globalPlaylists.indexOfFirst { it.name == selectedPlaylist!!.name && it.backgroundColor == selectedPlaylist!!.backgroundColor }
                    if (index != -1) {
                        globalPlaylists[index] = globalPlaylists[index].copy(name = newName)
                        selectedPlaylist = globalPlaylists[index]
                        savePlaylists(context)
                    }
                },
                onPlaySong = playSong,
                onAddFromLocalClick = { addingToPlaylist = selectedPlaylist },
                onAddFromOnlineClick = { addingOnlineToPlaylist = selectedPlaylist },
                onBackClick = { selectedPlaylist = null },
                onNowPlayingClick = { song -> nowPlayingSong = song }
            )
        } else {
            // Back handler when on non-home page (Online Songs or All Songs) to scroll back to Home
            if (pagerState.currentPage != 1) {
                BackHandler {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        OnlineSongsPage(
                            activeBarSong = activeBarSong,
                            onPlaySong = playSong,
                            onBackClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            onNowPlayingClick = { song -> nowPlayingSong = song },
                            onDownloadSong = { song ->
                                if (globalAllSongs.none { it.uriString == song.uriString }) {
                                    globalAllSongs.add(song)
                                    savePlaylists(context)
                                }
                            }
                        )
                    }
                    1 -> {
                        Page1(
                            activeBarSong = activeBarSong,
                            onPlaylistClick = { playlist -> selectedPlaylist = playlist },
                            onSwipeToAllSongs = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            },
                            onSwipeToOnlineSongs = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            onNowPlayingClick = { song -> nowPlayingSong = song },
                            onPlaySong = playSong,
                            onOpenNearbyShare = { showNearbyTopPopup = true }
                        )
                    }
                    2 -> {
                        AllSongsPage(
                            activeBarSong = activeBarSong,
                            addingToPlaylist = null,
                            onPlaylistModified = { savePlaylists(context) },
                            onPlaySong = playSong,
                            onBackSwipe = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            onNowPlayingClick = { song -> nowPlayingSong = song },
                            onSongAdded = { song ->
                                globalAllSongs.add(song)
                                savePlaylists(context)
                            },
                            onCloudClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Full Screen Music Player Modal Layer (opens smoothly from bottom when clicking Now Playing bar)
        if (nowPlayingSong != null) {
            BackHandler { nowPlayingSong = null }
        }

        AnimatedVisibility(
            visible = nowPlayingSong != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut()
        ) {
            val playerSong = MusicPlayerController.currentSong ?: nowPlayingSong
            if (playerSong != null) {
                MusicPlayerPage(
                    song = playerSong,
                    isPlaying = MusicPlayerController.isPlaying,
                    onBackClick = { nowPlayingSong = null },
                    onTogglePlayPause = {
                        if (MusicPlayerController.isPlaying) {
                            MusicPlayerController.pause()
                        } else {
                            if (!MusicPlayerController.isCurrentSong(playerSong.id)) {
                                MusicPlayerController.play(context, playerSong, activePlaybackQueue)
                            } else {
                                MusicPlayerController.resume()
                            }
                        }
                    },
                    onNextClick = { playNextSong() },
                    onPreviousClick = { playPreviousSong() }
                )
            }
        }

        // Offline Nearby Share: Top Popup (Slides down from top with rounded bottom corners)
        NearbyShareTopPopup(
            visible = showNearbyTopPopup,
            onDismiss = { showNearbyTopPopup = false },
            onSendClick = { launchNearbyShareAction("SEND") },
            onReceiveClick = { launchNearbyShareAction("RECEIVE") }
        )

        // Offline Nearby Share: Send Selection Screen (Playlists & Downloaded Songs)
        AnimatedVisibility(
            visible = showNearbySendSelection,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NearbySendSelectionScreen(
                onBack = { showNearbySendSelection = false },
                onProceedToRadar = {
                    showNearbySendSelection = false
                    showNearbyRadar = true
                }
            )
        }

        // Offline Nearby Share: Animated Radar Discovery & Transfer Screen
        AnimatedVisibility(
            visible = showNearbyRadar,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            NearbyRadarScreen(
                onClose = {
                    showNearbyRadar = false
                }
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MusicAppPreview() {
    MusicAppTheme {
        MusicApp()
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun InsidePlaylistPreview() {
    MusicAppTheme {
        InsidePlaylistPage(
            playlist = Playlist(
                name = "Name",
                songs = mutableStateListOf(
                    Song("Song", "Name of the Artist", R.drawable.pic_5),
                    Song("Song", "Name of the Artist", R.drawable.pic_4),
                    Song("Song", "Name of the Artist", R.drawable.pic_1),
                    Song("Song", "Name of the Artist", R.drawable.pic_5)
                ),
                coverImageRes = R.drawable.pic_5,
                backgroundColor = Color(0xFF4B5A69),
                textColor = Color(0xFFE5DED5),
                subTextColor = Color(0xFFA0AAB5)
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MusicPlayerPreview() {
    MusicAppTheme {
        MusicPlayerPage(
            song = Song("Song", "Name of the Artist", R.drawable.pic_5)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AllSongsPreview() {
    MusicAppTheme {
        AllSongsPage()
    }
}
