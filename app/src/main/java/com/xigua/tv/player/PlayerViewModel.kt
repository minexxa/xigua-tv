package com.xigua.tv.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.xigua.tv.data.FeedApi
import com.xigua.tv.data.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI 状态 */
sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    data object Loading : PlayerUiState
    data class Ready(val video: VideoInfo) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val feedApi = FeedApi()

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _controlsVisible = MutableStateFlow(false)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    /** ExoPlayer 实例 —— 由 ViewModel 持有，配置时复用 */
    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(application).build()
    }

    /** 加载单个视频并播放（默认取最高清晰度） */
    fun loadVideo(itemId: String) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            val result = feedApi.resolveVideo(itemId)
            result.fold(
                onSuccess = { video ->
                    val url = video.bestPlayUrl()
                    if (url.isNullOrBlank()) {
                        _uiState.value = PlayerUiState.Error("视频无可用播放地址")
                        return@fold
                    }
                    play(url)
                    _uiState.value = PlayerUiState.Ready(video)
                },
                onFailure = { e ->
                    _uiState.value = PlayerUiState.Error(e.message ?: "解析失败")
                },
            )
        }
    }

    /** 直接用已知地址播放（供测试/信息流预加载使用） */
    fun play(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    /** 暂停 / 播放切换 */
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    /** 快进 / 快退（毫秒） */
    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0, exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        exoPlayer.seekTo(target)
    }

    /** 切换控制条显示 */
    fun toggleControls() {
        _controlsVisible.value = !_controlsVisible.value
    }

    override fun onCleared() {
        exoPlayer.release()
        super.onCleared()
    }
}
