package com.xigua.tv.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_settings")

/**
 * 播放设置持久化（DataStore Preferences）。
 *
 * 设置项：
 *  - 清晰度偏好：0=自动(最高) 1=1080P 2=720P 3=480P 4=360P
 *  - 播放倍速：0.5 / 0.75 / 1.0 / 1.25 / 1.5 / 2.0
 *  - 自动连播：当前视频播完后是否自动切下一个（关闭则单集循环）
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_QUALITY = intPreferencesKey("quality_mode")
        private val KEY_SPEED = floatPreferencesKey("playback_speed")
        private val KEY_AUTO_NEXT = androidx.datastore.preferences.core.booleanPreferencesKey("auto_next")
        private val KEY_LANDSCAPE_ONLY = androidx.datastore.preferences.core.booleanPreferencesKey("landscape_only")

        val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val QUALITY_LABELS = listOf("自动(最高)", "1080P", "720P", "480P", "360P")

        @Volatile
        private var INSTANCE: SettingsRepository? = null
        fun get(context: Context): SettingsRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
        }
    }

    data class Settings(
        val qualityMode: Int = 0,
        val speed: Float = 1.0f,
        val autoNext: Boolean = true,
        val landscapeOnly: Boolean = false,
    )

    val settingsFlow: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            qualityMode = prefs[KEY_QUALITY] ?: 0,
            speed = prefs[KEY_SPEED] ?: 1.0f,
            autoNext = prefs[KEY_AUTO_NEXT] ?: true,
            landscapeOnly = prefs[KEY_LANDSCAPE_ONLY] ?: false,
        )
    }

    suspend fun setQualityMode(mode: Int) {
        context.settingsDataStore.edit { it[KEY_QUALITY] = mode }
    }

    suspend fun setSpeed(speed: Float) {
        context.settingsDataStore.edit { it[KEY_SPEED] = speed }
    }

    suspend fun setAutoNext(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_NEXT] = enabled }
    }

    suspend fun setLandscapeOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_LANDSCAPE_ONLY] = enabled }
    }
}
