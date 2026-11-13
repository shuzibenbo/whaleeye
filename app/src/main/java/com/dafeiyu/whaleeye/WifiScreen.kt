package com.dafeiyu.whaleeye

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dafeiyu.whaleeye.info.InfoGroup
import com.dafeiyu.whaleeye.wifi.WifiAp
import com.dafeiyu.whaleeye.wifi.WifiInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

private val WifiCard = Color(0xFF141A2C)
private val WifiText = Color(0xFFE6ECFA)
private val WifiDim = Color(0xFF7C87A0)
private val WifiDim2 = Color(0xFF6E7C99)
private val WifiAccent = Color(0xFF9B8CFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(WifiInfoProvider.hasPermission(context)) }
    var groups by remember { mutableStateOf(listOf<InfoGroup>()) }
    var aps by remember { mutableStateOf(listOf<WifiAp>()) }
    var refreshing by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val lastAt = remember { AtomicLong(0L) }

    fun reload() {
        scope.launch {
            val g = withContext(Dispatchers.IO) { WifiInfoProvider.collectCurrent(context) }
            val a = withContext(Dispatchers.IO) { WifiInfoProvider.collectScan(context) }
            groups = g
            aps = a
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = WifiInfoProvider.hasPermission(context)
        reloadKey++
    }

    LaunchedEffect(reloadKey, granted) {
        if (granted) reload()
    }

    DisposableEffect(granted) {
        if (!granted) {
            onDispose { }
        } else {
            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val now = System.currentTimeMillis()
                    if (now - lastAt.get() < 800) return
                    lastAt.set(now)
                    reload()
                }
            }
            try {
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            } catch (e: Exception) {
            }
            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                if (granted) {
                    WifiInfoProvider.triggerScan(context)
                    delay(1200)
                    val g = withContext(Dispatchers.IO) { WifiInfoProvider.collectCurrent(context) }
                    val a = withContext(Dispatchers.IO) { WifiInfoProvider.collectScan(context) }
                    groups = g
                    aps = a
                } else {
                    launcher.launch(WifiInfoProvider.requiredPermissions())
                }
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { ScreenHeader(Lang.s("P3 · WiFi 全景", "P3 · WiFi Panorama")) }
            if (!granted) {
                item { PermissionCard { launcher.launch(WifiInfoProvider.requiredPermissions()) } }
            } else {
                groups.forEach { g ->
                    item(key = "t_" + g.title) { GroupTitle(g.title) }
                    item(key = "c_" + g.title) { InfoCard(g.items) }
                }
                item(key = "t_scan") {
                    GroupTitle(Lang.s("附近网络（" + aps.size + " 个，按信号排序）", "Nearby (" + aps.size + ", by signal)"))
                }
                if (aps.isEmpty()) {
                    item(key = "empty") {
                        EmptyHint(Lang.s("暂未扫描到网络，下拉刷新试试（Android 限制每 2 分钟最多扫 4 次）", "No networks yet. Pull to refresh (Android limits 4 scans / 2 min)."))
                    }
                } else {
                    items(aps.size) { idx ->
                        ApRow(aps[idx])
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WifiCard, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(Lang.s("需要 WiFi 权限", "WiFi permission needed"), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WifiText)
        Text(
            "要读取频段、信道、周边热点，需要「附近的设备」和「位置信息」权限。数据只在本机显示，不会上传。",
            fontSize = 12.sp,
            color = WifiDim,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
        )
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = WifiAccent)
        ) {
            Text(Lang.s("授予权限", "Grant"), fontSize = 13.sp, color = Color(0xFF12142A))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WifiCard, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(text, fontSize = 12.sp, color = WifiDim)
    }
}

@Composable
private fun ApRow(ap: WifiAp) {
    val bars = WifiInfoProvider.rssiBars(ap.rssi)
    val barText = "▮".repeat(bars) + "▯".repeat(4 - bars)
    val barColor = when {
        bars >= 4 -> Color(0xFF4ADE80)
        bars == 3 -> Color(0xFFA3E635)
        bars == 2 -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WifiCard, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(barText, fontSize = 11.sp, color = barColor, modifier = Modifier.width(46.dp))
            Text(
                ap.ssid,
                fontSize = 14.sp,
                color = WifiText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(ap.rssi.toString() + " dBm", fontSize = 11.sp, color = WifiDim)
        }
        Text(
            ap.band + " · 信道 " + ap.channel +
                (if (ap.widthMhz > 0) " · " + ap.widthMhz + "MHz" else "") +
                " · " + ap.standard + " · " + ap.security,
            fontSize = 11.sp,
            color = WifiDim2,
            modifier = Modifier.padding(start = 46.dp, top = 4.dp)
        )
    }
}