package com.neonkaiju.kot.ai

import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*

/**
 * Décide l'action d'un bot. Une politique ne choisit que parmi [GameEngine.legalActions] :
 * elle ne peut pas tricher, seulement mal jouer.
 */
interface BotPolicy {
    fun chooseAction(state: GameState): GameAction
}

/** Le joueur dont on attend la décision : l'occupant de la Tour pour Fuir ou Rester, le joueur actif sinon. */
fun GameState.deciderId(): Int =
    if (phase == TurnPhase.DECISION_FUITE && pendingMaliceLevels.isEmpty()) pendingFlee!!.occupantId
    else activePlayer.id

/**
 * Bot heuristique, le même pour les 3 adversaires (§ Phase 5) :
 * - dés : garde ce qui sert sa situation (griffes, cœurs, Cellules, brelans) et relance le reste ;
 * - résolution : se soigne, marque, se recharge, puis frappe ;
 * - tuiles et achats : comparés avec [StateEvaluator], en simulant l'achat par [GameEngine.reduce] ;
 * - Fuir ou Rester : reste tant que les griffes attendues d'ici son prochain tour ne peuvent pas l'éliminer.
 */
class HeuristicBotPolicy : BotPolicy {

    override fun chooseAction(state: GameState): GameAction {
        val legal = GameEngine.legalActions(state)
        require(legal.isNotEmpty()) { "Aucune action légale disponible pour le bot" }

        val tiles = legal.filterIsInstance<GameAction.PickMaliceTile>()
        if (tiles.isNotEmpty()) return chooseTile(state, tiles)

        val bot = state.players.first { it.id == state.deciderId() }
        val action = when (state.phase) {
            TurnPhase.DECISION_FUITE -> if (shouldFlee(state, bot)) GameAction.Flee else GameAction.Stay
            TurnPhase.LANCER -> chooseRollAction(state, bot, legal)
            TurnPhase.RESOUDRE -> legal.filterIsInstance<GameAction.ResolveSymbols>()
                .minBy { RESOLUTION_ORDER.indexOf(it.symbolType) }
            TurnPhase.ACHETER -> chooseShopAction(state, bot, legal)
            else -> legal.first()
        }
        return action.takeIf { it in legal } ?: legal.first()
    }

    // --- Tuiles Méchanceté -------------------------------------------------------------------------

    private fun chooseTile(state: GameState, tiles: List<GameAction.PickMaliceTile>): GameAction =
        tiles.maxBy { action -> StateEvaluator.tileValue(state.availableTiles.first { it.id == action.tileId }) }

    // --- Fuir ou Rester ----------------------------------------------------------------------------

    internal fun shouldFlee(state: GameState, bot: PlayerState): Boolean {
        // Griffes attendues : chaque adversaire qui joue avant le prochain tour du bot frappe la Tour.
        val expectedDamage = rollersBeforeNextTurn(state, bot.id) * EXPECTED_CLAWS_PER_TURN
        // Rester rapporte 2 ★ au début du prochain tour : si cela fait gagner, le risque vaut d'être pris.
        if (bot.vp + 2 >= 20 && bot.hp > expectedDamage - 1) return false
        return bot.hp <= expectedDamage + SAFETY_MARGIN
    }

    /** Adversaires vivants qui joueront entre la fin du tour actif et le prochain tour de [botId]. */
    private fun rollersBeforeNextTurn(state: GameState, botId: Int): Int {
        val size = state.players.size
        var count = 0
        var index = (state.activePlayerIndex + 1) % size
        while (state.players[index].id != botId) {
            if (state.players[index].isAlive) count++
            index = (index + 1) % size
        }
        return count
    }

    // --- Dés ---------------------------------------------------------------------------------------

