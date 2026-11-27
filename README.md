# 西瓜视频 TV 版（第三方）— Kotlin 工程

## 当前进度：第五步 · 我的页（观看历史 + 收藏）（已完成）

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
│   ├── FeedApi.kt               # Feed 接口封装（多端点容灾 + JSON 解析）
│   ├── FeedRepository.kt        # 分页加载 + 去重累积
│   └── history/                 # Room 观看历史 + 收藏
│       ├── WatchHistory.kt      # 实体：进度/时长/观看时间/isFavorite
│       ├── WatchHistoryDao.kt   # DAO：保存/查询/续播/收藏/删除
│       └── AppDatabase.kt       # 数据库单例（v2）
├── player/
│   ├── PlayerViewModel.kt       # 单视频播放（第二步，保留作参考）
│   ├── StreamViewModel.kt       # 视频流：预加载 + 无限流 + 进度记忆 + 收藏
│   └── MyViewModel.kt           # 我的页：历史/收藏列表 + Tab + 删除（第五步）
├── ui/
│   ├── PlayerScreen.kt          # 单视频界面（第二步，保留作参考）
│   ├── StreamScreen.kt          # 沉浸式流：VerticalPager + 封面占位 + 续播提示
│   ├── MyScreen.kt              # 我的页：三列焦点网格 + 进度条（第五步）
│   └── theme/Theme.kt
└── res/                         # Manifest / TV banner / launcher icon
```

## 遥控器操作（第三步沉浸式流）

| 按键 | 行为 |
|---|---|
| 上 / 下 | VerticalPager 切换视频，自动播放 |
| OK / 中心键 | 暂停/播放 + 显示/隐藏信息浮层；错误态重试 |
| 左 / 右 | 快退 / 快进 10 秒 |
| 菜单键 (MENU) | 收藏/取消收藏当前视频（居中 Toast 提示） |
| GUIDE / F 键 | 进入「我的」页 |

启动后自动加载推荐流并播放第一个视频；滑到倒数第 2 个时自动加载下一批（无限流）。

**「我的」页遥控器：**

| 按键 | 行为 |
|---|---|
| 上 / 下 | 列表项间移动（三列网格，±3） |
| 左 / 右 | 切换 Tab（观看历史 / 我的收藏） |
| OK / 中心键 | 播放选中视频（按 ID 重新解析直链后播放） |
| 删除键 (DEL) | 删除选中记录 |
| 返回键 | 回到视频流 |

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

## 验证要点（第四步）

1. 启动后自动加载推荐流 → 第一个视频全屏自动播放
2. 按遥控器下键 → 切到下一个视频（已预缓冲，零延迟）
3. 按 OK → 暂停并显示标题/作者/清晰度浮层；再按 OK 恢复
4. 左右键快进快退 10 秒
5. 连续下滑到倒数第 2 个 → 自动追加下一批（无限流）
6. **上键切回看过的视频 → 从上次进度续播，顶部提示「继续上次观看 X 秒」**
7. 退出 App 再进入 → 进度已存 Room，切到该视频仍能续播

## 与第一步脚本的对应关系

| Python 脚本 | Kotlin 实现 |
|---|---|
| `resolve_feed()` / `_feed_request()` | `FeedApi.resolveVideo()` / `requestFeed()` |
| `get_feed()` | `FeedApi.getFeed()` + `FeedRepository.loadMore()` |
| `_parse_aweme()` | `FeedApi.parseAweme()` |
| `VideoInfo` 字典 | `VideoInfo` data class |
| `bestPlayUrl` 逻辑 | `VideoInfo.bestPlayUrl()` |

## 关于 Compose for TV 的说明

第三步的沉浸式全屏流场景**未引入 tv-material**：
- 全屏单视频流用不到它的卡片/导航组件
- tv-material 1.1.0 依赖 Compose 1.10.3，与当前 BOM（1.7.x）冲突
- 用 foundation 1.7.3 的 `VerticalPager` + `onKeyEvent` 实现，已编译验证

tv-material 留到后续做频道导航/信息流卡片时再引入（届时统一升级 BOM）。

## 预加载与进度记忆实现（第四步）

- **滑动窗口队列**：播放器队列始终 `[当前, 下一个]`，ExoPlayer 自动预缓冲下一个
- **下滑零延迟**：`seekToNext()` 播放已缓冲内容，随后 `removeMediaItem(0)` + `addMediaItem()` 补窗口
- **上滑/跳跃**：重建队列，按 Room 历史位置续播
- **进度保存**：每 3 秒 + 切换前 + 暂停时 + onCleared 时保存
- **看完判定**：进度 ≥95% 视为看完，下次从 0 播

## 我的页实现（第五步）

- **数据源**：复用 Room 的 `watch_history` 表，`isFavorite=true` 即收藏，无需新建表
- **收藏持久化**：进度保存时读取并保留 `isFavorite`，避免 REPLACE 覆盖收藏标记（这是关键 bug 点）
- **从列表播放**：历史/收藏项只存 `awemeId`（直链有时效不落库），点播时调 `FeedApi.resolveVideo(id)` 重新解析 → `prepend` 到流首位 → 播放
- **数据库迁移**：版本 v1→v2，`fallbackToDestructiveMigration()`（全新安装策略，符合项目习惯）
- **导航**：MainActivity 用状态切换 Stream / My 两个 Composable，My 页接 `BackHandler`

## 下一步候选

- 频道分类导航（引入 tv-material）
- 搜索
- 点赞 / 作者主页
- 清晰度手动选择设置
