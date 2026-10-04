/*
 * Copyright 2026, Youzix-Star
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Ported from NekoNeko (top.youzix.nekoneko), UpdateChecker.java.
 */

package top.youzix.dsha.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import top.youzix.dsha.BuildConfig

/** What a version check found. */
sealed interface UpdateResult {
    /** A newer release exists. [apkUrl] is null when the release carries no APK asset. */
    data class Available(
        val version: String,
        val notes: String,
        val apkUrl: String?,
    ) : UpdateResult

    data object UpToDate : UpdateResult

    data class Failed(val message: String) : UpdateResult
}

/**
 * Checks GitHub Releases for a newer build.
 *
 * The API is tried first; when it answers with an error — rate limiting, most often — the check
 * falls back to the releases page and reads the tag out of its redirect, which needs no token and
 * has no quota. That fallback is the difference between "check for updates" working and quietly
 * failing for the one user who taps it twice.
 *
 * A pre-release build asks for every release and a stable build only for the latest stable one:
 * `/releases/latest` deliberately ignores pre-releases, so an alpha that consulted it would never
 * see the next alpha — and a stable build that consulted the full list would be offered an alpha.
 */
object UpdateChecker {
    private const val REPO = "Youzix-Star/DSHA-Next"

    /** The newest stable release; GitHub deliberately leaves pre-releases out of this one. */
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    /** Every release, newest first — the only way a pre-release can see other pre-releases. */
    private const val API_LIST = "https://api.github.com/repos/$REPO/releases?per_page=20"

    private const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    private val main = Handler(Looper.getMainLooper())

    /** Runs the check off the main thread and calls back on it. */
    fun check(onResult: (UpdateResult) -> Unit) {
        val current = BuildConfig.VERSION_NAME
        Thread {
            val result = try {
                viaApi(current)
            } catch (error: Exception) {
                // A failed request is not the end of the story: try the page fallback first.
                runCatching { viaPage(current) }.getOrElse {
                    UpdateResult.Failed(error.message ?: error.toString())
                }
            }
            main.post { onResult(result) }
        }.apply { isDaemon = true }.start()
    }

