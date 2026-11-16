package com.neonkaiju.kot.feature.game

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Une capture par écran, en paysage, sur un téléphone et une tablette, avec API 29 et 31. */
@RunWith(Parameterized::class)
class ScreensSnapshotTest(private val device: DeviceConfig, private val deviceName: String, private val apiLevel: Int) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = device,
        showSystemUi = false,
        theme = "android:Theme.Material.NoActionBar",
        environment = detectEnvironment().copy(compileSdkVersion = apiLevel),
    )

    private fun capture(name: String, content: @Composable () -> Unit) =
        paparazzi.snapshot("${deviceName}_api${apiLevel}_$name") { NeonKaijuTheme(content) }

    @Test fun home() = capture("01_accueil") { HomeScreen(PreviewData.home, onEvent = {}) }
    @Test fun picker() = capture("02_monstre") { MonsterPickerScreen(PreviewData.picker, onEvent = {}) }
    @Test fun game() = capture("03_jeu") { GameScreen(PreviewData.game, onEvent = {}) }
    @Test fun botTurn() = capture("03b_tour_bot") { GameScreen(PreviewData.botTurn, onEvent = {}) }
    @Test fun shop() = capture("04_marche") { ShopSheet(PreviewData.shop, onBuy = { _, _ -> }, onSweep = {}, onClose = {}) }
    @Test fun flee() = capture("05_fuir_rester") { GameScreen(PreviewData.flee, onEvent = {}) }
    @Test fun malice() = capture("06_palier") { GameScreen(PreviewData.malicePick, onEvent = {}) }
    @Test fun gameOver() = capture("07_fin") { GameOverScreen(PreviewData.gameOver, onReplay = {}, onBackHome = {}) }
    @Test fun rules() = capture("regles") { RulesScreen(onBack = {}) }
    @Test fun settings() = capture("reglages") { SettingsScreen(PreviewData.settings, onToggleFastBot = {}, onToggleFleeCountdown = {}, onBack = {}) }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}_api{2}")
        fun devices() = listOf(29, 31).flatMap { api ->
            listOf(
                arrayOf(DeviceConfig.PIXEL_5.copy(orientation = ScreenOrientation.LANDSCAPE, softButtons = false), "telephone", api),
                arrayOf(DeviceConfig.NEXUS_10.copy(orientation = ScreenOrientation.LANDSCAPE, softButtons = false), "tablette", api),
            )
        }
    }
}
