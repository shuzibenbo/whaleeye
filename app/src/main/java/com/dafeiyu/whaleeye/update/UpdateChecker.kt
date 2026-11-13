package com.dafeiyu.whaleeye.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val changelog: String,
    val minSdk: Int
)

object UpdateChecker {

    private const val UA = "WhaleEye/0.8"

    suspend fun check(url: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val body = httpGet(url) ?: return@withContext null
        try {
            val o = JSONObject(body)
            UpdateInfo(
                versionCode = o.optInt("versionCode", 0),
                versionName = o.optString("versionName", "?"),
                apkUrl = o.optString("apkUrl", ""),
                sha256 = o.optString("sha256", "").lowercase(),
                changelog = o.optString("changelog", ""),
                minSdk = o.optInt("minSdk", 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            dest.parentFile?.mkdirs()
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code !in 200..299) return@withContext false
            val total = conn.contentLengthLong
            val buf = ByteArray(64 * 1024)
            var got = 0L
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(dest).use { out ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (total > 0) onProgress((got.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            false
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
            }
        }
    }

    fun sha256(file: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { String.format("%02x", it) }
    } catch (e: Exception) {
        ""
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Cache-Control", "no-store")
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
            }
        }
    }
}