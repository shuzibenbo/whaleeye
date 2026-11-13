package com.dafeiyu.whaleeye.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val mirrors: List<String>,
    val sha256: String,
    val changelog: String,
    val minSdk: Int
)

object UpdateChecker {

    private const val UA = "WhaleEye/0.9"

    /** 内置多源：任意一个能通就能检查更新（国内 raw 常被污染，所以并行抢答）。 */
    private val BUILTIN_SOURCES = listOf(
        "https://cdn.jsdelivr.net/gh/shuzibenbo/whaleeye@main/update.json",
        "https://raw.githubusercontent.com/shuzibenbo/whaleeye/main/update.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/shuzibenbo/whaleeye/main/update.json",
        "https://ghfast.top/https://raw.githubusercontent.com/shuzibenbo/whaleeye/main/update.json",
        "https://api.github.com/repos/shuzibenbo/whaleeye/contents/update.json"
    )

    suspend fun check(userUrl: String): UpdateInfo? = coroutineScope {
        val urls = mutableListOf<String>()
        if (userUrl.isNotBlank()) urls += userUrl.trim()
        urls += BUILTIN_SOURCES
        val jobs = urls.map { u ->
            async(Dispatchers.IO) {
                runCatching {
                    httpGet(bust(u))?.let { body -> parse(body) }
                }.getOrNull()
            }
        }
        var found: UpdateInfo? = null
        for (j in jobs) {
            val r = runCatching { j.await() }.getOrNull()
            if (r != null) {
                found = r
                break
            }
        }
        jobs.forEach { runCatching { it.cancel() } }
        found
    }

    private fun parse(body: String): UpdateInfo? = try {
        val o = JSONObject(body)
        val vc = o.optInt("versionCode", 0)
        if (vc <= 0) {
            null
        } else {
            val mirrors = mutableListOf<String>()
            val arr = o.optJSONArray("mirrors")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotBlank()) mirrors += s
                }
            }
            UpdateInfo(
                versionCode = vc,
                versionName = o.optString("versionName", "?"),
                apkUrl = o.optString("apkUrl", ""),
                mirrors = mirrors,
                sha256 = o.optString("sha256", "").lowercase(),
                changelog = o.optString("changelog", ""),
                minSdk = o.optInt("minSdk", 0)
            )
        }
    } catch (t: Throwable) {
        null
    }

    suspend fun downloadAny(
        urls: List<String>,
        dest: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        for (u in urls) {
            if (u.isBlank()) continue
            val ok = download(u, dest, onProgress)
            if (ok) return@withContext true
            runCatching { if (dest.exists()) dest.delete() }
            onProgress(0f)
        }
        false
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
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
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
        } catch (t: Throwable) {
            false
        } finally {
            runCatching { conn?.disconnect() }
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
    } catch (t: Throwable) {
        ""
    }

    private fun bust(url: String): String {
        val sep = if (url.contains("?")) "&" else "?"
        return url + sep + "t=" + System.currentTimeMillis()
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Cache-Control", "no-store")
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // GitHub API 的 contents 接口返回 base64 包装，解开
            if (text.trimStart().startsWith("{\"name\"")) {
                val o = JSONObject(text)
                val b64 = o.optString("content", "")
                if (b64.isNotBlank()) {
                    String(android.util.Base64.decode(b64.replace("\n", ""), android.util.Base64.DEFAULT))
                } else {
                    text
                }
            } else {
                text
            }
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}