package com.music.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.music.app.Playlist
import com.music.app.R
import com.music.app.Song
import com.music.app.api.NearbyShareController
import com.music.app.globalAllSongs
import com.music.app.globalPlaylists

/**
 * Selection screen displayed after tapping "Send" in the top popup.
 * Lists all Playlists and Downloaded Songs for multi-selection before transferring.
 */
@Composable
fun NearbySendSelectionScreen(
    onBack: () -> Unit,
    onProceedToRadar: () -> Unit
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val selectedIndividualSongs = remember { mutableStateListOf<Song>() }

    // Downloaded songs from library
    val downloadedSongs = remember(globalAllSongs.size) {
        globalAllSongs.filter {
            val uri = it.uriString
            uri != null && (uri.startsWith("file:") || uri.startsWith("content:") || (!uri.startsWith("http://") && !uri.startsWith("https://")))
        }
    }

    val filteredSongs = remember(searchQuery, downloadedSongs) {
        if (searchQuery.isBlank()) {
            downloadedSongs
        } else {
            downloadedSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val totalSelectedCount = if (selectedPlaylist != null) {
        selectedPlaylist!!.songs.size + selectedIndividualSongs.size
    } else {
        selectedIndividualSongs.size
    }

    BackHandler {
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // App background image texture
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlay matching the music app theme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF141820).copy(alpha = 0.95f),
                            Color(0xFF1C222C).copy(alpha = 0.97f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Select Items to Share",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5DED5)
                    )
                    Text(
                        text = "Choose playlists or downloaded songs",
                        fontSize = 13.sp,
                        color = Color(0xFFA0AAB5)
                    )
                }

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFE5DED5),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Tab Row: Playlists vs Downloaded Songs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFE5DED5),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFFE5DED5),
                        height = 3.dp
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "Playlists (${globalPlaylists.size})",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == 0) Color(0xFFE5DED5) else Color(0xFFA0AAB5).copy(alpha = 0.7f)
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "Downloaded Songs (${downloadedSongs.size})",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == 1) Color(0xFFE5DED5) else Color(0xFFA0AAB5).copy(alpha = 0.7f)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTabIndex == 0) {
                    // PLAYLISTS TAB
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
                    ) {
                        items(globalPlaylists) { playlist ->
                            val isSelected = (selectedPlaylist?.name == playlist.name)
                            PlaylistSelectionCard(
                                playlist = playlist,
                                isSelected = isSelected,
                                onClick = {
                                    selectedPlaylist = if (isSelected) null else playlist
                                }
                            )
                        }
                    }
                } else {
                    // DOWNLOADED SONGS TAB
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search songs or artists...", color = Color(0xFFA0AAB5).copy(alpha = 0.6f), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFFA0AAB5)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE5DED5).copy(alpha = 0.6f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color(0xFFE5DED5),
                                unfocusedTextColor = Color(0xFFE5DED5),
                                focusedContainerColor = Color(0xFF1C222C),
                                unfocusedContainerColor = Color(0xFF1C222C)
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Select All / Deselect All Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${filteredSongs.size} tracks available",
                                fontSize = 12.sp,
                                color = Color(0xFFA0AAB5)
                            )

                            Text(
                                text = if (selectedIndividualSongs.size == filteredSongs.size && filteredSongs.isNotEmpty()) "Deselect All" else "Select All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE5DED5),
                                modifier = Modifier
                                    .clickable {
                                        if (selectedIndividualSongs.size == filteredSongs.size && filteredSongs.isNotEmpty()) {
                                            selectedIndividualSongs.clear()
                                        } else {
                                            selectedIndividualSongs.clear()
                                            selectedIndividualSongs.addAll(filteredSongs)
                                        }
                                    }
                                    .padding(4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (filteredSongs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (downloadedSongs.isEmpty()) "No downloaded songs found.\nDownload some songs or share a playlist!" else "No matching songs found.",
                                    color = Color(0xFFA0AAB5),
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
                            ) {
                                items(filteredSongs) { song ->
                                    val isSelected = selectedIndividualSongs.any { it.id == song.id }
                                    SongSelectionRow(
                                        song = song,
                                        isSelected = isSelected,
                                        onToggle = {
                                            if (isSelected) {
                                                selectedIndividualSongs.removeAll { it.id == song.id }
                                            } else {
                                                selectedIndividualSongs.add(song)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // BOTTOM ACTION BAR
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            RoundedCornerShape(22.dp)
                        ),
                    color = Color(0xFF1C222C).copy(alpha = 0.98f),
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "$totalSelectedCount items selected",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE5DED5)
                            )
                            Text(
                                text = when {
                                    selectedPlaylist != null -> "Playlist '${selectedPlaylist!!.name}' + ${selectedIndividualSongs.size} tracks"
                                    selectedIndividualSongs.isNotEmpty() -> "${selectedIndividualSongs.size} individual tracks"
                                    else -> "Select items above"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFFA0AAB5),
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                val songsToSend = mutableListOf<Song>()
                                selectedPlaylist?.let { songsToSend.addAll(it.songs) }
                                for (s in selectedIndividualSongs) {
                                    if (songsToSend.none { it.id == s.id }) {
                                        songsToSend.add(s)
                                    }
                                }

                                if (songsToSend.isEmpty()) {
                                    Toast.makeText(context, "Please select at least one song or playlist", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Prepare manifest and files in controller
                                NearbyShareController.prepareSendPayload(
                                    context = context,
                                    playlistName = selectedPlaylist?.name,
                                    selectedSongs = songsToSend
                                )

                                onProceedToRadar()
                            },
                            enabled = (totalSelectedCount > 0),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4B5A69),
                                disabledContainerColor = Color.White.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_paper_plane),
                                    contentDescription = "Send",
                                    tint = if (totalSelectedCount > 0) Color(0xFFE5DED5) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Send",
                                    color = if (totalSelectedCount > 0) Color(0xFFE5DED5) else Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSelectionCard(
    playlist: Playlist,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .border(
                BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) Color(0xFFE5DED5).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                ),
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2E3A46) else Color(0xFF1C1F26)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cover Thumbnail
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = playlist.backgroundColor,
                    modifier = Modifier.size(56.dp)
                ) {
                    val firstSong = playlist.songs.firstOrNull()
                    if (firstSong?.imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(firstSong.imageUrl)
                                .placeholder(playlist.coverImageRes)
                                .error(playlist.coverImageRes)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = playlist.coverImageRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column {
                    Text(
                        text = playlist.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5DED5)
                    )
                    Text(
                        text = "${playlist.songs.size} songs",
                        fontSize = 13.sp,
                        color = Color(0xFFA0AAB5)
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4B5A69),
                    checkmarkColor = Color(0xFFE5DED5),
                    uncheckedColor = Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun SongSelectionRow(
    song: Song,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .border(
                BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) Color(0xFFE5DED5).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF2E3A46) else Color(0xFF1C1F26)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Song image
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.DarkGray,
                    modifier = Modifier.size(46.dp)
                ) {
                    if (song.imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(song.imageUrl)
                                .placeholder(song.imageRes)
                                .error(song.imageRes)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = song.imageRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column {
                    Text(
                        text = song.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE5DED5),
                        maxLines = 1
                    )
                    Text(
                        text = song.artist,
                        fontSize = 12.sp,
                        color = Color(0xFFA0AAB5),
                        maxLines = 1
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4B5A69),
                    checkmarkColor = Color(0xFFE5DED5),
                    uncheckedColor = Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}
