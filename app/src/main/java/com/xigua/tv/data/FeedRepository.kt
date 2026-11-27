package com.xigua.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频流数据仓库。
 *
 * 职责：
 *  1. 分页拉取推荐流，按 awemeId 去重累积（Feed 接口每次返回有重复）
 *  2. 维护已加载视频列表，供沉浸式流上下切换
 *  3. 当Buffer快见底时加载下一批
 *  4. TV 场景优先横屏视频（可开关）：多轮拉取补足，避免过滤后列表为空
 *
 * 播放地址已在 VideoInfo 中（Feed 接口一次性返回），无需二次解析，
 * 因此"预加载"在 ViewModel 层通过提前 prepare MediaItem 实现。
 */
class FeedRepository(private val api: FeedApi = FeedApi()) {

    private val videos = mutableListOf<VideoInfo>()
    private val seenIds = mutableSetOf<String>()
    private var isLoading = false

    /**
     * 是否仅保留横屏视频（TV 大屏优化）。
     * 注意：实测推荐流竖屏占绝大多数（横屏稀少且时段性出现），强制仅横屏会拉长加载时间，
     * 因此默认关闭；用户在「我的→设置」手动开启后会优先横屏、拉不到才用竖屏兜底。
     */
    var landscapeOnly: Boolean = false

    /** 当前已加载的视频列表（只读快照） */
    fun currentList(): List<VideoInfo> = videos.toList()

    /** 是否正在加载 */
    fun isLoading(): Boolean = isLoading

    /**
     * 加载更多视频（追加到列表尾部）。
     * @param targetCount 期望本次新增数量
     * 开启 landscapeOnly 时，单批过滤后横屏可能不足，会在同一锁内多拉几轮补足；
     * 竖屏候选暂存，仅在反复拉不到横屏时兜底加入，保证不会把流拉空。
     * @return 本次新增的视频数量
     */
    suspend fun loadMore(targetCount: Int = 5): Result<Int> = withContext(Dispatchers.IO) {
        if (isLoading) return@withContext Result.success(0)
        isLoading = true
        try {
            var added = 0
            val portraitBackup = mutableListOf<VideoInfo>()
            // 多拉一些，因为 Feed 接口有重复 + 竖屏要过滤
            val perFetch = (targetCount * 3).coerceAtLeast(12)
            val maxRounds = 4 // 最多拉取轮数，避免无限请求

            var round = 0
            while (added < targetCount && round < maxRounds) {
                round++
                val result = api.getFeed(count = perFetch)
                val batch = result.getOrNull() ?: continue
                for (video in batch) {
                    if (video.awemeId.isBlank() || !seenIds.add(video.awemeId)) continue
                    if (!landscapeOnly || video.isLandscape) {
                        videos.add(video)
                        added++
                    } else {
                        portraitBackup.add(video)
                    }
                }
            }

            // 兜底：反复拉取后横屏仍不足，用竖屏补足，避免流为空
            if (added < targetCount && portraitBackup.isNotEmpty()) {
                for (video in portraitBackup) {
                    if (added >= targetCount) break
                    videos.add(video)
                    added++
                }
            }
            Result.success(added)
        } finally {
            isLoading = false
        }
    }

    /** 首次加载：清空后拉一批 */
    suspend fun loadInitial(): Result<Int> = withContext(Dispatchers.IO) {
        videos.clear()
        seenIds.clear()
        loadMore(8)
    }

    /** 按 ID 查找已加载的视频 */
    fun findById(id: String): VideoInfo? = videos.firstOrNull { it.awemeId == id }

    /** 按 ID 从网络解析单个视频（历史/收藏页播放用） */
    suspend fun resolveById(id: String): Result<VideoInfo> = api.resolveVideo(id)

    /** 把指定视频放到流首位（已存在则不动，避免重复） */
    fun prepend(video: VideoInfo) {
        if (seenIds.contains(video.awemeId)) {
            // 已存在：移到首位
            val idx = videos.indexOfFirst { it.awemeId == video.awemeId }
            if (idx > 0) {
                val item = videos.removeAt(idx)
                videos.add(0, item)
            }
        } else {
            seenIds.add(video.awemeId)
            videos.add(0, video)
        }
    }
}
