package com.dafeiyu.whaleeye

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dafeiyu.whaleeye.info.DeviceInfoProvider
import com.dafeiyu.whaleeye.info.InfoGroup
import com.dafeiyu.whaleeye.info.InfoItem
import com.dafeiyu.whaleeye.net.NetworkInfoProvider
import com.dafeiyu.whaleeye.net.PublicIpFetcher
import com.dafeiyu.whaleeye.net.PublicIpInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

private val BgColor = Color(0xFF0B1020)
private val CardColor = Color(0xFF141A2C)
private val TextMain = Color(0xFFE6ECFA)
private val TextDim = Color(0xFF7C87A0)
private val TextDim2 = Color(0xFF6E7C99)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { WhaleEyeApp() }
    }
}

@Composable
fun WhaleEyeApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(containerColor = BgColor) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                TabRow(selectedTabIndex = tab, containerColor = BgColor) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(Lang.s("设备", "Device"), fontSize = 14.sp) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(Lang.s("网络", "Network"), fontSize = 14.sp) }
                    )
                    Tab(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        text = { Text("WiFi", fontSize = 14.sp) }
                    )
                    Tab(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        text = { Text(Lang.s("测速", "Speed"), fontSize = 14.sp) }
                    )
                    Tab(
                        selected = tab == 4,
                        onClick = { tab = 4 },
                        text = { Text(Lang.s("帧率", "FPS"), fontSize = 13.sp) }
                    )
                    Tab(
                        selected = tab == 5,
                        onClick = { tab = 5 },
                        text = { Text(Lang.s("设置", "Settings"), fontSize = 13.sp) }
                    )
                }
                when (tab) {
                    0 -> DeviceScreen()
                    1 -> NetworkScreen()
                    2 -> WifiScreen()
                    3 -> SpeedScreen()
                    4 -> FpsScreen()
                    else -> UpdateScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(DeviceInfoProvider.collect(context)) }
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                groups = DeviceInfoProvider.collect(context)
                delay(400)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        InfoList(groups, Lang.s("P1 · 设备信息", "P1 · Device"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(NetworkInfoProvider.collect(context)) }
    var info by remember { mutableStateOf<PublicIpInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    val lastAt = remember { AtomicLong(0L) }
    val lastFp = remember { arrayOf("") }

    fun reloadNetwork() {
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { NetworkInfoProvider.collect(context) }
            val fp = fresh.joinToString("|") { g -> g.title + g.items.joinToString(",") { it.value } }
            if (fp != lastFp[0]) {
                lastFp[0] = fp
                groups = fresh
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        var registered = false
        val cb = object : ConnectivityManager.NetworkCallback() {
            private fun onNetChange() {
                val now = System.currentTimeMillis()
                if (now - lastAt.get() < 800) return
                lastAt.set(now)
                reloadNetwork()
            }
            override fun onAvailable(network: Network) { onNetChange() }
            override fun onLost(network: Network) { onNetChange() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { onNetChange() }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!registered) {
                        try {
                            cm?.registerDefaultNetworkCallback(cb, handler)
                            registered = true
                        } catch (e: Exception) {
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (registered) {
                        try {
                            cm?.unregisterNetworkCallback(cb)
                            registered = false
                        } catch (e: Exception) {
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (registered) {
                try {
                    cm?.unregisterNetworkCallback(cb)
                } catch (e: Exception) {
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        info = PublicIpFetcher.fetch()
        loading = false
    }

    val viaVpn = NetworkInfoProvider.isActiveVpn(context)
    val extra = listOf(
        InfoGroup(
            "公网出口", listOf(
                InfoItem("公网 IPv4", ipShown(loading, info?.ipv4, "未获取到")),
                InfoItem("公网 IPv6", ipShown(loading, info?.ipv6, "未获取到")),
                InfoItem("归属地", ipShown(loading, info?.location, "—")),
                InfoItem("查询源", if (loading) "查询中…" else (info?.source ?: info?.error ?: "—")),
                InfoItem("查询路径", if (viaVpn) "经 VPN 出口" else "直连（本应用未走 VPN）")
            )
        )
    )

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                groups = NetworkInfoProvider.collect(context)
                info = PublicIpFetcher.fetch()
                loading = false
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        InfoList(groups + extra, Lang.s("P2 · 网络与 IP", "P2 · Network & IP"))
    }
}

private fun ipShown(loading: Boolean, value: String?, empty: String): String =
    if (loading) "查询中…" else (value ?: empty)

@Composable
fun InfoList(groups: List<InfoGroup>, subtitle: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader(subtitle) }
        groups.forEach { g ->
            item(key = "t_" + g.title) { GroupTitle(g.title) }
            item(key = "c_" + g.title) { InfoCard(g.items) }
        }
    }
}

@Composable
fun ScreenHeader(subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, bottom = 6.dp)) {
        Text("鲸眼 WhaleEye", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2F5FF))
        Text("一眼看穿你的手机与网络", fontSize = 13.sp, color = TextDim, modifier = Modifier.padding(top = 5.dp))
        Text(subtitle, fontSize = 11.sp, color = Color(0xFF4E5A73), modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
fun GroupTitle(t: String) {
    Text(
        text = t,
        fontSize = 12.sp,
        color = TextDim2,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 1.dp)
    )
}

@Composable
fun InfoCard(items: List<InfoItem>) {
    Column(
        modifier = Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(16.dp))
    ) {
        items.forEachIndexed { i, item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.label.isNotEmpty()) {
                    Text(
                        text = Lang.label(item.label),
                        fontSize = 12.sp,
                        color = TextDim,
                        modifier = Modifier.width(94.dp)
                    )
                }
                Text(
                    text = item.value,
                    fontSize = 14.sp,
                    color = TextMain,
                    modifier = Modifier.weight(1f)
                )
            }
            if (i != items.lastIndex) {
                HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.7.dp)
            }
        }
    }
}