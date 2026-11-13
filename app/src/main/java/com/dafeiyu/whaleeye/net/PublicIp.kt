package com.dafeiyu.whaleeye.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class PublicIpInfo(
    val ipv4: String? = null,
    val ipv6: String? = null,
    val location: String? = null,
    val source: String? = null,
    val error: String? = null
)

object PublicIpFetcher {

    suspend fun fetch(): PublicIpInfo = coroutineScope {
        val j4 = async(Dispatchers.IO) { httpGet("https://v4.ident.me") }
        val j6 = async(Dispatchers.IO) { httpGet("https://v6.ident.me") }
        val jl = async(Dispatchers.IO) { httpGet("https://myip.ipip.net") }
        val j4b = async(Dispatchers.IO) { httpGet("https://api.ipify.org") }

        var v4 = cleanIp(j4.await())
        var v6 = cleanIp(j6.await())
        val body = jl.await()
        var loc: String? = null
        if (body != null) {
            val m = Regex("来自于[:：]\\s*([^\\n]+)").find(body)
            if (m != null) loc = m.groupValues[1].trim()
            val ipInBody = extractEgressIp(body)
            if (ipInBody != null) {
                if (isIpv6(ipInBody)) { if (v6 == null) v6 = ipInBody } else { if (v4 == null) v4 = ipInBody }
            }
        }
        if (v4 == null) v4 = cleanIp(j4b.await())

        val src = mutableListOf<String>()
        if (v4 != null) src += "IPv4"
        if (v6 != null) src += "IPv6"
        if (loc != null) src += "归属地"
        val ok = v4 != null || v6 != null || loc != null
        PublicIpInfo(
            ipv4 = v4,
            ipv6 = v6,
            location = loc,
            source = if (src.isEmpty()) null else src.joinToString(" + "),
            error = if (ok) null else "所有查询源都失败（可能被网络限制）"
        )
    }

    private fun cleanIp(raw: String?): String? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty() || s.length > 60) return null
        return s.takeIf { looksLikeIp(it) }
    }

    private fun extractEgressIp(body: String): String? {
        val m = Regex("(?:当前\\s*IP|IP)\\s*[:：]\\s*([0-9a-fA-F:.]+)").find(body)
        val v = m?.groupValues?.get(1)?.trim() ?: return null
        return v.takeIf { looksLikeIp(it) }
    }

    private fun looksLikeIp(s: String): Boolean = isIpv4(s) || isIpv6(s)

    private fun isIpv4(s: String): Boolean =
        Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$").matches(s)

    private fun isIpv6(s: String): Boolean =
        s.length in 6..45 && s.count { it == ':' } >= 2 && Regex("^[0-9a-fA-F:.]+$").matches(s)

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "WhaleEye/0.3")
            val code = conn.responseCode
            if (code !in 200..299) return null
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { r ->
                var line = r.readLine()
                while (line != null && sb.length < 512) {
                    sb.append(line).append(" ")
                    line = r.readLine()
                }
            }
            sb.toString().trim()
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}