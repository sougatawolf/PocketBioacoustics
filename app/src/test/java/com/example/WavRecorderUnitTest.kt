package com.example

import com.example.audio.AcousticTriggerConfig
import com.example.audio.AcousticTriggerEvaluator
import com.example.audio.AiffFileWriter
import com.example.audio.AiffFormat
import com.example.audio.AlacFileWriter
import com.example.audio.AlacFormat
import com.example.audio.AudioCapabilities
import com.example.audio.AudioMetadataParser
import com.example.audio.AudioQuality
import com.example.audio.AudioSegmentWriterFactory
import com.example.audio.BatteryPowerEstimator
import com.example.audio.BatterySnapshot
import com.example.audio.FlacFileWriter
import com.example.audio.FlacFormat
import com.example.audio.GpsCoordinate
import com.example.audio.GpsTagMode
import com.example.audio.LowCutFilter
import com.example.audio.PcmRingBuffer
import com.example.audio.RecordingFileFormat
import com.example.audio.RecordingVisualizationExtractor
import com.example.audio.SpectrogramAnalyzer
import com.example.audio.SpectrogramZoomMath
import com.example.audio.StorageEstimator
import com.example.audio.StorageHealth
import com.example.audio.StorageSnapshot
import com.example.audio.TimeZoomMath
import com.example.audio.VisualizerMode
import com.example.audio.WavFileWriter
import com.example.audio.WavHeader
import com.example.data.RecorderSettings
import com.example.data.RecordingSchedule
import com.example.data.ScheduleMode
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Calendar

class WavRecorderUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun wavFileWriter_writesValidRiffHeaderAndDuration() {
        val wavFile = File(tempFolder.root, "test_segment.wav")
        val quality = AudioQuality.CD_44K // 44100 Hz, 1 ch, 16-bit => 88200 bytes/sec

        val writer = WavFileWriter(wavFile, quality)
        val twoSecondsBytes = ByteArray(88200 * 2) { 0x1A }
        writer.writePcm(twoSecondsBytes, twoSecondsBytes.size)
        writer.close()

        assertTrue(wavFile.exists())
        assertEquals(44L + twoSecondsBytes.size.toLong(), wavFile.length())

