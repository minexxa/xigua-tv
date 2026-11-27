package com.xigua.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xigua.tv.player.MyViewModel
import com.xigua.tv.player.StreamViewModel
import com.xigua.tv.ui.MyScreen
import com.xigua.tv.ui.StreamScreen
import com.xigua.tv.ui.theme.XiguaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiguaTheme {
                val streamViewModel: StreamViewModel = viewModel()
                val myViewModel: MyViewModel = viewModel()
                // false=视频流, true=我的页
                val showMy = remember { mutableStateOf(false) }

                if (showMy.value) {
                    BackHandler { showMy.value = false }
                    MyScreen(
                        viewModel = myViewModel,
                        onPlay = { id ->
                            streamViewModel.playById(id) { ok ->
                                // 解析成功后回到播放页（失败留在列表，可重试）
                                if (ok) showMy.value = false
                            }
                        },
                        onBack = { showMy.value = false },
                    )
                } else {
                    StreamScreen(
                        viewModel = streamViewModel,
                        onOpenMy = { showMy.value = true },
                    )
                }
            }
        }
    }
}
