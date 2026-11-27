package com.xigua.tv.data

import kotlinx.serialization.Serializable

/**
 * 统一视频模型 —— 对应第一步 Python 脚本的输出字段。
 * 供 UI 层与播放器层共用。
 */
@Serializable
data class VideoInfo(
    val awemeId: String = "",
    val title: String = "",
    val author: String = "",
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val cover: String? = null,
    val playUrl: String? = null,
    val backupUrls: List<String> = emptyList(),
    val qualities: List<Quality> = emptyList(),
    val source: String = "",
) {
    /** 是否横屏视频（TV 上需要不同的布局策略） */
    val isLandscape: Boolean get() = width >= height

    /**
     * TV 场景内容偏好评分：越高越适合 TV 大屏连续观看。
     * 用于「影视横屏优先」模式下对 Feed 流排序——把稀疏的横屏影视长视频
     * 从大量竖屏短视频里捞到前面（Feed 接口本身不支持服务端过滤）。
     *
     * 加分项：
     *  - 横屏 +4
     *  - 时长 >60s +2；>180s 再 +1
     *  - 标题命中影视解说关键词 +2
     */
    fun tvScore(): Int {
        var score = 0
        if (isLandscape) score += 4
        val seconds = durationMs / 1000
        if (seconds > 60) score += 2
        if (seconds > 180) score += 1
        if (isFilmContent()) score += 2
        return score
    }

    /** 标题是否命中影视/解说类关键词 */
    private fun isFilmContent(): Boolean {
        val lower = title.lowercase()
        return FILM_KEYWORDS.any { lower.contains(it) }
    }

    companion object {
        private val FILM_KEYWORDS = listOf(
            "解说", "一口气看完", "看完", "电影", "影视", "电视剧", "剧场", "剧集",
            "剧情", "悬疑", "结局", "速看", "分钟看", "经典", "大片", "预告片",
            "宅家", "观影", "说电影", "讲电影", "纪录片", "真人电影",
        )
    }

    /** 取最高清晰度的播放地址（width 最大），无则回退 playUrl */
    fun bestPlayUrl(): String? =
        qualities.maxByOrNull { it.width }?.url ?: playUrl

    /**
     * 按清晰度偏好选播放地址（第六步：手动选清晰度）。
     * @param mode 0=自动(最高) 1=1080P 2=720P 3=480P 4=360P
     */
    fun playUrlFor(mode: Int): String? {
        if (mode <= 0 || qualities.isEmpty()) return bestPlayUrl()
        val targetWidth = when (mode) {
            1 -> 1920
            2 -> 1280
            3 -> 854
            4 -> 640
            else -> Int.MAX_VALUE
        }
        // 选不超过目标宽度中最高的一档；若全都超过目标则取最小的一档
        val candidate = qualities.filter { it.width > 0 && it.url.isNotBlank() }
            .filter { it.width <= targetWidth }
            .maxByOrNull { it.width }
            ?: qualities.filter { it.url.isNotBlank() }.minByOrNull { it.width }
        return candidate?.url ?: bestPlayUrl()
    }
}

@Serializable
data class Quality(
    val quality: Int? = null,
    val bitrate: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val url: String = "",
)
