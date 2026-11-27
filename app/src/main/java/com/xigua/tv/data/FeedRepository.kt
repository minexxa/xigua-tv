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
 *
 * 播放地址已在 VideoInfo 中（Feed 接口一次性返回），无需二次解析，
 * 因此"预加载"在 ViewModel 层通过提前 prepare MediaItem 实现。
 */
class FeedRepository(private val api: FeedApi = FeedApi()) {

    private val videos = mutableListOf<VideoInfo>()
    private val seenIds = mutableSetOf<String>()
    private var isLoading = false

    /** 当前已加载的视频列表（只读快照） */
    fun currentList(): List<VideoInfo> = videos.toList()

    /** 是否正在加载 */
    fun isLoading(): Boolean = isLoading

    /**
     * 加载更多视频（追加到列表尾部）。
     * @return 本次新增的视频数量
     */
    suspend fun loadMore(targetCount: Int = 5): Result<Int> = withContext(Dispatchers.IO) {
        if (isLoading) return@withContext Result.success(0)
        isLoading = true
        try {
            // 多拉一些，因为 Feed 接口有重复，去重后可能不足
            val result = api.getFeed(count = (targetCount * 2).coerceAtLeast(8))
            result.fold(
                onSuccess = { batch ->
                    var added = 0
                    for (video in batch) {
                        if (video.awemeId.isNotBlank() && seenIds.add(video.awemeId)) {
                            videos.add(video)
                            added++
                        }
                    }
                    Result.success(added)
                },
                onFailure = { Result.failure(it) },
            )
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
