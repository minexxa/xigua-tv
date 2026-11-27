package com.xigua.tv.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.xigua.tv.data.FeedRepository
import com.xigua.tv.data.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 视频流 UI 状态 */
sealed interface StreamUiState {
    data object Loading : StreamUiState
    data class Ready(val videos: List<VideoInfo>) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

/**
 * 沉浸式视频流 ViewModel。
 *
 * 关键设计：
 *  - 单 ExoPlayer 实例复用，切换视频只换 MediaItem（省内存，TV 盒子友好）
 *  - 当前 index 变化时自动播放对应视频
 *  - 滑动到倒数第 2 个时触发 loadMore（无限流）
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FeedRepository()

    private val _uiState = MutableStateFlow<StreamUiState>(StreamUiState.Loading)
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _controlsVisible = MutableStateFlow(false)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** 当前视频 */
    val currentVideo: VideoInfo?
        get() = (uiState.value as? StreamUiState.Ready)?.videos?.getOrNull(currentIndex.value)

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(application).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE // 单个视频循环
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }
            })
        }
    }

    init {
        loadInitial()
    }

    /** 首次加载 */
    fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = StreamUiState.Loading
            val result = repository.loadInitial()
            result.fold(
                onSuccess = { count ->
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

    /** 切换到指定位置的视频 */
    fun onPageChanged(index: Int) {
        if (index == _currentIndex.value) return
        _currentIndex.value = index
        playAt(index)

        // 快到底时加载更多（无限流）
        val list = (uiState.value as? StreamUiState.Ready)?.videos ?: return
        if (index >= list.size - 2) {
            loadMore()
        }
    }

    /** 播放指定位置的视频 */
    private fun playAt(index: Int) {
        val video = (uiState.value as? StreamUiState.Ready)?.videos?.getOrNull(index) ?: return
        val url = video.bestPlayUrl() ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    /** 加载更多并更新列表 */
    private fun loadMore() {
        viewModelScope.launch {
            val result = repository.loadMore()
            result.fold(
                onSuccess = { added ->
                    if (added > 0) {
                        _uiState.value = StreamUiState.Ready(repository.currentList())
                    }
                },
                onFailure = { /* 加载更多失败不影响当前播放 */ },
            )
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
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
        exoPlayer.release()
        super.onCleared()
    }
}
