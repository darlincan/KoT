package com.neonkaiju.kot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.neonkaiju.kot.feature.game.GameViewModel
import com.neonkaiju.kot.feature.game.NeonKaijuTheme
import com.neonkaiju.kot.feature.game.SoundPlayer
import com.neonkaiju.kot.navigation.KotNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // partage par tous les ecrans de l'application
    private val gameViewModel: GameViewModel by viewModels()

    @Inject lateinit var soundPlayer: SoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeonKaijuTheme {
                KotNavHost(gameViewModel = gameViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        soundPlayer.onForeground()
    }

    override fun onStop() {
        soundPlayer.onBackground()
        super.onStop()
    }
}
