package com.expensemanager.android.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.expensemanager.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionName: String,
    val title: String,
    val notes: List<String>,
    val downloadUrl: String,
)

object CurrentRelease {
    val versionName: String = BuildConfig.VERSION_NAME
    val notes = listOf(
        "Browse the current week and a full month of earlier weekly spending on Home",
        "Create password-encrypted backups and send them safely to your Gmail account",
        "Restore transactions and budgets without duplicating existing records",
        "Enjoy faster startup and smoother, more responsive navigation animations",
    )
}

class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun shouldShowCurrentRelease(): Boolean =
        preferences.getInt(KEY_LAST_SEEN_VERSION, 0) < BuildConfig.VERSION_CODE

    fun markCurrentReleaseSeen() {
        preferences.edit().putInt(KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_CODE).apply()
    }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = NETWORK_TIMEOUT_MS
                connection.readTimeout = NETWORK_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "RupeyaWise-Android")
                if (connection.responseCode !in 200..299) return@runCatching null

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseLatestRelease(response, BuildConfig.VERSION_NAME)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun openDownload(downloadUrl: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    companion object {
        private const val PREFERENCES_NAME = "app_updates"
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version_code"
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/mibrahim805/RupeyaWise/releases/latest"
    }
}

internal fun parseLatestRelease(json: String, currentVersion: String): AppUpdateInfo? {
    val release = JSONObject(json)
    if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null

    val tagName = release.optString("tag_name")
    val versionName = tagName.trim().removePrefix("v").removePrefix("V")
    if (!isNewerVersion(versionName, currentVersion)) return null

    val assets = release.optJSONArray("assets") ?: return null
    val apkDownloadUrl = (0 until assets.length())
        .asSequence()
        .mapNotNull { assets.optJSONObject(it) }
        .firstOrNull { asset -> asset.optString("name").endsWith(".apk", ignoreCase = true) }
        ?.optString("browser_download_url")
        ?.takeIf { it.startsWith("https://") }
        ?: return null

    val notes = release.optString("body")
        .lineSequence()
        .map { line -> line.trim().trimStart('-', '*', '•').trim() }
        .filter { line -> line.isNotBlank() && !line.startsWith("#") }
        .take(8)
        .toList()
        .ifEmpty { listOf("Performance and reliability improvements") }

    return AppUpdateInfo(
        versionName = versionName,
        title = release.optString("name").ifBlank { "RupeyaWise $versionName" },
        notes = notes,
        downloadUrl = apkDownloadUrl,
    )
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = versionParts(candidate) ?: return false
    val currentParts = versionParts(current) ?: return false
    val count = maxOf(candidateParts.size, currentParts.size)

    repeat(count) { index ->
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun versionParts(version: String): List<Int>? {
    val normalized = version.trim().removePrefix("v").removePrefix("V").substringBefore('-')
    if (normalized.isBlank()) return null
    return normalized.split('.').map { part -> part.toIntOrNull() ?: return null }
}
