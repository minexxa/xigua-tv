package com.xigua.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xigua.tv.player.PlayerViewModel
import com.xigua.tv.ui.PlayerScreen
import com.xigua.tv.ui.theme.XiguaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiguaTheme {
                val viewModel: PlayerViewModel = viewModel()
                PlayerScreen(viewModel)
            }
        }
    }
}
