package com.example.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateCheckFrequency(
    val label: String,
    val shortLabel: String,
    val intervalMs: Long
) {
    WEEKLY("Once a week (7 days)", "Weekly 7d", 7L * 24L * 60L * 60L * 1000L),
    DAILY("Once a day (24h)", "Daily", 24L * 60L * 60L * 1000L),
    TWELVE_HOURS("Every 12 hours", "12h", 12L * 60L * 60L * 1000L),
    MANUAL_ONLY("Manual only", "Manual", Long.MAX_VALUE)
}

data class ParsedReleaseNotes(
    val features: List<String> = emptyList(),
    val corrections: List<String> = emptyList(),
    val generalNotes: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = features.isEmpty() && corrections.isEmpty() && generalNotes.isEmpty()
}

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val parsedNotes: ParsedReleaseNotes,
    val rawNotes: String,
    val apkDownloadUrl: String?,
    val releasePageUrl: String,
    val publishedAt: String
) {
    val preferredDownloadUrl: String
        get() = apkDownloadUrl ?: releasePageUrl

    val hasDirectApkAsset: Boolean
        get() = !apkDownloadUrl.isNullOrBlank()
}

sealed class UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult()
    data object NoInternet : UpdateCheckResult()
    data object CooldownNotElapsed : UpdateCheckResult()
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val checkedAtMs: Long
    ) : UpdateCheckResult()

    data class UpdateAvailable(
        val update: AppUpdateInfo,
        val currentVersion: String,
        val checkedAtMs: Long,
        val isSkipped: Boolean
    ) : UpdateCheckResult()

    data class Error(val message: String) : UpdateCheckResult()
}

object GitHubReleaseParser {

    private val repoSegmentRegex = Regex("^[A-Za-z0-9_.-]+$")
    private val markdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val bulletPrefixRegex = Regex("^([-*+•]|\\d+[.)])\\s+")
    private val headingPrefixRegex = Regex("^#{1,6}\\s*")

    fun normalizeRepoSlug(rawInput: String): String? {
        var cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return null

        cleaned = cleaned
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")

        if (cleaned.startsWith("github.com/", ignoreCase = true)) {
            cleaned = cleaned.substring("github.com/".length)
        } else if (cleaned.equals("github.com", ignoreCase = true)) {
            return null
        }

        cleaned = cleaned.trim('/')
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val owner = parts[0].trim()
        val repo = parts[1].trim().removeSuffix(".git")
        if (owner.isEmpty() || repo.isEmpty()) return null
        if (!repoSegmentRegex.matches(owner) || !repoSegmentRegex.matches(repo)) return null

        return "$owner/$repo"
    }

    fun normalizeVersionString(rawVersion: String): String {
        return rawVersion.trim().removePrefix("v").removePrefix("V").trim()
    }

