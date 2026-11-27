# 西瓜视频 TV 版（第三方）— Kotlin 工程

## 当前进度：第二步 · 单视频播放验证

基于第一步验证的解析链路（抖音移动 Feed 接口，免签名/免 Cookie），搭建 Android TV 工程并实现单视频播放。

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 语言 | Kotlin | 2.0.20 |
| 构建 | Gradle / AGP | 8.9 / 8.6.1 |
| UI | Compose + Material3 | BOM 2024.09.03 |
| 播放器 | Media3 ExoPlayer | 1.4.1 |
| 网络 | OkHttp + kotlinx-serialization | 4.12.0 / 1.7.3 |
| 图片 | Coil | 2.7.0 |
| SDK | compileSdk / minSdk | 35 / 24 |

> 说明：MVP 验证阶段先用标准 Compose + AndroidView(PlayerView)，TV 专用的 Compose for TV 组件（TvLazyColumn / tv-material）将在第三步做信息流时引入。核心解析与播放链路不受影响。

## 工程结构

```
app/src/main/java/com/xigua/tv/
├── MainActivity.kt              # 入口
├── data/
│   ├── VideoInfo.kt             # 统一视频模型（含清晰度/横竖屏判断）
│   └── FeedApi.kt               # Feed 接口封装（多端点容灾 + JSON 解析）
├── player/
│   └── PlayerViewModel.kt       # ExoPlayer 持有 + 播放控制 + UI 状态
├── ui/
│   ├── PlayerScreen.kt          # 全屏播放 + 遥控器按键 + 信息浮层
│   └── theme/Theme.kt
└── res/                         # Manifest / TV banner / launcher icon
```

## 遥控器操作（MVP）

| 按键 | 行为 |
|---|---|
| OK / 中心键 | Idle 时加载测试视频；播放时暂停/切换控制条 |
| 左 / 右 | 快退 / 快进 10 秒 |
| 上 / 下 | 预留给第三步视频流切换 |

## 编译

```bash
# 1. 修改 local.properties 里的 sdk.dir 为本机 Android SDK 路径
#    Windows 示例：sdk.dir=D:\\Android\\Sdk

# 2. 编译
./gradlew assembleDebug        # macOS/Linux
gradlew.bat assembleDebug      # Windows

# 产物
app/build/outputs/apk/debug/app-debug.apk
```

> 本次已在沙箱内实测编译通过（`BUILD SUCCESSFUL`），并验证了 APK 签名与 Leanback TV 声明。
> 工程使用腾讯云/阿里云 Maven 镜像加速依赖下载（`settings.gradle.kts`），国内网络可直接编译。

## 验证要点

1. 首屏显示「按 OK 播放测试视频」，测试 ID `7676450021063735414`
2. 按 OK 触发解析 → ExoPlayer 加载最高清晰度直链 → 自动播放
3. 播放中按 OK 暂停/恢复 + 显示标题/作者浮层
4. 左右键快进快退

## 与第一步脚本的对应关系

| Python 脚本 | Kotlin 实现 |
|---|---|
| `resolve_feed()` / `_feed_request()` | `FeedApi.resolveVideo()` / `requestFeed()` |
| `_parse_aweme()` | `FeedApi.parseAweme()` |
| `VideoInfo` 字典 | `VideoInfo` data class |
| `bestPlayUrl` 逻辑 | `VideoInfo.bestPlayUrl()` |

## 下一步（第三步）

- 引入 Compose for TV，实现上下键切换的沉浸式视频流
- 预加载下一视频（播放到 50% 时解析）
- Room 历史记录