    /** Opens the release page, for when the user would rather grab the APK by hand. */
    fun openReleasePage(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun viaApi(current: String): UpdateResult {
        // Which releases this build is allowed to see follows from what it is: a pre-release looks
        // at every release, because that is the channel it is on, while a stable build only ever
        // sees stable ones — otherwise an alpha would be pushed at everyone.
        val trackPreReleases = current.contains('-')
        val releases = fetchReleases(if (trackPreReleases) API_LIST else API_LATEST)
            ?: return viaPage(current)
        val newest = releases
            .filter { release ->
                !release.optBoolean("draft") &&
                    (trackPreReleases || !release.optBoolean("prerelease"))
            }
            .maxByOrNull { release -> release.optString("tag_name", "").removePrefix("v") }
            ?: return UpdateResult.UpToDate

        val version = newest.optString("tag_name", "").removePrefix("v")
        if (!isNewer(version, current)) return UpdateResult.UpToDate

        var apkUrl: String? = null
        newest.optJSONArray("assets")?.let { assets ->
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk") && !name.contains("debug")) {
                    apkUrl = asset.optString("browser_download_url", null)
                    break
                }
            }
        }
        return UpdateResult.Available(
            version = version,
            notes = newest.optString("body", ""),
            apkUrl = apkUrl,
        )
    }

    /** Both endpoints answer with JSON; one gives an object, the other an array. */
    private fun fetchReleases(url: String): List<JSONObject>? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }
        return try {
            if (connection.responseCode >= 400) return null
            val body = readAll(connection.inputStream)
            if (body.trimStart().startsWith("[")) {
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            } else {
                listOf(JSONObject(body))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun viaPage(current: String): UpdateResult {
        val connection = (URL(RELEASES_PAGE).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
        }
        return try {
            val code = connection.responseCode
            val location = if (code in 300..399) connection.getHeaderField("Location") else null
            if (location != null && location.contains("/tag/")) {
                val version = location.substringAfterLast("/").removePrefix("v")
                if (isNewer(version, current)) {
                    UpdateResult.Available(version = version, notes = "", apkUrl = null)
                } else {
                    UpdateResult.UpToDate
                }
            } else if (current.contains('-')) {
                // This fallback reads /releases/latest, which by definition skips pre-releases,
                // so for a pre-release build the honest answer is "cannot tell", not "up to date".
                UpdateResult.Failed("接口受限，暂时无法确认预发布版本")
            } else {
                UpdateResult.Failed("无法获取版本信息")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readAll(input: InputStream?): String {
        if (input == null) return ""
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}

/**
 * Whether [latest] is a later release than [current].
 *
 * Versions are compared on their numeric core first, and only then on the pre-release suffix —
 * so `2.0.0` counts as newer than `2.0.0-alpha.2`, which the plain "strip every non-digit"
 * comparison the original used would have missed, leaving alpha users stuck for good.
 */
internal fun isNewer(latest: String, current: String): Boolean {
    val (latestCore, latestPre) = splitVersion(latest)
    val (currentCore, currentPre) = splitVersion(current)

    for (index in 0 until maxOf(latestCore.size, currentCore.size)) {
        val l = latestCore.getOrElse(index) { 0 }
        val c = currentCore.getOrElse(index) { 0 }
        if (l != c) return l > c
    }

    // Same numbers: a finished release beats any pre-release of it.
    if (latestPre == null && currentPre != null) return true
    if (latestPre != null && currentPre == null) return false
    if (latestPre == null && currentPre == null) return false
    return comparePreRelease(latestPre!!, currentPre!!) > 0
}

/**
 * Puts the separators a version comparison needs into a version a person wrote.
 *
 * `2.0.2Beta1` is a perfectly normal thing to type, and without this it reads as the number 2.0.21
 * with no pre-release at all — which is how a beta build ends up being told there is a newer
 * version, or never told about the next one.
 */
private fun separateVersion(version: String): String {
    val out = StringBuilder(version.length + 4)
    version.forEachIndexed { index, character ->
        if (index > 0) {
            val previous = version[index - 1]
            val letterAfterDigit = previous.isDigit() && character.isLetter()
            val digitAfterLetter = previous.isLetter() && character.isDigit()
            if (letterAfterDigit) out.append('-') else if (digitAfterLetter) out.append('.')
        }
        out.append(character)
    }
    return out.toString()
}

private fun splitVersion(version: String): Pair<List<Int>, String?> {
    // "2.0.1 Beta 1" is a version name a person would write; the comparison wants a separator.
    val cleaned = separateVersion(version.trim().replace(' ', '-').removePrefix("v"))
    val core = cleaned.substringBefore('-')
    val pre = cleaned.substringAfter('-', missingDelimiterValue = "").ifBlank { null }
    val numbers = core.split('.').map { part ->
        part.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
    return numbers to pre
}

/** `alpha.2` < `alpha.10` < `beta.1` < `rc.1`, with plain words ranking by name. */
private fun comparePreRelease(latest: String, current: String): Int {
    val left = latest.split('.')
    val right = current.split('.')
    for (index in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrNull(index) ?: return -1
        val c = right.getOrNull(index) ?: return 1
        val ln = l.toIntOrNull()
        val cn = c.toIntOrNull()
        val comparison = when {
            ln != null && cn != null -> ln.compareTo(cn)
            ln != null -> -1
            cn != null -> 1
            else -> rank(l).compareTo(rank(c)).takeIf { it != 0 } ?: l.compareTo(c)
        }
        if (comparison != 0) return comparison
    }
    return 0
}

private fun rank(word: String): Int = when (word.lowercase()) {
    "alpha", "a" -> 0
    "beta", "b" -> 1
    "rc" -> 2
    else -> 3
}
