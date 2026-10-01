package pl.pogoda.mazowsze.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.pogoda.mazowsze.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateSnapshot(
    val checkedAt: Long,
    val latestVersion: String?,
    val apkUrl: String?,
    val apkSize: Long,
    val apkSha256: String?
) {
    val isAvailable: Boolean
        get() = latestVersion != null && VersionNumbers.isNewer(latestVersion, BuildConfig.VERSION_NAME)
}

internal object VersionNumbers {
    private val format = Regex("^v?\\d+(?:\\.\\d+)*$")

    fun isValid(value: String) = format.matches(value) &&
        value.removePrefix("v").split('.').all { it.toLongOrNull() != null }

    fun isNewer(candidate: String, installed: String): Boolean {
        val newer = parse(candidate) ?: return false
        val current = parse(installed) ?: return false
        for (index in 0 until maxOf(newer.size, current.size)) {
            val difference = newer.getOrElse(index) { 0 }.compareTo(current.getOrElse(index) { 0 })
            if (difference != 0) return difference > 0
        }
        return false
    }

    fun isSame(first: String, second: String): Boolean {
        val a = parse(first) ?: return false
        val b = parse(second) ?: return false
        return (0 until maxOf(a.size, b.size)).all { a.getOrElse(it) { 0 } == b.getOrElse(it) { 0 } }
    }

    private fun parse(value: String): List<Long>? {
        if (!format.matches(value)) return null
        return value.removePrefix("v").split('.').map { it.toLongOrNull() ?: return null }
    }
}

class AppUpdates(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    companion object {
        private val downloadMutex = Mutex()
        private const val MAX_APK_BYTES = 200L * 1024 * 1024
    }

    fun snapshot() = UpdateSnapshot(
        preferences.getLong("checked_at", 0L),
        preferences.getString("latest_version", null),
        preferences.getString("apk_url", null),
        preferences.getLong("apk_size", 0L),
        preferences.getString("apk_sha256", null)
    )

    fun notificationsEnabled() = preferences.getBoolean("notifications_enabled", false)

    fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun wasNotified(version: String) = preferences.getString("notified_version", null) == version

    fun markNotified(version: String) {
        preferences.edit().putString("notified_version", version).apply()
    }

    fun observe(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun stopObserving(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    suspend fun check(): UpdateSnapshot = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.github.com/repos/aimatthew/Cirral/releases/latest").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Cirral-Android")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub HTTP ${connection.responseCode}")
            val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val version = release.getString("tag_name")
            if (!VersionNumbers.isValid(version)) throw IllegalStateException("Nieznany format wersji")
            val assets = release.getJSONArray("assets")
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) && it.optString("state") == "uploaded" }
            val apk = if (apks.size == 1) apks.single() else apks.singleOrNull {
                it.optString("name").contains("universal", ignoreCase = true)
            }
            val apkUrl = apk?.getString("browser_download_url")
            if (apkUrl != null && !apkUrl.startsWith("https://github.com/aimatthew/Cirral/releases/download/"))
                throw IllegalStateException("Nieprawidłowy adres pliku APK")
            val size = apk?.optLong("size") ?: 0L
            if (size > MAX_APK_BYTES) throw IllegalStateException("Plik APK jest zbyt duży")
            val digest = apk?.optString("digest")?.removePrefix("sha256:")
                ?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            preferences.edit()
                .putLong("checked_at", System.currentTimeMillis())
                .putString("latest_version", version)
                .putString("apk_url", apkUrl)
                .putLong("apk_size", size)
                .putString("apk_sha256", digest)
                .apply()
            snapshot()
        } finally {
            connection.disconnect()
        }
    }

    fun downloadedApk(update: UpdateSnapshot): File? {
        val version = update.latestVersion?.takeIf(VersionNumbers::isValid) ?: return null
        val file = File(File(appContext.filesDir, "updates"), "Cirral-$version.apk")
        return file.takeIf { it.isFile && it.length() > 0 &&
            (update.apkSize <= 0 || it.length() == update.apkSize) &&
            (update.apkSha256 == null || sha256(it).equals(update.apkSha256, ignoreCase = true)) &&
            isValidApk(it, version)
        }
    }

    suspend fun download(update: UpdateSnapshot, onProgress: (Int) -> Unit = {}): File =
        downloadMutex.withLock {
            withContext(Dispatchers.IO) {
                downloadedApk(update)?.let { onProgress(100); return@withContext it }
                val version = update.latestVersion?.takeIf(VersionNumbers::isValid)
                    ?: throw IllegalStateException("Brak numeru wersji")
                val address = update.apkUrl?.takeIf {
                    it.startsWith("https://github.com/aimatthew/Cirral/releases/download/")
                } ?: throw IllegalStateException("Wydanie nie zawiera pliku APK")
                val directory = File(appContext.filesDir, "updates")
                if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Nie można zapisać pliku APK")
                val target = File(directory, "Cirral-$version.apk")
                val temporary = File(directory, "Cirral-$version.download.apk")
                val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Cirral-Android")
                }
                try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("Błąd pobierania HTTP ${connection.responseCode}")
                    val expected = update.apkSize
                    var downloaded = 0L
                    val digest = MessageDigest.getInstance("SHA-256")
                    connection.inputStream.use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                if (downloaded > MAX_APK_BYTES) throw IllegalStateException("Plik APK jest zbyt duży")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                if (expected > 0) onProgress((downloaded * 100 / expected).toInt().coerceIn(0, 99))
                            }
                        }
                    }
                    if (downloaded == 0L || (expected > 0 && downloaded != expected))
                        throw IllegalStateException("Pobrano niepełny plik APK")
                    val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
                    if (update.apkSha256 != null && !actualDigest.equals(update.apkSha256, ignoreCase = true))
                        throw IllegalStateException("Suma kontrolna APK jest nieprawidłowa")
                    if (!isValidApk(temporary, version)) throw IllegalStateException("Plik APK nie pasuje do Cirral")
                    if (target.exists()) target.delete()
                    if (!temporary.renameTo(target)) throw IllegalStateException("Nie można zapisać pliku APK")
                    onProgress(100)
                    preferences.edit().putString("downloaded_version", version).apply()
                    directory.listFiles()?.filter { it != target &&
                        it.name.matches(Regex("Cirral-v?\\d+(?:\\.\\d+)*\\.(?:download\\.)?apk"))
                    }?.forEach { it.delete() }
                    target
                } catch (error: Exception) {
                    temporary.delete()
                    throw error
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun isValidApk(file: File, releaseVersion: String): Boolean {
        val info = appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return info.packageName == BuildConfig.APPLICATION_ID && code > BuildConfig.VERSION_CODE &&
            VersionNumbers.isSame(info.versionName ?: "", releaseVersion)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
