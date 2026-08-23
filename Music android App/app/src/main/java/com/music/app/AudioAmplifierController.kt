package com.music.app

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Singleton Audio DSP & Amplifier Controller.
 * Manages LoudnessEnhancer (Volume Preamp Boost), BassBoost (Deep Bass),
 * Virtualizer (3D Spatial Surround), and 5-band Equalizer on the active MediaPlayer audio session.
 */
object AudioAmplifierController {

    private const val PREFS_NAME = "clef_audio_amplifier_prefs"
    private const val KEY_ENABLED = "amp_enabled"
    private const val KEY_VOLUME_BOOST = "amp_volume_boost"
    private const val KEY_BASS_BOOST = "amp_bass_boost"
    private const val KEY_VIRTUALIZER = "amp_virtualizer"
    private const val KEY_TREBLE_BOOST = "amp_treble_boost"
    private const val KEY_PRESET = "amp_preset"

    private var currentSessionId: Int = 0
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoostEffect: BassBoost? = null
    private var virtualizerEffect: Virtualizer? = null
    private var equalizerEffect: Equalizer? = null
    private var appContext: Context? = null

    // --- Observable Compose UI State ---
    var isEnabled by mutableStateOf(true)
        private set

    var volumeBoost by mutableFloatStateOf(0.35f) // 0.0 to 1.0 (0 mB to 2000 mB gain)
        private set

    var bassBoost by mutableFloatStateOf(0.55f) // 0.0 to 1.0 (0 to 1000 strength + sub-bass frequency multiplier)
        private set

    var virtualizerStrength by mutableFloatStateOf(0.30f) // 0.0 to 1.0 (0 to 1000 strength)
        private set

    var trebleBoost by mutableFloatStateOf(0.25f) // 0.0 to 1.0 (high frequency gain)
        private set

    var currentPreset by mutableStateOf("Bass Boost")
        private set

    val presets = listOf("Flat", "Bass Boost", "Vocal", "Pop", "Rock", "Electronic")

    /**
     * Initialize preferences on app startup.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        loadPreferences(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadPreferences(context: Context) {
        val prefs = getPrefs(context)
        isEnabled = prefs.getBoolean(KEY_ENABLED, true)
        volumeBoost = prefs.getFloat(KEY_VOLUME_BOOST, 0.35f)
        bassBoost = prefs.getFloat(KEY_BASS_BOOST, 0.40f)
        virtualizerStrength = prefs.getFloat(KEY_VIRTUALIZER, 0.30f)
        trebleBoost = prefs.getFloat(KEY_TREBLE_BOOST, 0.25f)
        currentPreset = prefs.getString(KEY_PRESET, "Bass Boost") ?: "Bass Boost"
    }

    private fun savePreferences() {
        val ctx = appContext ?: return
        getPrefs(ctx).edit()
            .putBoolean(KEY_ENABLED, isEnabled)
            .putFloat(KEY_VOLUME_BOOST, volumeBoost)
            .putFloat(KEY_BASS_BOOST, bassBoost)
            .putFloat(KEY_VIRTUALIZER, virtualizerStrength)
            .putFloat(KEY_TREBLE_BOOST, trebleBoost)
            .putString(KEY_PRESET, currentPreset)
            .apply()
    }

    /**
     * Attach DSP effects to an active MediaPlayer audio session ID.
     */
    fun attachToSession(sessionId: Int, context: Context) {
        if (sessionId <= 0) return
        appContext = context.applicationContext

        if (currentSessionId == sessionId && loudnessEnhancer != null) {
            applyAllEffects()
            return
        }

        release()
        currentSessionId = sessionId

        try {
            // 1. LoudnessEnhancer (Volume Preamp Boost)
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = isEnabled
            }
        } catch (_: Exception) {
            loudnessEnhancer = null
        }

        try {
            // 2. BassBoost (Low end sub-bass extension with priority 1000)
            bassBoostEffect = BassBoost(1000, sessionId).apply {
                enabled = isEnabled
            }
        } catch (_: Exception) {
            bassBoostEffect = null
        }

        try {
            // 3. Virtualizer (3D Spatial surround with priority 1000)
            virtualizerEffect = Virtualizer(1000, sessionId).apply {
                enabled = isEnabled
            }
        } catch (_: Exception) {
            virtualizerEffect = null
        }

        try {
            // 4. Equalizer (Frequency bands & Treble / Clarity with priority 1000)
            equalizerEffect = Equalizer(1000, sessionId).apply {
                enabled = isEnabled
            }
        } catch (_: Exception) {
            equalizerEffect = null
        }

