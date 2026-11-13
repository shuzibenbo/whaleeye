package com.dafeiyu.whaleeye.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import com.dafeiyu.whaleeye.info.InfoGroup
import com.dafeiyu.whaleeye.info.InfoItem
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfoProvider {

    /** 任何一项读取失败都只降级成一行提示，绝不抛出异常导致闪退。 */
    private fun guard(block: () -> List<InfoItem>): List<InfoItem> = try {
        block()
    } catch (t: Throwable) {
        listOf(InfoItem("读取失败", t.javaClass.simpleName + (t.message?.let { ": " + it.take(40) } ?: "")))
    }

    fun collect(context: Context): List<InfoGroup> {
        val groups = mutableListOf<InfoGroup>()
        val cm = try {
            context.getSystemService(ConnectivityManager::class.java)
        } catch (t: Throwable) {
            null
        }
        if (cm == null) {
            groups += InfoGroup("当前网络", listOf(InfoItem("状态", "系统未提供网络服务")))
            return groups
        }
        val active = try { cm.activeNetwork } catch (t: Throwable) { null }
        val caps = try { active?.let { cm.getNetworkCapabilities(it) } } catch (t: Throwable) { null }
        val lp = try { active?.let { cm.getLinkProperties(it) } } catch (t: Throwable) { null }

        groups += InfoGroup("当前网络", guard { currentItems(caps, lp) })
        groups += InfoGroup("VPN 隧道", guard { vpnItems(context, cm) })
        groups += InfoGroup("物理网卡", guard { ifaceItems() })
        return groups
    }

    fun isActiveVpn(context: Context): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val net = cm?.activeNetwork
        val caps = if (cm != null && net != null) cm.getNetworkCapabilities(net) else null
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (t: Throwable) {
        false
    }

    private fun currentItems(caps: NetworkCapabilities?, lp: LinkProperties?): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        if (caps == null || lp == null) {
            items += InfoItem("状态", "无网络连接")
            return items
        }
        items += InfoItem("类型", transportText(caps))
        items += InfoItem("接口", lp.interfaceName ?: "—")
        items += InfoItem("可上网", if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "是" else "否")
        items += InfoItem("计费网络", if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "否（不计流量）" else "是")
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            items += InfoItem("联网验证", "已通过")
        }

        val addrs = try { lp.linkAddresses } catch (t: Throwable) { emptyList() }
        val v4 = addrs.mapNotNull { try { it.address } catch (t: Throwable) { null } }.firstOrNull { it is Inet4Address }
        items += InfoItem("本机 IP", v4?.hostAddress?.substringBefore("%") ?: "—")
        val prefix = addrs.firstOrNull { it.address is Inet4Address }?.prefixLength
        if (prefix != null && prefix in 0..32) items += InfoItem("子网掩码", "/" + prefix + "  " + prefixToMask(prefix))

        val routes = try { lp.routes } catch (t: Throwable) { emptyList() }
        val gw = routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        items += InfoItem("网关", gw ?: "—")
        val dns = try { lp.dnsServers } catch (t: Throwable) { emptyList() }
        val dnsText = dns.joinToString(", ") { it.hostAddress ?: "?" }
        items += InfoItem("DNS", dnsText.ifEmpty { "—" })

        val down = caps.linkDownstreamBandwidthKbps
        val up = caps.linkUpstreamBandwidthKbps
        if (down > 0 || up > 0) {
            items += InfoItem("链路估算", (down / 1000).toString() + " ↓ / " + (up / 1000) + " ↑ Mbps")
        }
        return items
    }

    private fun vpnItems(context: Context, cm: ConnectivityManager): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val vpnNets = try {
            cm.allNetworks.filter { n ->
                try {
                    cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                } catch (t: Throwable) {
                    false
                }
            }
        } catch (t: Throwable) {
            emptyList()
        }
        if (vpnNets.isEmpty()) {
            items += InfoItem("状态", "未启用")
            items += InfoItem("说明", "开启 VPN 后这里会显示隧道详情")
            return items
        }
        val net = vpnNets.first()
        val caps = try { cm.getNetworkCapabilities(net) } catch (t: Throwable) { null }
        val lp = try { cm.getLinkProperties(net) } catch (t: Throwable) { null }
        items += InfoItem("状态", "已启用")
        items += InfoItem("隧道接口", lp?.interfaceName ?: "—")

        val addrs = try { lp?.linkAddresses ?: emptyList() } catch (t: Throwable) { emptyList() }
        val v4 = addrs.mapNotNull { try { it.address } catch (t: Throwable) { null } }.firstOrNull { it is Inet4Address }
        items += InfoItem("隧道 IP", v4?.hostAddress?.substringBefore("%") ?: "—")

        val dnsText = try {
            lp?.dnsServers?.joinToString(", ") { it.hostAddress ?: "?" } ?: ""
        } catch (t: Throwable) {
            ""
        }
        items += InfoItem("隧道 DNS", dnsText.ifEmpty { "—" })

        val routes = try { lp?.routes ?: emptyList() } catch (t: Throwable) { emptyList() }
        val def = routes.firstOrNull { it.isDefaultRoute }
        items += InfoItem("流量走向", if (def != null) "全部走 VPN（全局）" else "分流模式（部分走 VPN）")
        if (def != null) items += InfoItem("VPN 网关", def.gateway?.hostAddress ?: "—")
        items += InfoItem("路由条数", routes.size.toString())

        val underlying = try {
            cm.allNetworks.firstOrNull { n ->
                val c = try { cm.getNetworkCapabilities(n) } catch (t: Throwable) { null }
                c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        } catch (t: Throwable) {
            null
        }
        val ucaps = try { underlying?.let { cm.getNetworkCapabilities(it) } } catch (t: Throwable) { null }
        items += InfoItem("底层网络", ucaps?.let { transportText(it) } ?: "—")

        val uid = try { caps?.ownerUid ?: -1 } catch (t: Throwable) { -1 }
        var pkgText = "系统未公开"
        if (uid > 0) {
            try {
                val pkgs = context.packageManager.getPackagesForUid(uid)
                if (!pkgs.isNullOrEmpty()) pkgText = pkgs.joinToString(", ")
            } catch (t: Throwable) {
                pkgText = "读取受限"
            }
        }
        items += InfoItem("VPN 应用", pkgText)
        return items
    }

    private fun ifaceItems(): List<InfoItem> {
        val out = mutableListOf<InfoItem>()
        val list = try {
            NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        } catch (t: Throwable) {
            emptyList()
        }
        for (nif in list) {
            try {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name ?: "unknown"
                val v4 = nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                if (v4.isEmpty()) continue
                out += InfoItem(name, v4.joinToString(", ") { it.hostAddress ?: "?" })
            } catch (t: Throwable) {
                continue
            }
        }
        if (out.isEmpty()) out += InfoItem("状态", "没有可用网卡")
        return out
    }

    private fun transportText(caps: NetworkCapabilities): String = try {
        val list = mutableListOf<String>()
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) list += "WiFi"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) list += "蜂窝"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) list += "以太网"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) list += "VPN"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) list += "蓝牙"
        if (list.isEmpty()) "未知" else list.joinToString(" + ")
    } catch (t: Throwable) {
        "未知"
    }

    private fun prefixToMask(p: Int): String {
        if (p <= 0) return "0.0.0.0"
        if (p >= 32) return "255.255.255.255"
        val m = (0xFFFFFFFFL shl (32 - p)) and 0xFFFFFFFFL
        return ((m shr 24) and 0xFFL).toString() + "." +
            ((m shr 16) and 0xFFL).toString() + "." +
            ((m shr 8) and 0xFFL).toString() + "." +
            (m and 0xFFL).toString()
    }
}