package com.xigua.tv.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.xigua.tv.data.FeedRepository
import com.xigua.tv.data.VideoInfo
import com.xigua.tv.data.history.WatchHistory
import com.xigua.tv.data.history.AppDatabase
import com.xigua.tv.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 视频流 UI 状态 */
sealed interface StreamUiState {
    data object Loading : StreamUiState
    data class Ready(val videos: List<VideoInfo>) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

/**
 * 沉浸式视频流 ViewModel（第四步：预加载 + 续播）。
 *
 * 预加载策略 —— 滑动窗口队列：
 *  播放器队列始终维持 [当前, 下一个]，ExoPlayer 自动预缓冲下一个。
 *  下滑时 seekToNext() 直接播放已缓冲的下一个（零延迟），随后补窗口。
 *  上滑/跳跃无法用队列回退，重建队列并 seek 到历史位置。
 *
 * 进度记忆 —— Room：
 *  每 3 秒保存当前播放位置；切换视频前立即保存。
 *  切到某视频时查历史位置，未看完则续播。
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FeedRepository()
    private val historyDao = AppDatabase.get(application).watchHistoryDao()
    private val settingsRepo = SettingsRepository.get(application)

    /** 当前播放设置（DataStore 收集） */
    private var currentSettings = SettingsRepository.Settings()
    private var settingsJob: Job? = null

    private val _uiState = MutableStateFlow<StreamUiState>(StreamUiState.Loading)
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _controlsVisible = MutableStateFlow(false)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** 是否命中续播（UI 可提示"继续上次观看"） */
    private val _resumedFrom = MutableStateFlow<Long>(0L)
    val resumedFrom: StateFlow<Long> = _resumedFrom.asStateFlow()

    val currentVideo: VideoInfo?
        get() = (uiState.value as? StreamUiState.Ready)?.videos?.getOrNull(currentIndex.value)