    fun isRemoteVersionNewer(installedVersion: String, remoteTagOrVersion: String): Boolean {
        val local = parseSemVer(installedVersion)
        val remote = parseSemVer(remoteTagOrVersion)
        if (remote.parts.isEmpty()) return false

        val maxLen = maxOf(local.parts.size, remote.parts.size)
        for (i in 0 until maxLen) {
            val l = local.parts.getOrElse(i) { 0 }
            val r = remote.parts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        // Numeric parts are equal; a pre-release (e.g. 1.0.0-beta) is not newer than 1.0.0,
        // while a stable release (1.0.0) is newer than an installed pre-release (1.0.0-beta).
        return local.isPreRelease && !remote.isPreRelease
    }

    fun shouldCheckNow(
        frequency: UpdateCheckFrequency,
        lastCheckMs: Long,
        nowMs: Long,
        force: Boolean
    ): Boolean {
        if (force) return true
        if (frequency == UpdateCheckFrequency.MANUAL_ONLY) return false
        if (lastCheckMs <= 0L) return true
        return (nowMs - lastCheckMs) >= frequency.intervalMs
    }

    fun parseLatestReleaseJson(jsonText: String): AppUpdateInfo? {
        val root = try {
            SimpleJsonParser.parseObject(jsonText)
        } catch (_: Exception) {
            return null
        } ?: return null

        if ((root["draft"] as? Boolean) == true) return null

        val tagName = (root["tag_name"] as? String)?.trim().orEmpty()
        if (tagName.isEmpty()) return null

        val versionName = normalizeVersionString(tagName).ifEmpty { tagName }
        val releaseTitle = (root["name"] as? String)?.trim().orEmpty().ifEmpty { "Version $versionName" }
        val rawBody = (root["body"] as? String)?.trim().orEmpty()
        val htmlUrl = (root["html_url"] as? String)?.trim().orEmpty()
        val publishedAt = (root["published_at"] as? String)?.trim().orEmpty()

        val apkUrls = mutableListOf<Pair<String, String>>()
        val assets = root["assets"] as? List<*>
        if (assets != null) {
            for (item in assets) {
                val asset = item as? Map<*, *> ?: continue
                val name = (asset["name"] as? String)?.trim().orEmpty()
                val downloadUrl = (asset["browser_download_url"] as? String)?.trim().orEmpty()
                if (downloadUrl.isNotEmpty() &&
                    (name.endsWith(".apk", ignoreCase = true) ||
                        downloadUrl.substringBefore('?').endsWith(".apk", ignoreCase = true))
                ) {
                    apkUrls.add(name to downloadUrl)
                }
            }
        }

        val preferredApkUrl = apkUrls.firstOrNull { (name, _) ->
            name.contains("release", ignoreCase = true) && !name.contains("unsigned", ignoreCase = true)
        }?.second
            ?: apkUrls.firstOrNull { (name, _) ->
                !name.contains("unsigned", ignoreCase = true)
            }?.second
            ?: apkUrls.firstOrNull()?.second

        return AppUpdateInfo(
            tagName = tagName,
            versionName = versionName,
            releaseTitle = releaseTitle,
            parsedNotes = parseReleaseNotes(rawBody),
            rawNotes = rawBody,
            apkDownloadUrl = preferredApkUrl,
            releasePageUrl = htmlUrl,
            publishedAt = publishedAt
        )
    }

    fun parseReleaseNotes(body: String): ParsedReleaseNotes {
        if (body.isBlank()) return ParsedReleaseNotes()

        val features = mutableListOf<String>()
        val corrections = mutableListOf<String>()
        val general = mutableListOf<String>()

        var currentBucket = NoteBucket.GENERAL

        body.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("<!--") || trimmed == "---" || trimmed == "***") {
                return@forEach
            }

            if (trimmed.startsWith("#") || isStandaloneSectionLabel(trimmed)) {
                val heading = stripMarkdownNoise(trimmed.replace(headingPrefixRegex, "")).lowercase()
                currentBucket = classifyHeading(heading)
                return@forEach
            }

            val strippedBullet = trimmed.replace(bulletPrefixRegex, "")
            val cleanLine = stripMarkdownNoise(strippedBullet)
            if (cleanLine.isEmpty()) return@forEach

            // Skip auto-generated GitHub changelog footer links if no real content
            if (cleanLine.startsWith("Full Changelog:", ignoreCase = true) &&
                cleanLine.contains("github.com", ignoreCase = true)
            ) {
                return@forEach
            }

            val targetBucket = if (currentBucket != NoteBucket.GENERAL) {
                currentBucket
            } else {
                classifyLineByVerb(cleanLine)
            }

            when (targetBucket) {
                NoteBucket.FEATURES -> if (features.size < 10) features.add(cleanLine)
                NoteBucket.CORRECTIONS -> if (corrections.size < 10) corrections.add(cleanLine)
                NoteBucket.GENERAL -> if (general.size < 10) general.add(cleanLine)
            }
        }

