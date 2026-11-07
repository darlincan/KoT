package com.neonkaiju.kot.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class RulesPage(val title: String, val lines: List<String>)

/** Reprend le §7 des spécifications, avec le vocabulaire Néon Kaiju. */
internal val rulesPages = listOf(
    RulesPage(
        "But",
        listOf(
            "Gagnez en finissant un tour avec 20 ★, ou en restant le dernier Kaiju en vie.",
            "Il faut être vivant à la fin du tour pour gagner avec 20 ★.",
            "Si tous les Kaiju sont éliminés en même temps, personne ne gagne.",
            "Chaque Kaiju commence avec 10 ♥, 0 ★ et 0 Surcharge.",
            "À 0 ♥, un Kaiju est éliminé : ses cartes POUVOIR, ses tuiles et ses Cellules sont défaussées, et il libère la Tour s'il l'occupait.",
        ),
    ),
    RulesPage(
        "Déroulement",
        listOf(
            "1. Lancer : 6 dés, 3 lancers au maximum, le premier est automatique. Gardez les dés voulus, reprenez-en si besoin, arrêtez quand vous voulez.",
            "2. Résoudre : seuls comptent les dés du dernier lancer, dans l'ordre de votre choix.",
            "3. Entrer : si la Tour centrale est libre, vous devez y entrer.",
            "4. Acheter : dépensez vos Cellules au Marché noir.",
            "5. Finir : les effets de fin de tour s'appliquent, puis la victoire est testée.",
        ),
    ),
    RulesPage(
        "Dés",
        listOf(
            "1, 2, 3 : un triple de n rapporte n ★, et chaque dé de plus du même chiffre rapporte 1 ★.",
            "⚡ : 1 Cellule par dé, conservée jusqu'à dépense.",
            "♥ : 1 ♥ par dé hors de la Tour, sans dépasser 10 ♥. Dans la Tour, ces dés ne soignent pas.",
            "🐾 hors de la Tour : 1 dégât par griffe au seul occupant. Les Kaiju hors de la Tour ne se blessent jamais entre eux.",
            "🐾 dans la Tour : 1 dégât par griffe à tous les autres Kaiju.",
        ),
    ),
    RulesPage(
        "Tour centrale",
        listOf(
            "Si la Tour est libre, le joueur actif doit y entrer et gagne 1 ★. Elle est vide au début : les griffes du premier joueur ne touchent personne.",
            "Commencer son tour dans la Tour rapporte 2 ★.",
            "Dans la Tour, les ♥ des dés ne soignent pas : seules les cartes et les tuiles le peuvent.",
            "Blessé par les griffes d'un autre Kaiju, l'occupant choisit de fuir ou de rester. Les ♥ sont perdus dans les deux cas.",
            "On ne quitte la Tour qu'en fuyant ainsi : une perte de ♥ due à une carte ne permet pas de fuir.",
        ),
    ),
    RulesPage(
        "Surcharge",
        listOf(
            "Un triple de 1 rapporte 2 points de Surcharge, un triple de 2 en rapporte 1. Les 3 n'en rapportent pas.",
            "En atteignant ou dépassant les paliers 3, 6 et 10, choisissez une tuile de ce palier parmi celles encore disponibles.",
            "Un Kaiju détient au plus une tuile par palier.",
            "La jauge ne recule jamais : repasser un palier déjà franchi ne donne rien.",
        ),
    ),
    RulesPage(
        "Marché noir",
        listOf(
            "Achetez une ou plusieurs des 3 cartes visibles en payant leur prix en Cellules.",
            "Une carte achetée est remplacée aussitôt et la nouvelle est disponible dans le même tour.",
            "Pour 2 Cellules, défaussez les 3 cartes visibles et révélez-en 3 nouvelles.",
            "Une carte POUVOIR reste devant vous jusqu'à la fin de la partie. Une carte ACTION s'applique immédiatement, puis est défaussée.",
        ),
    ),
)

@Composable
fun RulesScreen(onBack: () -> Unit) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RÈGLES", color = NeonPalette.NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            NeonButton("Retour", onClick = onBack, accentColor = NeonPalette.NeonCyan)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            rulesPages.forEachIndexed { index, page ->
                RulesTab(page.title, isSelected = index == pageIndex, Modifier.weight(1f)) { pageIndex = index }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rulesPages[pageIndex].lines.forEach { line ->
                Text(line, color = NeonPalette.TextPrimary, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun RulesTab(title: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = if (isSelected) NeonPalette.NeonPink else NeonPalette.NeonCyan.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { selected = isSelected }
            .border(1.dp, accent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) NeonPalette.NeonPink else NeonPalette.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingsScreen(state: SettingsUiState, onToggleFastBot: () -> Unit, onBack: () -> Unit) {
    val fastBot = state.fastBots
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonPalette.Background)
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("RÉGLAGES", color = NeonPalette.NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        SettingSwitchRow(
            title = "Bots rapides",
            description = "Raccourcit la pause entre les actions des bots.",
            checked = fastBot,
            onToggle = onToggleFastBot
        )
        Spacer(Modifier.weight(1f))
        NeonButton("Retour", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** Ligne de réglage : toute la ligne est cliquable, et TalkBack l'annonce comme un seul interrupteur. */
@Composable
private fun SettingSwitchRow(title: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(NeonPalette.Surface, RoundedCornerShape(10.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = NeonPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = NeonPalette.TextPrimary.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonPalette.DarkLabel,
                checkedTrackColor = NeonPalette.NeonPink,
            )
        )
    }
}
