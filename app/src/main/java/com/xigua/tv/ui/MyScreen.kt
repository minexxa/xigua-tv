package com.xigua.tv.ui

import android.view.KeyEvent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xigua.tv.data.history.WatchHistory
import com.xigua.tv.data.settings.SettingsRepository
import com.xigua.tv.player.MyViewModel

/**
 * 「我的」页 —— 观看历史 / 我的收藏 / 播放设置。
 *
 * 遥控器：
 *   上/下  -> 列表项间移动（±3，三列网格）/ 设置项间移动
 *   左/右  -> 切换 Tab（历史 / 收藏 / 设置）/ 修改设置值
 *   OK     -> 播放选中视频 / 循环切换设置值
 *   删除键 -> 删除选中项（历史/收藏页）
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
    val settings by viewModel.settings.collectAsState(SettingsRepository.Settings())
    val list = when (tab) {
        0 -> history
        1 -> favorites
        else -> emptyList()
    }

    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var selected by remember { mutableIntStateOf(0) }
    // 设置页当前选中项：0=清晰度 1=倍速 2=自动连播 3=仅横屏
    var settingSelected by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        focusRequester.requestFocus()
    }
    LaunchedEffect(tab, list.size) {
        if (selected >= list.size) selected = 0
    }
    LaunchedEffect(selected) {
        if (tab in 0..1 && selected in list.indices) gridState.animateScrollToItem(selected)
    }

    // 设置项当前值与可选数量
    val settingCount = 4
    val qualityCount = SettingsRepository.QUALITY_LABELS.size
    val speedCount = SettingsRepository.SPEEDS.size
    val currentQuality = settings.qualityMode.coerceIn(0, qualityCount - 1)
    val currentSpeedIdx = SettingsRepository.SPEEDS.indexOf(settings.speed).let { if (it < 0) 2 else it }

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
                        if (tab == 2) settingSelected = (settingSelected - 1).coerceAtLeast(0)
                        else selected = (selected - 3).coerceAtLeast(0)
                        true
                    }
                    Key.DirectionDown, Key(KeyEvent.KEYCODE_CHANNEL_DOWN) -> {
                        if (tab == 2) settingSelected = (settingSelected + 1).coerceAtMost(settingCount - 1)
                        else selected = (selected + 3).coerceAtMost((list.size - 1).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionLeft -> {
                        if (tab == 2) {
                            // 设置页：减小当前项的值
                            when (settingSelected) {
                                0 -> viewModel.setQualityMode((currentQuality - 1).coerceAtLeast(0))
                                1 -> viewModel.setSpeed(SettingsRepository.SPEEDS[(currentSpeedIdx - 1).coerceAtLeast(0)])
                                2 -> viewModel.setAutoNext(!settings.autoNext)
                                3 -> viewModel.setLandscapeOnly(!settings.landscapeOnly)
                            }
                        } else {
                            viewModel.setTab((tab - 1).coerceAtLeast(0)); selected = 0
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (tab == 2) {
                            // 设置页：增大当前项的值
                            when (settingSelected) {
                                0 -> viewModel.setQualityMode((currentQuality + 1).coerceAtMost(qualityCount - 1))
                                1 -> viewModel.setSpeed(SettingsRepository.SPEEDS[(currentSpeedIdx + 1).coerceAtMost(speedCount - 1)])
                                2 -> viewModel.setAutoNext(!settings.autoNext)
                                3 -> viewModel.setLandscapeOnly(!settings.landscapeOnly)
                            }
                        } else {
                            viewModel.setTab((tab + 1).coerceAtMost(2)); selected = 0
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        when (tab) {
                            0, 1 -> list.getOrNull(selected)?.let { onPlay(it.awemeId) }
                            2 -> when (settingSelected) {
                                0 -> viewModel.setQualityMode((currentQuality + 1) % qualityCount)
                                1 -> viewModel.setSpeed(SettingsRepository.SPEEDS[(currentSpeedIdx + 1) % speedCount])
                                2 -> viewModel.setAutoNext(!settings.autoNext)
                                3 -> viewModel.setLandscapeOnly(!settings.landscapeOnly)
                            }
                        }
                        true
                    }
                    Key.Backspace, Key(KeyEvent.KEYCODE_FORWARD_DEL) -> {
                        if (tab in 0..1) list.getOrNull(selected)?.let { viewModel.delete(it.awemeId) }
                        true
                    }
                    Key.Escape, Key(KeyEvent.KEYCODE_BACK) -> { onBack(); true }
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            // 顶部标题 + Tab
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "我的", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(32.dp))
                TabChip("观看历史 (${history.size})", active = tab == 0)
                Spacer(Modifier.width(14.dp))
                TabChip("我的收藏 (${favorites.size})", active = tab == 1)
                Spacer(Modifier.width(14.dp))
                TabChip("播放设置", active = tab == 2)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (tab == 2) "上下选择 · 左右调整数值 · OK 循环切换 · 返回键回到播放"
                       else "上下选择 · 左右切换 Tab · OK 播放 · 删除键移除 · 返回键回到播放",
                color = Color(0xFF888888),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))

            when (tab) {
                0, 1 -> ListContent(
                    tab = tab,
                    list = list,
                    selected = selected,
                    gridState = gridState,
                    onPlay = onPlay,
                )
                2 -> SettingsContent(
                    settings = settings,
                    selected = settingSelected,
                    qualityLabels = SettingsRepository.QUALITY_LABELS,
                    speeds = SettingsRepository.SPEEDS,
                )
            }
        }
    }
}

@Composable
private fun ListContent(
    tab: Int,
    list: List<WatchHistory>,
    selected: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onPlay: (String) -> Unit,
) {
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
                HistoryCard(item = item, selected = index == selected, onClick = { onPlay(item.awemeId) })
            }
        }
    }
}

@Composable
private fun SettingsContent(
    settings: SettingsRepository.Settings,
    selected: Int,
    qualityLabels: List<String>,
    speeds: List<Float>,
) {
    val quality = settings.qualityMode.coerceIn(0, qualityLabels.size - 1)
    val speedIdx = speeds.indexOf(settings.speed).let { if (it < 0) 2 else it }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingRow(
            label = "清晰度",
            value = qualityLabels[quality],
            selected = selected == 0,
        )
        SettingRow(
            label = "播放倍速",
            value = "${speeds[speedIdx]}x",
            selected = selected == 1,
        )
        SettingRow(
            label = "自动连播",
            value = if (settings.autoNext) "开（播完切下一个）" else "关（单集循环）",
            selected = selected == 2,
        )
        SettingRow(
            label = "影视横屏优先",
            value = if (settings.landscapeOnly) "开（横屏影视长视频排前面）" else "关（默认推荐流）",
            selected = selected == 3,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "清晰度：选择后当前视频自动重新加载\n倍速：播放在线生效\n自动连播：关闭则单个视频循环播放\n影视横屏优先：按「横屏+长视频+影视解说」加权排序，把稀少的横屏影视内容捞到前面（切换时重建视频流）",
            color = Color(0xFF666666),
            fontSize = 13.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1A1A1A) else Color(0xFF141414))
            .then(
                if (selected) Modifier.border(3.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            )
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color.White, fontSize = 20.sp, modifier = Modifier.width(200.dp))
        Text(text = value, color = Color(0xFFFF9800), fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
private fun HistoryCard(item: WatchHistory, selected: Boolean, onClick: () -> Unit) {
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
                Text(text = "★", color = Color(0xFFFFC107), fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = item.title.ifBlank { "（无标题）" }, color = Color.White, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(text = "@${item.author}", color = Color(0xFF999999), fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color(0xFFFF9800),
                trackColor = Color(0xFF333333),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}
