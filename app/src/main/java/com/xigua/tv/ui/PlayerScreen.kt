package com.xigua.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.xigua.tv.player.PlayerUiState
import com.xigua.tv.player.PlayerViewModel

/**
 * 单视频播放验证界面（MVP 第二步核心）。
 *
 * 遥控器按键：
 *   OK/ENTER  -> 暂停/播放 + 显示/隐藏控制条
 *   左/右     -> 快退/快进 10 秒
 *   上/下     -> 预留给下一阶段的视频流切换
 */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter -> {
                        when (uiState) {
                            is PlayerUiState.Idle -> viewModel.loadVideo(TEST_ID) // MVP: 加载测试视频
                            is PlayerUiState.Ready -> {
                                viewModel.togglePlayPause()
                                viewModel.toggleControls()
                            }
                            else -> {}
                        }
                        true
                    }
                    Key.DirectionLeft -> { viewModel.seekBy(-10_000); true }
                    Key.DirectionRight -> { viewModel.seekBy(10_000); true }
                    else -> false
                }
            },
    ) {
        // ExoPlayer 全屏播放
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.exoPlayer
                    useController = false // 用自绘 TV 控件
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 加载中
        if (uiState is PlayerUiState.Loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // 错误提示
        if (uiState is PlayerUiState.Error) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = (uiState as PlayerUiState.Error).message,
                    color = Color.White,
                    fontSize = 20.sp,
                )
            }
        }

        // 视频信息浮层（控制条显示时）
        val video = (uiState as? PlayerUiState.Ready)?.video
        if (controlsVisible && video != null) {
            VideoInfoOverlay(video)
        }

        // 顶部调试提示：默认给一个测试 ID，遥控器确认即可播放
        if (uiState is PlayerUiState.Idle) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "西瓜视频 TV · 按 OK 播放测试视频\n$TEST_ID",
                    color = Color.White,
                    fontSize = 22.sp,
                )
            }
        }
    }

    // 进入界面时抢占焦点，保证遥控器按键生效
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

private const val TEST_ID = "7676450021063735414"

@Composable
private fun BoxScope.VideoInfoOverlay(video: com.xigua.tv.data.VideoInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .background(Color(0x99000000))
            .padding(24.dp),
    ) {
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 24.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            video.cover?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .background(Color.Gray),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(text = video.author, color = Color(0xFFCCCCCC), fontSize = 16.sp)
        }
    }
}
