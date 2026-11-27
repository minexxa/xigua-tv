package com.xigua.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频流数据仓库。
 *
 * 职责：
 *  1. 分页拉取推荐流，按 awemeId 去重累积（Feed 接口每次返回有重复）
 *  2. 维护已加载视频列表，供沉浸式流上下切换
 *  3. 影视横屏优先（可开关）：服务端不支持过滤，端上多轮拉候选池 + 加权评分，
 *     把稀疏的横屏影视长视频捞到前面
 *
 * 播放地址已在 VideoInfo 中（Feed 接口一次性返回），无需二次解析，
 * 因此"预加载"在 ViewModel 层通过提前 prepare MediaItem 实现。
 */
class FeedRepository(private val api: FeedApi = FeedApi()) {

    private val videos = mutableListOf<VideoInfo>()
    private val seenIds = mutableSetOf<String>()
    private var isLoading = false

    /**
     * 影视横屏优先模式（TV 大屏优化）。
     *
     * 实测 Feed 接口不支持服务端过滤（channel_id/source/is_long_video 参数均无效），
     * 横屏影视长视频约占 20~25%。开启后采用「候选池 + tvScore 加权排序」：
     * 一次性拉多轮攒够候选，按评分降序投放，横屏影视长视频优先入流，
     * 竖屏短视频沉底兜底，避免过滤后列表为空。
     *
     * 默认关闭；用户在「我的 → 设置」手动开启。
     */
    var landscapeOnly: Boolean = false

    /** 当前已加载的视频列表（只读快照） */
    fun currentList(): List<VideoInfo> = videos.toList()

    /** 是否正在加载 */
    fun isLoading(): Boolean = isLoading

    /**
     * 加载更多视频（追加到列表尾部）。
     *
     * 开启 landscapeOnly 时：
     *  - 多轮拉取攒候选池（去重），按 tvScore 降序排序
     *  - 优先投放高分（横屏/长视频/影视解说），低分沉底
     *  - 分数并列时用随机数打散，避免每次都是同一批
     * @return 本次新增的视频数量
     */
    suspend fun loadMore(targetCount: Int = 5): Result<Int> = withContext(Dispatchers.IO) {
        if (isLoading) return@withContext Result.success(0)
        isLoading = true
        try {
            if (!landscapeOnly) {
                // 关闭模式：按 Feed 原顺序拉取，仅去重
                var added = 0
                var round = 0
                while (added < targetCount && round < 3) {
                    round++
                    val batch = api.getFeed(count = targetCount * 2).getOrNull() ?: continue
                    for (video in batch) {
                        if (video.awemeId.isBlank() || !seenIds.add(video.awemeId)) continue
                        videos.add(video)
                        added++
                        if (added >= targetCount) break
                    }
                }
                return@withContext Result.success(added)
            }

            // 开启模式：多轮拉候选池 + 加权排序
            val candidates = mutableSetOf<VideoInfo>()
            val rounds = 6
            repeat(rounds) {
                val batch = api.getFeed(count = 10).getOrNull() ?: return@repeat
                for (video in batch) {
                    if (video.awemeId.isBlank() || video.awemeId in seenIds) continue
                    candidates.add(video)
                }
            }

            if (candidates.isEmpty()) return@withContext Result.success(0)

            // 按分数降序；同分随机打散，避免重复内容固定在头部
            val sorted = candidates.sortedWith(
                compareByDescending<VideoInfo> { it.tvScore() }
                    .thenBy { kotlin.random.Random.nextInt() }
            )

            var added = 0
            for (video in sorted) {
                if (added >= targetCount) break
                if (!seenIds.add(video.awemeId)) continue
                videos.add(video)
                added++
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