        val parsed = WavHeader.parse(wavFile)
        assertNotNull(parsed)
        parsed!!
        assertEquals(44100, parsed.sampleRateHz)
        assertEquals(1, parsed.channels)
        assertEquals(16, parsed.bitsPerSample)
        assertEquals(twoSecondsBytes.size.toLong(), parsed.dataBytes)
        assertEquals(2000L, parsed.durationMs)
    }

    @Test
    fun formatters_recordingDurationMappingStaysWithin30sTo60m() {
        assertEquals(30, Formatters.durationSliderFractionToSeconds(0f))
        assertEquals(3600, Formatters.durationSliderFractionToSeconds(1f))

        listOf(30, 60, 300, 900, 1800, 3600).forEach { seconds ->
            val fraction = Formatters.durationSecondsToSliderFraction(seconds)
            val roundTrip = Formatters.durationSliderFractionToSeconds(fraction)
            assertEquals("Recording duration round-trip failed for $seconds", seconds, roundTrip)
        }
    }

    @Test
    fun formatters_intervalMappingStaysWithin5mTo24h() {
        assertEquals(300, Formatters.intervalSliderFractionToSeconds(0f))
        assertEquals(86400, Formatters.intervalSliderFractionToSeconds(1f))

        listOf(300, 900, 1800, 3600, 14400, 43200, 86400).forEach { seconds ->
            val fraction = Formatters.intervalSecondsToSliderFraction(seconds)
            val roundTrip = Formatters.intervalSliderFractionToSeconds(fraction)
            assertEquals("Interval round-trip failed for $seconds", seconds, roundTrip)
        }
    }

    @Test
    fun recorderSettings_cycleDurationMatchesDurationPlusInterval() {
        // 15 min recording + 15 min interval = 30 min (half an hour) cycle
        val halfHourCycle = RecorderSettings(
            recordingDurationSeconds = 15 * 60,
            intervalSeconds = 15 * 60,
            continuousNoGap = false
        )
        assertEquals(30 * 60, halfHourCycle.totalCycleSeconds)

        // 5 min recording + 30 min interval = 35 min cycle
        val fiveInThirtyFive = RecorderSettings(
            recordingDurationSeconds = 5 * 60,
            intervalSeconds = 30 * 60,
            continuousNoGap = false
        )
        assertEquals(35 * 60, fiveInThirtyFive.totalCycleSeconds)

        // Continuous no-gap ignores wait interval
        val noGap = RecorderSettings(
            recordingDurationSeconds = 15 * 60,
            intervalSeconds = 15 * 60,
            continuousNoGap = true
        )
        assertEquals(15 * 60, noGap.totalCycleSeconds)
    }

    @Test
    fun formatters_durationAndIntervalLabels() {
        assertEquals("00:30", Formatters.formatDurationHms(30_000L))
        assertEquals("05:00", Formatters.formatDurationHms(300_000L))
        assertEquals("01:00:00", Formatters.formatDurationHms(3_600_000L))
        assertEquals("30 sec", Formatters.formatRecordingDurationVerbose(30))
        assertEquals("60 min", Formatters.formatRecordingDurationVerbose(3600))
        assertEquals("5 min", Formatters.formatIntervalVerbose(300))
        assertEquals("24 hr", Formatters.formatIntervalVerbose(86400))
    }

    @Test
    fun audioCapabilities_enforcesNineBioacousticClassesAndHardwareBounds() {
        assertEquals(
            listOf(8000, 12000, 16000, 24000, 44100, 48000, 96000, 192000, 320000),
            AudioQuality.STANDARD_CANDIDATE_RATES
        )

        val caps = AudioCapabilities(
            supportedRates = listOf(
                AudioQuality(8000),
                AudioQuality(12000),
                AudioQuality(16000),
                AudioQuality(24000)
            ),
            maxSampleRateHz = 24000,
            isHardwareVerified = true,
            isProbing = false
        )
        assertEquals(24000, caps.closestSupported(48000).sampleRateHz)
        assertEquals(16000, caps.closestSupported(16000).sampleRateHz)
        assertEquals(8000, caps.closestSupported(8000).sampleRateHz)
        assertTrue(caps.isRateSupported(24000))
        assertFalse(caps.isRateSupported(192000))
        assertFalse(caps.isRateSupported(320000))
        assertEquals(
            AudioQuality.STANDARD_CANDIDATE_RATES,
            caps.displayCandidateRates().map { it.sampleRateHz }
        )

        // Legacy sample rates snap onto the 9 defined classes
        assertEquals(24000, AudioQuality.fromSampleRate(22050).sampleRateHz)
        assertEquals(320000, AudioQuality.fromSampleRate(384000).sampleRateHz)

        // Check cumulative bioacoustic coverage at 16 kHz includes baseline (8k) + 12k + 16k
        val cov16 = AudioQuality.cumulativeCoverageFor(16000)
        assertEquals(8000, cov16.baselineSpec.sampleRateHz)
        assertEquals(listOf(12000, 16000), cov16.addedTiers.map { it.sampleRateHz })
        assertTrue(cov16.addedTiers.first().animalGroupsAdded.contains("Mid-high songbirds"))
        assertEquals("~50 Hz – 7.2 kHz", cov16.selectedSpec.usableFreqRange)
        assertEquals("~1.92 MB/min (~115 MB/hr)", cov16.selectedSpec.wavSizeSummary)
        assertEquals("Negligible (+1–4%)", cov16.selectedSpec.extraBatteryCost)

        // Check 320 kHz metadata
        val cov320 = AudioQuality.cumulativeCoverageFor(320000)
        assertEquals(8, cov320.addedTiers.size)
        assertEquals("~50 Hz – 144.0 kHz**", cov320.selectedSpec.usableFreqRange)
        assertEquals("Very High (+30–50%+)", cov320.selectedSpec.extraBatteryCost)
    }

    @Test
    fun recordingSchedule_evaluatesDayOnlySunriseSunsetAndCustomWindows() {
        val always = RecordingSchedule(mode = ScheduleMode.ALWAYS)
        assertTrue(always.isActiveAtMinute(0))
        assertTrue(always.isActiveAtMinute(13 * 60))

        // Day Only: 6:00 AM (360) to 6:00 PM (1080)
        val dayOnly = RecordingSchedule(mode = ScheduleMode.DAY_ONLY)
        assertFalse(dayOnly.isActiveAtMinute(5 * 60 + 59))
        assertTrue(dayOnly.isActiveAtMinute(6 * 60))
        assertTrue(dayOnly.isActiveAtMinute(12 * 60))
        assertFalse(dayOnly.isActiveAtMinute(18 * 60))

        // Sunrise & Sunset: 5:30-7:30 AM and 5:30-7:30 PM
        val sun = RecordingSchedule(mode = ScheduleMode.SUNRISE_SUNSET)
        assertTrue(sun.isActiveAtMinute(6 * 60))
        assertFalse(sun.isActiveAtMinute(12 * 60))
        assertTrue(sun.isActiveAtMinute(18 * 60))
        assertFalse(sun.isActiveAtMinute(22 * 60))

        // Specific Time: 12:00 PM (720) to 2:00 PM (840)
        val noonToTwo = RecordingSchedule(
            mode = ScheduleMode.CUSTOM_WINDOW,
            customStartMinuteOfDay = 12 * 60,
            customEndMinuteOfDay = 14 * 60
        )
        assertFalse(noonToTwo.isActiveAtMinute(11 * 60 + 59))
        assertTrue(noonToTwo.isActiveAtMinute(12 * 60))
        assertTrue(noonToTwo.isActiveAtMinute(13 * 60 + 30))
        assertFalse(noonToTwo.isActiveAtMinute(14 * 60))

        // Overnight wrap: 10:00 PM (1320) to 6:00 AM (360)
        val overnight = RecordingSchedule(
            mode = ScheduleMode.CUSTOM_WINDOW,
            customStartMinuteOfDay = 22 * 60,
            customEndMinuteOfDay = 6 * 60
        )
        assertTrue(overnight.isActiveAtMinute(23 * 60))
        assertTrue(overnight.isActiveAtMinute(2 * 60))
        assertFalse(overnight.isActiveAtMinute(12 * 60))

        // Verify countdown from 11:30 AM to 12:00 PM window
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(30 * 60_000L, noonToTwo.millisUntilNextWindow(cal))
        assertEquals(12 * 60, noonToTwo.nextWindowStartMinute(cal))
    }

    @Test
    fun batteryPowerEstimator_enforcesThresholdAndCalculatesSettingsRuntime() {
        val lowSnap = BatterySnapshot(levelPercent = 14, isCharging = false, chargeCounterMah = 630)
        assertTrue(BatteryPowerEstimator.isBelowThreshold(lowSnap, protectionEnabled = true, thresholdPercent = 15))
        assertFalse(BatteryPowerEstimator.isBelowThreshold(lowSnap, protectionEnabled = false, thresholdPercent = 15))
        assertFalse(
            BatteryPowerEstimator.isBelowThreshold(
                lowSnap.copy(isCharging = true),
                protectionEnabled = true,
                thresholdPercent = 15
            )
        )

        val healthySnap = BatterySnapshot(levelPercent = 80, isCharging = false, chargeCounterMah = 3600)

        val continuousEstimate = BatteryPowerEstimator.estimate(
            snapshot = healthySnap,
            protectionEnabled = true,
            thresholdPercent = 20,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = true,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS)
        )
        assertEquals(60, continuousEstimate.usablePercent)
        assertEquals(100, continuousEstimate.dutyCyclePercent)

        val intervalEstimate = BatteryPowerEstimator.estimate(
            snapshot = healthySnap,
            protectionEnabled = true,
            thresholdPercent = 20,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = false,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS)
        )
        // Interval waiting must yield significantly longer field runtime than continuous recording
        assertTrue(intervalEstimate.estimatedMinutesRemaining > continuousEstimate.estimatedMinutesRemaining * 2)

        val twoHourWindowEstimate = BatteryPowerEstimator.estimate(
            snapshot = healthySnap,
            protectionEnabled = true,
            thresholdPercent = 20,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = false,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(
                mode = ScheduleMode.CUSTOM_WINDOW,
                customStartMinuteOfDay = 12 * 60,
                customEndMinuteOfDay = 14 * 60
            )
        )
        assertTrue(twoHourWindowEstimate.estimatedMinutesRemaining > intervalEstimate.estimatedMinutesRemaining)
    }

    @Test
    fun storageEstimator_computesRemainingCapacityFieldSpanAndHealthThresholds() {
        val cdBps = AudioQuality.CD_44K.bytesPerSecond // 88,200 B/s (~317.5 MB/hr)

        // Healthy: 10 GB free
        val healthySnap = StorageSnapshot(
            availableBytes = 10L * 1024L * 1024L * 1024L,
            totalBytes = 64L * 1024L * 1024L * 1024L,
            recordingsUsedBytes = 512L * 1024L * 1024L,
            recordingsCount = 4
        )
        val continuousEst = StorageEstimator.estimate(
            snapshot = healthySnap,
            bytesPerSecond = cdBps,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = true,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS)
        )
        assertEquals(StorageHealth.HEALTHY, continuousEst.health)
        assertEquals(cdBps * 3600L, continuousEst.bytesPerHourActive)
        assertEquals(continuousEst.audioMinutesAvailable, continuousEst.deploymentMinutesUntilFull)
        assertTrue(continuousEst.audioMinutesAvailable > 1800L)

        // Interval recording extends deploymentMinutesUntilFull significantly
        val intervalEst = StorageEstimator.estimate(
            snapshot = healthySnap,
            bytesPerSecond = cdBps,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = false,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS)
        )
        assertEquals(continuousEst.audioMinutesAvailable, intervalEst.audioMinutesAvailable)
        assertTrue(intervalEst.deploymentMinutesUntilFull > continuousEst.deploymentMinutesUntilFull * 5)

        // Low: 500 MB free (< 1 GB threshold)
        val lowSnap = StorageSnapshot(
            availableBytes = 500L * 1024L * 1024L,
            totalBytes = 32L * 1024L * 1024L * 1024L
        )
        val lowEst = StorageEstimator.estimate(
            snapshot = lowSnap,
            bytesPerSecond = cdBps,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = true,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS)
        )
        assertEquals(StorageHealth.LOW, lowEst.health)

        // Critical: 100 MB free (< 200 MB threshold)
        val criticalSnap = StorageSnapshot(
            availableBytes = 100L * 1024L * 1024L,
            totalBytes = 32L * 1024L * 1024L * 1024L
        )
        val criticalEst = StorageEstimator.estimate(
            snapshot = criticalSnap,
            bytesPerSecond = cdBps,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = true,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS)
        )
        assertEquals(StorageHealth.CRITICAL, criticalEst.health)
    }

    @Test
    fun losslessWriters_flacAlacWavAiffRoundTripBitExactAndParseMetadata() {
        val quality = AudioQuality(sampleRateHz = 96000)
        // Generate 5000 samples (spans >1 full 4096-sample block + 904-sample partial block)
        val totalSamples = 5000
        val pcm = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val sample = (kotlin.math.sin(i * 0.08) * 12000.0).toInt().toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
        }

        // 1. AIFF round-trip
        val aiffFile = File(tempFolder.root, "sample.aiff")
        AudioSegmentWriterFactory.create(aiffFile, quality, RecordingFileFormat.AIFF).use { w ->
            w.writePcm(pcm, pcm.size)
        }
        val aiffMeta = AudioMetadataParser.parse(aiffFile)
        assertNotNull(aiffMeta)
        assertEquals(RecordingFileFormat.AIFF, aiffMeta!!.format)
        assertEquals(96000, aiffMeta.sampleRateHz)
        assertEquals(1, aiffMeta.channels)
        assertEquals(16, aiffMeta.bitsPerSample)
        val aiffDecoded = AiffFormat.decodeToLittleEndianPcm(aiffFile)
        assertNotNull(aiffDecoded)
        assertTrue(pcm.contentEquals(aiffDecoded!!.pcmLittleEndian))

        // 2. FLAC round-trip (verifies compression + Rice/Fixed-1 bit-exact reconstruction)
        val flacFile = File(tempFolder.root, "sample.flac")
        AudioSegmentWriterFactory.create(flacFile, quality, RecordingFileFormat.FLAC).use { w ->
            w.writePcm(pcm, pcm.size)
        }
        val flacMeta = AudioMetadataParser.parse(flacFile)
        assertNotNull(flacMeta)
        assertEquals(RecordingFileFormat.FLAC, flacMeta!!.format)
        assertEquals(96000, flacMeta.sampleRateHz)
        assertEquals(1, flacMeta.channels)
        assertEquals(16, flacMeta.bitsPerSample)
        assertTrue("FLAC should compress smooth sine wave smaller than raw PCM", flacFile.length() < pcm.size)
        val flacDecoded = FlacFormat.decodeToLittleEndianPcm(flacFile)
        assertNotNull(flacDecoded)
        assertTrue(pcm.contentEquals(flacDecoded!!.pcmLittleEndian))

        // 3. ALAC (.m4a) round-trip
        val alacFile = File(tempFolder.root, "sample.m4a")
        AudioSegmentWriterFactory.create(alacFile, quality, RecordingFileFormat.ALAC).use { w ->
            w.writePcm(pcm, pcm.size)
        }
        val alacMeta = AudioMetadataParser.parse(alacFile)
        assertNotNull(alacMeta)
        assertEquals(RecordingFileFormat.ALAC, alacMeta!!.format)
        assertEquals(96000, alacMeta.sampleRateHz)
        assertEquals(1, alacMeta.channels)
        assertEquals(16, alacMeta.bitsPerSample)
        val alacDecoded = AlacFormat.decodeToLittleEndianPcm(alacFile)
        assertNotNull(alacDecoded)
        assertTrue(pcm.contentEquals(alacDecoded!!.pcmLittleEndian))

        // 4. WAV round-trip via factory
        val wavFile = File(tempFolder.root, "sample.wav")
        AudioSegmentWriterFactory.create(wavFile, quality, RecordingFileFormat.WAV).use { w ->
            w.writePcm(pcm, pcm.size)
        }
        val wavMeta = AudioMetadataParser.parse(wavFile)
        assertNotNull(wavMeta)
        assertEquals(RecordingFileFormat.WAV, wavMeta!!.format)
        assertEquals(96000, wavMeta.sampleRateHz)

        // Format order check: FLAC, ALAC, WAV, AIFF
        assertEquals(
            listOf(
                RecordingFileFormat.FLAC,
                RecordingFileFormat.ALAC,
                RecordingFileFormat.WAV,
                RecordingFileFormat.AIFF
            ),
            RecordingFileFormat.entries
        )
    }

    @Test
    fun spectrogramAnalyzer_detectsDominantFrequencyAndModelsExtraBatteryCost() {
        val sampleRateHz = 44100
        val targetFreqHz = 4000.0
        val numSamples = SpectrogramAnalyzer.FFT_SIZE
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * targetFreqHz * i / sampleRateHz) * 16000.0)
                .toInt()
                .toShort()
            pcm[i * 2] = (s.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
        }

        val spectrum = SpectrogramAnalyzer.analyzePcmChunk(pcm, pcm.size, sampleRateHz)
        assertEquals(SpectrogramAnalyzer.BIN_COUNT, spectrum.bins.size)
        assertTrue(spectrum.bins.all { it in 0f..1f })
        assertTrue(
            "Expected peak frequency near 4000 Hz, got ${spectrum.peakFrequencyHz}",
            kotlin.math.abs(spectrum.peakFrequencyHz - targetFreqHz.toInt()) <= 150
        )

        val snap = BatterySnapshot(levelPercent = 80, isCharging = false, chargeCounterMah = 3600)
        val waveformEst = BatteryPowerEstimator.estimate(
            snapshot = snap,
            protectionEnabled = true,
            thresholdPercent = 15,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = true,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS),
            visualizerMode = VisualizerMode.WAVEFORM
        )
        val spectrogramEst = BatteryPowerEstimator.estimate(
            snapshot = snap,
            protectionEnabled = true,
            thresholdPercent = 15,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = true,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS),
            visualizerMode = VisualizerMode.SPECTROGRAM
        )
        assertEquals(0, waveformEst.visualizerExtraPercent)
        assertTrue(spectrogramEst.visualizerExtraPercent > 0)
        assertTrue(spectrogramEst.visualizerExtraMa > 0)
        assertEquals(waveformEst.estimatedMinutesRemaining, spectrogramEst.baselineMinutesWithoutVisualizer)
        assertTrue(spectrogramEst.estimatedMinutesRemaining < waveformEst.estimatedMinutesRemaining)
    }

    @Test
    fun gpsTagMode_formatsCoordinatesAndModelsMinimalBatteryImpact() {
        assertEquals(
            listOf(GpsTagMode.OFF, GpsTagMode.ONE_TIME, GpsTagMode.EACH_FILE),
            GpsTagMode.entries
        )

        val coord = GpsCoordinate(latitude = 22.5726, longitude = 88.3639, accuracyMeters = 8.4f)
        assertEquals("22.5726° N, 88.3639° E", coord.formatShort())
        assertEquals("22.5726° N, 88.3639° E (±8m)", coord.formatDetailed())

        val southWest = GpsCoordinate(latitude = -3.4653, longitude = -62.2159)
        assertEquals("3.4653° S, 62.2159° W", southWest.formatShort())

        val snap = BatterySnapshot(levelPercent = 85, isCharging = false, chargeCounterMah = 3825)
        val offEst = BatteryPowerEstimator.estimate(
            snapshot = snap,
            protectionEnabled = true,
            thresholdPercent = 15,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = false,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS),
            gpsTagMode = GpsTagMode.OFF
        )
        val oneTimeEst = BatteryPowerEstimator.estimate(
            snapshot = snap,
            protectionEnabled = true,
            thresholdPercent = 15,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = false,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS),
            gpsTagMode = GpsTagMode.ONE_TIME
        )
        val eachFileEst = BatteryPowerEstimator.estimate(
            snapshot = snap,
            protectionEnabled = true,
            thresholdPercent = 15,
            recordingDurationSeconds = 300,
            intervalSeconds = 1800,
            continuousNoGap = false,
            sampleRateHz = 44100,
            schedule = RecordingSchedule(mode = ScheduleMode.ALWAYS),
            gpsTagMode = GpsTagMode.EACH_FILE
        )
        assertEquals(GpsTagMode.ONE_TIME, oneTimeEst.gpsTagMode)
        assertTrue(oneTimeEst.estimatedAverageCurrentMa <= offEst.estimatedAverageCurrentMa + 1)
        assertTrue(eachFileEst.estimatedMinutesRemaining <= oneTimeEst.estimatedMinutesRemaining)
    }

    @Test
    fun lowCutFilter_attenuatesSubCutoffAndPreservesHighFrequencies() {
        assertEquals(0, LowCutFilter.normalizeCutoffHz(0))
        assertEquals(20, LowCutFilter.normalizeCutoffHz(5))
        assertEquals(90, LowCutFilter.normalizeCutoffHz(90))
        assertEquals(200, LowCutFilter.normalizeCutoffHz(350))

        val sampleRate = 44100
        val totalSamples = sampleRate / 2 // 0.5 seconds so filter reaches steady state

        fun generateSinePcm(freqHz: Double): ByteArray {
            val bytes = ByteArray(totalSamples * 2)
            for (i in 0 until totalSamples) {
                val s = (kotlin.math.sin(2.0 * kotlin.math.PI * freqHz * i / sampleRate) * 14000.0)
                    .toInt()
                    .toShort()
                bytes[i * 2] = (s.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
            }
            return bytes
        }

        fun rmsTail(bytes: ByteArray): Double {
            val startSample = totalSamples / 2
            var sumSq = 0.0
            var count = 0
            for (i in startSample until totalSamples) {
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt()
                val v = ((hi shl 8) or lo).toShort().toDouble()
                sumSq += v * v
                count++
            }
            return kotlin.math.sqrt(sumSq / count)
        }

        val filter = LowCutFilter()

        // 0 Hz = Off (bit-exact passthrough)
        val raw30Hz = generateSinePcm(30.0)
        val offFiltered = raw30Hz.copyOf()
        filter.configure(0, sampleRate)
        filter.processPcm16LeInPlace(offFiltered, offFiltered.size)
        assertTrue(raw30Hz.contentEquals(offFiltered))

        // At 90 Hz cutoff: 30 Hz wind rumble is strongly attenuated, 2500 Hz call is preserved
        val lowCutAt90 = raw30Hz.copyOf()
        filter.configure(90, sampleRate)
        filter.processPcm16LeInPlace(lowCutAt90, lowCutAt90.size)
        assertTrue(
            "30 Hz rumble should drop below 20% RMS with 90 Hz low-cut",
            rmsTail(lowCutAt90) < rmsTail(raw30Hz) * 0.20
        )

        val rawBird2500 = generateSinePcm(2500.0)
        val highCutAt200 = rawBird2500.copyOf()
        filter.configure(200, sampleRate)
        filter.processPcm16LeInPlace(highCutAt200, highCutAt200.size)
        assertTrue(
            "2500 Hz bird call should retain >95% RMS even with 200 Hz low-cut",
            rmsTail(highCutAt200) > rmsTail(rawBird2500) * 0.95
        )
    }

    @Test
    fun spectrogramZoomMath_zoomsPansAndFormatsFrequencyBounds() {
        assertEquals(256, SpectrogramAnalyzer.BIN_COUNT)

        // Zoom in 2x centered at 0.5 -> [0.25, 0.75]
        val (zStart, zEnd) = SpectrogramZoomMath.zoomRange(0f, 1f, zoomFactor = 2f, anchorNormalizedInView = 0.5f)
        assertEquals(0.25f, zStart, 0.001f)
        assertEquals(0.75f, zEnd, 0.001f)
        assertEquals(2.0f, SpectrogramZoomMath.zoomLevel(zStart, zEnd), 0.01f)

        // Pan up by +0.4 -> clamped to [0.5, 1.0] preserving 0.5 span
        val (pStart, pEnd) = SpectrogramZoomMath.panRange(zStart, zEnd, deltaFrac = 0.4f)
        assertEquals(0.5f, pStart, 0.001f)
        assertEquals(1.0f, pEnd, 0.001f)

        // Minimum zoom span enforcement (<= 12.5x)
        val (minStart, minEnd) = SpectrogramZoomMath.zoomRange(0.4f, 0.5f, zoomFactor = 10f)
        assertTrue((minEnd - minStart) >= SpectrogramZoomMath.MIN_ZOOM_SPAN - 0.001f)

        // Axis frequency formatting
        assertEquals("0 Hz", SpectrogramZoomMath.formatFreqAxisHz(0))
        assertEquals("450 Hz", SpectrogramZoomMath.formatFreqAxisHz(450))
        assertEquals("2.40k", SpectrogramZoomMath.formatFreqAxisHz(2400))
        assertEquals("22.0k", SpectrogramZoomMath.formatFreqAxisHz(22000))
        assertEquals("22.1k", SpectrogramZoomMath.formatFreqAxisHz(22050))
    }

    @Test
    fun recordingVisualizationExtractor_extractsWaveformAndSpectrogramAcrossFormats() {
        val sampleRate = 48000
        val quality = AudioQuality(sampleRateHz = sampleRate)
        val totalSamples = sampleRate / 2 // 0.5 seconds
        val pcm = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 2000.0 * i / sampleRate) * 14000.0)
                .toInt()
                .toShort()
            pcm[i * 2] = (s.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
        }

        val wavFile = File(tempFolder.root, "20261124_051530.wav")
        AudioSegmentWriterFactory.create(wavFile, quality, RecordingFileFormat.WAV).use { w ->
            w.writePcm(pcm, pcm.size)
        }
        val wavViz = RecordingVisualizationExtractor.extract(
            file = wavFile,
            waveformBarCount = 48,
            spectrogramColumnCount = 32
        )
        assertNotNull(wavViz)
        assertEquals(48, wavViz!!.waveformAmplitudes.size)
        assertEquals(32, wavViz.spectrogramColumns.size)
        assertEquals(256, wavViz.spectrogramColumns.first().size)
        assertTrue(wavViz.waveformAmplitudes.all { it in 0.03f..1f })
        assertTrue(
            "Peak frequency should be near 2000 Hz (was ${wavViz.peakFrequencyHz})",
            wavViz.peakFrequencyHz in 1800..2200
        )

        val aiffFile = File(tempFolder.root, "20261124_051530.aiff")
        AudioSegmentWriterFactory.create(aiffFile, quality, RecordingFileFormat.AIFF).use { w ->
            w.writePcm(pcm, pcm.size)
        }
        val aiffViz = RecordingVisualizationExtractor.extract(
            file = aiffFile,
            waveformBarCount = 48,
            spectrogramColumnCount = 32
        )
        assertNotNull(aiffViz)
        assertEquals(48, aiffViz!!.waveformAmplitudes.size)
        assertEquals(32, aiffViz.spectrogramColumns.size)
        assertTrue(
            "AIFF peak frequency should be near 2000 Hz (was ${aiffViz.peakFrequencyHz})",
            aiffViz.peakFrequencyHz in 1800..2200
        )
    }

    @Test
    fun resolveRecordingStartMs_parsesTimestampPrefixAndFallsBackForRenamedFiles() {
        val parsed = Formatters.resolveRecordingStartMs(
            displayName = "20261124_051530",
            createdAtMs = 9_999_999_999L,
            durationMs = 300_000L
        )
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = parsed }
        assertEquals(2026, cal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.NOVEMBER, cal.get(java.util.Calendar.MONTH))
        assertEquals(24, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(5, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(java.util.Calendar.MINUTE))
        assertEquals(30, cal.get(java.util.Calendar.SECOND))

        val fallback = Formatters.resolveRecordingStartMs(
            displayName = "dawn_chorus_meadow",
            createdAtMs = 1_700_000_300_000L,
            durationMs = 300_000L
        )
        assertEquals(1_700_000_000_000L, fallback)
    }

    @Test
    fun timeZoomMath_zoomsPansClampsDurationAndFormatsTimestamps() {
        // Minimum span scales down to ~250ms for long files and >= MIN_TIME_SPAN overall
        assertEquals(0.01f, TimeZoomMath.minSpanForDuration(60_000L), 0.0001f)
        assertEquals(0.125f, TimeZoomMath.minSpanForDuration(2_000L), 0.0001f)
        assertEquals(1.0f, TimeZoomMath.minSpanForDuration(100L), 0.0001f)

        // Anchored zoom at 0.25 inside [0..1] by 2x -> anchor stays at 0.25 -> [0.125..0.625]
        val (zStart, zEnd) = TimeZoomMath.zoomRange(
            startFrac = 0f,
            endFrac = 1f,
            zoomFactor = 2f,
            anchorNormalizedInView = 0.25f
        )
        assertEquals(0.125f, zStart, 0.001f)
        assertEquals(0.625f, zEnd, 0.001f)

        // Pan shifts and clamps cleanly to [0..1] while preserving span
        val (pStart, pEnd) = TimeZoomMath.panRange(zStart, zEnd, deltaFrac = 0.6f)
        assertEquals(0.5f, pStart, 0.001f)
        assertEquals(1.0f, pEnd, 0.001f)

        val (leftStart, leftEnd) = TimeZoomMath.panRange(zStart, zEnd, deltaFrac = -0.5f)
        assertEquals(0f, leftStart, 0.001f)
        assertEquals(0.5f, leftEnd, 0.001f)

        // Time label formatting with and without tenths
        assertEquals("00:05", TimeZoomMath.formatTimeMs(5_400L, showTenths = false))
        assertEquals("00:05.4", TimeZoomMath.formatTimeMs(5_400L, showTenths = true))
        assertEquals("01:02:03", TimeZoomMath.formatTimeMs(3_723_000L, showTenths = false))
    }

    @Test
    fun recordingVisualizationExtractor_extractsZoomedTimeSliceAtFullResolution() {
        val sampleRate = 48000
        val quality = AudioQuality(sampleRateHz = sampleRate)
        val totalSamples = sampleRate // 1.0 second total
        val pcm = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val frac = i.toDouble() / totalSamples.toDouble()
            // Middle slice [0.25 .. 0.75] holds a loud 5000 Hz call; outer edges hold 1000 Hz tone
            val freq = if (frac in 0.25..0.75) 5000.0 else 1000.0
            val amp = if (frac in 0.25..0.75) 16000.0 else 4000.0
            val s = (kotlin.math.sin(2.0 * kotlin.math.PI * freq * i / sampleRate) * amp)
                .toInt()
                .toShort()
            pcm[i * 2] = (s.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
        }

        val wavFile = File(tempFolder.root, "20261124_060000.wav")
        AudioSegmentWriterFactory.create(wavFile, quality, RecordingFileFormat.WAV).use { w ->
            w.writePcm(pcm, pcm.size)
        }

        val sliced = RecordingVisualizationExtractor.extract(
            file = wavFile,
            waveformBarCount = 72,
            spectrogramColumnCount = 56,
            startFraction = 0.25f,
            endFraction = 0.75f
        )
        assertNotNull(sliced)
        sliced!!
        assertEquals(0.25f, sliced.startFraction, 0.001f)
        assertEquals(0.75f, sliced.endFraction, 0.001f)
        assertEquals(72, sliced.waveformAmplitudes.size)
        assertEquals(56, sliced.spectrogramColumns.size)
        assertTrue(
            "Expected peak frequency inside [0.25..0.75] slice near 5000 Hz, got ${sliced.peakFrequencyHz}",
            sliced.peakFrequencyHz in 4700..5300
        )
    }

    @Test
    fun pcmRingBuffer_wrapsChronologicallyAndRetainsNewestTail() {
        // 10 bytes capacity aligned to 2-byte PCM samples
        val ring = PcmRingBuffer(capacityBytes = 10, frameAlignmentBytes = 2)
        assertEquals(10, ring.capacityBytes)

        // Write 6 bytes: [1, 2, 3, 4, 5, 6]
        ring.write(byteArrayOf(1, 2, 3, 4, 5, 6), 6)
        assertEquals(6, ring.bufferedBytes)

        // Write 8 bytes: [7, 8, 9, 10, 11, 12, 13, 14] -> wraps around, retains newest 10 bytes [5..14]
        ring.write(byteArrayOf(7, 8, 9, 10, 11, 12, 13, 14), 8)
        assertEquals(10, ring.bufferedBytes)

        val drained = ring.drainChronological()
        assertEquals(10, drained.size)
        assertTrue(byteArrayOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14).contentEquals(drained))
        assertEquals(0, ring.bufferedBytes)

        // Oversized chunk keeps exact newest capacityBytes tail
        ring.write(byteArrayOf(20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31), 12)
        val tailDrained = ring.drainChronological()
        assertTrue(byteArrayOf(22, 23, 24, 25, 26, 27, 28, 29, 30, 31).contentEquals(tailDrained))
    }

    @Test
    fun acousticTriggerEvaluator_respectsFrequencyBandThresholdAndNormalization() {
        // Verify normalization clamps auto-stop silence (1..30m), pre-rec (0..10s), and Nyquist bounds
        val outOfBounds = AcousticTriggerConfig(
            enabled = true,
            entireFrequencyRange = false,
            minFreqHz = 500,
            maxFreqHz = 25000,
            amplitudeThresholdDb = -80f,
            preRecordSeconds = 25,
            autoStopSilenceMinutes = 45
        ).normalizedForSampleRate(sampleRateHz = 16000) // Nyquist = 8000 Hz

        assertEquals(500, outOfBounds.minFreqHz)
        assertEquals(8000, outOfBounds.maxFreqHz)
        assertEquals(-60f, outOfBounds.amplitudeThresholdDb, 0.01f)
        assertEquals(10, outOfBounds.preRecordSeconds)
        assertEquals(30, outOfBounds.autoStopSilenceMinutes)

        val zeroSilence = AcousticTriggerConfig(autoStopSilenceMinutes = 0).normalizedForSampleRate(44100)
        assertEquals(1, zeroSilence.autoStopSilenceMinutes)

        val sampleRate = 48000
        val sampleCount = 2048

        fun generateTonePcm(freqHz: Double, amplitudeScale: Double): ByteArray {
            val out = ByteArray(sampleCount * 2)
            for (i in 0 until sampleCount) {
                val s = (kotlin.math.sin(2.0 * kotlin.math.PI * freqHz * i / sampleRate) * 32767.0 * amplitudeScale)
                    .toInt()
                    .toShort()
                out[i * 2] = (s.toInt() and 0xFF).toByte()
                out[i * 2 + 1] = ((s.toInt() ushr 8) and 0xFF).toByte()
            }
            return out
        }

        // -12 dBFS amplitude ~ 0.251 peak
        val loud3kHz = generateTonePcm(freqHz = 3000.0, amplitudeScale = 0.25)
        val loud500Hz = generateTonePcm(freqHz = 500.0, amplitudeScale = 0.25)

        val band2kTo5k = AcousticTriggerConfig(
            enabled = true,
            entireFrequencyRange = false,
            minFreqHz = 2000,
            maxFreqHz = 5000,
            amplitudeThresholdDb = -25f
        )

        val inBandResult = AcousticTriggerEvaluator.evaluate(loud3kHz, loud3kHz.size, sampleRate, band2kTo5k)
        assertTrue("3 kHz tone should trigger inside 2–5 kHz band (got ${inBandResult.bandLevelDb} dB)", inBandResult.isTriggered)
        assertTrue(inBandResult.peakFreqInBandHz in 2700..3300)

        val outOfBandResult = AcousticTriggerEvaluator.evaluate(loud500Hz, loud500Hz.size, sampleRate, band2kTo5k)
        assertFalse("500 Hz tone should NOT trigger inside 2–5 kHz band (got ${outOfBandResult.bandLevelDb} dB)", outOfBandResult.isTriggered)

        val entireSpectrum = AcousticTriggerConfig(
            enabled = true,
            entireFrequencyRange = true,
            amplitudeThresholdDb = -25f
        )
        val fullSpectrumResult = AcousticTriggerEvaluator.evaluate(loud500Hz, loud500Hz.size, sampleRate, entireSpectrum)
        assertTrue("500 Hz tone should trigger in Entire Spectrum mode", fullSpectrumResult.isTriggered)
    }
}

