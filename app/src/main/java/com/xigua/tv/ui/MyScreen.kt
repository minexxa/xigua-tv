package com.xigua.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xigua.tv.data.history.WatchHistory
import com.xigua.tv.player.MyViewModel

/**
 * 「我的」页 —— 观看历史 / 我的收藏。
 *
 * 遥控器：
 *   上/下  -> 列表项间移动（±3，三列网格）
 *   左/右  -> 切换 Tab（历史 / 收藏）
 *   OK     -> 播放选中视频
 *   删除键 -> 删除选中项（Backspace / DEL）
 *   返回键 -> 回到视频流
 */
@Composable
fun MyScreen(
    viewModel: MyViewModel,
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tab by viewModel.tab.collectAsState()
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val list = if (tab == 0) history else favorites

    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var selected by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        focusRequester.requestFocus()
    }
    // 切换 Tab 或列表变化时重置选中
    LaunchedEffect(tab, list.size) {
        if (selected >= list.size) selected = 0
    }
    // 选中项滚动进入可视区
    LaunchedEffect(selected) {
        if (selected in list.indices) gridState.animateScrollToItem(selected)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key(KeyEvent.KEYCODE_CHANNEL_UP) -> {
                        selected = (selected - 3).coerceAtLeast(0); true
                    }
                    Key.DirectionDown, Key(KeyEvent.KEYCODE_CHANNEL_DOWN) -> {
                        selected = (selected + 3).coerceAtMost((list.size - 1).coerceAtLeast(0)); true
                    }
                    Key.DirectionLeft -> {
                        viewModel.setTab(0); selected = 0; true
                    }
                    Key.DirectionRight -> {
                        viewModel.setTab(1); selected = 0; true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        list.getOrNull(selected)?.let { onPlay(it.awemeId) }; true
                    }
                    Key.Backspace, Key(KeyEvent.KEYCODE_FORWARD_DEL) -> {
                        list.getOrNull(selected)?.let { viewModel.delete(it.awemeId) }; true
                    }
                    Key.Escape, Key(KeyEvent.KEYCODE_BACK) -> { onBack(); true }
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            // 顶部标题 + Tab
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "我的",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(40.dp))
                TabChip("观看历史 (${history.size})", active = tab == 0)
                Spacer(Modifier.width(16.dp))
                TabChip("我的收藏 (${favorites.size})", active = tab == 1)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "上下选择 · 左右切换 · OK 播放 · 删除键移除 · 返回键回到播放",
                color = Color(0xFF888888),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))

            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (tab == 0) "还没有观看记录\n去播放一个视频吧" else "还没有收藏\n播放时按菜单键收藏",
                        color = Color(0xFF777777),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(list) { index, item ->
                        HistoryCard(
                            item = item,
                            selected = index == selected,
                            onClick = { onPlay(item.awemeId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(text: String, active: Boolean) {
    Text(
        text = text,
        color = if (active) Color(0xFF000000) else Color(0xFFCCCCCC),
        fontSize = 18.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(
                color = if (active) Color(0xFFFF9800) else Color(0xFF2A2A2A),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun HistoryCard(
    item: WatchHistory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val progress = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .then(
                if (selected) Modifier.border(3.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A2A)),
        ) {
            if (!item.cover.isNullOrBlank()) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (item.isFavorite) {
                Text(
                    text = "★",
                    color = Color(0xFFFFC107),
                    fontSize = 22.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title.ifBlank { "（无标题）" },
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "@${item.author}",
            color = Color(0xFF999999),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color(0xFFFF9800),
                trackColor = Color(0xFF333333),
            )
        } else {
            // 占位保持高度一致
            Spacer(Modifier.height(4.dp))
        }
    }
}
