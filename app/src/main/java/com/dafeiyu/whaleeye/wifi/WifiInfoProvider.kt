package com.dafeiyu.whaleeye.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dafeiyu.whaleeye.info.InfoGroup
import com.dafeiyu.whaleeye.info.InfoItem
import java.util.Locale

data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val freqMhz: Int,
    val band: String,
    val channel: Int,
    val widthMhz: Int,
    val standard: String,
    val security: String,
    val ageSec: Long
)

object WifiInfoProvider {

    fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.NEARBY_WIFI_DEVICES
        list += Manifest.permission.ACCESS_FINE_LOCATION
        return list.toTypedArray()
    }

    fun hasPermission(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun manager(context: Context): WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun isWifiEnabled(context: Context): Boolean = manager(context)?.isWifiEnabled == true

    fun collectCurrent(context: Context): List<InfoGroup> {
        val wm = manager(context) ?: return emptyList()
        val groups = mutableListOf<InfoGroup>()
        val info: WifiInfo? = try {
            @Suppress("DEPRECATION")
            wm.connectionInfo
        } catch (e: Exception) {
            null
        }
        if (info == null || info.networkId == -1) {
            groups += InfoGroup("当前 WiFi", listOf(
                InfoItem("状态", if (wm.isWifiEnabled) "已开启，未连接到 SSID" else "WiFi 已关闭")
            ))
            return groups
        }
        val freq = info.frequency
        val items = mutableListOf<InfoItem>()
        items += InfoItem("SSID", safeSsid(info.ssid))
        items += InfoItem("BSSID", info.bssid ?: "—")
        items += InfoItem("频段", bandOf(freq) + "  信道 " + channelOf(freq))
        items += InfoItem("频率", freq.toString() + " MHz")
        items += InfoItem("信号强度", info.rssi.toString() + " dBm  (" + rssiText(info.rssi) + ")")
        items += InfoItem("当前速率", info.linkSpeed.toString() + " Mbps")
        if (Build.VERSION.SDK_INT >= 30) {
            val tx = info.maxSupportedTxLinkSpeedMbps
            val rx = info.maxSupportedRxLinkSpeedMbps
            if (tx > 0 || rx > 0) items += InfoItem("协商上限", tx.toString() + " ↑ / " + rx + " ↓ Mbps")
            items += InfoItem("WiFi 标准", standardOf(info.wifiStandard))
        }
        if (Build.VERSION.SDK_INT >= 31) {
            items += InfoItem("安全类型", securityOfType(info.currentSecurityType))
        }
        items += InfoItem("网络 ID", info.networkId.toString())
        items += InfoItem("隐藏网络", if (info.hiddenSSID) "是" else "否")
        groups += InfoGroup("当前 WiFi", items)
        return groups
    }

    fun collectScan(context: Context): List<WifiAp> {
        val wm = manager(context) ?: return emptyList()
        val results: List<ScanResult> = try {
            wm.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val now = System.currentTimeMillis()
        return results.map { r ->
            val ssid = try { r.SSID ?: "" } catch (e: Exception) { "" }
            WifiAp(
                ssid = if (ssid.isNullOrBlank()) "（隐藏网络）" else ssid,
                bssid = r.BSSID ?: "—",
                rssi = r.level,
                freqMhz = r.frequency,
                band = bandOf(r.frequency),
                channel = channelOf(r.frequency),
                widthMhz = widthOf(r),
                standard = if (Build.VERSION.SDK_INT >= 30) standardOf(r.wifiStandard) else "—",
                security = securityOfCaps(r.capabilities),
                ageSec = ((now - r.timestamp / 1000L) / 1000L).coerceAtLeast(0L)
            )
        }.sortedByDescending { it.rssi }
    }

    fun triggerScan(context: Context) {
        try {
            manager(context)?.startScan()
        } catch (e: Exception) {
        }
    }

    private fun safeSsid(raw: String?): String {
        val s = raw ?: return "（需要位置权限）"
        val clean = s.replace("\"", "")
        return when {
            clean == "<unknown ssid>" -> "（需要位置权限）"
            clean.isBlank() -> "（隐藏网络）"
            else -> clean
        }
    }

    fun bandOf(freq: Int): String = when {
        freq == 2484 -> "2.4G"
        freq in 2400..2500 -> "2.4G"
        freq in 4900..5895 -> "5G"
        freq in 5925..7125 -> "6G"
        else -> "未知"
    }

    fun channelOf(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2400..2500 -> (freq - 2407) / 5
        freq in 4900..5895 -> (freq - 5000) / 5
        freq in 5925..7125 -> (freq - 5950) / 5
        else -> 0
    }

    private fun widthOf(r: ScanResult): Int = try {
        when (r.channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> 20
            ScanResult.CHANNEL_WIDTH_40MHZ -> 40
            ScanResult.CHANNEL_WIDTH_80MHZ -> 80
            ScanResult.CHANNEL_WIDTH_160MHZ -> 160
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    fun standardOf(s: Int): String = when (s) {
        ScanResult.WIFI_STANDARD_LEGACY -> "WiFi 1~3"
        ScanResult.WIFI_STANDARD_11N -> "WiFi 4 (11n)"
        ScanResult.WIFI_STANDARD_11AC -> "WiFi 5 (11ac)"
        ScanResult.WIFI_STANDARD_11AX -> "WiFi 6 (11ax)"
        ScanResult.WIFI_STANDARD_11AD -> "WiGig (11ad)"
        else -> {
            if (Build.VERSION.SDK_INT >= 34 && s == 8) "WiFi 7 (11be)" else "未知"
        }
    }

    private fun securityOfCaps(caps: String?): String {
        val c = caps ?: return "未知"
        return when {
            c.contains("WPA3") && c.contains("WPA2") -> "WPA2/WPA3"
            c.contains("WPA3") -> "WPA3"
            c.contains("WPA2") -> "WPA2"
            c.contains("WPA") -> "WPA"
            c.contains("WEP") -> "WEP"
            c.contains("EAP") -> "企业级"
            c.contains("OWE") -> "OWE"
            else -> "开放"
        }
    }

    private fun securityOfType(t: Int): String = when (t) {
        0 -> "开放"
        1 -> "WEP"
        2 -> "WPA"
        3 -> "WPA2"
        4 -> "WPA3"
        5 -> "WPA2/WPA3 混合"
        6 -> "WPA3 企业"
        7 -> "WPA2 企业"
        8 -> "OWE"
        else -> "类型 " + t
    }

    fun rssiText(rssi: Int): String = when {
        rssi >= -50 -> "极强"
        rssi >= -60 -> "强"
        rssi >= -70 -> "中"
        rssi >= -80 -> "弱"
        else -> "极弱"
    }

    fun rssiBars(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -78 -> 2
        else -> 1
    }
}