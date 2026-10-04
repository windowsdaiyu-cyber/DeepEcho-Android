package com.deepecho.mobile.net

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import com.deepecho.mobile.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * PC-style updater for the sideloaded Android build.
 *
 * Flow:
 * 1) Check the official GitHub Releases endpoint on app launch (throttled).
 * 2) Show an in-app update dialog when a newer tag is available.
 * 3) Download the APK with progress.
 * 4) Verify optional SHA-256 + package name.
 * 5) Hand the APK to Android's package installer.
 *
 * Android intentionally requires the user to approve APK installation; a normal app cannot
 * silently replace itself. After the one-time "Install unknown apps" permission is granted,
 * later updates still use Android's final install confirmation screen.
 */
object AppUpdater {
    private const val OWNER = "windowsdaiyu-cyber"
    private const val REPO = "DeepEcho-Android"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val PREFS = "deepecho_updater"
    private const val LAST_CHECK = "last_successful_check"

    data class Release(
        val tag: String,
        val version: String,
        val title: String,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val sha256Url: String?,
        val releasePageUrl: String
    )

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data class Available(val release: Release) : State()
        data class Downloading(val release: Release, val percent: Int) : State()
        data class Ready(val release: Release, val filePath: String) : State()
        data class InstallPermission(val release: Release, val filePath: String) : State()
        data class Error(val message: String) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var manualCheck = false

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    }.getOrDefault("0.0.0")

    fun autoCheck(context: Context) {
        if (!Settings.autoUpdateCheck.value) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(context, manual = false)
    }

    fun checkNow(context: Context) = check(context, manual = true)

    private fun check(context: Context, manual: Boolean) {
        val current = _state.value
        if (current is State.Checking || current is State.Downloading) return

        manualCheck = manual
        _state.value = State.Checking
        val appContext = context.applicationContext

        scope.launch {
            try {
                val release = fetchLatestRelease()
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()

                if (isNewer(release.version, currentVersion(appContext))) {
                    _state.value = State.Available(release)
                } else {
                    _state.value = State.Idle
                    if (manualCheck) Bus.toast("DEEP-ECHO is already up to date")
                }
            } catch (t: Throwable) {
                val message = cleanError(t)
                if (manualCheck) {
                    _state.value = State.Error(message)
                } else {
                    _state.value = State.Idle
                }
            } finally {
                manualCheck = false
            }
        }
    }

    fun dismiss() {
        if (_state.value !is State.Downloading) _state.value = State.Idle
    }

    fun download(context: Context, release: Release) {
        if (_state.value is State.Downloading) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                val base = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: appContext.filesDir
                val updateDir = File(base, "updates").apply { mkdirs() }
                val safeName = release.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val finalFile = File(updateDir, safeName)
                val partFile = File(updateDir, "$safeName.part")
                partFile.delete()

                val request = Request.Builder()
                    .url(release.apkUrl)
                    .header("User-Agent", "DEEP-ECHO-Mobile-Updater/${currentVersion(appContext)}")
                    .build()

                Net.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Update download failed: HTTP ${response.code}")
                    val body = response.body ?: error("Update download was empty")
                    val total = body.contentLength()
                    var written = 0L
                    var lastPercent = -1
                    _state.value = State.Downloading(release, 0)

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val percent = ((written * 100L) / total).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        _state.value = State.Downloading(release, percent)
                                    }
                                }
                            }
                            output.fd.sync()
                        }
                    }
                }

                if (partFile.length() <= 0L) error("Downloaded update file is empty")

                release.sha256Url?.let { checksumUrl ->
                    val expected = fetchText(checksumUrl)
                        .let { Regex("(?i)\\b[0-9a-f]{64}\\b").find(it)?.value }
                        ?: error("Release checksum is invalid")
                    val actual = sha256(partFile)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        error("Update checksum verification failed")
                    }
                }

                val packageName = archivePackageName(appContext, partFile)
                if (packageName != appContext.packageName) {
                    error("Downloaded APK is not a DEEP-ECHO Mobile package")
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                _state.value = State.Ready(release, finalFile.absolutePath)
                withContext(Dispatchers.Main) {
                    requestInstall(appContext, release, finalFile)
                }
            } catch (t: Throwable) {
                _state.value = State.Error(cleanError(t))
            }
        }
    }

    fun installReady(context: Context) {
        when (val state = _state.value) {
            is State.Ready -> requestInstall(context, state.release, File(state.filePath))
            is State.InstallPermission -> requestInstall(context, state.release, File(state.filePath))
            else -> Unit
        }
    }

    fun openInstallPermission(context: Context) {
        val state = _state.value as? State.InstallPermission ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            requestInstall(context, state.release, File(state.filePath))
            return
        }
        val intent = Intent(
            AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { _state.value = State.Error("Could not open Android install permission settings") }
    }

    fun onAppResumed(context: Context) {
        val state = _state.value as? State.InstallPermission ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            requestInstall(context, state.release, File(state.filePath))
        }
    }

    private fun requestInstall(context: Context, release: Release, file: File) {
        if (!file.exists()) {
            _state.value = State.Error("Downloaded update file is missing")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _state.value = State.InstallPermission(release, file.absolutePath)
            return
        }

        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrElse {
            _state.value = State.Error("Could not prepare update installer")
            return
        }

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onSuccess { _state.value = State.Idle }
            .onFailure { _state.value = State.Error("Android installer could not be opened") }
    }

    private fun fetchLatestRelease(): Release {
        val json = JSONObject(fetchText(LATEST_RELEASE_API))
        val tag = json.optString("tag_name").trim()
        val version = extractVersion(tag)
            ?: error("Latest release tag has no valid version")
        val assets = json.optJSONArray("assets") ?: error("Latest release has no assets")

        var apkName: String? = null
        var apkUrl: String? = null
        val checksumAssets = mutableMapOf<String, String>()

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".sha256", ignoreCase = true) && url.isNotBlank()) {
                checksumAssets[name] = url
            }
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                val prefer = name.contains("DEEP-ECHO", ignoreCase = true)
                if (apkUrl == null || prefer) {
                    apkName = name
                    apkUrl = url
                }
            }
        }

        val finalApkName = apkName ?: error("Latest release does not contain an APK")
        val finalApkUrl = apkUrl ?: error("Latest release APK URL is missing")
        val checksumUrl = checksumAssets["$finalApkName.sha256"]
            ?: checksumAssets.entries.firstOrNull { it.key.contains(finalApkName, ignoreCase = true) }?.value

        return Release(
            tag = tag,
            version = version,
            title = json.optString("name").ifBlank { "DEEP-ECHO Mobile $version" },
            notes = json.optString("body"),
            apkName = finalApkName,
            apkUrl = finalApkUrl,
            sha256Url = checksumUrl,
            releasePageUrl = json.optString("html_url")
        )
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DEEP-ECHO-Mobile-Updater")
            .build()
        return Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    error("Update server/release not found. The Android GitHub release must be public.")
                }
                error("Update server error: HTTP ${response.code}")
            }
            response.body?.string() ?: error("Update server returned an empty response")
        }
    }

    private fun extractVersion(value: String): String? =
        Regex("\\d+(?:\\.\\d+){1,3}").find(value)?.value

    private fun isNewer(remote: String, local: String): Boolean {
        val r = versionParts(remote)
        val l = versionParts(local)
        val max = maxOf(r.size, l.size)
        for (i in 0 until max) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        extractVersion(value)?.split('.')?.map { it.toIntOrNull() ?: 0 } ?: listOf(0)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageName(context: Context, file: File): String? {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageArchiveInfo(file.absolutePath, 0)
        }
        return info?.packageName
    }

    private fun cleanError(t: Throwable): String {
        val msg = t.message?.trim().orEmpty()
        return if (msg.isBlank()) "Update failed. Please try again." else msg.take(220)
    }
}
