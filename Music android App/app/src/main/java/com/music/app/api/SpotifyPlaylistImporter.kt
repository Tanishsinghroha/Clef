package com.music.app.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.music.app.R
import com.music.app.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class SpotifyImportResult(
    val playlistTitle: String,
    val playlistCoverUrl: String?,
    val importedSongs: List<Song>,
    val totalFoundOnSpotify: Int,
    val totalMatched: Int
)

data class SpotifyRawTrack(
    val title: String,
    val artist: String,
    val imageUrl: String? = null
)

object SpotifyPlaylistImporter {

    private val gson = Gson()

    /**
     * Extracts Spotify playlist ID from URLs like:
     * - https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=...
     * - https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M
     * - spotify:playlist:37i9dQZF1DXcBWIGoYBM5M
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        
        // Match standard URL or embed URL
        val urlPattern = Pattern.compile("(?:spotify\\.com(?:/embed)?/playlist/|spotify:playlist:)([a-zA-Z0-9]+)")
        val matcher = urlPattern.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }

        // Direct ID if user just pasted the alphanumeric string
        if (trimmed.matches(Regex("^[a-zA-Z0-9]{15,30}$"))) {
            return trimmed
        }

        return null
    }

    /**
     * Fetch raw tracks and metadata from public Spotify Playlist Embed
     */
    private suspend fun fetchSpotifyTracks(playlistId: String): Pair<String, List<SpotifyRawTrack>> = withContext(Dispatchers.IO) {
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        var htmlContent = ""

        try {
            val url = URL(embedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.connectTimeout = 12000
            connection.readTimeout = 15000

            if (connection.responseCode in 200..299) {
                htmlContent = connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var playlistTitle = "Spotify Playlist"
        val tracksList = mutableListOf<SpotifyRawTrack>()

        if (htmlContent.isNotEmpty()) {
            // Try parsing __NEXT_DATA__ or embedded JSON
            try {
                val nextDataRegex = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL)
                val nextMatcher = nextDataRegex.matcher(htmlContent)
                if (nextMatcher.find()) {
                    val jsonStr = nextMatcher.group(1)
                    val root = gson.fromJson(jsonStr, JsonObject::class.java)
                    val pageProps = root.getAsJsonObject("props")?.getAsJsonObject("pageProps")
                    val state = pageProps?.getAsJsonObject("state")
                    val data = state?.getAsJsonObject("data")
                    val entity = data?.getAsJsonObject("entity")

                    if (entity != null) {
                        playlistTitle = entity.get("name")?.asString ?: "Spotify Playlist"
                        val trackList = entity.getAsJsonArray("trackList")
                        if (trackList != null) {
                            for (elem in trackList) {
                                val trackObj = elem.asJsonObject
                                val title = trackObj.get("title")?.asString ?: ""
                                val subtitle = trackObj.get("subtitle")?.asString ?: ""
                                if (title.isNotEmpty()) {
                                    tracksList.add(SpotifyRawTrack(title = title, artist = subtitle))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback parsing if __NEXT_DATA__ structure differs
            if (tracksList.isEmpty()) {
                try {
                    // Match track items in JSON strings or HTML
                    val trackPattern = Pattern.compile("\"title\":\"([^\"]+)\"[^}]*\"subtitle\":\"([^\"]+)\"")
                    val trackMatcher = trackPattern.matcher(htmlContent)
                    while (trackMatcher.find()) {
                        val title = JioSaavnApi.cleanString(trackMatcher.group(1) ?: "").trim()
                        val artist = JioSaavnApi.cleanString(trackMatcher.group(2) ?: "").trim()
                        if (title.isNotEmpty() && !tracksList.any { it.title == title }) {
                            tracksList.add(SpotifyRawTrack(title = title, artist = artist))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // If embed page parsing yielded no tracks, try public oEmbed for playlist title
        if (tracksList.isEmpty()) {
            try {
                val oEmbedUrl = "https://open.spotify.com/oembed?url=https://open.spotify.com/playlist/$playlistId"
                val conn = URL(oEmbedUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (conn.responseCode in 200..299) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    val oEmbedJson = gson.fromJson(res, JsonObject::class.java)
                    playlistTitle = oEmbedJson.get("title")?.asString ?: playlistTitle
                }
            } catch (_: Exception) {}
        }

        playlistTitle to tracksList
    }

    /**
     * Import a Spotify playlist and match its tracks against JioSaavn for full streaming playback.
     */
    suspend fun importSpotifyPlaylist(
        context: Context,
        playlistUrlOrId: String,
        onProgress: (current: Int, total: Int, songName: String) -> Unit = { _, _, _ -> }
    ): SpotifyImportResult = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(playlistUrlOrId)
            ?: throw IllegalArgumentException("Invalid Spotify playlist URL or ID.")

        val (title, rawTracks) = fetchSpotifyTracks(playlistId)
        if (rawTracks.isEmpty()) {
            throw IllegalStateException("Could not extract tracks from the provided Spotify playlist. Make sure the playlist is Public.")
        }

        val resolvedSongs = mutableListOf<Song>()
        val total = rawTracks.size

        for (i in rawTracks.indices) {
            val raw = rawTracks[i]
            withContext(Dispatchers.Main) {
                onProgress(i + 1, total, "${raw.title} - ${raw.artist}")
            }

            try {
                val searchQuery = "${raw.title} ${raw.artist}".trim()
                val response = JioSaavnApi.service.searchSongs(query = searchQuery, limit = 5)
                val matchingTrack = response.results.firstOrNull { it.disabled != "true" && it.encryptedMediaUrl.isNotEmpty() }
                    ?: response.results.firstOrNull { it.encryptedMediaUrl.isNotEmpty() }

                if (matchingTrack != null) {
                    val streamUrl = JioSaavnApi.getStreamUrl(matchingTrack)
                    if (streamUrl.isNotEmpty()) {
                        val highResImage = JioSaavnApi.getHighResImageUrl(matchingTrack)
                        val song = Song(
                            title = matchingTrack.cleanTitle.ifEmpty { raw.title },
                            artist = matchingTrack.cleanArtist.ifEmpty { raw.artist },
                            imageRes = R.drawable.pic_4,
                            uriString = streamUrl,
                            imageUrl = highResImage.ifEmpty { raw.imageUrl },
                            onlineStreamUrl = streamUrl
                        )
                        resolvedSongs.add(song)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Small delay to prevent API rate limiting
            delay(120)
        }

        SpotifyImportResult(
            playlistTitle = title,
            playlistCoverUrl = resolvedSongs.firstOrNull()?.imageUrl,
            importedSongs = resolvedSongs,
            totalFoundOnSpotify = total,
            totalMatched = resolvedSongs.size
        )
    }
}
