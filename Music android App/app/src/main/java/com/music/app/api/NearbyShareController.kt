package com.music.app.api

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import com.music.app.Playlist
import com.music.app.R
import com.music.app.Song
import com.music.app.globalAllSongs
import com.music.app.globalPlaylists
import com.music.app.savePlaylists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

enum class NearbyShareRole {
    IDLE,
    SENDER,
    RECEIVER
}

enum class NearbyShareState {
    IDLE,
    ADVERTISING,     // Receiver is waiting for connections
    DISCOVERING,     // Sender is searching for nearby receivers
    CONNECTING,      // Handshake initiated
    CONFIRMING_PIN,  // Prompting user to verify 4-digit PIN
    TRANSFERRING,    // Payload transfer in progress
    COMPLETED,       // Finished successfully
    ERROR            // Something failed
}

data class DiscoveredReceiver(
    val endpointId: String,
    val endpointName: String
)

data class ShareManifest(
    val transferId: String = UUID.randomUUID().toString(),
    val playlistName: String? = null,
    val songs: List<ShareSongMetadata> = emptyList()
)

data class ShareSongMetadata(
    val payloadId: Long,
    val title: String,
    val artist: String,
    val fileName: String,
    val fileSize: Long,
    val imageUrl: String? = null,
    val onlineStreamUrl: String? = null
)

data class ShareTransferProgress(
    val currentSongTitle: String = "",
    val currentItemIndex: Int = 0,
    val totalItems: Int = 0,
    val progressFraction: Float = 0f,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L
)

object NearbyShareController {

    private const val SERVICE_ID = "com.music.app.nearby_share"
    private val STRATEGY = Strategy.P2P_STAR
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Compose Observable State
    var role by mutableStateOf(NearbyShareRole.IDLE)
        private set
    var state by mutableStateOf(NearbyShareState.IDLE)
        private set
    var statusMessage by mutableStateOf("Ready to share")
        private set
    var authPin by mutableStateOf<String?>(null)
        private set
    var connectedEndpointId by mutableStateOf<String?>(null)
        private set
    var connectedEndpointName by mutableStateOf<String?>(null)
        private set
    var transferProgress by mutableStateOf<ShareTransferProgress?>(null)
        private set
    var completionMessage by mutableStateOf<String?>(null)
        private set

    val discoveredReceivers = mutableStateListOf<DiscoveredReceiver>()

    // Internal Transfer Context
    private var pendingManifestToSend: ShareManifest? = null
    private var pendingSongFilesToSend: List<Pair<ShareSongMetadata, File>> = emptyList()
    private var activeIncomingManifest: ShareManifest? = null
    private val incomingPayloadFiles = mutableMapOf<Long, File>() // payloadId -> temp downloaded File
    private var isTransferCancelled = false

    /**
     * Check if all required runtime permissions for Nearby Share are granted.
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns list of permissions needed based on Android version.
     */
    fun getRequiredPermissions(): List<String> {
        val list = mutableListOf<String>()
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            list.add(Manifest.permission.BLUETOOTH)
            list.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return list
    }

    /**
     * Get a friendly local device name (e.g., "Pixel 8", "Galaxy S23", etc.)
     */
    fun getLocalDeviceName(): String {
        val model = Build.MODEL ?: "Android Device"
        val manufacturer = Build.MANUFACTURER ?: ""
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
    }

    // ==========================================
    // RECEIVE FLOW (Advertising)
    // ==========================================

    fun startReceiving(context: Context) {
        resetState()
        role = NearbyShareRole.RECEIVER
        state = NearbyShareState.ADVERTISING
        statusMessage = "Looking for senders nearby..."

        val deviceName = getLocalDeviceName()
        val options = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        Nearby.getConnectionsClient(context.applicationContext)
            .startAdvertising(
                deviceName,
                SERVICE_ID,
                connectionLifecycleCallback(context),
                options
            )
            .addOnSuccessListener {
                statusMessage = "Waiting for sender to connect..."
            }
            .addOnFailureListener { e ->
                state = NearbyShareState.ERROR
                statusMessage = "Failed to start receiver: ${e.localizedMessage ?: "Unknown error"}"
            }
    }

    // ==========================================
    // SEND FLOW (Discovery & Payload Transmission)
    // ==========================================