        applyAllEffects()
    }

    /**
     * Apply all current slider parameters to the attached audio effects.
     */
    private fun applyAllEffects() {
        if (!isEnabled) {
            try { loudnessEnhancer?.enabled = false } catch (_: Exception) {}
            try { bassBoostEffect?.enabled = false } catch (_: Exception) {}
            try { virtualizerEffect?.enabled = false } catch (_: Exception) {}
            try { equalizerEffect?.enabled = false } catch (_: Exception) {}
            return
        }

        // Apply Volume Boost (0 to +2000 mB gain)
        try {
            loudnessEnhancer?.apply {
                enabled = true
                val targetGainMb = (volumeBoost * 2000f).toInt()
                setTargetGain(targetGainMb)
            }
        } catch (_: Exception) {}

        // Apply Hardware Bass Boost (0 to 1000 strength)
        try {
            bassBoostEffect?.apply {
                enabled = true
                if (strengthSupported) {
                    val strength = (bassBoost * 1000f).toInt().coerceIn(0, 1000).toShort()
                    setStrength(strength)
                }
            }
        } catch (_: Exception) {}

        // Apply 3D Virtualizer (0 to 1000 strength)
        try {
            virtualizerEffect?.apply {
                enabled = true
                if (strengthSupported) {
                    val strength = (virtualizerStrength * 1000f).toInt().coerceIn(0, 1000).toShort()
                    setStrength(strength)
                }
            }
        } catch (_: Exception) {}

        // Apply Dual Equalizer Bands with dynamic sub-bass and punch
        applyEqualizerBands()
    }

    private fun applyEqualizerBands() {
        val eq = equalizerEffect ?: return
        try {
            eq.enabled = isEnabled
            val numBands = eq.numberOfBands.toInt()
            if (numBands <= 0) return

            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            val range = (maxLevel - minLevel).toFloat()

            // Enhanced Base EQ profiles (-1.0 to 1.0)
            val profile = when (currentPreset) {
                "Bass Boost" -> floatArrayOf(1.0f, 0.75f, 0.1f, 0.1f, 0.3f)
                "Vocal" -> floatArrayOf(-0.2f, 0.2f, 0.7f, 0.5f, 0.2f)
                "Pop" -> floatArrayOf(0.4f, 0.5f, 0.2f, 0.4f, 0.5f)
                "Rock" -> floatArrayOf(0.7f, 0.4f, -0.1f, 0.4f, 0.7f)
                "Electronic" -> floatArrayOf(0.9f, 0.6f, -0.1f, 0.3f, 0.8f)
                else -> floatArrayOf(0f, 0f, 0f, 0f, 0f) // Flat / Normal
            }

            for (i in 0 until numBands) {
                var bandWeight = if (i < profile.size) profile[i] else 0f
                
                // 1. Sub-Bass Supercharge: Boost Band 0 (sub-bass ~60Hz) and Band 1 (punch bass ~230Hz)
                if (i == 0) {
                    bandWeight += (bassBoost * 1.5f)
                } else if (i == 1) {
                    bandWeight += (bassBoost * 0.95f)
                } else if (i == 2) {
                    bandWeight += (bassBoost * 0.20f)
                }

                // 2. Treble / Clarity enhancement to the higher frequency bands (top 2 bands)
                if (i >= numBands - 2) {
                    bandWeight += (trebleBoost * 0.9f)
                }

                val level = (bandWeight * (range / 2f)).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
                eq.setBandLevel(i.toShort(), level)
            }
        } catch (_: Exception) {}
    }

    // --- Public Mutators ---

    fun setMasterEnabled(enabled: Boolean) {
        isEnabled = enabled
        applyAllEffects()
        savePreferences()
    }

    fun setVolumeBoostValue(value: Float) {
        volumeBoost = value.coerceIn(0f, 1f)
        try {
            if (isEnabled) {
                val targetGainMb = (volumeBoost * 2000f).toInt()
                loudnessEnhancer?.setTargetGain(targetGainMb)
            }
        } catch (_: Exception) {}
        savePreferences()
    }

    fun setBassBoostValue(value: Float) {
        bassBoost = value.coerceIn(0f, 1f)
        try {
            if (isEnabled && bassBoostEffect?.strengthSupported == true) {
                val strength = (bassBoost * 1000f).toInt().coerceIn(0, 1000).toShort()
                bassBoostEffect?.setStrength(strength)
            }
            applyEqualizerBands()
        } catch (_: Exception) {}
        savePreferences()
    }

    fun setVirtualizerValue(value: Float) {
        virtualizerStrength = value.coerceIn(0f, 1f)
        try {
            if (isEnabled && virtualizerEffect?.strengthSupported == true) {
                val strength = (virtualizerStrength * 1000f).toInt().coerceIn(0, 1000).toShort()
                virtualizerEffect?.setStrength(strength)
            }
        } catch (_: Exception) {}
        savePreferences()
    }

    fun setTrebleBoostValue(value: Float) {
        trebleBoost = value.coerceIn(0f, 1f)
        applyEqualizerBands()
        savePreferences()
    }

    fun setPresetValue(presetName: String) {
        currentPreset = presetName
        applyEqualizerBands()
        savePreferences()
    }

    fun resetToDefaults() {
        isEnabled = true
        volumeBoost = 0.0f
        bassBoost = 0.0f
        virtualizerStrength = 0.0f
        trebleBoost = 0.0f
        currentPreset = "Flat"
        applyAllEffects()
        savePreferences()
    }

    /**
     * Release all DSP effects on audio session teardown.
     */
    fun release() {
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        try { bassBoostEffect?.release() } catch (_: Exception) {}
        try { virtualizerEffect?.release() } catch (_: Exception) {}
        try { equalizerEffect?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        bassBoostEffect = null
        virtualizerEffect = null
        equalizerEffect = null
        currentSessionId = 0
    }
}