        return ParsedReleaseNotes(
            features = features,
            corrections = corrections,
            generalNotes = general
        )
    }

    private enum class NoteBucket {
        FEATURES,
        CORRECTIONS,
        GENERAL
    }

    private fun isStandaloneSectionLabel(line: String): Boolean {
        val noBullets = line.replace(bulletPrefixRegex, "").trim()
        return (noBullets.startsWith("**") && noBullets.endsWith("**") && noBullets.length <= 48) ||
            (noBullets.endsWith(":") && noBullets.length <= 36)
    }

    private fun classifyHeading(headingLower: String): NoteBucket {
        return when {
            headingLower.contains("feature") ||
                headingLower.contains("what's new") ||
                headingLower.contains("whats new") ||
                headingLower.contains("added") ||
                headingLower.contains("new") ||
                headingLower.contains("enhancement") ||
                headingLower.contains("highlight") -> NoteBucket.FEATURES

            headingLower.contains("fix") ||
                headingLower.contains("correct") ||
                headingLower.contains("bug") ||
                headingLower.contains("patch") ||
                headingLower.contains("resolv") ||
                headingLower.contains("improv") -> NoteBucket.CORRECTIONS

            else -> NoteBucket.GENERAL
        }
    }

    private fun classifyLineByVerb(line: String): NoteBucket {
        val lower = line.lowercase()
        return when {
            lower.startsWith("add ") ||
                lower.startsWith("added ") ||
                lower.startsWith("new:") ||
                lower.startsWith("new ") ||
                lower.startsWith("feature:") ||
                lower.startsWith("feature ") ||
                lower.startsWith("introduce ") ||
                lower.startsWith("introduced ") ||
                lower.startsWith("support ") ||
                lower.startsWith("supports ") -> NoteBucket.FEATURES

            lower.startsWith("fix ") ||
                lower.startsWith("fixed ") ||
                lower.startsWith("fixes ") ||
                lower.startsWith("correct ") ||
                lower.startsWith("corrected ") ||
                lower.startsWith("correction:") ||
                lower.startsWith("resolve ") ||
                lower.startsWith("resolved ") ||
                lower.startsWith("patch ") ||
                lower.startsWith("patched ") ||
                lower.startsWith("bugfix") ||
                lower.startsWith("improve ") ||
                lower.startsWith("improved ") -> NoteBucket.CORRECTIONS

            else -> NoteBucket.GENERAL
        }
    }

    private fun stripMarkdownNoise(text: String): String {
        return text
            .replace(markdownLinkRegex, "$1")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(':')
    }

    private data class SemVer(
        val parts: List<Int>,
        val isPreRelease: Boolean
    )

    private fun parseSemVer(raw: String): SemVer {
        val cleaned = normalizeVersionString(raw)
        val withoutBuild = cleaned.substringBefore('+')
        val hasPreRelease = withoutBuild.contains('-')
        val core = withoutBuild.substringBefore('-')
        val nums = core.split('.')
            .mapNotNull { segment ->
                val digits = segment.takeWhile { it.isDigit() }
                digits.toIntOrNull()
            }
        return SemVer(parts = nums, isPreRelease = hasPreRelease)
    }
}

private class SimpleJsonParser(private val src: String) {
    private var pos = 0

    companion object {
        fun parseObject(json: String): Map<String, Any?>? {
            val parser = SimpleJsonParser(json)
            val raw = parser.readValue() as? Map<*, *> ?: return null
            val out = LinkedHashMap<String, Any?>(raw.size)
            for ((k, v) in raw) {
                if (k is String) {
                    out[k] = v
                }
            }
            return out
        }
    }

    private fun skipWhitespace() {
        while (pos < src.length && src[pos].isWhitespace()) {
            pos++
        }
    }

