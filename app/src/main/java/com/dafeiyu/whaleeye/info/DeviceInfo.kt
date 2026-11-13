package com.dafeiyu.whaleeye.info

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.util.Locale
import kotlin.math.sqrt

data class InfoItem(val label: String, val value: String)

data class InfoGroup(val title: String, val items: List<InfoItem>)

object DeviceInfoProvider {

    fun collect(context: Context): List<InfoGroup> = listOf(
        InfoGroup("系统", systemItems()),
        InfoGroup("设备与芯片", deviceItems()),
        InfoGroup("内存", memoryItems(context)),
        InfoGroup("存储", storageItems()),
        InfoGroup("屏幕", screenItems(context)),
        InfoGroup("电池", batteryItems(context)),
        InfoGroup("传感器", sensorItems(context))
    )

    private fun systemItems(): List<InfoItem> {
        val items = mutableListOf(
            InfoItem("Android 版本", Build.VERSION.RELEASE),
            InfoItem("SDK 级别", Build.VERSION.SDK_INT.toString()),
            InfoItem("安全补丁", Build.VERSION.SECURITY_PATCH.ifBlank { "—" }),
            InfoItem("内核版本", System.getProperty("os.version") ?: "—"),
            InfoItem("系统版本号", Build.DISPLAY)
        )
        getPropRaw("ro.mi.os.version.incremental")?.let { items += InfoItem("HyperOS", it) }
        getPropRaw("ro.build.version.oplusrom")?.let { items += InfoItem("ColorOS", it) }
        getPropRaw("ro.vivo.os.version")?.let { items += InfoItem("OriginOS", it) }
        getPropRaw("ro.product.system.name")?.let { items += InfoItem("系统 ROM", it) }
        items += InfoItem("构建类型", Build.TYPE)
        return items
    }

    private fun deviceItems(): List<InfoItem> {
        val items = mutableListOf(
            InfoItem("型号", Build.MODEL),
            InfoItem("品牌", Build.BRAND),
            InfoItem("厂商", Build.MANUFACTURER),
            InfoItem("设备代号", Build.DEVICE),
            InfoItem("主板", Build.BOARD),
            InfoItem("硬件", Build.HARDWARE)
        )
        if (Build.VERSION.SDK_INT >= 31) {
            items += InfoItem("SoC 型号", Build.SOC_MODEL.ifBlank { "—" })
            items += InfoItem("SoC 厂商", Build.SOC_MANUFACTURER.ifBlank { "—" })
        }
        items += InfoItem("CPU 核心", Runtime.getRuntime().availableProcessors().toString())
        items += InfoItem("CPU 频率", readCpuMaxFreq())
        items += InfoItem("ABI", Build.SUPPORTED_ABIS.joinToString(" / "))
        items += InfoItem("64 位", if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "是" else "否")
        return items
    }

    private fun memoryItems(context: Context): List<InfoItem> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        return listOf(
            InfoItem("总内存", bytes(mi.totalMem)),
            InfoItem("可用内存", bytes(mi.availMem)),
            InfoItem("已用内存", bytes(mi.totalMem - mi.availMem)),
            InfoItem("低内存阈值", bytes(mi.threshold)),
            InfoItem("当前低内存", if (mi.lowMemory) "是" else "否"),
            InfoItem("JVM 上限", bytes(rt.maxMemory()))
        )
    }

    private fun storageItems(): List<InfoItem> {
        val sf = StatFs(Environment.getDataDirectory().absolutePath)
        val total = sf.totalBytes
        val avail = sf.availableBytes
        return listOf(
            InfoItem("内部存储 总", bytes(total)),
            InfoItem("内部存储 可用", bytes(avail)),
            InfoItem("内部存储 已用", bytes(total - avail)),
            InfoItem("使用率", if (total > 0) String.format(Locale.US, "%.1f%%", (total - avail) * 100.0 / total) else "—")
        )
    }

    private fun screenItems(context: Context): List<InfoItem> {
        val dm = context.resources.displayMetrics
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val refresh = wm.defaultDisplay.refreshRate
        val w = dm.widthPixels
        val h = dm.heightPixels
        val inch = sqrt(((w / dm.xdpi) * (w / dm.xdpi) + (h / dm.ydpi) * (h / dm.ydpi)).toDouble())
        return listOf(
            InfoItem("分辨率", w.toString() + " × " + h),
            InfoItem("逻辑密度", dm.densityDpi.toString() + " dpi"),
            InfoItem("刷新率", String.format(Locale.US, "%.1f Hz", refresh)),
            InfoItem("屏幕尺寸", String.format(Locale.US, "%.2f 英寸", inch)),
            InfoItem("字体密度", dm.scaledDensity.toString())
        )
    }

    private fun batteryItems(context: Context): List<InfoItem> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val volt = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return listOf(
            InfoItem("当前电量", if (level >= 0) level.toString() + "%" else "—"),
            InfoItem("估算容量", if (counter > 0) (counter / 1000).toString() + " mAh" else "—"),
            InfoItem("电压", if (volt > 0) String.format(Locale.US, "%.2f V", volt / 1000.0) else "—"),
            InfoItem("温度", if (temp > 0) String.format(Locale.US, "%.1f ℃", temp / 10.0) else "—"),
            InfoItem("健康状态", healthText(health)),
            InfoItem("充电状态", statusText(status))
        )
    }

    private fun sensorItems(context: Context): List<InfoItem> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val all: List<Sensor> = sm.getSensorList(Sensor.TYPE_ALL)
        val items = mutableListOf(InfoItem("传感器数量", all.size.toString()))
        all.take(12).forEach { items += InfoItem("", it.name) }
        if (all.size > 12) items += InfoItem("更多", "另有 " + (all.size - 12) + " 个未列出")
        return items
    }

    private fun bytes(v: Long): String {
        val gb = v / 1073741824.0
        return if (gb >= 0.1) String.format(Locale.US, "%.2f GB", gb) else String.format(Locale.US, "%.0f MB", v / 1048576.0)
    }

    private fun healthText(h: Int): String = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
        BatteryManager.BATTERY_HEALTH_DEAD -> "已损坏"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
        BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "未知故障"
        else -> "未知"
    }

    private fun statusText(s: Int): String = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
        BatteryManager.BATTERY_STATUS_FULL -> "已充满"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
        else -> "未知"
    }

    private fun readCpuMaxFreq(): String = try {
        val text = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim()
        val khz = text.toLongOrNull()
        if (khz != null) String.format(Locale.US, "%.2f GHz", khz / 1000000.0) else "—"
    } catch (e: Exception) {
        "系统限制"
    }

    private fun getPropRaw(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
    private fun getProp(key: String): String = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() } ?: "—"
    } catch (e: Exception) {
        "—"
    }
}