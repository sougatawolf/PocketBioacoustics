package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class PlaybackState(
    val activeFilePath: String? = null,
    val activeFileName: String? = null,
    val activeFileExtension: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

class AudioPlaybackManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null

    private var pcmPlaybackJob: Job? = null
    private var pcmAudio: DecodedPcmAudio? = null
    private var pcmTrack: AudioTrack? = null
    private val pcmByteCursor = AtomicInteger(0)
    @Volatile
    private var pcmPlaying = false

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun togglePlay(file: File, startPositionMs: Long = 0L) {
        val current = _state.value
        val hasActive = mediaPlayer != null || pcmAudio != null
        if (current.activeFilePath == file.absolutePath && hasActive) {
            if (current.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }
        playFile(file, startPositionMs)
    }

    fun playFile(file: File, startPositionMs: Long = 0L) {
        releasePlayer()
        if (!file.exists()) {
            _state.value = PlaybackState(errorMessage = "Audio file could not be found.")
            return
        }

        val format = RecordingFileFormat.fromFile(file)
        if (format == RecordingFileFormat.WAV || format == RecordingFileFormat.FLAC) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        tickerJob?.cancel()
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            positionMs = _state.value.durationMs
                        )
                    }
                }
                val duration = player.duration.toLong().coerceAtLeast(0L)
                val clampedStart = startPositionMs.coerceIn(0L, duration)
                if (clampedStart > 0L) {
                    player.seekTo(clampedStart.toInt())
                }
                player.start()
                mediaPlayer = player
                _state.value = PlaybackState(
                    activeFilePath = file.absolutePath,
                    activeFileName = file.nameWithoutExtension,
                    activeFileExtension = file.extension,
                    isPlaying = true,
                    positionMs = clampedStart,
                    durationMs = duration
                )
                startTicker()
                return
            } catch (_: Exception) {
                releaseMediaPlayerOnly()
            }
        }

        // Direct PCM playback via AudioTrack for AIFF, ALAC (.m4a), and high-rate FLAC fallback
        scope.launch(Dispatchers.IO) {
            val decoded = when (format) {
                RecordingFileFormat.AIFF -> AiffFormat.decodeToLittleEndianPcm(file)
                RecordingFileFormat.ALAC -> AlacFormat.decodeToLittleEndianPcm(file)
                RecordingFileFormat.FLAC -> FlacFormat.decodeToLittleEndianPcm(file)
                else -> null
            }
            if (decoded == null || decoded.pcmLittleEndian.isEmpty()) {
                _state.value = PlaybackState(errorMessage = "Unable to play ${file.name}.")
                return@launch
            }
            val origBytesPerSec = (decoded.sampleRateHz.toLong() * decoded.channels * 2L).coerceAtLeast(1L)
            val durationMs = (decoded.pcmLittleEndian.size.toLong() * 1000L) / origBytesPerSec
            val (playableRate, playableBytes, playableChannels) = preparePlayablePcm(decoded)
            val playable = DecodedPcmAudio(
                sampleRateHz = playableRate,
                channels = playableChannels,
                bitsPerSample = decoded.bitsPerSample,
                pcmLittleEndian = playableBytes
            )
            pcmAudio = playable
            val clampedStart = startPositionMs.coerceIn(0L, durationMs)
            val playableBytesPerSec = (playable.sampleRateHz.toLong() * playable.channels * 2L).coerceAtLeast(1L)
            val startByte = (((clampedStart * playableBytesPerSec) / 1000L).toInt() and 1.inv())
                .coerceIn(0, playable.pcmLittleEndian.size)
            pcmByteCursor.set(startByte)
            pcmPlaying = true
            _state.value = PlaybackState(
                activeFilePath = file.absolutePath,
                activeFileName = file.nameWithoutExtension,
                activeFileExtension = file.extension,
                isPlaying = true,
                positionMs = clampedStart,
                durationMs = durationMs
            )
            startPcmLoop(playable, durationMs)
        }
    }

    private fun startPcmLoop(decoded: DecodedPcmAudio, durationMs: Long) {
        pcmPlaybackJob?.cancel()
        pcmPlaybackJob = scope.launch(Dispatchers.IO) {
            val sampleRate = decoded.sampleRateHz.coerceIn(8000, 192000)
            val channelMask = if (decoded.channels == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelMask)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                _state.value = PlaybackState(
                    errorMessage = e.localizedMessage ?: "AudioTrack initialization failed."
                )
                return@launch
            }

            pcmTrack = track
            val bytesPerSecond = (sampleRate.toLong() * decoded.channels * 2L).coerceAtLeast(1L)
            val chunkSize = 4096
            val pcm = decoded.pcmLittleEndian

            try {
                track.play()
                while (isActive) {
                    if (!pcmPlaying) {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.pause()
                        }
                        delay(60L)
                        continue
                    }
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                    val cursor = pcmByteCursor.get()
                    if (cursor >= pcm.size) {
                        pcmPlaying = false
                        pcmByteCursor.set(0)
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            positionMs = durationMs
                        )
                        delay(80L)
                        continue
                    }
                    val count = minOf(chunkSize, pcm.size - cursor)
                    val written = track.write(pcm, cursor, count)
                    if (written > 0) {
                        val nextCursor = pcmByteCursor.addAndGet(written) and 1.inv()
                        val posMs = ((nextCursor.toLong() * 1000L) / bytesPerSecond).coerceAtMost(durationMs)
                        _state.value = _state.value.copy(positionMs = posMs)
                    } else {
                        delay(20L)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                } catch (_: Exception) {
                }
                try {
                    track.release()
                } catch (_: Exception) {
                }
                if (pcmTrack === track) pcmTrack = null
            }
        }
    }

    private fun preparePlayablePcm(decoded: DecodedPcmAudio): Triple<Int, ByteArray, Int> {
        val channelMask = if (decoded.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val rawRate = decoded.sampleRateHz.coerceAtLeast(8000)
        if (rawRate <= 192000 &&
            AudioTrack.getMinBufferSize(rawRate, channelMask, AudioFormat.ENCODING_PCM_16BIT) > 0
        ) {
            return Triple(rawRate, decoded.pcmLittleEndian, decoded.channels)
        }
        // Decimate ultrasonic recordings (e.g. 320 kHz -> 160 kHz or 80 kHz) to a supported AudioTrack rate
        var divisor = 2
        while (rawRate / divisor > 192000 || (rawRate / divisor >= 8000 &&
                AudioTrack.getMinBufferSize(rawRate / divisor, channelMask, AudioFormat.ENCODING_PCM_16BIT) <= 0)
        ) {
            divisor *= 2
            if (rawRate / divisor < 8000) break
        }
        val targetRate = (rawRate / divisor).coerceIn(8000, 192000)
        val frameBytes = decoded.channels * 2
        val totalFrames = decoded.pcmLittleEndian.size / frameBytes
        val outFrames = totalFrames / divisor
        val decimated = ByteArray(outFrames * frameBytes)
        for (f in 0 until outFrames) {
            val srcOffset = f * divisor * frameBytes
            val dstOffset = f * frameBytes
            System.arraycopy(decoded.pcmLittleEndian, srcOffset, decimated, dstOffset, frameBytes)
        }
        return Triple(targetRate, decimated, decoded.channels)
    }

    fun pause() {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            player.pause()
            tickerJob?.cancel()
            _state.value = _state.value.copy(
                isPlaying = false,
                positionMs = player.currentPosition.toLong().coerceAtLeast(0L)
            )
            return
        }
        if (pcmAudio != null && pcmPlaying) {
            pcmPlaying = false
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun resume() {
        val player = mediaPlayer
        if (player != null && !player.isPlaying) {
            player.start()
            _state.value = _state.value.copy(isPlaying = true)
            startTicker()
            return
        }
        if (pcmAudio != null && !pcmPlaying) {
            pcmPlaying = true
            _state.value = _state.value.copy(isPlaying = true)
        }
    }

    fun seekTo(positionMs: Long) {
        val clampedMs = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        val player = mediaPlayer
        if (player != null) {
            player.seekTo(clampedMs.toInt())
            _state.value = _state.value.copy(positionMs = clampedMs)
            return
        }
        val decoded = pcmAudio ?: return
        val bytesPerSecond = (decoded.sampleRateHz.toLong() * decoded.channels * 2L).coerceAtLeast(1L)
        val bytePos = (((clampedMs * bytesPerSecond) / 1000L).toInt() and 1.inv())
            .coerceIn(0, decoded.pcmLittleEndian.size)
        pcmByteCursor.set(bytePos)
        _state.value = _state.value.copy(positionMs = clampedMs)
    }

    fun stop() {
        releasePlayer()
        _state.value = PlaybackState()
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition.toLong().coerceAtLeast(0L)
                    )
                }
                delay(70L)
            }
        }
    }

    private fun releaseMediaPlayerOnly() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun releasePlayer() {
        releaseMediaPlayerOnly()
        pcmPlaying = false
        pcmPlaybackJob?.cancel()
        pcmPlaybackJob = null
        pcmAudio = null
        pcmByteCursor.set(0)
        try {
            pcmTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            pcmTrack?.release()
        } catch (_: Exception) {
        }
        pcmTrack = null
    }
}
