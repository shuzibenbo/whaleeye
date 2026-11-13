package com.dafeiyu.whaleeye

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dafeiyu.whaleeye.fps.FpsMonitor
import com.dafeiyu.whaleeye.fps.FpsOverlay
import com.dafeiyu.whaleeye.info.InfoItem
import java.util.Locale

private val FpsCard = Color(0xFF141A2C)
private val FpsText = Color(0xFFE6ECFA)
private val FpsDim = Color(0xFF7C87A0)
private val FpsAccent = Color(0xFF7CFFB2)

@Composable
fun FpsScreen() {
    val context = LocalContext.current
    var fps by remember { mutableFloatStateOf(0f) }
    var overlay by remember { mutableStateOf<FpsOverlay?>(null) }
    var overlayOn by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }

    val monitor = remember {
        FpsMonitor { f ->
            fps = f
            overlay?.update(f)
        }
    }

    DisposableEffect(Unit) {
        monitor.start()
        onDispose {
            monitor.stop()
            overlay?.hide()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permTick++
    }

    LaunchedEffect(permTick) {
        if (pendingOpen && Settings.canDrawOverlays(context)) {
            val ov = FpsOverlay(context)
            if (ov.show()) {
                overlay = ov
                overlayOn = true
            }
            pendingOpen = false
        }
    }

    fun toggleOverlay() {
        if (overlayOn) {
            overlay?.hide()
            overlay = null
            overlayOn = false
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            pendingOpen = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
            try {
                launcher.launch(intent)
            } catch (e: Exception) {
            }
            return
        }
        val ov = FpsOverlay(context)
        if (ov.show()) {
            overlay = ov
            overlayOn = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("P5 · 帧率") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FpsCard, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("本 App 当前渲染帧率", fontSize = 12.sp, color = FpsDim)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        String.format(Locale.US, "%.0f", fps),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = FpsAccent
                    )
                    Text(" FPS", fontSize = 16.sp, color = FpsDim, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    "由 Choreographer 逐帧统计，每 0.5 秒刷新一次",
                    fontSize = 11.sp,
                    color = FpsDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        item { GroupTitle("左上角悬浮显示") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FpsCard, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text(
                    if (overlayOn) "悬浮窗已开启，切到别的 App 也能看到" else "开启后会在屏幕左上角常驻一个小胶囊",
                    fontSize = 13.sp,
                    color = FpsText
                )
                Text(
                    "需要「显示在其他应用上层」权限；授权后回到这里会自动开启",
                    fontSize = 11.sp,
                    color = FpsDim,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = { toggleOverlay() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (overlayOn) Color(0xFF3A3F55) else FpsAccent
                    ),
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    Text(
                        if (overlayOn) "关闭悬浮窗" else "开启悬浮窗",
                        fontSize = 13.sp,
                        color = Color(0xFF12142A)
                    )
                }
            }
        }
        item { GroupTitle("说明") }
        item {
            InfoCard(
                listOf(
                    InfoItem("L1", "App 内帧率（当前实现，零权限）"),
                    InfoItem("L2", "系统悬浮窗（已实现，需授权）"),
                    InfoItem("L3", "整机真实帧率（需 Shizuku，M6.1）")
                )
            )
        }
    }
}