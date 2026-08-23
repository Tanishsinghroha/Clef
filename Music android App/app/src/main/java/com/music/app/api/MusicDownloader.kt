package com.music.app.api

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.music.app.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MusicDownloader {

    /**
     * Checks if a song is already downloaded locally on the device.
     * If already downloaded, returns the Song with its valid local file URI.
     * If not downloaded, returns null.
     */
    fun findExistingDownloadedSong(context: Context, song: Song, existingLibrary: List<Song>? = null): Song? {
        // 1. Direct check: uriString is already a local file that exists
        val currentUri = song.uriString
        if (currentUri != null && !currentUri.startsWith("http://") && !currentUri.startsWith("https://")) {
            if (currentUri.startsWith("file:")) {
                val path = Uri.parse(currentUri).path
                if (path != null && File(path).let { it.exists() && it.length() > 1024 }) {
                    return song
                }
            } else if (currentUri.startsWith("content:")) {
                return song
            }
        }

        // 2. Check existing library (e.g. globalAllSongs / Downloads) for matching title & artist
        if (existingLibrary != null) {
            val matching = existingLibrary.firstOrNull {
                it.title.trim().equals(song.title.trim(), ignoreCase = true) &&
                it.artist.trim().equals(song.artist.trim(), ignoreCase = true) &&
                it.uriString != null &&
                !it.uriString.startsWith("http://") &&
                !it.uriString.startsWith("https://")
            }
            if (matching != null && matching.uriString != null) {
                if (matching.uriString.startsWith("file:")) {
                    val path = Uri.parse(matching.uriString).path
                    if (path != null && File(path).let { it.exists() && it.length() > 1024 }) {
                        return song.copy(uriString = matching.uriString, onlineStreamUrl = song.onlineStreamUrl ?: matching.onlineStreamUrl)
                    }
                } else {
                    return song.copy(uriString = matching.uriString, onlineStreamUrl = song.onlineStreamUrl ?: matching.onlineStreamUrl)
                }
            }
        }

        // 3. Check Clef directory for existing file with consistent name
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val clefFolder = File(musicDir, "Clef")
        if (clefFolder.exists() && clefFolder.isDirectory) {
            val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifEmpty { "Song" }
            val cleanArtist = song.artist.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
            val baseName = "${cleanTitle}_$cleanArtist".take(50).trimEnd()
            
            val targetFile = File(clefFolder, "$baseName.mp3")
            if (targetFile.exists() && targetFile.length() > 1024) {
                val localUri = Uri.fromFile(targetFile).toString()
                return song.copy(uriString = localUri, onlineStreamUrl = song.onlineStreamUrl ?: song.uriString)
            }

            // Also check for any file starting with safeTitle in Clef folder
            val matchedFile = clefFolder.listFiles()?.firstOrNull { file ->
                file.isFile && file.name.endsWith(".mp3") && file.length() > 1024 &&
                file.nameWithoutExtension.startsWith(cleanTitle, ignoreCase = true)
            }
            if (matchedFile != null) {
                val localUri = Uri.fromFile(matchedFile).toString()
                return song.copy(uriString = localUri, onlineStreamUrl = song.onlineStreamUrl ?: song.uriString)
            }
        }

        return null
    }

    /**
     * Download an online song to local app storage (Clef directory).
     * If track is already downloaded, skips downloading and returns the local Song.
     * Returns a new Song with local file URI, or null if download failed.
     */
    suspend fun downloadSong(context: Context, song: Song, existingLibrary: List<Song>? = null): Song? = withContext(Dispatchers.IO) {
        // Fast-path: Check if track is already downloaded on disk or in library
        val existing = findExistingDownloadedSong(context, song, existingLibrary)
        if (existing != null) {
            return@withContext existing
        }

        val streamUrl = song.onlineStreamUrl ?: song.uriString ?: return@withContext null
        if (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://")) {
            // Already a local file
            return@withContext song
        }

        val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifEmpty { "Song" }
        val cleanArtist = song.artist.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
        val fileName = "${cleanTitle}_$cleanArtist".take(50).trimEnd() + ".mp3"

        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val clefFolder = File(musicDir, "Clef")
        if (!clefFolder.exists()) {
            clefFolder.mkdirs()
        }

        val targetFile = File(clefFolder, fileName)
        val tempFile = File(clefFolder, "$fileName.tmp")

        try {
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.connect()

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 1024) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                    val localUri = Uri.fromFile(targetFile).toString()
                    return@withContext song.copy(
                        uriString = localUri,
                        onlineStreamUrl = streamUrl
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
        null
    }
}
