package com.neonkaiju.kot.ai

import com.neonkaiju.kot.model.EffectOperation
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MaliceTileDef
import com.neonkaiju.kot.model.TurnPhase
import kotlin.math.abs

const val MIN_EVALUATION = -1_000_000.0
const val MAX_EVALUATION = 1_000_000.0

private const val POINTS_PER_STAR = 10.0
private const val POINTS_PER_HEART = 4.0
private const val POINTS_PER_CELL = 2.0
private const val POINTS_PER_TILE = 12.0

/** Une carte POUVOIR vaut plus que les Cellules payées, sinon le bot n'en achèterait jamais. */
private const val POINTS_PER_CARD_COST = 2.5

private const val CRITICAL_HEARTS = 4
private const val POINTS_PER_MISSING_CRITICAL_HEART = 25.0

/** Les ★ comptent bien plus à partir de ce seuil, pour soi comme chez l'adversaire. */
private const val STARS_NEAR_VICTORY = 14
private const val THREATENING_STARS = 15

private const val TOWER_GAIN = 15.0
private const val TOWER_RISK = 30.0
private const val POINTS_PER_OPPONENT_HEART = 1.0

/**
 * Note la situation d'un joueur : plus elle est haute, mieux il se porte.
 *
 * Fonction pure qui ne lit que l'information publique : ni les dés, ni la pioche, ni le marché.
 * Simuler une action puis l'évaluer ne donne donc aucun avantage caché au bot.
 */
fun evaluate(state: GameState, playerId: Int): Double {
    val player = state.players.first { it.id == playerId }
    if (!player.isAlive || player.hp <= 0) return MIN_EVALUATION
    if (state.phase == TurnPhase.FIN_PARTIE) {
        return if (state.winnerId == playerId) MAX_EVALUATION else MIN_EVALUATION
    }
    if (player.vp >= 20) return MAX_EVALUATION

    val opponents = state.players.filter { it.id != playerId && it.isAlive }
    val nearVictory = (player.vp - STARS_NEAR_VICTORY).coerceAtLeast(0)
    val stars = POINTS_PER_STAR * player.vp + 3.0 * nearVictory * nearVictory

    val missingCritical = (CRITICAL_HEARTS - player.hp).coerceAtLeast(0)
    val hearts = POINTS_PER_HEART * player.hp - POINTS_PER_MISSING_CRITICAL_HEART * missingCritical

    val cells = POINTS_PER_CELL * player.energy
    val tiles = player.tiles.sumOf(::tileValue)
    val cards = player.cards.sumOf { POINTS_PER_CARD_COST * it.cout }

    val tower = if (state.tokyoOccupantId == playerId) {
        TOWER_GAIN - TOWER_RISK * opponents.size / player.hp
    } else 0.0

    val threats = opponents.sumOf { opponent ->
        val closeToVictory = (opponent.vp - THREATENING_STARS).coerceAtLeast(0)
        2.0 * closeToVictory * closeToVictory + POINTS_PER_OPPONENT_HEART * opponent.hp
    }

    return stars + hearts + cells + tiles + cards + tower - threats
}

private fun tileValue(tile: MaliceTileDef): Double =
    POINTS_PER_TILE + tile.effets.sumOf { effect ->
        val weight = when (effect.operation) {
            EffectOperation.gainVp -> POINTS_PER_STAR
            EffectOperation.gainHp -> POINTS_PER_HEART
            EffectOperation.gainEnergy, EffectOperation.modifyCost -> POINTS_PER_CELL
            EffectOperation.dealDamage -> 3.0
            EffectOperation.annulerDegat -> 5.0
            EffectOperation.rerollDice, EffectOperation.changerFace, EffectOperation.setFlag -> 1.0
        }
        weight * abs(effect.valeur)
    }
