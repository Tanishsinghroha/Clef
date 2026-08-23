package com.music.app.api

import android.content.Context
import android.content.SharedPreferences
import com.music.app.Song
import com.music.app.TrendingArtistAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent User Taste & Recommendation Engine.
 * Analyzes the user's listening habits over time (frequencies, recent plays,
 * artist affinities, and genres) to build a personalized "For U" feed.
 * Also dynamically resolves and caches live artist artwork from JioSaavn.
 */
object UserFeedManager {

    private const val PREFS_NAME = "clef_user_feed_prefs"
    private const val PREFS_ARTIST_IMAGES = "clef_artist_live_images"
    private const val KEY_RECENT_ARTISTS = "recent_artists"
    private const val KEY_LAST_PLAYED_ARTIST = "last_played_artist"
    private const val KEY_LAST_PLAYED_TITLE = "last_played_title"
    private const val KEY_ARTIST_COUNTS = "artist_play_counts"
    private const val KEY_GENRE_COUNTS = "genre_play_counts"

    private val liveImageMemoryCache = ConcurrentHashMap<String, String>()
    
    // In-memory caches to prevent repeated disk SharedPreferences reads during rendering
    @Volatile private var cachedRecentArtists: List<String>? = null
    @Volatile private var cachedArtistCounts: Map<String, Int>? = null
    @Volatile private var cachedGenreCounts: Map<String, Int>? = null
    @Volatile private var cachedTopTasteArtists: List<String>? = null

    // Intelligent Artist & Genre Affinity Map for Indian and Global Music
    private val RELATED_ARTISTS_MAP = mapOf(
        "sidhu moose wala" to listOf("Sidhu Moose Wala", "Karan Aujla", "Diljit Dosanjh", "Shubh", "AP Dhillon", "Amrit Maan", "Prem Dhillon", "Yo Yo Honey Singh"),
        "karan aujla" to listOf("Karan Aujla", "Sidhu Moose Wala", "Diljit Dosanjh", "Shubh", "AP Dhillon", "Badshah", "Amrit Maan"),
        "diljit dosanjh" to listOf("Diljit Dosanjh", "Sidhu Moose Wala", "Karan Aujla", "AP Dhillon", "Badshah", "Guru Randhawa", "Yo Yo Honey Singh"),
        "shubh" to listOf("Shubh", "Sidhu Moose Wala", "Karan Aujla", "AP Dhillon", "Diljit Dosanjh"),
        "ap dhillon" to listOf("AP Dhillon", "Gurinder Gill", "Shubh", "Sidhu Moose Wala", "Karan Aujla", "Diljit Dosanjh"),
        "arijit singh" to listOf("Arijit Singh", "Pritam", "Atif Aslam", "Shreya Ghoshal", "Armaan Malik", "Jubin Nautiyal", "Mohit Chauhan", "Sachin-Jigar"),
        "pritam" to listOf("Pritam", "Arijit Singh", "KK", "Mohit Chauhan", "Atif Aslam", "Shreya Ghoshal"),
        "shreya ghoshal" to listOf("Shreya Ghoshal", "Arijit Singh", "Sunidhi Chauhan", "Pritam", "Sonu Nigam", "Jubin Nautiyal", "Armaan Malik"),
        "anirudh ravichander" to listOf("Anirudh Ravichander", "A.R. Rahman", "Sid Sriram", "Yuvan Shankar Raja", "Devi Sri Prasad", "Harris Jayaraj", "Santhosh Narayanan"),
        "a.r. rahman" to listOf("A.R. Rahman", "Anirudh Ravichander", "Sid Sriram", "Pritam", "Javed Ali", "Hariharan", "Mohit Chauhan"),
        "ar rahman" to listOf("A.R. Rahman", "Anirudh Ravichander", "Sid Sriram", "Pritam", "Javed Ali", "Hariharan"),
        "badshah" to listOf("Badshah", "Yo Yo Honey Singh", "Karan Aujla", "Diljit Dosanjh", "Raftaar", "King", "Guru Randhawa", "Neha Kakkar"),
        "yo yo honey singh" to listOf("Yo Yo Honey Singh", "Badshah", "Raftaar", "Diljit Dosanjh", "Guru Randhawa", "Karan Aujla", "Alfaaz", "Mafia Mundeer"),
        "honey singh" to listOf("Yo Yo Honey Singh", "Badshah", "Raftaar", "Diljit Dosanjh", "Guru Randhawa", "Karan Aujla"),
        "sonu nigam" to listOf("Sonu Nigam", "Alka Yagnik", "Udit Narayan", "Kumar Sanu", "Arijit Singh", "Shaan", "Abhijeet Bhattacharya"),
        "sid sriram" to listOf("Sid Sriram", "Anirudh Ravichander", "A.R. Rahman", "Yuvan Shankar Raja", "Armaan Malik", "Vijay Prakash"),
        "armaan malik" to listOf("Armaan Malik", "Arijit Singh", "Jubin Nautiyal", "Amaal Mallik", "Darshan Raval", "Atif Aslam"),
        "jubin nautiyal" to listOf("Jubin Nautiyal", "Arijit Singh", "Armaan Malik", "Darshan Raval", "Pritam", "Rochak Kohli"),
        "neha kakkar" to listOf("Neha Kakkar", "Tony Kakkar", "Badshah", "Guru Randhawa", "Yo Yo Honey Singh", "Mika Singh"),
        "sunidhi chauhan" to listOf("Sunidhi Chauhan", "Shreya Ghoshal", "Pritam", "Vishal-Shekhar", "Arijit Singh")
    )

