package com.neonkaiju.kot.ai

import com.neonkaiju.kot.model.CardDef
import com.neonkaiju.kot.model.CardType
import com.neonkaiju.kot.model.EffectDef
import com.neonkaiju.kot.model.EffectOperation
import com.neonkaiju.kot.model.EffectTarget
import com.neonkaiju.kot.model.EffectTrigger
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MaliceTileDef
import com.neonkaiju.kot.model.TurnPhase

/**
 * Fonction d'évaluation d'un état du point de vue d'un joueur (§ Phase 5) :
 * ★, ♥, Cellules, position dans la Tour, pouvoirs possédés, et la menace que représentent les adversaires.
 *
 * Plus le score est haut, meilleur est l'état pour [playerId]. Seules les différences de score ont un sens.
 */
object StateEvaluator {

    const val WIN = 10_000.0
    const val LOSS = -10_000.0

    fun evaluate(state: GameState, playerId: Int): Double {
        val me = state.players.first { it.id == playerId }
        if (state.phase == TurnPhase.FIN_PARTIE) {
            return if (state.winnerId == playerId) WIN else LOSS
        }
        if (!me.isAlive) return LOSS

        val opponents = state.players.filter { it.isAlive && it.id != playerId }
        val inTokyo = state.tokyoOccupantId == playerId

        var score = 0.0
        score += vpValue(me.vp)
        score += hpValue(me.hp, opponents.size, inTokyo)
        score += me.energy * ENERGY_WEIGHT
        score += me.malice * MALICE_WEIGHT
        score += me.cards.sumOf { powerValue(it.effets) }
        score += me.tiles.sumOf { powerValue(it.effets) }
        if (inTokyo) score += TOKYO_BONUS

        // Un adversaire proche de la victoire est une menace ; un adversaire affaibli, une opportunité.
        for (opp in opponents) {
            score -= vpValue(opp.vp) * OPPONENT_VP_WEIGHT
            score -= opp.hp * OPPONENT_HP_WEIGHT
        }
        score += (3 - opponents.size) * ELIMINATED_OPPONENT_BONUS
        return score
    }

    /** Les ★ valent de plus en plus à l'approche des 20. */
    private fun vpValue(vp: Int): Double = vp * 3.0 + if (vp >= 15) (vp - 14) * 4.0 else 0.0

    /**
     * Chaque ♥ compte, mais les derniers comptent beaucoup plus : sous le seuil de danger, un tour de griffes
     * peut éliminer le monstre. Dans la Tour, tout le monde frappe, donc le seuil monte.
     */
    private fun hpValue(hp: Int, opponents: Int, inTokyo: Boolean): Double {
        val danger = if (inTokyo) 2 + opponents * 2 else 3
        val safe = hp.coerceAtMost(danger)
        val extra = (hp - danger).coerceAtLeast(0)
        return safe * 5.0 + extra * 1.5
    }

    /** Valeur estimée, en points d'évaluation, d'un pouvoir permanent (carte POUVOIR ou tuile). */
    fun powerValue(effects: List<EffectDef>): Double = effects.sumOf { effect ->
        val base = when (effect.operation) {
            EffectOperation.gainVp -> 3.0 * effect.valeur
            EffectOperation.gainEnergy -> 1.0 * effect.valeur
            EffectOperation.gainHp -> 1.5 * effect.valeur
            EffectOperation.dealDamage -> 1.0 * effect.valeur * targetCount(effect.cible)
            EffectOperation.annulerDegat -> 2.5 * effect.valeur
            EffectOperation.modifyCost -> -1.5 * effect.valeur
            EffectOperation.rerollDice -> 2.5 * effect.valeur
            EffectOperation.changerFace -> 1.5 * effect.valeur
            EffectOperation.setFlag -> 1.0
        }
        val selfHarm = effect.operation == EffectOperation.dealDamage && effect.cible in SELF_TARGETS
        val conditional = if (effect.condition != null) 0.6 else 1.0
        (if (selfHarm) -base else base) * frequency(effect.declencheur) * conditional
    }

    fun cardValue(card: CardDef): Double = if (card.type == CardType.POUVOIR) powerValue(card.effets) else 0.0

    fun tileValue(tile: MaliceTileDef): Double = powerValue(tile.effets)

    /** Combien de fois, en gros, un effet se déclenche avant la fin de la partie. */
    private fun frequency(trigger: EffectTrigger): Double = when (trigger) {
        EffectTrigger.DEBUT_TOUR, EffectTrigger.FIN_TOUR, EffectTrigger.AVANT_LANCER,
        EffectTrigger.APRES_RESOLUTION, EffectTrigger.PERMANENT -> 3.0
        EffectTrigger.AVANT_DEGAT_SUBI -> 1.5
        EffectTrigger.APRES_DEGAT_INFLIGE -> 1.2
        EffectTrigger.ENTREE_TOKYO -> 0.8
        EffectTrigger.ACHAT -> 0.0
    }

    private fun targetCount(target: EffectTarget): Double = when (target) {
        EffectTarget.AUTRES, EffectTarget.TOUS, EffectTarget.HORS_TOKYO -> 2.0
        EffectTarget.VOISINS -> 1.5
        else -> 1.0
    }

    private val SELF_TARGETS = setOf(EffectTarget.SOI)

    private const val ENERGY_WEIGHT = 0.5
    private const val MALICE_WEIGHT = 0.4
    private const val TOKYO_BONUS = 2.0
    private const val OPPONENT_VP_WEIGHT = 0.35
    private const val OPPONENT_HP_WEIGHT = 0.4
    private const val ELIMINATED_OPPONENT_BONUS = 6.0
}
