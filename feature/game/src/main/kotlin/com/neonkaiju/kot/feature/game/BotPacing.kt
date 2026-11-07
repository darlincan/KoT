package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState

/** Décision d'un bot pour un état donné : choisie une seule fois, affichée puis jouée. */
internal data class BotPlan(val state: GameState, val action: GameAction, val explanation: String)

/**
 * Temps pendant lequel l'action d'un bot reste affichée avant d'être jouée (règle transverse 1 : 400 ms au moins).
 * Le rythme normal laisse lire l'explication ; le mode rapide reste disponible dans les réglages.
 */
internal fun botDelayMillis(action: GameAction, fast: Boolean): Long = when {
    fast -> 400L
    action is GameAction.ToggleKeep -> 450L
    action == GameAction.EndTurn -> 2200L // laisse le résultat du tour à l'écran avant de changer de joueur.
    else -> 1800L // lancers, résolution, achats et décisions de la Tour.
}
