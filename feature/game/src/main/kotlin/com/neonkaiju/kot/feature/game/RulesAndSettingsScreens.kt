package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RulesScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Text("RÈGLES DE NÉON KAIJU", color = NeonPalette.NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("1. But du jeu: Atteindre 20 ★ et être vivant en fin de tour, ou être le dernier Kaiju en vie.", color = NeonPalette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("2. Tour de jeu: Le premier lancer est automatique. Vous avez 3 lancers au maximum. Touchez chaque dé pour le conserver ou le libérer. Les résultats sont résolus automatiquement, dans l’ordre choisi.", color = NeonPalette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("3. Symboles des dés:\n- 1, 2, 3: Obtenez les points correspondants si vous avez au moins un brelan (3 dés identiques).\n- Énergie: Gagnez des Cellules pour acheter des cartes.\n- Soin (♥): Gagnez 1 point de vie. Ne fonctionne pas dans la Tour Centrale.\n- Griffes: Infligez des dégâts. Si vous êtes hors de Tokyo, vous tapez celui dans Tokyo. Si vous êtes dans Tokyo, vous tapez TOUS les autres.", color = NeonPalette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("4. La Tour Centrale (Tokyo):\n- Après la résolution, si la Tour est vide, le joueur actif doit y entrer (+1 ★), même sans griffe.\n- Si vous y commencez votre tour, vous gagnez 2 VP.\n- Vous pouvez fuir quand vous prenez des dégâts d'un autre Kaiju.", color = NeonPalette.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("5. Surcharge : un triple de 1 donne +2, un triple de 2 donne +1. Aux paliers 3 / 6 / 10, choisissez un pouvoir encore disponible.", color = NeonPalette.TextPrimary)
        }
        Spacer(Modifier.height(16.dp))
        NeonButton("Retour", onClick = onBack, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
    }
}

@Composable
fun SettingsScreen(fastBot: Boolean, onToggleFastBot: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp)
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        Text("RÉGLAGES", color = NeonPalette.NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            NeonButton(
                text = "Vitesse Bot : ${if (fastBot) "RAPIDE" else "NORMALE"}",
                onClick = onToggleFastBot,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        NeonButton("Retour", onClick = onBack, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, start = 16.dp, end = 16.dp))
    }
}