    /** 进度保存定时任务 */
    private var progressJob: Job? = null

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(application).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                /** 自动连播：当前视频播完切到下一个（REPEAT_MODE_OFF 时触发） */
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED && currentSettings.autoNext) {
                        val list = (uiState.value as? StreamUiState.Ready)?.videos ?: return
                        val next = _currentIndex.value + 1
                        if (next < list.size) {
                            onPageChanged(next)
                        } else {
                            // 流末尾：先加载更多，加载后再切（下方 loadMore 会扩展列表）
                            loadMore(thenPlayIndex = next)
                        }
                    }
                }
            })
        }
    }

    init {
        loadInitial()
        startProgressSaver()
        collectSettings()
    }

    /** 收集设置变更并即时应用到播放器 */
    private fun collectSettings() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settingsRepo.settingsFlow.collect { settings ->
                val qualityChanged = settings.qualityMode != currentSettings.qualityMode
                currentSettings = settings
                // 倍速即时生效
                exoPlayer.setPlaybackParameters(
                    androidx.media3.common.PlaybackParameters(settings.speed)
                )
                // 自动连播：开=不循环(播完切下一个)，关=单集循环
                exoPlayer.repeatMode = if (settings.autoNext) {
                    Player.REPEAT_MODE_OFF
                } else {
                    Player.REPEAT_MODE_ONE
                }
                // 清晰度变更：重新加载当前视频
                if (qualityChanged && uiState.value is StreamUiState.Ready) {
                    playAt(_currentIndex.value)
                }
            }
        }
    }

    /** 首次加载 */
    fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = StreamUiState.Loading
            val result = repository.loadInitial()
            result.fold(
                onSuccess = {
                    val list = repository.currentList()
                    if (list.isEmpty()) {
                        _uiState.value = StreamUiState.Error("没有加载到视频")
                    } else {
                        _uiState.value = StreamUiState.Ready(list)
                        _currentIndex.value = 0
                        playAt(0)
                    }
                },
                onFailure = { _uiState.value = StreamUiState.Error(it.message ?: "加载失败") },
            )
        }
    }

    /**
     * 页面切换。
     * 只处理"下移一格"走 seekToNext 零延迟；其余情况重建队列。
     */
    fun onPageChanged(index: Int) {
        if (index == _currentIndex.value) return
        val list = (uiState.value as? StreamUiState.Ready)?.videos ?: return
        if (index !in list.indices) return

        // 切换前保存当前进度
        saveProgressNow()

        if (index == _currentIndex.value + 1 && exoPlayer.hasNextMediaItem()) {
            // 下滑一格：播放已预缓冲的下一个
            exoPlayer.seekToNext()
            _currentIndex.value = index
            // 补充窗口：移除已播的第一个，追加新的下一个
            exoPlayer.removeMediaItem(0)
            if (index + 1 < list.size) {
                exoPlayer.addMediaItem(
                    MediaItem.fromUri(list[index + 1].playUrlFor(currentSettings.qualityMode) ?: "")
                )
            }
            applyResumePosition(list[index])
        } else {
            // 上滑或跳跃：重建队列 [当前, 下一个]
            _currentIndex.value = index
            playAt(index)
        }

        if (index >= list.size - 2) loadMore()
    }

    /** 重建播放队列并播放指定位置（带续播 + 清晰度偏好） */
    private fun playAt(index: Int) {
        val list = (uiState.value as? StreamUiState.Ready)?.videos ?: return
        val mode = currentSettings.qualityMode
        val items = mutableListOf<MediaItem>()
        list.getOrNull(index)?.playUrlFor(mode)?.let { items.add(MediaItem.fromUri(it)) }
        // 预加载下一个
        list.getOrNull(index + 1)?.playUrlFor(mode)?.let { items.add(MediaItem.fromUri(it)) }
        if (items.isEmpty()) return

        exoPlayer.setMediaItems(items, 0, 0L)
        exoPlayer.prepare()
        exoPlayer.play()
        applyResumePosition(list[index])
    }

    /** 查询历史位置并续播 */
    private fun applyResumePosition(video: VideoInfo) {
        viewModelScope.launch {
            val pos = historyDao.resumePosition(video.awemeId) ?: 0L
            if (pos > 0L) {
                exoPlayer.seekTo(pos)
                _resumedFrom.value = pos
            } else {
                _resumedFrom.value = 0L
            }
        }
    }

    /** 立即保存当前播放进度（保留已有收藏状态） */
    private fun saveProgressNow() {
        val video = currentVideo ?: return
        val pos = exoPlayer.currentPosition
        if (pos <= 0L) return
        viewModelScope.launch {
            val favorite = historyDao.findById(video.awemeId)?.isFavorite ?: false
            historyDao.save(
                WatchHistory(
                    awemeId = video.awemeId,
                    title = video.title,
                    author = video.author,
                    cover = video.cover,
                    positionMs = pos,
                    durationMs = exoPlayer.duration.takeIf { it > 0 } ?: video.durationMs,
                    lastWatchAt = System.currentTimeMillis(),
                    isFavorite = favorite,
                )
            )
        }
    }

    /**
     * 切换当前视频收藏状态。
     * 若视频尚无历史记录，先构造一条再更新收藏标记。
     */
    fun toggleFavorite() {
        val video = currentVideo ?: return
        viewModelScope.launch {
            val existing = historyDao.findById(video.awemeId)
            val newState = !(existing?.isFavorite ?: false)
            historyDao.save(
                WatchHistory(
                    awemeId = video.awemeId,
                    title = video.title,
                    author = video.author,
                    cover = video.cover,
                    positionMs = existing?.positionMs ?: 0L,
                    durationMs = existing?.durationMs ?: video.durationMs,
                    lastWatchAt = System.currentTimeMillis(),
                    isFavorite = newState,
                )
            )
            _favoriteToast.value = if (newState) "已加入收藏" else "已取消收藏"
        }
    }

    /** 收藏操作提示（一次性事件，UI 消费后清空） */
    private val _favoriteToast = MutableStateFlow<String?>(null)
    val favoriteToast: StateFlow<String?> = _favoriteToast.asStateFlow()
    fun clearFavoriteToast() { _favoriteToast.value = null }

    /**
     * 从历史/收藏页按 ID 播放：解析直链后把该视频放到流首位并播放。
     * @return 是否成功发起
     */
    fun playById(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.resolveById(id)
            val video = result.getOrNull()
            if (video == null) {
                onResult(false)
                return@launch
            }
            repository.prepend(video)
            val list = repository.currentList()
            _uiState.value = StreamUiState.Ready(list)
            _currentIndex.value = 0
            playAt(0)
            onResult(true)
        }
    }

    /** 每 3 秒保存一次进度 */
    private fun startProgressSaver() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                if (exoPlayer.isPlaying) saveProgressNow()
            }
        }
    }

    private fun loadMore(thenPlayIndex: Int? = null) {
        viewModelScope.launch {
            val result = repository.loadMore()
            result.fold(
                onSuccess = { added ->
                    if (added > 0) {
                        _uiState.value = StreamUiState.Ready(repository.currentList())
                        // 自动连播到流末尾后，加载成功则切到下一个
                        if (thenPlayIndex != null && thenPlayIndex < repository.currentList().size) {
                            onPageChanged(thenPlayIndex)
                        }
                    }
                },
                onFailure = { /* 不影响当前播放 */ },
            )
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            saveProgressNow()
        } else {
            exoPlayer.play()
        }
    }

    fun toggleControls() {
        _controlsVisible.value = !_controlsVisible.value
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs)
            .coerceIn(0, exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        exoPlayer.seekTo(target)
    }

    override fun onCleared() {
        progressJob?.cancel()
        settingsJob?.cancel()
        saveProgressNow()
        exoPlayer.release()
        super.onCleared()
    }
}
