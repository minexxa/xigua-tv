package com.xigua.tv.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WatchHistoryDao {

    /** 保存/更新播放进度 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(history: WatchHistory)

    /** 查询单个视频的观看记录 */
    @Query("SELECT * FROM watch_history WHERE awemeId = :id LIMIT 1")
    suspend fun findById(id: String): WatchHistory?

    /** 最近观看列表（按时间倒序） */
    @Query("SELECT * FROM watch_history ORDER BY lastWatchAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<WatchHistory>

    /** 获取续播位置：未看完的视频返回 positionMs，看完的返回 0 */
    @Query("SELECT CASE WHEN durationMs > 0 AND positionMs >= durationMs * 0.95 THEN 0 ELSE positionMs END FROM watch_history WHERE awemeId = :id LIMIT 1")
    suspend fun resumePosition(id: String): Long?

    /** 清空历史 */
    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
