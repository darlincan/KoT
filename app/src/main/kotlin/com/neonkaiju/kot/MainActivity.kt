package com.neonkaiju.kot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.neonkaiju.kot.feature.game.GameViewModel
import com.neonkaiju.kot.feature.game.NeonKaijuTheme
import com.neonkaiju.kot.navigation.KotNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // partage par tous les ecrans de l'application
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeonKaijuTheme {
                KotNavHost(gameViewModel = gameViewModel)
            }
        }
    }
}