    private val GENRE_LANGUAGE_MAP = mapOf(
        "sidhu moose wala" to "Punjabi Hits",
        "karan aujla" to "Punjabi Hits",
        "diljit dosanjh" to "Punjabi Hits",
        "shubh" to "Punjabi Hits",
        "ap dhillon" to "Punjabi Hits",
        "arijit singh" to "Bollywood Romance",
        "pritam" to "Bollywood Hits",
        "shreya ghoshal" to "Melodious Hindi",
        "anirudh ravichander" to "South Indian Blockbusters",
        "a.r. rahman" to "Indian Classics",
        "ar rahman" to "Indian Classics",
        "badshah" to "Desi Hip-Hop",
        "yo yo honey singh" to "Desi Hip-Hop",
        "honey singh" to "Desi Hip-Hop",
        "sonu nigam" to "Evergreen 90s Hits",
        "sid sriram" to "Soulful South Hits",
        "armaan malik" to "Indian Pop",
        "jubin nautiyal" to "Romantic Ballads"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Clean and extract primary artist from raw artist string
     */
    fun extractPrimaryArtist(artistStr: String): String {
        val cleaned = JioSaavnApi.cleanString(artistStr).trim()
        if (cleaned.isEmpty()) return ""

        val delimiters = listOf(",", " feat.", " feat ", " ft.", " ft ", " & ", " and ", " x ", " X ", "/")
        var first = cleaned
        for (delimiter in delimiters) {
            if (first.contains(delimiter, ignoreCase = true)) {
                first = first.split(Regex(Regex.escape(delimiter), RegexOption.IGNORE_CASE))[0].trim()
            }
        }
        return first.trim()
    }

    /**
     * Record a played song into the user's taste profile.
     * Builds long-term preference weighting by tracking artist and genre frequency.
     */
    fun recordPlayedSong(context: Context, song: Song) {
        val primaryArtist = extractPrimaryArtist(song.artist)
        if (primaryArtist.isEmpty() || primaryArtist.equals("Unknown", ignoreCase = true)) return

        val prefs = getPrefs(context)
        val currentHistory = getRecentArtists(context).toMutableList()

        currentHistory.remove(primaryArtist)
        currentHistory.add(0, primaryArtist)
        val trimmed = currentHistory.take(15)

        // Update artist play counts
        val countMap = getArtistPlayCounts(context).toMutableMap()
        val currentCount = countMap[primaryArtist] ?: 0
        countMap[primaryArtist] = currentCount + 1

        // Update genre counts
        val genre = getGenreTag(primaryArtist)
        val genreMap = getGenrePlayCounts(context).toMutableMap()
        val currentGenreCount = genreMap[genre] ?: 0
        genreMap[genre] = currentGenreCount + 1

        // Update memory caches
        cachedRecentArtists = trimmed
        cachedArtistCounts = countMap
        cachedGenreCounts = genreMap
        cachedTopTasteArtists = null

        val encodedCounts = countMap.entries.joinToString(";") { "${it.key}:::${it.value}" }
        val encodedGenres = genreMap.entries.joinToString(";") { "${it.key}:::${it.value}" }
        val encodedHistory = trimmed.joinToString(";;;")

        prefs.edit()
            .putString(KEY_RECENT_ARTISTS, encodedHistory)
            .putString(KEY_LAST_PLAYED_ARTIST, primaryArtist)
            .putString(KEY_LAST_PLAYED_TITLE, song.title)
            .putString(KEY_ARTIST_COUNTS, encodedCounts)
            .putString(KEY_GENRE_COUNTS, encodedGenres)
            .apply()
    }

    fun getLastPlayedArtist(context: Context): String? {
        val artist = getPrefs(context).getString(KEY_LAST_PLAYED_ARTIST, null)
        return artist?.ifEmpty { null }
    }

    fun getRecentArtists(context: Context): List<String> {
        val cached = cachedRecentArtists
        if (cached != null) return cached

        val encoded = getPrefs(context).getString(KEY_RECENT_ARTISTS, null) ?: return emptyList()
        val parsed = encoded.split(";;;").map { it.trim() }.filter { it.isNotEmpty() }
        cachedRecentArtists = parsed
        return parsed
    }

    fun getArtistPlayCounts(context: Context): Map<String, Int> {
        val cached = cachedArtistCounts
        if (cached != null) return cached

        val encoded = getPrefs(context).getString(KEY_ARTIST_COUNTS, null) ?: return emptyMap()
        val parsed = try {
            encoded.split(";").mapNotNull {
                val parts = it.split(":::")
                if (parts.size == 2) {
                    val name = parts[0].trim()
                    val count = parts[1].toIntOrNull() ?: 0
                    if (name.isNotEmpty()) name to count else null
                } else null
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
        cachedArtistCounts = parsed
        return parsed
    }

    fun getGenrePlayCounts(context: Context): Map<String, Int> {
        val cached = cachedGenreCounts
        if (cached != null) return cached

        val encoded = getPrefs(context).getString(KEY_GENRE_COUNTS, null) ?: return emptyMap()
        val parsed = try {
            encoded.split(";").mapNotNull {
                val parts = it.split(":::")
                if (parts.size == 2) {
                    val name = parts[0].trim()
                    val count = parts[1].toIntOrNull() ?: 0
                    if (name.isNotEmpty()) name to count else null
                } else null
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
        cachedGenreCounts = parsed
        return parsed
    }

    /**
     * Get top artists sorted by weighted taste score (play count + recency).
     */
    fun getTopTasteArtists(context: Context): List<String> {
        val cached = cachedTopTasteArtists
        if (cached != null) return cached

        val counts = getArtistPlayCounts(context)
        val recents = getRecentArtists(context)

        if (counts.isEmpty() && recents.isEmpty()) {
            return emptyList()
        }

        val allArtists = (counts.keys + recents).distinct()
        val sorted = allArtists.sortedByDescending { artist ->
            val playCount = counts[artist] ?: 0
            val recencyIndex = recents.indexOf(artist)
            val recencyBonus = if (recencyIndex >= 0) (15 - recencyIndex).coerceAtLeast(0) else 0
            (playCount * 3) + recencyBonus
        }
        cachedTopTasteArtists = sorted
        return sorted
    }

    /**
     * Get related artists based on an artist name.
     */
    fun getRelatedArtists(artistName: String): List<String> {
        val key = artistName.lowercase().trim()
        val directMatch = RELATED_ARTISTS_MAP[key]
        if (directMatch != null) return directMatch

        for ((mapKey, list) in RELATED_ARTISTS_MAP) {
            if (key.contains(mapKey) || mapKey.contains(key)) {
                return list
            }
        }
        return listOf(artistName, "$artistName Hits")
    }

    /**
     * Get genre or mood tag for the artist
     */
    fun getGenreTag(artistName: String): String {
        val key = artistName.lowercase().trim()
        val directMatch = GENRE_LANGUAGE_MAP[key]
        if (directMatch != null) return directMatch

        for ((mapKey, genre) in GENRE_LANGUAGE_MAP) {
            if (key.contains(mapKey) || mapKey.contains(key)) {
                return genre
            }
        }
        return "Top Trending"
    }

    /**
     * Dynamically resolve and cache a live, high-resolution artwork from JioSaavn API.
     */
    suspend fun resolveArtistLiveImage(context: Context, artistName: String): String? = withContext(Dispatchers.IO) {
        val key = artistName.lowercase().trim()
        val mem = liveImageMemoryCache[key]
        if (mem != null && mem.isNotEmpty()) return@withContext mem

        val prefs = context.getSharedPreferences(PREFS_ARTIST_IMAGES, Context.MODE_PRIVATE)
        val saved = prefs.getString(key, null)
        if (saved != null && saved.isNotEmpty()) {
            liveImageMemoryCache[key] = saved
            return@withContext saved
        }

        try {
            val response = JioSaavnApi.service.searchSongs(query = "$artistName top songs", limit = 3)
            val track = response.results.firstOrNull { it.image.isNotEmpty() }
            if (track != null) {
                val highRes = JioSaavnApi.getHighResImageUrl(track)
                if (highRes.isNotEmpty()) {
                    liveImageMemoryCache[key] = highRes
                    prefs.edit().putString(key, highRes).apply()
                    return@withContext highRes
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Dynamically resolve live images for all artist albums in parallel.
     */
    suspend fun resolveAllAlbumImages(
        context: Context,
        albums: List<TrendingArtistAlbum>
    ): List<TrendingArtistAlbum> = withContext(Dispatchers.IO) {
        coroutineScope {
            val deferred = albums.map { album ->
                async {
                    val liveUrl = resolveArtistLiveImage(context, album.artist)
                    if (liveUrl != null && liveUrl.isNotEmpty()) {
                        album.copy(imageUrl = liveUrl)
                    } else {
                        album
                    }
                }
            }
            deferred.map { it.await() }
        }
    }

    /**
     * Fetch a dynamic, blended "For U" feed personalized to the user's analyzed taste.
     */
    suspend fun fetchForYouFeed(context: Context): List<SaavnTrack> = withContext(Dispatchers.IO) {
        val topArtists = getTopTasteArtists(context)
        val lastArtist = getLastPlayedArtist(context)

        if (topArtists.isEmpty() && lastArtist == null) {
            return@withContext JioSaavnApi.fetchRecentSongs()
        }

        val primaryArtist = topArtists.firstOrNull() ?: lastArtist ?: "Sidhu Moose Wala"
        val secondaryArtist = topArtists.getOrNull(1)
            ?: getRelatedArtists(primaryArtist).firstOrNull { !it.equals(primaryArtist, ignoreCase = true) }
            ?: "Karan Aujla"

        val relatedSeed = getRelatedArtists(primaryArtist).filterNot {
            it.equals(primaryArtist, ignoreCase = true) || it.equals(secondaryArtist, ignoreCase = true)
        }.shuffled().firstOrNull() ?: "Diljit Dosanjh"

        try {
            coroutineScope {
                val q1 = async {
                    try {
                        JioSaavnApi.service.searchSongs(query = "$primaryArtist songs", limit = 10).results
                    } catch (_: Exception) { emptyList() }
                }
                val q2 = async {
                    try {
                        JioSaavnApi.service.searchSongs(query = "$secondaryArtist hits", limit = 10).results
                    } catch (_: Exception) { emptyList() }
                }
                val q3 = async {
                    try {
                        JioSaavnApi.service.searchSongs(query = "$relatedSeed top", limit = 8).results
                    } catch (_: Exception) { emptyList() }
                }

                val list1 = q1.await().filter { it.disabled != "true" && it.encryptedMediaUrl.isNotEmpty() }
                val list2 = q2.await().filter { it.disabled != "true" && it.encryptedMediaUrl.isNotEmpty() }
                val list3 = q3.await().filter { it.disabled != "true" && it.encryptedMediaUrl.isNotEmpty() }

                val combined = mutableListOf<SaavnTrack>()
                val maxLen = maxOf(list1.size, list2.size, list3.size)
                val seenIds = mutableSetOf<String>()

                for (i in 0 until maxLen) {
                    if (i < list1.size && seenIds.add(list1[i].id)) combined.add(list1[i])
                    if (i < list2.size && seenIds.add(list2[i].id)) combined.add(list2[i])
                    if (i < list3.size && seenIds.add(list3[i].id)) combined.add(list3[i])
                }

                if (combined.isNotEmpty()) {
                    combined
                } else {
                    JioSaavnApi.fetchRecentSongs()
                }
            }
        } catch (e: Exception) {
            JioSaavnApi.fetchRecentSongs()
        }
    }

    /**
     * Dynamically prioritize and reorder the Album carousel based on user's analyzed taste profile.
     */
    fun getPersonalizedAlbums(
        context: Context,
        allAlbums: List<TrendingArtistAlbum>
    ): List<TrendingArtistAlbum> {
        return try {
            val topArtists = getTopTasteArtists(context)
            val lastArtist = getLastPlayedArtist(context)
            val relatedToTop = topArtists.flatMap { getRelatedArtists(it) }.map { it.lowercase() }

            if (topArtists.isEmpty() && lastArtist == null) return allAlbums

            allAlbums.sortedByDescending { album ->
                val albumArtistLower = album.artist.lowercase()
                val topIndex = topArtists.indexOfFirst { it.lowercase().contains(albumArtistLower) || albumArtistLower.contains(it.lowercase()) }

                when {
                    topIndex == 0 -> 100
                    topIndex > 0 -> 80 - (topIndex * 5)
                    lastArtist != null && (albumArtistLower.contains(lastArtist.lowercase()) || lastArtist.lowercase().contains(albumArtistLower)) -> 60
                    relatedToTop.any { albumArtistLower.contains(it) || it.contains(albumArtistLower) } -> 40
                    else -> 0
                }
            }
        } catch (_: Exception) {
            allAlbums
        }
    }
}
