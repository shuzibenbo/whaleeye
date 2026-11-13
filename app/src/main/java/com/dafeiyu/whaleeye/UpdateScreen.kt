package com.dafeiyu.whaleeye

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dafeiyu.whaleeye.info.InfoGroup
import com.dafeiyu.whaleeye.info.InfoItem
import com.dafeiyu.whaleeye.update.UpdateChecker
import com.dafeiyu.whaleeye.update.UpdateInfo
import com.dafeiyu.whaleeye.update.UpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val REPO_URL = "https://github.com/GITHUB_USER/whaleeye"
private const val DEVELOPER_NAME = "GITHUB_USER"

private val UpCard = Color(0xFF141A2C)
private val UpText = Color(0xFFE6ECFA)
private val UpDim = Color(0xFF7C87A0)
private val UpAccent = Color(0xFF9B8CFF)

@Composable
fun UpdateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var message by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var apkFile by remember { mutableStateOf<File?>(null) }

    val pkg = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val currentCode = remember {
        if (pkg == null) 0 else if (Build.VERSION.SDK_INT >= 28) pkg.longVersionCode.toInt() else @Suppress("DEPRECATION") pkg.versionCode
    }
    val currentName = remember { pkg?.versionName ?: "?" }

    fun doCheck() {
        checking = true
        info = null
        message = ""
        apkFile = null
        scope.launch {
            val r = UpdateChecker.check(url.trim())
            checking = false
            if (r == null) {
                message = "检查失败：地址打不开或格式不对"
                return@launch
            }
            if (r.versionCode <= currentCode) {
                message = "已是最新版本（远端 " + r.versionName + "）"
            } else {
                info = r
                message = "发现新版本 " + r.versionName
            }
        }
    }

    fun doDownload() {
        val target = info ?: return
        if (target.apkUrl.isBlank()) {
            message = "清单里没有 apkUrl"
            return
        }
        downloading = true
        progress = 0f
        message = "下载中…"
        scope.launch {
            val dir = File(context.cacheDir, "updates")
            dir.mkdirs()
            val dest = File(dir, "whaleeye-" + target.versionName + ".apk")
            if (dest.exists()) dest.delete()
            val ok = UpdateChecker.download(target.apkUrl, dest) { p -> progress = p }
            if (!ok) {
                downloading = false
                message = "下载失败"
                return@launch
            }
            val digest = withContext(Dispatchers.IO) { UpdateChecker.sha256(dest) }
            downloading = false
            if (target.sha256.isNotBlank() && digest != target.sha256) {
                dest.delete()
                message = "SHA-256 校验不通过，已删除文件"
                return@launch
            }
            apkFile = dest
            message = "下载完成并通过校验"
        }
    }

    fun doInstall() {
        val f = apkFile ?: return
        if (!UpdateInstaller.canInstall(context)) {
            message = "需要先允许「安装未知应用」，已跳转设置"
            UpdateInstaller.openInstallSettings(context)
            return
        }
        if (!UpdateInstaller.install(context, f)) {
            message = "拉起安装器失败"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("P6 · 设置与更新") }
        item {
            InfoCard(
                listOf(
                    InfoItem("当前版本", currentName),
                    InfoItem("versionCode", currentCode.toString())
                )
            )
        }
        item { GroupTitle("更新源") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UpCard, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("update.json 地址", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { if (!checking) doCheck() },
                    colors = ButtonDefaults.buttonColors(containerColor = UpAccent),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(if (checking) "检查中…" else "检查更新", fontSize = 13.sp, color = Color(0xFF12142A))
                }
            }
        }
        if (message.isNotBlank()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UpCard, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(message, fontSize = 13.sp, color = UpText)
                }
            }
        }
        info?.let { it ->
            item { GroupTitle("新版本详情") }
            item {
                InfoCard(
                    listOf(
                        InfoItem("版本", it.versionName),
                        InfoItem("versionCode", it.versionCode.toString()),
                        InfoItem("最低要求", if (it.minSdk > 0) "SDK " + it.minSdk else "未声明"),
                        InfoItem("更新说明", it.changelog.ifBlank { "—" })
                    )
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UpCard, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = UpAccent
                        )
                        Text(
                            (progress * 100).toInt().toString() + "%",
                            fontSize = 11.sp,
                            color = UpDim,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (apkFile == null) {
                        Button(
                            onClick = { doDownload() },
                            colors = ButtonDefaults.buttonColors(containerColor = UpAccent)
                        ) {
                            Text("下载 APK", fontSize = 13.sp, color = Color(0xFF12142A))
                        }
                    } else {
                        Button(
                            onClick = { doInstall() },
                            colors = ButtonDefaults.buttonColors(containerColor = UpAccent)
                        ) {
                            Text("安装更新", fontSize = 13.sp, color = Color(0xFF12142A))
                        }
                        Text(
                            apkFile!!.absolutePath,
                            fontSize = 10.sp,
                            color = UpDim,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        item { GroupTitle("关于") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UpCard, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text("鲸眼 WhaleEye", fontSize = 17.sp, color = UpText)
                Text(
                    "一眼看穿你的手机与网络。设备信息、网络与 VPN、WiFi 全景、测速、帧率，一个 App 全看完。",
                    fontSize = 12.sp,
                    color = UpDim,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "开发者：" + DEVELOPER_NAME + "  ·  开源协议：Apache-2.0",
                    fontSize = 12.sp,
                    color = UpDim,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    REPO_URL,
                    fontSize = 12.sp,
                    color = UpAccent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        item { GroupTitle("声明") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UpCard, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Text("AI 生成声明", fontSize = 12.sp, color = UpText)
                Text(
                    "本项目包含大量由大语言模型生成的代码初稿，全部内容经过人工审核与重构；无法保证 AI 输出不存在第三方版权片段，使用者请自行评估风险。",
                    fontSize = 11.sp,
                    color = UpDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text("隐私说明", fontSize = 12.sp, color = UpText, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "本应用不收集、不上传任何设备信息。仅在查询公网 IP 时访问第三方接口（myip.ipip.net、ident.me），测速时访问 Cloudflare，检查更新时访问你填写的更新源。除此之外没有其他网络行为。",
                    fontSize = 11.sp,
                    color = UpDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text("免责声明", fontSize = 12.sp, color = UpText, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "本软件按「现状」提供，不提供任何明示或暗示的担保，不保证无漏洞，不对使用过程中产生的任何直接或间接损失承担责任。",
                    fontSize = 11.sp,
                    color = UpDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}