    fun prepareSendPayload(
        context: Context,
        playlistName: String?,
        selectedSongs: List<Song>
    ) {
        resetState()
        role = NearbyShareRole.SENDER
        state = NearbyShareState.DISCOVERING
        statusMessage = "Scanning for nearby receivers..."
        discoveredReceivers.clear()

        scope.launch(Dispatchers.IO) {
            val validFiles = mutableListOf<Pair<ShareSongMetadata, File>>()
            val metadataList = mutableListOf<ShareSongMetadata>()

            for (song in selectedSongs) {
                val file = resolveLocalSongFile(context, song)
                if (file != null && file.exists() && file.length() > 0) {
                    val payloadId = System.currentTimeMillis() + (1000..9999).random()
                    val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifEmpty { "Song" }
                    val cleanArtist = song.artist.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
                    val fileName = "${cleanTitle}_$cleanArtist".take(50).trimEnd() + ".mp3"

                    val meta = ShareSongMetadata(
                        payloadId = payloadId,
                        title = song.title,
                        artist = song.artist,
                        fileName = fileName,
                        fileSize = file.length(),
                        imageUrl = song.imageUrl,
                        onlineStreamUrl = song.onlineStreamUrl
                    )
                    metadataList.add(meta)
                    validFiles.add(Pair(meta, file))
                }
            }

            pendingManifestToSend = ShareManifest(
                playlistName = playlistName,
                songs = metadataList
            )
            pendingSongFilesToSend = validFiles

            withContext(Dispatchers.Main) {
                if (validFiles.isEmpty()) {
                    state = NearbyShareState.ERROR
                    statusMessage = "No local audio files found for the selected songs."
                } else {
                    startDiscovery(context)
                }
            }
        }
    }

