package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.audio.AudioMetadataParser
import com.example.audio.GpsCoordinate
import com.example.audio.RecordingFileFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingRepository(private val context: Context) {

    private val _recordings = MutableStateFlow<List<RecordingFile>>(emptyList())
    val recordings: StateFlow<List<RecordingFile>> = _recordings.asStateFlow()

    private val gpsLock = Any()
    private val gpsIndexFile: File
        get() = File(context.filesDir, "recording_gps_tags.json")

    fun recordingsDirectory(): File {
        val externalMusic = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val dir = if (externalMusic != null) {
            File(externalMusic, "WaveLoop")
        } else {
            File(context.filesDir, "WaveLoop")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createSegmentFile(format: RecordingFileFormat = RecordingFileFormat.WAV): File {
        val ext = format.extension
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var candidate = File(recordingsDirectory(), "$timestamp.$ext")
        var collision = 1
        while (candidate.exists()) {
            candidate = File(recordingsDirectory(), "${timestamp}_$collision.$ext")
            collision++
        }
        return candidate
    }

    fun saveGpsTagForFile(file: File, coordinate: GpsCoordinate) {
        synchronized(gpsLock) {
            val root = readGpsIndexLocked()
            val entry = JSONObject().apply {
                put("lat", coordinate.latitude)
                put("lon", coordinate.longitude)
                coordinate.accuracyMeters?.let { put("acc", it.toDouble()) }
                put("ts", coordinate.capturedAtMs)
            }
            root.put(file.name, entry)
            writeGpsIndexLocked(root)
        }
    }

    suspend fun refresh(activeRecordingPath: String? = null) {
        val list = withContext(Dispatchers.IO) {
            val dir = recordingsDirectory()
            val files = dir.listFiles { file ->
                file.isFile &&
                    file.extension.lowercase() in RecordingFileFormat.SUPPORTED_EXTENSIONS &&
                    file.absolutePath != activeRecordingPath &&
                    file.length() > 42L
            } ?: emptyArray()

            val gpsTags = synchronized(gpsLock) { readGpsMapLocked() }

            files.mapNotNull { file ->
                val meta = AudioMetadataParser.parse(file) ?: return@mapNotNull null
                val gps = gpsTags[file.name]
                RecordingFile(
                    path = file.absolutePath,
                    file = file,
                    displayName = file.nameWithoutExtension,
                    createdAtMs = file.lastModified(),
                    durationMs = meta.durationMs,
                    sizeBytes = file.length(),
                    sampleRateHz = meta.sampleRateHz,
                    channels = meta.channels,
                    bitsPerSample = meta.bitsPerSample,
                    format = meta.format,
                    latitude = gps?.latitude,
                    longitude = gps?.longitude,
                    gpsAccuracyMeters = gps?.accuracyMeters
                )
            }.sortedByDescending { it.createdAtMs }
        }
        _recordings.value = list
    }

    suspend fun renameRecording(recording: RecordingFile, newName: String): Boolean {
        val ext = recording.file.extension.ifEmpty { recording.format.extension }
        val sanitized = newName
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\.(wav|flac|m4a|aiff|aif)$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (sanitized.isEmpty()) return false

        return withContext(Dispatchers.IO) {
            val target = File(recording.file.parentFile, "$sanitized.$ext")
            if (target.exists() && target.absolutePath != recording.file.absolutePath) {
                return@withContext false
            }
            val oldName = recording.file.name
            val success = recording.file.renameTo(target)
            if (success) {
                synchronized(gpsLock) {
                    val root = readGpsIndexLocked()
                    if (root.has(oldName)) {
                        val existing = root.opt(oldName)
                        root.remove(oldName)
                        if (existing != null) {
                            root.put(target.name, existing)
                        }
                        writeGpsIndexLocked(root)
                    }
                }
                refresh()
            }
            success
        }
    }

    suspend fun deleteRecording(recording: RecordingFile): Boolean {
        return withContext(Dispatchers.IO) {
            val fileName = recording.file.name
            val deleted = recording.file.delete()
            if (deleted) {
                synchronized(gpsLock) {
                    val root = readGpsIndexLocked()
                    if (root.has(fileName)) {
                        root.remove(fileName)
                        writeGpsIndexLocked(root)
                    }
                }
                refresh()
            }
            deleted
        }
    }

    suspend fun copyToUri(recording: RecordingFile, destinationUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    recording.file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } != null
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun readGpsIndexLocked(): JSONObject {
        val file = gpsIndexFile
        if (!file.exists()) return JSONObject()
        return try {
            val text = file.readText()
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeGpsIndexLocked(root: JSONObject) {
        try {
            gpsIndexFile.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    private fun readGpsMapLocked(): Map<String, GpsCoordinate> {
        val root = readGpsIndexLocked()
        val result = HashMap<String, GpsCoordinate>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val obj = root.optJSONObject(name) ?: continue
            val lat = obj.optDouble("lat", Double.NaN)
            val lon = obj.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val acc = if (obj.has("acc")) obj.optDouble("acc").toFloat() else null
            val ts = obj.optLong("ts", 0L)
            result[name] = GpsCoordinate(
                latitude = lat,
                longitude = lon,
                accuracyMeters = acc,
                capturedAtMs = ts
            )
        }
        return result
    }
}
