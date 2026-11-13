# 鲸眼 WhaleEye

**一眼看穿你的手机与网络。**

[English](#english) · 简体中文

---

## 这是什么

一个原生 Android 工具应用，把「设备信息 / 网络与 VPN / WiFi 全景 / 网速测试 / 帧率」集中在一屏。
单机、离线优先、**无账号、无广告、无埋点、不上传任何设备信息**。

## 功能

| 页面 | 内容 |
|---|---|
| P1 设备 | 型号、SoC、ABI、内存、存储、屏幕刷新率、电池、88 个传感器 |
| P2 网络 | 内网 IP / 掩码 / 网关 / DNS、公网 IPv4 与 IPv6、归属地、**VPN 隧道详情（接口/IP/DNS/分流判断）** |
| P3 WiFi | 当前连接全字段（频段/信道/带宽/标准/加密/速率）+ 周边热点列表 |
| P4 测速 | 下载 / 上传 / 延迟 / 抖动，带时限保护，慢网也能出结果 |
| P5 帧率 | App 内实时帧率 + 系统悬浮窗（左上角常驻） |
| P6 设置 | 检查更新、关于、声明 |

## 下载与安装

到 [Releases](../../releases) 下载最新 `whaleeye-*-arm64.apk`。

**安装前请核对 SHA-256**（Release 说明里有），确认包没被篡改。

**系统要求**：Android 10（API 29）及以上，arm64-v8a 设备。

### 国内 ROM 会拦一次，属正常

> HyperOS / MIUI / ColorOS 等会提示「**未查询到此应用的 ICP 备案信息**」。
> 这是国内 ROM 对**所有未在应用商店备案的 App** 的统一拦截，与代码质量和安全性无关。
> 点「继续安装」即可。所有侧载的开源应用都会遇到。

## 权限说明

| 权限 | 用途 | 拒绝后会怎样 |
|---|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | 查询公网 IP、测速、检查更新 | 网络页和测速不可用 |
| `NEARBY_WIFI_DEVICES` + `ACCESS_FINE_LOCATION` | 读取 WiFi 频段、信道、周边热点 | WiFi 页显示引导卡片 |
| `SYSTEM_ALERT_WINDOW` | 帧率悬浮窗 | 只能用 App 内帧率 |
| `REQUEST_INSTALL_PACKAGES` | 应用内更新 | 需手动去浏览器下载 |

**位置权限只用于读取 WiFi 信息**（Android 系统强制要求），不采集、不上传任何位置数据。

## 隐私

本应用**不收集、不上传任何设备信息**。网络行为仅限三处：

1. 查询公网 IP：`myip.ipip.net`、`v4.ident.me`、`v6.ident.me`
2. 测速：`speed.cloudflare.com`
3. 检查更新：你自己填写的 `update.json` 地址

除此之外没有任何网络请求，没有统计 SDK，没有崩溃上报。

## 从源码构建

需要：JDK 21、Android SDK（compileSdk 37）、Gradle 9.9.1

```bash
git clone https://github.com/shuzibenbo/whaleeye.git
cd whaleeye
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
```

**打 Release 包**需要自己的签名密钥（`keystore.properties` 已在 `.gitignore` 中，不会进仓库）：

```properties
storeFile=/absolute/path/to/your.jks
storePassword=******
keyAlias=whaleeye
keyPassword=******
```

## 许可证

[Apache License 2.0](LICENSE)

## AI 生成声明

本项目包含大量由大语言模型生成的代码初稿，全部内容经过人工审核与重构；
无法保证 AI 输出不存在第三方版权片段，使用者请自行评估风险。

## 免责声明

本软件按「现状」提供，不提供任何明示或暗示的担保，不保证无漏洞，
不对使用过程中产生的任何直接或间接损失承担责任。

## 作者

- GitHub：[@shuzibenbo](https://github.com/shuzibenbo)

---

<a id="english"></a>

# WhaleEye (English)

**See through your phone and network at a glance.**

A native Android toolbox that puts device info, network & VPN status, full WiFi panorama,
speed test and an FPS overlay in one place. Offline-first, no account, no ads, no telemetry.

## Features

| Page | What you get |
|---|---|
| P1 Device | Model, SoC, ABI, RAM, storage, refresh rate, battery, sensors |
| P2 Network | Private IP / gateway / DNS, public IPv4 & IPv6, geo, **VPN tunnel details** |
| P3 WiFi | Current connection (band / channel / width / standard / security) + nearby APs |
| P4 Speed | Download / upload / latency / jitter, with hard time limits |
| P5 FPS | In-app frame rate + system overlay |
| P6 Settings | Update check, about, notices |

## Requirements

Android 10 (API 29)+, arm64-v8a.

> Chinese OEM ROMs (HyperOS / MIUI / ColorOS) will warn about missing ICP filing —
> that is a system-level check for all sideloaded apps, choose "Continue installation".

## Privacy

No data is collected or uploaded. Network calls are limited to public-IP lookups
(`myip.ipip.net`, `v4.ident.me`, `v6.ident.me`), speed test (`speed.cloudflare.com`)
and the update manifest URL you configure.

## License

[Apache License 2.0](LICENSE)

## AI disclosure

Substantial portions of the code were drafted by large language models and then
reviewed and refactored by a human. No guarantee that AI output contains no
third-party code fragments; evaluate the risk yourself.

## Disclaimer

Provided "AS IS", without warranty of any kind. No guarantee of being bug-free.
The author is not liable for any damages arising from its use.
