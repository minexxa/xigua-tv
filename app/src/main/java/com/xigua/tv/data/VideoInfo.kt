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
}

@Serializable
data class Quality(
    val quality: Int? = null,
    val bitrate: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val url: String = "",
)
