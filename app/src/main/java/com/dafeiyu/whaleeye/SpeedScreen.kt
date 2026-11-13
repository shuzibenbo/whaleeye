package com.dafeiyu.whaleeye

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dafeiyu.whaleeye.info.InfoGroup
import com.dafeiyu.whaleeye.info.InfoItem
import com.dafeiyu.whaleeye.speed.SpeedResult
import com.dafeiyu.whaleeye.speed.SpeedTestEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private val SpCard = Color(0xFF141A2C)
private val SpText = Color(0xFFE6ECFA)
private val SpDim = Color(0xFF7C87A0)
private val SpAccent = Color(0xFF4ADE80)

@Composable
fun SpeedScreen() {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("准备就绪") }
    var mbps by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<SpeedResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun start() {
        if (running) {
            job?.cancel()
            running = false
            phase = "已取消"
            return
        }
        running = true
        result = null
        mbps = 0f
        progress = 0f
        job = scope.launch {
            try {
                phase = "① 测量延迟…"
                val lat = SpeedTestEngine.measureLatency(8)
                progress = 0.15f

                phase = "② 下载测试（最多 10 MB / 20 秒）…"
                val downPair = SpeedTestEngine.download(10L * 1024 * 1024) { s, p ->
                    mbps = s.toFloat()
                    progress = 0.15f + p * 0.5f
                }

                phase = "③ 上传测试（最多 4 MB / 15 秒）…"
                val upPair = SpeedTestEngine.upload(4 * 1024 * 1024) { s, p ->
                    mbps = s.toFloat()
                    progress = 0.65f + p * 0.35f
                }

                result = SpeedResult(
                    lat, downPair.first, upPair.first,
                    10L * 1024 * 1024, 4L * 1024 * 1024,
                    downPair.second, upPair.second
                )
                mbps = downPair.first.toFloat()
                phase = "完成（大字为下载结果）"
                progress = 1f
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                phase = "失败：" + (e.message ?: e.javaClass.simpleName)
            } finally {
                running = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("P4 · 网络测速") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpCard, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(phase, fontSize = 12.sp, color = SpDim)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        String.format(Locale.US, "%.1f", mbps),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (running) SpAccent else SpText
                    )
                    Text(" Mbps", fontSize = 14.sp, color = SpDim, modifier = Modifier.padding(bottom = 10.dp))
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 12.dp),
                    color = SpAccent,
                    trackColor = Color(0x22FFFFFF)
                )
                Button(
                    onClick = { start() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) Color(0xFF3A3F55) else SpAccent
                    ),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(if (running) "停止" else "开始测速", fontSize = 14.sp, color = Color(0xFF12142A))
                }
                Text(
                    "一次最多消耗 14 MB 流量（下载 10 + 上传 4），慢网会在时限内提前结束",
                    fontSize = 11.sp,
                    color = SpDim,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        result?.let { r ->
            item { GroupTitle("本次结果") }
            item {
                val rows = mutableListOf(
                    InfoItem("下载", String.format(Locale.US, "%.1f Mbps", r.downMbps)),
                    InfoItem("上传", String.format(Locale.US, "%.1f Mbps", r.upMbps)),
                    InfoItem("延迟 平均", String.format(Locale.US, "%.0f ms", r.latency.avgMs)),
                    InfoItem("延迟 最低", String.format(Locale.US, "%.0f ms", r.latency.minMs)),
                    InfoItem("抖动", String.format(Locale.US, "%.1f ms", r.latency.jitterMs)),
                    InfoItem("失败次数", r.latency.failCount.toString())
                )
                if (r.downCut || r.upCut) {
                    rows += InfoItem("提示", "达到时间上限，按已传输量估算")
                }
                InfoCard(rows)
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "测速节点：Cloudflare（Anycast）。结果受当前 Wi-Fi/蜂窝、宿舍热点与 ISP 影响，仅供参考。",
                    fontSize = 11.sp,
                    color = SpDim
                )
            }
        }
    }
}