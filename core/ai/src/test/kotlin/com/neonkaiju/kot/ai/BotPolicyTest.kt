package com.neonkaiju.kot.ai

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BotPolicyTest {

    private val catalog = CatalogLoader.loadCatalog()
    private val bot = HeuristicBotPolicy()

    /** Joue une partie complète ; [policyFor] donne la politique de chaque siège. */
    private fun playOut(seed: Long, policyFor: (Int) -> BotPolicy): GameState {
        var state = GameEngine.createGame(seed, MonsterId.entries[(seed % 6).toInt()], catalog)
        var steps = 0
        while (state.phase != TurnPhase.FIN_PARTIE) {
            val action = policyFor(state.deciderId()).chooseAction(state)
            assertTrue(action in GameEngine.legalActions(state), "Action illégale $action (graine $seed, phase ${state.phase})")
            state = GameEngine.reduce(state, action)
            assertTrue(++steps < MAX_STEPS, "La partie de graine $seed ne se termine pas")
        }
        return state
    }

    private class RandomPolicy(seed: Long) : BotPolicy {
        private val random = Random(seed)
        override fun chooseAction(state: GameState) = GameEngine.legalActions(state).random(random)
    }

    @Test
    fun `les bots ne jouent que des actions legales et terminent leurs parties`() {
        repeat(200) { seed ->
            val end = playOut(seed.toLong()) { bot }
            assertEquals(TurnPhase.FIN_PARTIE, end.phase)
        }
    }

    @Test
    fun `une partie de bots se rejoue a l'identique depuis son journal`() {
        val end = playOut(7L) { bot }
        assertEquals(end, GameEngine.replay(GameEngine.journalOf(end), catalog))
    }

    @Test
    fun `le bot heuristique bat nettement un joueur aleatoire`() {
        val games = 200
        var wins = 0
        repeat(games) { seed ->
            // Siège 0 : le bot heuristique ; les trois autres jouent au hasard.
            val random = RandomPolicy(seed.toLong())
            val end = playOut(seed.toLong()) { if (it == 0) bot else random }
            if (end.winnerId == 0) wins++
        }
        // Le hasard seul donnerait environ 25 %.
        assertTrue(wins > games / 2, "Le bot heuristique ne gagne que $wins parties sur $games")
    }

    @Test
    fun `le bot fuit la Tour quand les griffes attendues peuvent l'eliminer`() {
        val state = fleeState(occupantHp = 3, occupantVp = 5)
        assertEquals(GameAction.Flee, bot.chooseAction(state))
    }

    @Test
    fun `le bot reste dans la Tour quand il est en bonne sante`() {
        val state = fleeState(occupantHp = 9, occupantVp = 5)
        assertEquals(GameAction.Stay, bot.chooseAction(state))
    }

    @Test
    fun `le bot reste dans la Tour si ses 2 etoiles de debut de tour le font gagner`() {
        val state = fleeState(occupantHp = 4, occupantVp = 18)
        assertEquals(GameAction.Stay, bot.chooseAction(state))
    }

    @Test
    fun `le bot choisit la tuile la plus forte du niveau`() {
        val base = GameEngine.createGame(1L, MonsterId.VOLTRAX, catalog)
        val state = base.copy(activePlayerIndex = 1, phase = TurnPhase.RESOUDRE, pendingMaliceLevels = listOf(10))
        val action = bot.chooseAction(state) as GameAction.PickMaliceTile
        val chosen = state.availableTiles.first { it.id == action.tileId }
        val best = state.availableTiles.filter { it.niveau == 10 }.maxOf { StateEvaluator.tileValue(it) }
        assertEquals(best, StateEvaluator.tileValue(chosen))
    }

    @Test
    fun `dans la Tour le bot garde ses griffes et ignore les coeurs`() {
        val base = GameEngine.createGame(1L, MonsterId.VOLTRAX, catalog)
        val dice = listOf(DieFace.CLAW, DieFace.CLAW, DieFace.HEART, DieFace.HEART, DieFace.ONE, DieFace.TWO)
            .mapIndexed { i, f -> Die(i, f) }
        val state = base.copy(activePlayerIndex = 1, tokyoOccupantId = 1, dice = dice)
            .withPlayer(1) { it.copy(hp = 5) }
        assertEquals(setOf(0, 1), bot.diceToKeep(state, state.players[1]))
    }

    @Test
    fun `hors de la Tour un bot blesse garde ses coeurs`() {
        val base = GameEngine.createGame(1L, MonsterId.VOLTRAX, catalog)
        val dice = listOf(DieFace.HEART, DieFace.HEART, DieFace.ENERGY, DieFace.ONE, DieFace.TWO, DieFace.THREE)
            .mapIndexed { i, f -> Die(i, f) }
        val state = base.copy(activePlayerIndex = 1, tokyoOccupantId = 2, dice = dice)
            .withPlayer(1) { it.copy(hp = 4) }
        assertEquals(setOf(0, 1, 2), bot.diceToKeep(state, state.players[1]))
    }

    @Test
    fun `le bot achete une carte qui le fait gagner`() {
        val win = catalog.cards.first { it.id == "canon_railgun" }
        val base = GameEngine.createGame(1L, MonsterId.VOLTRAX, catalog)
        val state = base.copy(activePlayerIndex = 1, phase = TurnPhase.ACHETER, shop = listOf(win) + base.shop.drop(1))
            .withPlayer(1) { it.copy(vp = 17, energy = 10) }
        assertEquals(GameAction.BuyCard(win.id), bot.chooseAction(state))
    }

    @Test
    fun `sans Cellules le bot termine son tour`() {
        val base = GameEngine.createGame(1L, MonsterId.VOLTRAX, catalog)
        val state = base.copy(activePlayerIndex = 1, phase = TurnPhase.ACHETER)
        assertEquals(GameAction.EndTurn, bot.chooseAction(state))
    }

    @Test
    fun `l'evaluation prefere plus d'etoiles, plus de coeurs et la victoire`() {
        val base = GameEngine.createGame(1L, MonsterId.VOLTRAX, catalog)
        val e = { s: GameState -> StateEvaluator.evaluate(s, 1) }
        assertTrue(e(base.withPlayer(1) { it.copy(vp = 5) }) > e(base))
        assertTrue(e(base.withPlayer(1) { it.copy(hp = 2) }) < e(base))
        assertTrue(e(base.withPlayer(2) { it.copy(vp = 15) }) < e(base))
        assertEquals(StateEvaluator.WIN, e(base.copy(phase = TurnPhase.FIN_PARTIE, winnerId = 1)))
        assertEquals(StateEvaluator.LOSS, e(base.withPlayer(1) { it.copy(hp = 0, isAlive = false) }))
    }

    /** Le joueur 1 frappe le joueur 2, occupant de la Tour ; c'est au tour du joueur 2 de jouer ensuite. */
    private fun fleeState(occupantHp: Int, occupantVp: Int): GameState {
        val base = GameEngine.createGame(1L, MonsterId.VOLTRAX, catalog)
        return base.copy(
            activePlayerIndex = 3,
            tokyoOccupantId = 2,
            phase = TurnPhase.DECISION_FUITE,
            pendingFlee = PendingFleeDecision(attackerId = 3, occupantId = 2, damageTaken = 2),
        ).withPlayer(2) { it.copy(hp = occupantHp, vp = occupantVp) }
    }

    private fun GameState.withPlayer(id: Int, block: (PlayerState) -> PlayerState) =
        copy(players = players.map { if (it.id == id) block(it) else it })

    private companion object {
        const val MAX_STEPS = 20_000
    }
}
