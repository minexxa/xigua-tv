package com.xigua.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.xigua.tv.player.StreamViewModel

/**
 * 导航主页 —— 从沉浸播放按返回键进入。
 * 视频缩小到左上角继续播放（复用同一个 ExoPlayer），右侧/下方显示导航菜单与「我的」入口。
 *
 * 遥控器：
 *   上/下  -> 菜单项间移动
 *   OK     -> 确认（继续观看 / 我的）
 *   返回键 -> 退出应用
 */
@Composable
fun HomeScreen(
    streamViewModel: StreamViewModel,
    onResumeFeed: () -> Unit,
    onOpenMy: () -> Unit,
    onExit: () -> Unit,
) {
    val currentVideo = streamViewModel.currentVideo
    val focusRequester = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(0) }
    val menu = listOf("继续观看（全屏播放）", "我的（历史 / 收藏 / 设置）")

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                    Key.DirectionDown -> { selected = (selected + 1).coerceAtMost(menu.lastIndex); true }
                    Key.Enter, Key.DirectionCenter -> {
                        when (selected) {
                            0 -> onResumeFeed()
                            1 -> onOpenMy()
                        }
                        true
                    }
                    Key(KeyEvent.KEYCODE_BACK) -> { onExit(); true }
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
            // 顶部：小窗播放 + 当前视频信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 480.dp, height = 270.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = streamViewModel.exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(32.dp))
                Column {
                    Text(
                        text = "西瓜视频 TV",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = currentVideo?.title ?: "暂无播放内容",
                        color = Color(0xFFDDDDDD),
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (currentVideo != null) {
                        Text(
                            text = "@${currentVideo.author} · ${currentVideo.width}x${currentVideo.height}",
                            color = Color(0xFF888888),
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(56.dp))

            Text(text = "导航", color = Color(0xFF888888), fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))

            menu.forEachIndexed { index, label ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected) Color(0xFF4FC3F7) else Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 22.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "上/下 选择 · OK 确认 · 返回键 退出应用",
                color = Color(0xFF666666),
                fontSize = 15.sp,
            )
        }
    }
}
