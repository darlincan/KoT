package com.neonkaiju.kot.ai

import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*

interface BotPolicy {
    fun chooseAction(state: GameState): GameAction
}

/** One policy for all bots; priorities depend on health, opponents, and the current dice. */
class HeuristicBotPolicy : BotPolicy {
    override fun chooseAction(state: GameState): GameAction {
        val legal = GameEngine.legalActions(state)
        require(legal.isNotEmpty()) { "Aucune action légale disponible pour le bot" }
        val bot = state.decisionPlayer
        legal.filterIsInstance<GameAction.PickMaliceTile>().maxByOrNull { action ->
            state.availableTiles.first { it.id == action.tileId }.effets.sumOf { effectValue(it, bot) }
        }?.let { return it }

        return when (state.phase) {
            TurnPhase.DECISION_FUITE -> {
                val opponents = state.players.filter { it.isAlive && it.id != bot.id }
                val threshold = if (bot.vp >= 18) 2 else if (opponents.size >= 3) 5 else 3
                if (bot.hp <= threshold) GameAction.Flee else GameAction.Stay
            }
            TurnPhase.LANCER -> chooseRollAction(state, bot, legal)
            TurnPhase.RESOUDRE -> legal.filterIsInstance<GameAction.ResolveSymbols>()
                .minByOrNull { state.resolutionOrder.indexOf(it.symbolType) } ?: legal.first()
            TurnPhase.ACHETER -> {
                legal.filterIsInstance<GameAction.BuyCard>().map { action ->
                    val card = state.shop.first { it.id == action.cardId }
                    val result = GameEngine.reduce(state, action)
                    val after = result.players.first { it.id == bot.id }
                    val score = if (!after.isAlive) -1000.0 else {
                        (after.vp - bot.vp) * 4.0 + (after.hp - bot.hp) * (if (bot.hp <= 5) 3.0 else 1.0) +
                            (after.energy - bot.energy) * 0.6 +
                            state.players.filter { it.id != bot.id }.sumOf { old ->
                                (old.hp - result.players.first { it.id == old.id }.hp).toDouble()
                            } + if (card.type == CardType.POUVOIR) card.effets.sumOf { effectValue(it, bot) } * 2 else 0.0
                    }
                    action to score
                }.filter { it.second > 0 }.maxByOrNull { it.second }?.first ?: GameAction.EndTurn
            }
            else -> legal.first()
        }.also { require(it in legal) }
    }

    /** Explanations describe the same action that was selected, without consuming randomness. */
    fun explainAction(state: GameState, action: GameAction): String {
        val name = state.decisionPlayer.monster.displayName
        val reason = when (action) {
            is GameAction.ToggleKeep -> {
                val die = state.dice.first { it.id == action.dieId }
                if (die.isKept) "libère le dé ${die.id + 1} pour chercher un résultat plus utile"
                else when (die.face) {
                    DieFace.HEART -> "conserve un cœur pour récupérer des PV hors de la Tour"
                    DieFace.ENERGY -> "conserve un éclair pour financer un achat"
                    DieFace.CLAW -> if (state.tokyoOccupantId == state.activePlayer.id)
                        "conserve une griffe pour attaquer les monstres hors de la Tour"
                        else "conserve une griffe pour attaquer l’occupant de la Tour"
                    else -> "conserve un chiffre pour compléter ou renforcer un brelan"
                }
            }
            GameAction.Reroll -> "relance les ${state.dice.count { !it.isKept }} dés non conservés pour améliorer son résultat"
            GameAction.StopRolling -> "termine ses lancers et résout les dés conservés"
            GameAction.Flee -> "fuit la Tour pour limiter le risque d’élimination"
            GameAction.Stay -> "reste dans la Tour pour défendre sa position et gagner des points au prochain tour"
            is GameAction.BuyCard -> "achète ${state.shop.first { it.id == action.cardId }.nom}, évaluée utile selon ses ressources et ses effets"
            is GameAction.PickMaliceTile -> "choisit ${state.availableTiles.first { it.id == action.tileId }.nom} selon sa santé et ses points"
            GameAction.EndTurn -> "termine son tour : aucun achat supplémentaire n’est jugé assez utile"
            is GameAction.ResolveSymbols -> "résout automatiquement ses résultats"
            GameAction.SweepShop -> "renouvelle les cartes du marché"
            is GameAction.SetResolutionOrder -> "prépare l’ordre de résolution de ses résultats"
        }
        return "$name $reason."
    }

    private fun effectValue(effect: EffectDef, bot: PlayerState): Double = when (effect.operation) {
        EffectOperation.gainHp -> effect.valeur * if (bot.hp <= 5) 2.0 else 0.6
        EffectOperation.gainVp -> effect.valeur * if (bot.vp >= 15) 4.0 else 2.0
        EffectOperation.gainEnergy -> effect.valeur * 1.5
        EffectOperation.annulerDegat -> effect.valeur * if (bot.hp <= 6) 3.0 else 2.0
        EffectOperation.modifyCost -> -effect.valeur * 1.5
        EffectOperation.dealDamage -> effect.valeur * 1.2
        EffectOperation.changerFace, EffectOperation.rerollDice -> 1.5
        EffectOperation.setFlag -> 0.0
    }

    private fun chooseRollAction(state: GameState, bot: PlayerState, legal: List<GameAction>): GameAction {
        val inTokyo = state.tokyoOccupantId == bot.id
        val counts = state.dice.groupingBy { it.face }.eachCount()
        val number = listOf(DieFace.ONE, DieFace.TWO, DieFace.THREE).maxBy { face ->
            (counts[face] ?: 0) * 10 + when (face) {
                DieFace.ONE -> if (bot.malice in listOf(1, 2, 4, 5, 8, 9)) 4 else 0
                DieFace.TWO -> if (bot.malice in listOf(2, 5, 9)) 3 else 1
                else -> 2
            }
        }
        val healingNeeded = if (inTokyo) 0 else bot.maxHp - bot.hp
        val canAttack = inTokyo || state.tokyoOccupantId != null
        val desiredEnergy = (state.shop.minOfOrNull { it.cout } ?: 3) - bot.energy
        val priority = ((state.rng.seed xor bot.id.toLong() xor state.rng.cursor.toLong()) and 1L).toInt()
        val desired = state.dice.filter { die ->
            when (die.face) {
                DieFace.HEART -> healingNeeded >= 3 && state.dice.filter { it.face == DieFace.HEART }.indexOf(die) < healingNeeded
                DieFace.CLAW -> canAttack && (inTokyo || bot.vp < 16 || (counts[DieFace.CLAW] ?: 0) >= 2)
                DieFace.ENERGY -> desiredEnergy > 0 && (priority == 0 || (counts[number] ?: 0) < 2) && healingNeeded < 6
                else -> die.face == number && (counts[number] ?: 0) >= 2 && healingNeeded < 6
            }
        }.map { it.id }.toSet()

        state.dice.firstOrNull { it.isKept != (it.id in desired) }?.let {
            val toggle = GameAction.ToggleKeep(it.id)
            if (toggle in legal) return toggle
        }
        return if (GameAction.Reroll in legal) GameAction.Reroll else GameAction.StopRolling
    }
}
