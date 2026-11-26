package com.example.audio

import android.media.AudioFormat

data class SampleRateClassSpec(
    val sampleRateHz: Int,
    val displayRateLabel: String,
    val tierLabel: String,
    val usableFreqRange: String,
    val wavSizeSummary: String,
    val extraBatteryCost: String,
    val animalGroupsAdded: String,
    val rateFootnoteMark: String = "",
    val ultrasonicHardwareNote: Boolean = false
) {
    val shortChipLabel: String
        get() = "$displayRateLabel$rateFootnoteMark"
}

data class CumulativeBioacousticCoverage(
    val selectedSpec: SampleRateClassSpec,
    val baselineSpec: SampleRateClassSpec,
    val addedTiers: List<SampleRateClassSpec>
)

data class AudioQuality(
    val sampleRateHz: Int,
    val channels: Int = 1,
    val bitsPerSample: Int = 16
) {
    val spec: SampleRateClassSpec
        get() = specFor(sampleRateHz)

    val shortLabel: String
        get() = spec.displayRateLabel

    val chipLabel: String
        get() = spec.shortChipLabel

    val tierLabel: String
        get() = spec.tierLabel

    val channelConfig: Int
        get() = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO

    val audioEncoding: Int
        get() = AudioFormat.ENCODING_PCM_16BIT

    val bytesPerSampleFrame: Int
        get() = channels * (bitsPerSample / 8)

    val bytesPerSecond: Long
        get() = sampleRateHz.toLong() * bytesPerSampleFrame.toLong()

    fun bytesPerSecondFor(format: RecordingFileFormat): Long {
        return (bytesPerSecond.toDouble() * format.estimatedSizeRatioToPcm).toLong().coerceAtLeast(1L)
    }

    fun estimateSegmentBytes(
        intervalSeconds: Int,
        format: RecordingFileFormat = RecordingFileFormat.WAV
    ): Long {
        return 44L + bytesPerSecondFor(format) * intervalSeconds.toLong()
    }

    companion object {
        val VOICE_16K = AudioQuality(sampleRateHz = 16000)
        val CD_44K = AudioQuality(sampleRateHz = 44100)
        val PRO_48K = AudioQuality(sampleRateHz = 48000)

        val SAMPLE_RATE_CLASSES: List<SampleRateClassSpec> = listOf(
            SampleRateClassSpec(
                sampleRateHz = 8000,
                displayRateLabel = "8 kHz",
                tierLabel = "Baseline",
                usableFreqRange = "~50 Hz – 3.6 kHz",
                wavSizeSummary = "~0.96 MB/min (~58 MB/hr)",
                extraBatteryCost = "Baseline (vs 8 kHz, Screen Off)",
                animalGroupsAdded = "Large & medium mammals / Larger & non-songbirds / Most frogs & toads"
            ),
            SampleRateClassSpec(
                sampleRateHz = 12000,
                displayRateLabel = "12 kHz",
                tierLabel = "Mid-Song",
                usableFreqRange = "~50 Hz – 5.4 kHz",
                wavSizeSummary = "~1.44 MB/min (~86 MB/hr)",
                extraBatteryCost = "Negligible (+0–2%)",
                animalGroupsAdded = "Mid-high songbirds / High-pitched tree frogs / Lower cicadas & field crickets"
            ),
            SampleRateClassSpec(
                sampleRateHz = 16000,
                displayRateLabel = "16 kHz",
                tierLabel = "Passerines",
                usableFreqRange = "~50 Hz – 7.2 kHz",
                wavSizeSummary = "~1.92 MB/min (~115 MB/hr)",
                extraBatteryCost = "Negligible (+1–4%)",
                animalGroupsAdded = "Full songbirds (passerines) / High-pitched mammal calls (canids, primates, squirrels) / Tropical cicadas & crickets"
            ),
            SampleRateClassSpec(
                sampleRateHz = 24000,
                displayRateLabel = "24 kHz",
                tierLabel = "Canopy",
                usableFreqRange = "~50 Hz – 10.8 kHz",
                wavSizeSummary = "~2.88 MB/min (~173 MB/hr)",
                extraBatteryCost = "Very Low (+2–5%)",
                animalGroupsAdded = "Tiny canopy birds & high bird harmonics / High-frequency rainforest crickets / Low-frequency bat edge"
            ),
            SampleRateClassSpec(
                sampleRateHz = 44100,
                displayRateLabel = "44.1 kHz",
                tierLabel = "Audible+",
                usableFreqRange = "~50 Hz – 19.8 kHz",
                wavSizeSummary = "~5.29 MB/min (~318 MB/hr)",
                extraBatteryCost = "Very Low (+2–6%)",
                animalGroupsAdded = "High bird predator alarms / Bush-crickets, katydids & tree crickets / Audible bats & fruit bat social calls",
                rateFootnoteMark = "*"
            ),
            SampleRateClassSpec(
                sampleRateHz = 48000,
                displayRateLabel = "48 kHz",
                tierLabel = "Native OS",
                usableFreqRange = "~50 Hz – 21.6 kHz",
                wavSizeSummary = "~5.76 MB/min (~346 MB/hr)",
                extraBatteryCost = "Very Low (+1–5%, Native OS rate)",
                animalGroupsAdded = "Wild rat ultrasonic distress calls / Upper katydid bands / Closest low-frequency bat edge"
            ),
            SampleRateClassSpec(
                sampleRateHz = 96000,
                displayRateLabel = "96 kHz",
                tierLabel = "Low Bats",
                usableFreqRange = "~50 Hz – 43.2 kHz**",
                wavSizeSummary = "~11.52 MB/min (~691 MB/hr)",
                extraBatteryCost = "Moderate (+10–20%)",
                animalGroupsAdded = "Open & edge-space insectivorous bats / Rodents (rat pup calls) / Ultrasonic katydids & moths",
                ultrasonicHardwareNote = true
            ),
            SampleRateClassSpec(
                sampleRateHz = 192000,
                displayRateLabel = "192 kHz",
                tierLabel = "Core Bats",
                usableFreqRange = "~50 Hz – 86.4 kHz**",
                wavSizeSummary = "~23.04 MB/min (~1.38 GB/hr)",
                extraBatteryCost = "High (+20–40%)",
                animalGroupsAdded = "Core forest & urban bats (pipistrelles, horseshoe & larger leaf-nosed bats) / Mice / River dolphins (with hydrophone)",
                rateFootnoteMark = "*",
                ultrasonicHardwareNote = true
            ),
            SampleRateClassSpec(
                sampleRateHz = 320000,
                displayRateLabel = "320 kHz",
                tierLabel = "Cave Bats",
                usableFreqRange = "~50 Hz – 144.0 kHz**",
                wavSizeSummary = "~38.40 MB/min (~2.30 GB/hr)",
                extraBatteryCost = "Very High (+30–50%+)",
                animalGroupsAdded = "High-frequency cave & forest bats (small horseshoe & leaf-nosed bats)",
                rateFootnoteMark = "*",
                ultrasonicHardwareNote = true
            )
        )

        val STANDARD_CANDIDATE_RATES: List<Int> = SAMPLE_RATE_CLASSES.map { it.sampleRateHz }

        fun specFor(sampleRateHz: Int): SampleRateClassSpec {
            return SAMPLE_RATE_CLASSES.firstOrNull { it.sampleRateHz == sampleRateHz }
                ?: SAMPLE_RATE_CLASSES.minBy { kotlin.math.abs(it.sampleRateHz - sampleRateHz) }
        }

        fun cumulativeCoverageFor(sampleRateHz: Int): CumulativeBioacousticCoverage {
            val selected = specFor(sampleRateHz)
            val baseline = SAMPLE_RATE_CLASSES.first()
            val added = SAMPLE_RATE_CLASSES.filter {
                it.sampleRateHz > baseline.sampleRateHz && it.sampleRateHz <= selected.sampleRateHz
            }
            return CumulativeBioacousticCoverage(
                selectedSpec = selected,
                baselineSpec = baseline,
                addedTiers = added
            )
        }

        fun fromSampleRate(rateHz: Int): AudioQuality {
            val safeRate = rateHz.coerceIn(
                STANDARD_CANDIDATE_RATES.first(),
                STANDARD_CANDIDATE_RATES.last()
            )
            val snappedRate = specFor(safeRate).sampleRateHz
            return AudioQuality(sampleRateHz = snappedRate)
        }
    }
}
