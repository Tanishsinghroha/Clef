package com.music.app.api

import android.util.Base64
import android.util.LruCache
import com.google.gson.annotations.SerializedName
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// --- Data Classes for JioSaavn API response ---

data class SaavnSearchResponse(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<SaavnTrack> = emptyList()
)

data class SaavnPlaylistResponse(
    val songs: List<SaavnTrack> = emptyList()
)

data class SaavnTrack(
    val id: String = "",
    @SerializedName("song") val title: String = "",
    val album: String = "",
    @SerializedName("primary_artists") val primaryArtists: String = "",
    val singers: String = "",
    val image: String = "",
    val duration: String = "0",
    @SerializedName("encrypted_media_url") val encryptedMediaUrl: String = "",
    @SerializedName("media_preview_url") val mediaPreviewUrl: String = "",
    @SerializedName("320kbps") val has320kbps: String = "false",
    val language: String = "",
    val year: String = "",
    @SerializedName("play_count") val playCount: String = "0",
    val disabled: String = "false",
    @SerializedName("has_lyrics") val hasLyrics: String = "false"
) {
    val cleanTitle: String
        get() = JioSaavnApi.cleanString(title)

    val cleanArtist: String
        get() = JioSaavnApi.cleanString(singers.ifEmpty { primaryArtists })
}

// --- Retrofit Service Interface ---

interface JioSaavnApiService {
    @GET("api.php?__call=search.getResults&_format=json&_marker=0&cc=in")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("p") page: Int = 1,
        @Query("n") limit: Int = 20
    ): SaavnSearchResponse

    @GET("api.php?__call=playlist.getDetails&_format=json&_marker=0&cc=in")
    suspend fun getPlaylistDetails(
        @Query("listid") listId: String = "110858205" // Trending Today playlist ID
    ): SaavnPlaylistResponse
}

// --- Singleton API Client ---

object JioSaavnApi {
    private const val BASE_URL = "https://www.jiosaavn.com/"
    private const val DES_KEY = "38346591"

    // High-performance thread-safe LRU caches
    private val mediaUrlCache = LruCache<String, String>(1000)
    private val cleanStringCache = LruCache<String, String>(1000)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: JioSaavnApiService = retrofit.create(JioSaavnApiService::class.java)

    /**
     * Unescape HTML entities like &quot;, &#039;, &amp;, etc. with LRU caching.
     */
    fun cleanString(str: String): String {
        if (str.isEmpty()) return ""
        val cached = cleanStringCache.get(str)
        if (cached != null) return cached

        val cleaned = str
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        cleanStringCache.put(str, cleaned)
        return cleaned
    }

    /**
     * Decrypt a JioSaavn encrypted_media_url using DES-ECB with key "38346591".
     * Uses LRU cache to avoid repeating expensive crypto on the UI thread.
     */
    fun decryptMediaUrl(encryptedUrl: String): String {
        if (encryptedUrl.isEmpty()) return ""
        val cached = mediaUrlCache.get(encryptedUrl)
        if (cached != null) return cached

        val decrypted = try {
            val keyBytes = DES_KEY.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            val encryptedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8).trim().replace("http://", "https://")
        } catch (e: Exception) {
            ""
        }
        if (decrypted.isNotEmpty()) {
            mediaUrlCache.put(encryptedUrl, decrypted)
        }
        return decrypted
    }

    /**
     * Get a direct playable URL from a SaavnTrack.
     * Decrypts the encrypted_media_url and upgrades quality to 320kbps if available.
     */
    fun getStreamUrl(track: SaavnTrack): String {
        val decryptedUrl = decryptMediaUrl(track.encryptedMediaUrl)
        if (decryptedUrl.isEmpty()) return ""

        // The decrypted URL typically looks like:
        // https://aac.saavncdn.com/.../songid_96.mp4
        // We can replace _96 with _320 for higher quality
        return if (track.has320kbps == "true") {
            decryptedUrl.replace("_96.mp4", "_320.mp4")
        } else {
            decryptedUrl.replace("_96.mp4", "_160.mp4")
        }
    }

    /**
     * Get a high-resolution image URL from a SaavnTrack.
     * JioSaavn returns 150x150 by default; we upgrade to 500x500.
     */
    fun getHighResImageUrl(track: SaavnTrack): String {
        val raw = track.image.trim()
        if (raw.isEmpty()) return ""
        return raw
            .replace("http://", "https://")
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
    }

    /**
     * Fetch recent/latest songs from JioSaavn playlist with fallback to latest songs search.
     */
    suspend fun fetchRecentSongs(): List<SaavnTrack> {
        return try {
            val playlistResponse = service.getPlaylistDetails("946682072")
            val validSongs = playlistResponse.songs.filter { it.disabled != "true" && it.encryptedMediaUrl.isNotEmpty() }
            if (validSongs.isNotEmpty()) {
                validSongs
            } else {
                val searchResponse = service.searchSongs("Latest Songs", limit = 20)
                searchResponse.results.filter { it.disabled != "true" }
            }
        } catch (e: Exception) {
            try {
                val searchResponse = service.searchSongs("Latest Songs", limit = 20)
                searchResponse.results.filter { it.disabled != "true" }
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }
}