    private fun readValue(): Any? {
        skipWhitespace()
        if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
        return when (src[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            '-', in '0'..'9' -> readNumber()
            else -> throw IllegalArgumentException("Unexpected char at $pos: ${src[pos]}")
        }
    }

    private fun readObject(): Map<String, Any?> {
        pos++ // '{'
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (pos < src.length && src[pos] == '}') {
            pos++
            return map
        }
        while (pos < src.length) {
            skipWhitespace()
            if (src[pos] != '"') throw IllegalArgumentException("Expected string key at $pos")
            val key = readString()
            skipWhitespace()
            if (pos >= src.length || src[pos] != ':') throw IllegalArgumentException("Expected ':' at $pos")
            pos++
            val value = readValue()
            map[key] = value
            skipWhitespace()
            if (pos >= src.length) break
            when (src[pos]) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return map
                }
                else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
            }
        }
        throw IllegalArgumentException("Unterminated JSON object")
    }

    private fun readArray(): List<Any?> {
        pos++ // '['
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (pos < src.length && src[pos] == ']') {
            pos++
            return list
        }
        while (pos < src.length) {
            list.add(readValue())
            skipWhitespace()
            if (pos >= src.length) break
            when (src[pos]) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return list
                }
                else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
            }
        }
        throw IllegalArgumentException("Unterminated JSON array")
    }

    private fun readString(): String {
        pos++ // '"'
        val sb = StringBuilder()
        while (pos < src.length) {
            val c = src[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                            val hex = src.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun readLiteral(expected: String, result: Any?): Any? {
        if (!src.startsWith(expected, pos)) {
            throw IllegalArgumentException("Expected $expected at $pos")
        }
        pos += expected.length
        return result
    }

    private fun readNumber(): Number {
        val start = pos
        while (pos < src.length && src[pos] in "0123456789+-.eE") {
            pos++
        }
        val raw = src.substring(start, pos)
        return raw.toLongOrNull() ?: raw.toDouble()
    }
}

class GitHubUpdateChecker(private val context: Context) {

    fun hasActiveInternetConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun checkLatestRelease(
        repoInput: String,
        installedVersion: String,
        skippedTag: String,
        frequency: UpdateCheckFrequency,
        lastCheckMs: Long,
        force: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): UpdateCheckResult {
        val slug = GitHubReleaseParser.normalizeRepoSlug(repoInput)
            ?: return UpdateCheckResult.NotConfigured

        if (!GitHubReleaseParser.shouldCheckNow(frequency, lastCheckMs, nowMs, force)) {
            return UpdateCheckResult.CooldownNotElapsed
        }

        if (!hasActiveInternetConnection()) {
            return UpdateCheckResult.NoInternet
        }

        return withContext(Dispatchers.IO) {
            fetchLatestReleaseFromGitHub(
                repoSlug = slug,
                installedVersion = installedVersion,
                skippedTag = skippedTag,
                nowMs = nowMs
            )
        }
    }

    private fun fetchLatestReleaseFromGitHub(
        repoSlug: String,
        installedVersion: String,
        skippedTag: String,
        nowMs: Long
    ): UpdateCheckResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://api.github.com/repos/$repoSlug/releases/latest")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "PocketBioacoustics/$installedVersion")
            }

            val code = connection.responseCode
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = GitHubReleaseParser.parseLatestReleaseJson(body)
                        ?: return UpdateCheckResult.Error("GitHub release metadata was invalid or draft-only.")

                    val isNewer = GitHubReleaseParser.isRemoteVersionNewer(
                        installedVersion = installedVersion,
                        remoteTagOrVersion = parsed.tagName
                    )

                    if (!isNewer) {
                        UpdateCheckResult.UpToDate(
                            currentVersion = GitHubReleaseParser.normalizeVersionString(installedVersion),
                            latestVersion = parsed.versionName,
                            checkedAtMs = nowMs
                        )
                    } else {
                        val isSkipped = skippedTag.isNotBlank() &&
                            GitHubReleaseParser.normalizeVersionString(skippedTag)
                                .equals(
                                    GitHubReleaseParser.normalizeVersionString(parsed.tagName),
                                    ignoreCase = true
                                )
                        UpdateCheckResult.UpdateAvailable(
                            update = parsed,
                            currentVersion = GitHubReleaseParser.normalizeVersionString(installedVersion),
                            checkedAtMs = nowMs,
                            isSkipped = isSkipped
                        )
                    }
                }

                HttpURLConnection.HTTP_NOT_FOUND ->
                    UpdateCheckResult.Error("No published GitHub release found for $repoSlug.")

                HttpURLConnection.HTTP_FORBIDDEN, 429 ->
                    UpdateCheckResult.Error("GitHub API rate limit reached. Try again later.")

                else ->
                    UpdateCheckResult.Error("GitHub check failed (HTTP $code).")
            }
        } catch (e: IOException) {
            UpdateCheckResult.Error("Could not reach GitHub: ${e.message ?: "network timeout"}")
        } catch (e: Exception) {
            UpdateCheckResult.Error("Update check error: ${e.message ?: "unexpected error"}")
        } finally {
            connection?.disconnect()
        }
    }
}
