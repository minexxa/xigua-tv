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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.xigua.tv.data.VideoInfo
import com.xigua.tv.player.StreamUiState
import com.xigua.tv.player.StreamViewModel

/**
 * 沉浸式短视频流 —— 第三步核心。
 *
 * 交互（遥控器）：
 *   上/下  -> VerticalPager 切换视频（自动播放）
 *   OK     -> 暂停/播放 + 显示信息浮层
 *   左/右  -> 快退/快进 10 秒
 *
 * 资源策略：
 *   只有一个 ExoPlayer 实例。当前页挂载 PlayerView 并绑定播放器，
 *   其他页显示封面图，避免多个 PlayerView 争抢同一播放器导致黑屏。
 */
@Composable
fun StreamScreen(viewModel: StreamViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val resumedFrom by viewModel.resumedFrom.collectAsState()
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
                            is StreamUiState.Error -> viewModel.loadInitial() // 错误态重试
                            is StreamUiState.Ready -> {
                                viewModel.togglePlayPause()
                                viewModel.toggleControls()
                            }
                            else -> {}
                        }
                        true
                    }
                    Key.DirectionLeft -> { viewModel.seekBy(-10_000); true }
                    Key.DirectionRight -> { viewModel.seekBy(10_000); true }
                    // 上下键交给 VerticalPager 处理滚动
                    else -> false
                }
            },
    ) {
        when (val state = uiState) {
            is StreamUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is StreamUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = state.message, color = Color.White, fontSize = 20.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(text = "按 OK 重试", color = Color.Gray, fontSize = 16.sp)
                    }
                }
            }
            is StreamUiState.Ready -> {
                VideoPager(
                    videos = state.videos,
                    currentIndex = currentIndex,
                    viewModel = viewModel,
                    onPageChanged = { viewModel.onPageChanged(it) },
                )
                // 续播提示（顶部，命中历史进度时短暂显示）
                if (resumedFrom > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color(0x99000000))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "继续上次观看 ${resumedFrom / 1000} 秒",
                            color = Color.White,
                            fontSize = 16.sp,
                        )
                    }
                }

                // 信息浮层（全屏，不随 pager 滚动）
                if (controlsVisible) {
                    viewModel.currentVideo?.let { StreamInfoOverlay(it) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun VideoPager(
    videos: List<VideoInfo>,
    currentIndex: Int,
    viewModel: StreamViewModel,
    onPageChanged: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { videos.size })

    // 外部 index 变化（如 ViewModel 主动切换）时同步 pager
    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }

    // pager 滑动停止后通知 ViewModel 切换播放
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        val video = videos[page]
        VideoPage(
            video = video,
            isActive = page == pagerState.currentPage,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun VideoPage(
    video: VideoInfo,
    isActive: Boolean,
    viewModel: StreamViewModel,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isActive) {
            // 当前页：挂载 PlayerView 绑定共享播放器
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = viewModel.exoPlayer
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 非当前页：显示封面占位（横竖屏自适应裁剪）
            AsyncImage(
                model = video.cover,
                contentDescription = video.title,
                contentScale = if (video.isLandscape) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BoxScope.StreamInfoOverlay(video: VideoInfo) {
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
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = video.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).background(Color.Gray),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = "@${video.author}", color = Color.White, fontSize = 16.sp)
                Text(
                    text = "${video.width}x${video.height} · ${video.durationMs / 1000}s · ${video.qualities.size}档清晰度",
                    color = Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
