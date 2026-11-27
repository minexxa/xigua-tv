package com.xigua.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xigua.tv.player.MyViewModel
import com.xigua.tv.player.StreamViewModel
import com.xigua.tv.ui.HomeScreen
import com.xigua.tv.ui.MyScreen
import com.xigua.tv.ui.StreamScreen
import com.xigua.tv.ui.theme.XiguaTheme

class MainActivity : ComponentActivity() {

    private enum class Page { STREAM, HOME, MY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiguaTheme {
                val streamViewModel: StreamViewModel = viewModel()
                val myViewModel: MyViewModel = viewModel()
                var page by remember { mutableStateOf(Page.STREAM) }

                // 返回键：我的页 -> 主页；主页 -> 退出；沉浸流交给 StreamScreen 回调切主页
                BackHandler(enabled = page == Page.MY) { page = Page.HOME }
                BackHandler(enabled = page == Page.HOME) { finish() }

                when (page) {
                    Page.STREAM -> StreamScreen(
                        viewModel = streamViewModel,
                        onOpenMy = { page = Page.MY },
                        onBack = { page = Page.HOME },
                    )
                    Page.HOME -> HomeScreen(
                        streamViewModel = streamViewModel,
                        onResumeFeed = { page = Page.STREAM },
                        onOpenMy = { page = Page.MY },
                        onExit = { finish() },
                    )
                    Page.MY -> MyScreen(
                        viewModel = myViewModel,
                        onPlay = { id ->
                            streamViewModel.playById(id) { ok ->
                                if (ok) page = Page.STREAM
                            }
                        },
                        onBack = { page = Page.HOME },
                    )
                }
            }
        }
    }
}
