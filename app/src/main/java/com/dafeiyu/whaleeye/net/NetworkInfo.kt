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

    fun collect(context: Context): List<InfoGroup> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val lp = active?.let { cm.getLinkProperties(it) }
        val groups = mutableListOf<InfoGroup>()
        groups += InfoGroup("当前网络", currentItems(caps, lp))
        groups += InfoGroup("VPN 隧道", vpnItems(context, cm))
        groups += InfoGroup("物理网卡", ifaceItems())
        return groups
    }

    fun isActiveVpn(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
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
        val v4 = lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address }
        items += InfoItem("本机 IP", v4?.hostAddress?.substringBefore("%") ?: "—")
        val prefix = lp.linkAddresses.firstOrNull { it.address is Inet4Address }?.prefixLength
        if (prefix != null) items += InfoItem("子网掩码", "/" + prefix + "  " + prefixToMask(prefix))
        val gw = lp.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        items += InfoItem("网关", gw ?: "—")
        val dns = lp.dnsServers.joinToString(", ") { it.hostAddress ?: "?" }
        items += InfoItem("DNS", dns.ifEmpty { "—" })
        val down = caps.linkDownstreamBandwidthKbps
        val up = caps.linkUpstreamBandwidthKbps
        if (down > 0 || up > 0) {
            items += InfoItem("链路估算", (down / 1000).toString() + " ↓ / " + (up / 1000) + " ↑ Mbps")
        }
        return items
    }

    private fun vpnItems(context: Context, cm: ConnectivityManager): List<InfoItem> {
        val items = mutableListOf<InfoItem>()
        val vpnNets = cm.allNetworks.filter { n ->
            val c = cm.getNetworkCapabilities(n)
            c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        if (vpnNets.isEmpty()) {
            items += InfoItem("状态", "未启用")
            items += InfoItem("说明", "开启 VPN 后这里会显示隧道详情")
            return items
        }
        val net = vpnNets.first()
        val caps = cm.getNetworkCapabilities(net)
        val lp = cm.getLinkProperties(net)
        items += InfoItem("状态", "已启用")
        items += InfoItem("隧道接口", lp?.interfaceName ?: "—")
        val v4 = lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }
        items += InfoItem("隧道 IP", v4?.hostAddress?.substringBefore("%") ?: "—")
        val dns = lp?.dnsServers?.joinToString(", ") { it.hostAddress ?: "?" }
        items += InfoItem("隧道 DNS", if (dns.isNullOrEmpty()) "—" else dns)
        val routes = lp?.routes ?: emptyList()
        val def = routes.firstOrNull { it.isDefaultRoute }
        items += InfoItem("流量走向", if (def != null) "全部走 VPN（全局）" else "分流模式（部分走 VPN）")
        if (def != null) items += InfoItem("VPN 网关", def.gateway?.hostAddress ?: "—")
        items += InfoItem("路由条数", routes.size.toString())
        val underlying = cm.allNetworks.firstOrNull { n ->
            val c = cm.getNetworkCapabilities(n)
            c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        val ucaps = underlying?.let { cm.getNetworkCapabilities(it) }
        items += InfoItem("底层网络", ucaps?.let { transportText(it) } ?: "—")
        val uid = try { caps?.ownerUid ?: -1 } catch (e: Exception) { -1 }
        val pkgs = if (uid > 0) context.packageManager.getPackagesForUid(uid) else null
        items += InfoItem("VPN 应用", pkgs?.joinToString(", ") ?: "系统未公开")
        return items
    }

    private fun ifaceItems(): List<InfoItem> {
        val out = mutableListOf<InfoItem>()
        try {
            val list = NetworkInterface.getNetworkInterfaces()?.toList() ?: return out
            for (nif in list) {
                if (!nif.isUp || nif.isLoopback) continue
                val v4 = nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                if (v4.isEmpty()) continue
                out += InfoItem(nif.name, v4.joinToString(", ") { it.hostAddress ?: "?" })
            }
        } catch (e: Exception) {
            out += InfoItem("读取失败", e.javaClass.simpleName)
        }
        if (out.isEmpty()) out += InfoItem("状态", "没有可用网卡")
        return out
    }

    private fun transportText(caps: NetworkCapabilities): String {
        val list = mutableListOf<String>()
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) list += "WiFi"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) list += "蜂窝"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) list += "以太网"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) list += "VPN"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) list += "蓝牙"
        return if (list.isEmpty()) "未知" else list.joinToString(" + ")
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