    private fun startDiscovery(context: Context) {
        val options = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        Nearby.getConnectionsClient(context.applicationContext)
            .startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            )
            .addOnSuccessListener {
                statusMessage = "Scanning for devices on radar..."
            }
            .addOnFailureListener { e ->
                state = NearbyShareState.ERROR
                statusMessage = "Discovery failed: ${e.localizedMessage ?: "Bluetooth/Wi-Fi error"}"
            }
    }

    fun connectToReceiver(context: Context, receiver: DiscoveredReceiver) {
        state = NearbyShareState.CONNECTING
        statusMessage = "Connecting to ${receiver.endpointName}..."
        connectedEndpointName = receiver.endpointName

        Nearby.getConnectionsClient(context.applicationContext)
            .stopDiscovery()

        Nearby.getConnectionsClient(context.applicationContext)
            .requestConnection(
                getLocalDeviceName(),
                receiver.endpointId,
                connectionLifecycleCallback(context)
            )
            .addOnFailureListener { e ->
                state = NearbyShareState.ERROR
                statusMessage = "Connection request failed: ${e.localizedMessage}"
            }
    }

    fun acceptConnection(context: Context) {
        val endpointId = connectedEndpointId ?: return
        state = NearbyShareState.CONNECTING
        statusMessage = "Accepting connection..."

        Nearby.getConnectionsClient(context.applicationContext)
            .acceptConnection(endpointId, payloadCallback(context))
            .addOnFailureListener { e ->
                state = NearbyShareState.ERROR
                statusMessage = "Failed to accept connection: ${e.localizedMessage}"
            }
    }

    fun rejectConnection(context: Context) {
        val endpointId = connectedEndpointId ?: return
        Nearby.getConnectionsClient(context.applicationContext)
            .rejectConnection(endpointId)
        resetState()
    }

    // ==========================================
    // SENDING EXECUTION
    // ==========================================

    private fun startTransmittingPayloads(context: Context, endpointId: String) {
        val manifest = pendingManifestToSend ?: return
        val songFiles = pendingSongFilesToSend

        state = NearbyShareState.TRANSFERRING
        statusMessage = "Sending playlist metadata..."

        val totalBytes = songFiles.sumOf { it.second.length() }
        transferProgress = ShareTransferProgress(
            currentSongTitle = "Preparing transfer...",
            currentItemIndex = 0,
            totalItems = songFiles.size,
            progressFraction = 0f,
            bytesTransferred = 0L,
            totalBytes = totalBytes
        )

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Send JSON Manifest
                val manifestJson = gson.toJson(manifest)
                val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
                val manifestPayload = Payload.fromBytes(manifestBytes)

                Nearby.getConnectionsClient(context.applicationContext)
                    .sendPayload(endpointId, manifestPayload)

                delay(300) // Brief handshake delay

                // 2. Send Audio File Payloads
                var songIndex = 0
                for ((meta, file) in songFiles) {
                    if (isTransferCancelled) break
                    songIndex++

                    withContext(Dispatchers.Main) {
                        transferProgress = transferProgress?.copy(
                            currentSongTitle = meta.title,
                            currentItemIndex = songIndex,
                            totalItems = songFiles.size
                        )
                        statusMessage = "Sending ${meta.title} ($songIndex of ${songFiles.size})..."
                    }

                    // Open file descriptor for direct file payload
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val filePayload = Payload.fromFile(pfd)

                    // Note: Nearby Connections uses internal payload IDs. We send the payload.
                    Nearby.getConnectionsClient(context.applicationContext)
                        .sendPayload(endpointId, filePayload)
                }

                withContext(Dispatchers.Main) {
                    state = NearbyShareState.COMPLETED
                    completionMessage = if (manifest.playlistName != null) {
                        "Successfully sent '${manifest.playlistName}' (${songFiles.size} songs) to $connectedEndpointName!"
                    } else {
                        "Successfully sent ${songFiles.size} songs to $connectedEndpointName!"
                    }
                    statusMessage = "Transfer complete!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state = NearbyShareState.ERROR
                    statusMessage = "Failed during file transmission: ${e.localizedMessage}"
                }
            }
        }
    }

    // ==========================================
    // CALLBACKS & PROTOCOL LIFECYCLE
    // ==========================================

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (discoveredReceivers.none { it.endpointId == endpointId }) {
                discoveredReceivers.add(DiscoveredReceiver(endpointId, info.endpointName))
            }
        }

        override fun onEndpointLost(endpointId: String) {
            discoveredReceivers.removeAll { it.endpointId == endpointId }
        }
    }

    private fun connectionLifecycleCallback(context: Context) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectedEndpointId = endpointId
            connectedEndpointName = info.endpointName
            authPin = info.authenticationDigits
            state = NearbyShareState.CONFIRMING_PIN
            statusMessage = "Verify PIN to connect with ${info.endpointName}"
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    statusMessage = "Connected to $connectedEndpointName!"
                    if (role == NearbyShareRole.SENDER) {
                        startTransmittingPayloads(context, endpointId)
                    } else {
                        state = NearbyShareState.TRANSFERRING
                        statusMessage = "Waiting for incoming songs from $connectedEndpointName..."
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    state = NearbyShareState.ERROR
                    statusMessage = "Connection rejected by $connectedEndpointName"
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    state = NearbyShareState.ERROR
                    statusMessage = "Connection failed"
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (state != NearbyShareState.COMPLETED) {
                state = NearbyShareState.IDLE
                statusMessage = "Disconnected"
            }
        }
    }

    private fun payloadCallback(context: Context) = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    // JSON Manifest arrived
                    try {
                        val json = String(payload.asBytes()!!, Charsets.UTF_8)
                        val manifest = gson.fromJson(json, ShareManifest::class.java)
                        activeIncomingManifest = manifest
                        incomingPayloadFiles.clear()

                        val totalBytes = manifest.songs.sumOf { it.fileSize }
                        transferProgress = ShareTransferProgress(
                            currentSongTitle = "Incoming: ${manifest.playlistName ?: "Songs"}",
                            currentItemIndex = 0,
                            totalItems = manifest.songs.size,
                            progressFraction = 0f,
                            bytesTransferred = 0L,
                            totalBytes = totalBytes
                        )
                        statusMessage = "Receiving ${manifest.songs.size} songs from $connectedEndpointName..."
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Payload.Type.FILE -> {
                    // Nearby saves received file payload to a temp file in app cache
                    val javaFile = payload.asFile()?.asJavaFile()
                    if (javaFile != null) {
                        incomingPayloadFiles[payload.id] = javaFile
                    }
                }
                Payload.Type.STREAM -> {
                    // If stream payload is used
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val progress = transferProgress
            val totalBytes = update.totalBytes
            val bytesTransferred = update.bytesTransferred

            if (totalBytes > 0) {
                val fraction = (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                transferProgress = progress?.copy(
                    progressFraction = fraction,
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes
                )
            }

            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                // Check if all payloads for incoming manifest are completed
                val manifest = activeIncomingManifest
                if (role == NearbyShareRole.RECEIVER && manifest != null) {
                    scope.launch(Dispatchers.IO) {
                        finalizeReceivedTransfer(context, manifest)
                    }
                }
            }
        }
    }

    // ==========================================
    // PERSISTENCE & IMPORTING ON RECEIVER
    // ==========================================

    private suspend fun finalizeReceivedTransfer(context: Context, manifest: ShareManifest) {
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val clefFolder = File(musicDir, "Clef")
        if (!clefFolder.exists()) clefFolder.mkdirs()

        val importedSongs = mutableListOf<Song>()

        for ((index, meta) in manifest.songs.withIndex()) {
            val tempFile = incomingPayloadFiles.values.toList().getOrNull(index)
            val targetFile = File(clefFolder, meta.fileName)

            if (tempFile != null && tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                try {
                    copyFile(tempFile, targetFile)
                    tempFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val validFile = if (targetFile.exists() && targetFile.length() > 1024) targetFile else tempFile
            val fileUri = if (validFile != null && validFile.exists()) Uri.fromFile(validFile).toString() else null

            val randomIndex = (1..25).random()
            val imageResId = context.resources.getIdentifier("slideshow_$randomIndex", "drawable", context.packageName)

            val song = Song(
                title = meta.title,
                artist = meta.artist,
                imageRes = if (imageResId != 0) imageResId else R.drawable.pic_4,
                uriString = fileUri,
                imageUrl = meta.imageUrl,
                onlineStreamUrl = meta.onlineStreamUrl
            )
            importedSongs.add(song)
        }

        withContext(Dispatchers.Main) {
            // 1. Add all imported songs to globalAllSongs if not already present
            for (s in importedSongs) {
                if (s.uriString != null && globalAllSongs.none { it.uriString == s.uriString }) {
                    globalAllSongs.add(s)
                }
            }

            // 2. If a playlist was shared, create or add to globalPlaylists
            val pName = manifest.playlistName
            if (!pName.isNullOrBlank()) {
                val existingPlaylist = globalPlaylists.firstOrNull { it.name.equals(pName, ignoreCase = true) }
                if (existingPlaylist != null) {
                    for (s in importedSongs) {
                        if (existingPlaylist.songs.none { it.title == s.title && it.artist == s.artist }) {
                            existingPlaylist.songs.add(s)
                        }
                    }
                } else {
                    // Pick matching colors for new playlist
                    val newPlaylist = Playlist(
                        name = pName,
                        songs = mutableStateListOf<Song>().apply { addAll(importedSongs) },
                        coverImageRes = R.drawable.pic_1,
                        backgroundColor = androidx.compose.ui.graphics.Color(0xFF2E3A46),
                        textColor = androidx.compose.ui.graphics.Color(0xFFE5DED5),
                        subTextColor = androidx.compose.ui.graphics.Color(0xFFA0AAB5)
                    )
                    globalPlaylists.add(newPlaylist)
                }
            }

            savePlaylists(context)

            state = NearbyShareState.COMPLETED
            completionMessage = if (!pName.isNullOrBlank()) {
                "Received '${pName}' with ${importedSongs.size} songs from $connectedEndpointName!"
            } else {
                "Received ${importedSongs.size} songs from $connectedEndpointName!"
            }
            statusMessage = "Import completed successfully!"
        }
    }

    private fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Locate existing physical file for a given Song.
     */
    private fun resolveLocalSongFile(context: Context, song: Song): File? {
        val uriStr = song.uriString
        if (!uriStr.isNullOrBlank()) {
            if (uriStr.startsWith("file:")) {
                val path = Uri.parse(uriStr).path
                if (path != null) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0) return f
                }
            } else if (uriStr.startsWith("content:")) {
                // If it's a content URI from Android Document Provider, copy to temp file for sharing
                try {
                    val uri = Uri.parse(uriStr)
                    val tempFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.mp3")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        return tempFile
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Check Clef folder
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val clefFolder = File(musicDir, "Clef")
        if (clefFolder.exists() && clefFolder.isDirectory) {
            val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().ifEmpty { "Song" }
            val matched = clefFolder.listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith(cleanTitle, ignoreCase = true) && it.length() > 1024
            }
            if (matched != null) return matched
        }

        return null
    }

    // ==========================================
    // CLEANUP & DISCONNECT
    // ==========================================

    fun stopAll(context: Context) {
        try {
            isTransferCancelled = true
            Nearby.getConnectionsClient(context.applicationContext).stopAllEndpoints()
            Nearby.getConnectionsClient(context.applicationContext).stopAdvertising()
            Nearby.getConnectionsClient(context.applicationContext).stopDiscovery()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            resetState()
        }
    }

    fun resetState() {
        isTransferCancelled = false
        role = NearbyShareRole.IDLE
        state = NearbyShareState.IDLE
        statusMessage = "Ready to share"
        authPin = null
        connectedEndpointId = null
        connectedEndpointName = null
        transferProgress = null
        completionMessage = null
        discoveredReceivers.clear()
        pendingManifestToSend = null
        pendingSongFilesToSend = emptyList()
        activeIncomingManifest = null
        incomingPayloadFiles.clear()
    }
}
