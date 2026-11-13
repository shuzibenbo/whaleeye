package com.dafeiyu.whaleeye.speed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

data class LatencyResult(
    val minMs: Double,
    val avgMs: Double,
    val maxMs: Double,
    val jitterMs: Double,
    val failCount: Int
)

data class SpeedResult(
    val latency: LatencyResult,
    val downMbps: Double,
    val upMbps: Double,
    val downBytes: Long,
    val upBytes: Long,
    val downCut: Boolean,
    val upCut: Boolean
)

object SpeedTestEngine {

    private const val UA = "WhaleEye/0.6"

    fun downUrl(bytes: Long): String = "https://speed.cloudflare.com/__down?bytes=" + bytes
    fun latencyUrl(): String = "https://speed.cloudflare.com/__down?bytes=0"
    fun upUrl(): String = "https://speed.cloudflare.com/__up"

    suspend fun measureLatency(count: Int = 8): LatencyResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Double>()
        var fails = 0
        repeat(count) {
            val t0 = System.nanoTime()
            var ok = false
            try {
                val conn = URL(latencyUrl()).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Cache-Control", "no-store")
                val code = conn.responseCode
                if (code in 200..399) ok = true
                try {
                    conn.inputStream.close()
                } catch (e: Exception) {
                }
                conn.disconnect()
            } catch (e: Exception) {
                ok = false
            }
            val ms = (System.nanoTime() - t0) / 1_000_000.0
            if (ok) samples += ms else fails++
        }
        if (samples.isEmpty()) {
            LatencyResult(0.0, 0.0, 0.0, 0.0, fails)
        } else {
            var jitter = 0.0
            for (i in 1 until samples.size) jitter += abs(samples[i] - samples[i - 1])
            if (samples.size > 1) jitter /= (samples.size - 1)
            LatencyResult(
                minMs = samples.minOrNull() ?: 0.0,
                avgMs = samples.average(),
                maxMs = samples.maxOrNull() ?: 0.0,
                jitterMs = jitter,
                failCount = fails
            )
        }
    }

    suspend fun download(
        bytes: Long,
        maxDurationMs: Long = 20000,
        onSample: (mbps: Double, progress: Float) -> Unit
    ): Pair<Double, Boolean> = withContext(Dispatchers.IO) {
        val conn = URL(downUrl(bytes)).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Cache-Control", "no-store")
        val buf = ByteArray(64 * 1024)
        var total = 0L
        var lastTotal = 0L
        var lastTs = System.currentTimeMillis()
        val startTs = lastTs
        var cut = false
        try {
            conn.inputStream.use { input ->
                while (total < bytes) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    val now = System.currentTimeMillis()
                    if (now - lastTs >= 250) {
                        val instBps = (total - lastTotal) * 1000.0 / (now - lastTs)
                        onSample(instBps * 8 / 1_000_000, (total.toFloat() / bytes).coerceIn(0f, 1f))
                        lastTs = now
                        lastTotal = total
                    }
                    if (now - startTs >= maxDurationMs) {
                        cut = true
                        break
                    }
                }
            }
        } catch (e: Exception) {
            cut = true
        } finally {
            try {
                conn.disconnect()
            } catch (e: Exception) {
            }
        }
        val elapsed = (System.currentTimeMillis() - startTs) / 1000.0
        val mbps = if (elapsed <= 0.3 || total <= 0) 0.0 else total * 8.0 / 1_000_000 / elapsed
        Pair(mbps, cut)
    }

    suspend fun upload(
        bytes: Int,
        maxDurationMs: Long = 15000,
        onSample: (mbps: Double, progress: Float) -> Unit
    ): Pair<Double, Boolean> = withContext(Dispatchers.IO) {
        val conn = URL(upUrl()).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setFixedLengthStreamingMode(bytes)
        val chunk = ByteArray(64 * 1024)
        var sent = 0
        var lastSent = 0
        var lastTs = System.currentTimeMillis()
        val startTs = lastTs
        var cut = false
        try {
            conn.outputStream.use { out ->
                while (sent < bytes) {
                    val n = minOf(chunk.size, bytes - sent)
                    out.write(chunk, 0, n)
                    sent += n
                    val now = System.currentTimeMillis()
                    if (now - lastTs >= 250) {
                        val instBps = (sent - lastSent) * 1000.0 / (now - lastTs)
                        onSample(instBps * 8 / 1_000_000, (sent.toFloat() / bytes).coerceIn(0f, 1f))
                        lastTs = now
                        lastSent = sent
                    }
                    if (now - startTs >= maxDurationMs) {
                        cut = true
                        break
                    }
                }
                out.flush()
            }
            try {
                conn.responseCode
            } catch (e: Exception) {
            }
        } catch (e: Exception) {
            cut = true
        } finally {
            try {
                conn.disconnect()
            } catch (e: Exception) {
            }
        }
        val elapsed = (System.currentTimeMillis() - startTs) / 1000.0
        val mbps = if (elapsed <= 0.3 || sent <= 0) 0.0 else sent * 8.0 / 1_000_000 / elapsed
        Pair(mbps, cut)
    }
}