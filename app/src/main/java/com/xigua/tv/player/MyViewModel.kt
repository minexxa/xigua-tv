package com.xigua.tv.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xigua.tv.data.history.AppDatabase
import com.xigua.tv.data.history.WatchHistory
import com.xigua.tv.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「我的」页 ViewModel —— 观看历史 + 收藏列表。
 *
 * 数据直接来自 Room（WatchHistory 表，isFavorite 区分收藏）。
 * 每次进入页面主动 refresh，保证列表最新。
 */
class MyViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).watchHistoryDao()
    private val settingsRepo = SettingsRepository.get(application)

    /** 播放设置（供设置页读写） */
    val settings = settingsRepo.settingsFlow

    fun setQualityMode(mode: Int) { viewModelScope.launch { settingsRepo.setQualityMode(mode) } }
    fun setSpeed(speed: Float) { viewModelScope.launch { settingsRepo.setSpeed(speed) } }
    fun setAutoNext(enabled: Boolean) { viewModelScope.launch { settingsRepo.setAutoNext(enabled) } }

    private val _history = MutableStateFlow<List<WatchHistory>>(emptyList())
    val history: StateFlow<List<WatchHistory>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow<List<WatchHistory>>(emptyList())
    val favorites: StateFlow<List<WatchHistory>> = _favorites.asStateFlow()

    /** 0=观看历史 1=我的收藏 */
    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _history.value = dao.recent(200)
            _favorites.value = dao.favorites(200)
        }
    }

    fun setTab(index: Int) { _tab.value = index }

    fun delete(id: String) {
        viewModelScope.launch {
            dao.deleteById(id)
            refresh()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
            refresh()
        }
    }
}
