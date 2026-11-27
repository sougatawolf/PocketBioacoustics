package com.example

import android.app.Application
import com.example.audio.AudioCapabilityProber
import com.example.audio.AudioPlaybackManager
import com.example.audio.BatteryMonitor
import com.example.audio.GpsTagManager
import com.example.audio.RecordingEngine
import com.example.audio.StorageMonitor
import com.example.data.RecorderPreferencesRepository
import com.example.data.RecordingRepository
import com.example.update.GitHubUpdateChecker

class WaveLoopApplication : Application() {

    lateinit var preferencesRepository: RecorderPreferencesRepository
        private set

    lateinit var recordingRepository: RecordingRepository
        private set

    lateinit var capabilityProber: AudioCapabilityProber
        private set

    lateinit var batteryMonitor: BatteryMonitor
        private set

    lateinit var storageMonitor: StorageMonitor
        private set

    lateinit var gpsTagManager: GpsTagManager
        private set

    lateinit var recordingEngine: RecordingEngine
        private set

    lateinit var playbackManager: AudioPlaybackManager
        private set

    lateinit var updateChecker: GitHubUpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = RecorderPreferencesRepository(this)
        recordingRepository = RecordingRepository(this)
        capabilityProber = AudioCapabilityProber(this)
        batteryMonitor = BatteryMonitor(this)
        storageMonitor = StorageMonitor(recordingRepository)
        gpsTagManager = GpsTagManager(this)
        recordingEngine = RecordingEngine(recordingRepository, batteryMonitor, gpsTagManager)
        playbackManager = AudioPlaybackManager()
        updateChecker = GitHubUpdateChecker(this)
    }
}
