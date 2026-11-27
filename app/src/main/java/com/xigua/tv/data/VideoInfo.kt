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
