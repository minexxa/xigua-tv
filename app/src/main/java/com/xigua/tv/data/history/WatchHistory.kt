package com.xigua.tv.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 观看历史实体。
 * 每个视频一条记录，保存播放进度与累计观看信息，用于续播。
 */
@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey
    val awemeId: String,
    val title: String = "",
    val author: String = "",
    val cover: String? = null,
    /** 上次播放位置（毫秒） */
    val positionMs: Long = 0L,
    /** 视频总时长（毫秒） */
    val durationMs: Long = 0L,
    /** 最后观看时间戳（毫秒） */
    val lastWatchAt: Long = 0L,
) {
    /** 是否已看完（进度超过 95%） */
    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs * 0.95
}
