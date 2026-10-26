package com.neonkaiju.kot.ai

import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*

interface BotPolicy {
    fun chooseAction(state: GameState): GameAction
}

class HeuristicBotPolicy : BotPolicy {
    override fun chooseAction(state: GameState): GameAction {
        val legal = GameEngine.legalActions(state)
        require(legal.isNotEmpty()) { "Aucune action légale disponible pour le bot" }

        legal.filterIsInstance<GameAction.PickMaliceTile>().firstOrNull()?.let { return it }

        val bot = if (state.phase == TurnPhase.DECISION_FUITE) {
            state.players.first { it.id == state.pendingFlee!!.occupantId }
        } else state.activePlayer

        return when (state.phase) {
            TurnPhase.DECISION_FUITE -> {
                if (bot.hp <= 4 && bot.vp < 18 && GameAction.Flee in legal) GameAction.Flee else GameAction.Stay
            }
            TurnPhase.LANCER -> chooseRollAction(state, bot, legal)
            TurnPhase.RESOUDRE -> {
                legal.filterIsInstance<GameAction.ResolveSymbols>()
                    .minByOrNull { it.symbolType.ordinal } ?: legal.first()
            }
            TurnPhase.ACHETER -> {
                val buyActions = legal.filterIsInstance<GameAction.BuyCard>()
                if (buyActions.isNotEmpty()) {
                    buyActions.maxBy { action ->
                        state.shop.first { it.id == action.cardId }.cout
                    }
                } else {
                    GameAction.EndTurn
                }
            }
            else -> legal.first()
        }
    }

    private fun chooseRollAction(state: GameState, bot: PlayerState, legal: List<GameAction>): GameAction {
        val inTokyo = state.tokyoOccupantId == bot.id
        val counts = state.dice.groupingBy { it.face }.eachCount()

        for (die in state.dice) {
            val shouldKeep = when (die.face) {
                DieFace.HEART -> !inTokyo && bot.hp <= 6
                DieFace.CLAW -> !inTokyo || bot.hp >= 6
                DieFace.ENERGY -> true
                DieFace.THREE -> (counts[DieFace.THREE] ?: 0) >= 2
                DieFace.TWO -> (counts[DieFace.TWO] ?: 0) >= 2
                DieFace.ONE -> (counts[DieFace.ONE] ?: 0) >= 2 && bot.malice < 10
            }
            if (die.isKept != shouldKeep) {
                val toggle = GameAction.ToggleKeep(die.id)
                if (toggle in legal) return toggle
            }
        }

        return if (state.dice.any { !it.isKept } && GameAction.Reroll in legal) {
            GameAction.Reroll
        } else {
            GameAction.StopRolling
        }
    }
}
