package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MaliceTileDef
import com.neonkaiju.kot.model.PlayerState

private fun MaliceTileDef.toUi() = TileUi(id, nom, texte)

private fun PlayerState.toUi() = PlayerUi(id, monster, hp, vp, energy, isAlive)

/** La décision n'est affichée que si l'occupant de la Tour est le joueur humain. */
internal fun GameState.toFleeUi(): FleeUi? {
    val human = players.first { !it.isBot }
    return pendingFlee
        ?.takeIf { it.occupantId == human.id }
        ?.let { pending ->
            FleeUi(
                attackerName = players.first { it.id == pending.attackerId }.monster.displayName,
                damageTaken = pending.damageTaken,
                remainingHp = human.hp,
            )
        }
}

internal fun GameState.toMalicePickUi(): MalicePickUi? {
    val human = players.first { !it.isBot }
    return pendingMaliceLevels.firstOrNull()
        ?.takeIf { !activePlayer.isBot }
        ?.let { level ->
            MalicePickUi(
                level = level,
                tiles = availableTiles.filter { it.niveau == level }.map { it.toUi() },
                ownedTiles = human.tiles.map { it.toUi() },
            )
        }
}

fun GameState.toGameOverUiState() = GameOverUiState(
    winnerName = players.firstOrNull { it.id == winnerId }?.monster?.displayName,
    // Même priorité que le moteur : le dernier survivant l'emporte avant le test des 20 ★.
    endReason = when {
        winnerId == null -> GameEndReason.ALL_ELIMINATED
        players.count { it.isAlive } == 1 -> GameEndReason.LAST_SURVIVOR
        else -> GameEndReason.TWENTY_STARS
    },
    humanId = players.first { !it.isBot }.id,
    seed = rng.seed,
    ranking = players
        .sortedWith(compareByDescending<PlayerState> { it.isAlive }.thenByDescending { it.vp }.thenByDescending { it.hp })
        .map { it.toUi() },
)
