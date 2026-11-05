package com.neonkaiju.kot.ai

import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One awaited action at a time; the UI owns and cancels the coroutine on navigation/lifecycle changes. */
class BotTurnPresenter(private val policy: BotPolicy = HeuristicBotPolicy()) {
    suspend fun nextAction(state: GameState, fast: Boolean, isCurrent: () -> Boolean): GameAction? {
        if (!isCurrent()) return null
        val action = policy.chooseAction(state)
        delay(delayMillis(action, fast))
        currentCoroutineContext().ensureActive()
        return if (isCurrent()) action else null
    }

    fun delayMillis(action: GameAction, fast: Boolean): Long = when {
        fast -> 400L
        action is GameAction.ToggleKeep -> 450L
        action == GameAction.EndTurn -> 2200L // Leave the resolved results on screen before changing player.
        else -> 1800L // Rolls, resolution, purchases and Tokyo decisions.
    }
}
