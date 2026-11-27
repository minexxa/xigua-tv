package com.xigua.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xigua.tv.player.StreamViewModel
import com.xigua.tv.ui.StreamScreen
import com.xigua.tv.ui.theme.XiguaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiguaTheme {
                val viewModel: StreamViewModel = viewModel()
                StreamScreen(viewModel)
            }
        }
    }
}
