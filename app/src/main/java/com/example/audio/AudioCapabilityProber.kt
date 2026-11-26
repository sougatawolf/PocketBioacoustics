package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AudioCapabilities(
    val supportedRates: List<AudioQuality> = listOf(
        AudioQuality.VOICE_16K,
        AudioQuality.CD_44K,
        AudioQuality.PRO_48K
    ),
    val maxSampleRateHz: Int = 48000,
    val isHardwareVerified: Boolean = false,
    val isProbing: Boolean = true,
    val inputDeviceLabel: String? = null
) {
    val maxQuality: AudioQuality
        get() = AudioQuality.fromSampleRate(maxSampleRateHz)

    fun isRateSupported(sampleRateHz: Int): Boolean {
        return supportedRates.any { it.sampleRateHz == sampleRateHz }
    }

    fun displayCandidateRates(): List<AudioQuality> {
        return AudioQuality.STANDARD_CANDIDATE_RATES.map { AudioQuality(it) }
    }

    fun closestSupported(preferredRateHz: Int): AudioQuality {
        if (supportedRates.isEmpty()) return AudioQuality.CD_44K
        supportedRates.firstOrNull { it.sampleRateHz == preferredRateHz }?.let { return it }
        return supportedRates
            .filter { it.sampleRateHz <= preferredRateHz }
            .maxByOrNull { it.sampleRateHz }
            ?: supportedRates.first()
    }
}

class AudioCapabilityProber(private val context: Context) {

    private val probeMutex = Mutex()
    private val _capabilities = MutableStateFlow(AudioCapabilities())
    val capabilities: StateFlow<AudioCapabilities> = _capabilities.asStateFlow()

    suspend fun probe(isRecordingActive: Boolean = false): AudioCapabilities = probeMutex.withLock {
        if (isRecordingActive && !_capabilities.value.isProbing) {
            return _capabilities.value
        }

        _capabilities.value = _capabilities.value.copy(isProbing = true)

        val result = withContext(Dispatchers.IO) {
            performProbe()
        }
        _capabilities.value = result
        result
    }

    @SuppressLint("MissingPermission")
    private fun performProbe(): AudioCapabilities {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val inputDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.toList().orEmpty()

        val explicitDeviceRates = inputDevices
            .flatMap { it.sampleRates.toList() }
            .filter { it in 8000..384000 }
            .distinct()

        val deviceLabel = pickPrimaryInputDeviceLabel(inputDevices)
        val nativeOutputRate = audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?.takeIf { it in 8000..384000 }

        // Probe strictly the 9 bioacoustic sampling rate classes
        val candidates = AudioQuality.STANDARD_CANDIDATE_RATES

        val verifiedRates = mutableListOf<Int>()
        val maxExplicitRate = explicitDeviceRates.maxOrNull()

        for (rate in candidates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue

            // Respect hardware input device reported ceiling if available so Android AudioFlinger
            // resampling does not falsely unlock ultrasonic rates (192 kHz / 320 kHz) on 48 kHz mics.
            val withinHardwareBounds = if (maxExplicitRate != null) {
                rate <= maxExplicitRate && (rate in explicitDeviceRates || rate <= 48000)
            } else {
                true
            }

            if (hasMicPermission) {
                if (withinHardwareBounds && canInitializeAudioRecord(rate, minBuf)) {
                    verifiedRates.add(rate)
                }
            } else {
                val allowedPrePermission = if (maxExplicitRate != null) {
                    withinHardwareBounds
                } else {
                    val ceiling = maxOf(48000, nativeOutputRate ?: 48000)
                    rate <= ceiling
                }
                if (allowedPrePermission) {
                    verifiedRates.add(rate)
                }
            }
        }

        if (verifiedRates.isEmpty()) {
            // Safe fallback if a device/emulator rejects standard probing
            val fallback = listOf(16000, 44100, 48000).filter { rate ->
                AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) > 0
            }
            if (fallback.isNotEmpty()) {
                verifiedRates.addAll(fallback)
            } else {
                verifiedRates.add(44100)
            }
        }

        val sortedQualities = verifiedRates.distinct().sorted().map { AudioQuality(sampleRateHz = it) }
        val maxRate = sortedQualities.maxOf { it.sampleRateHz }

        return AudioCapabilities(
            supportedRates = sortedQualities,
            maxSampleRateHz = maxRate,
            isHardwareVerified = hasMicPermission,
            isProbing = false,
            inputDeviceLabel = deviceLabel
        )
    }

    @SuppressLint("MissingPermission")
    private fun canInitializeAudioRecord(sampleRateHz: Int, minBufBytes: Int): Boolean {
        var recorder: AudioRecord? = null
        return try {
            val bufferSize = maxOf(minBufBytes * 2, (sampleRateHz * 2) / 5)
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            recorder.state == AudioRecord.STATE_INITIALIZED
        } catch (_: Exception) {
            false
        } finally {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun pickPrimaryInputDeviceLabel(devices: List<AudioDeviceInfo>): String? {
        if (devices.isEmpty()) return null
        val preferred = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        } ?: devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        } ?: devices.first()

        return when (preferred.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Mic"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Mic"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth Mic"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
            else -> preferred.productName?.toString()?.takeIf { it.isNotBlank() }
        }
    }
}