    private fun chooseRollAction(state: GameState, bot: PlayerState, legal: List<GameAction>): GameAction {
        if (state.rollsLeft > 0) {
            val wanted = diceToKeep(state, bot)
            state.dice.firstOrNull { it.isKept != (it.id in wanted) }
                ?.let { GameAction.ToggleKeep(it.id) }
                ?.takeIf { it in legal }
                ?.let { return it }
            if (GameAction.Reroll in legal) return GameAction.Reroll
        }
        return GameAction.StopRolling
    }

    /** Les dés que le bot veut garder pour la relance, selon sa position et ses besoins. */
    internal fun diceToKeep(state: GameState, bot: PlayerState): Set<Int> {
        val inTokyo = state.tokyoOccupantId == bot.id
        val occupant = state.players.firstOrNull { it.id == state.tokyoOccupantId }
        val counts = state.dice.groupingBy { it.face }.eachCount()
        val claws = counts[DieFace.CLAW] ?: 0
        val keep = mutableSetOf<Int>()

        // Cellules : toujours utiles au Marché noir.
        keep += state.dice.filter { it.face == DieFace.ENERGY }.map { it.id }

        // Griffes : dans la Tour, elles frappent tout le monde ; dehors, elles visent l'occupant.
        val keepClaws = when {
            inTokyo -> true
            occupant == null -> false
            claws >= occupant.hp -> true // de quoi l'éliminer
            else -> bot.hp >= CLAW_HP_THRESHOLD
        }
        if (keepClaws) keep += state.dice.filter { it.face == DieFace.CLAW }.map { it.id }

        // Cœurs : seulement hors de la Tour, et seulement ceux qui soignent vraiment.
        if (!inTokyo && bot.hp <= HEAL_THRESHOLD) {
            val missing = bot.maxHp - bot.hp
            keep += state.dice.filter { it.face == DieFace.HEART }.take(missing).map { it.id }
        }

        // Chiffres : un brelan se garde ; une paire seulement si elle est la meilleure piste.
        val racing = bot.vp >= RACE_VP
        val numberFaces = listOf(DieFace.THREE, DieFace.TWO, DieFace.ONE)
        for (face in numberFaces) {
            if ((counts[face] ?: 0) >= 3) keep += state.dice.filter { it.face == face }.map { it.id }
        }
        val pair = numberFaces.firstOrNull { face ->
            counts[face] == 2 && (face != DieFace.ONE || bot.malice < 10 || racing)
        }
        val hasTriple = numberFaces.any { (counts[it] ?: 0) >= 3 }
        if (pair != null && !hasTriple && (racing || keep.size <= 3)) {
            keep += state.dice.filter { it.face == pair }.map { it.id }
        }
        return keep
    }

    // --- Marché noir -------------------------------------------------------------------------------

    private fun chooseShopAction(state: GameState, bot: PlayerState, legal: List<GameAction>): GameAction {
        val current = StateEvaluator.evaluate(state, bot.id)
        val best = legal.filterIsInstance<GameAction.BuyCard>()
            .map { it to StateEvaluator.evaluate(GameEngine.reduce(state, it), bot.id) - current }
            .maxByOrNull { it.second }
        if (best != null && best.second > MIN_PURCHASE_GAIN) return best.first

        // Rien ne vaut le coup et la réserve déborde : on renouvelle la vitrine.
        if (GameAction.SweepShop in legal && bot.energy >= SWEEP_ENERGY) return GameAction.SweepShop
        return GameAction.EndTurn
    }

    private companion object {
        val RESOLUTION_ORDER = listOf(
            SymbolResolutionType.HEARTS,
            SymbolResolutionType.NUMBERS,
            SymbolResolutionType.ENERGY,
            SymbolResolutionType.CLAWS,
        )
        const val EXPECTED_CLAWS_PER_TURN = 2
        const val SAFETY_MARGIN = 1
        const val CLAW_HP_THRESHOLD = 5
        const val HEAL_THRESHOLD = 7
        const val RACE_VP = 15
        const val MIN_PURCHASE_GAIN = 0.0
        const val SWEEP_ENERGY = 8
    }
}
