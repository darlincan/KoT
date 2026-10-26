package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.PendingFleeDecision
import com.neonkaiju.kot.model.TurnPhase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameUiMapperTest {

    private val catalog = CatalogLoader.loadCatalog()
    private val game = GameEngine.createGame(seed = 7L, humanMonster = MonsterId.VOLTRAX, catalog = catalog)

    @Test
    fun `first roll of the human turn enables dice and reroll, blocks shop and end of turn with a reason`() {
        val ui = game.toGameUiState(isBotFastForward = false)

        assertEquals(MonsterId.VOLTRAX, ui.me.monster)
        assertEquals(3, ui.opponents.size)
        assertTrue(ui.dice.all { it.canToggle })
        assertEquals(CommandUi(enabled = true), ui.reroll)
        assertEquals(CommandUi(false, BlockedReason.NOT_BUY_PHASE), ui.openShop)
        assertEquals(CommandUi(false, BlockedReason.RESOLVE_FIRST), ui.endTurn)
        assertNull(ui.botTurn)
        assertNull(ui.flee)
    }

    @Test
    fun `reroll explains why it is disabled`() {
        val allKept = game.copy(dice = game.dice.map { it.copy(isKept = true) })
        assertEquals(BlockedReason.ALL_DICE_KEPT, allKept.toGameUiState(false).reroll.blockedReason)

        val noRollLeft = game.copy(rollsLeft = 0).toGameUiState(false)
        assertEquals(BlockedReason.NO_ROLLS_LEFT, noRollLeft.reroll.blockedReason)
        assertTrue(noRollLeft.dice.none { it.canToggle })
    }

    @Test
    fun `bot turn shows the read-only overlay`() {
        val ui = game.copy(activePlayerIndex = 1).toGameUiState(isBotFastForward = true)

        assertEquals(game.players[1].monster.displayName, ui.botTurn?.botName)
        assertTrue(ui.isBotFastForward)
    }

    @Test
    fun `flee dialog is shown only when the human occupies Tokyo`() {
        val human = game.players.first { !it.isBot }
        val attacker = game.players[1]
        val pending = game.copy(
            phase = TurnPhase.DECISION_FUITE,
            tokyoOccupantId = human.id,
            pendingFlee = PendingFleeDecision(attackerId = attacker.id, occupantId = human.id, damageTaken = 2),
        )

        val flee = pending.toGameUiState(false).flee

        assertEquals(FleeUi(attacker.monster.displayName, damageTaken = 2, remainingHp = human.hp), flee)
        assertTrue(pending.toGameUiState(false).me.isInTokyo)
    }

    @Test
    fun `shop shows real prices and what the player can afford`() {
        val buyPhase = game.copy(phase = TurnPhase.ACHETER)
        val broke = buyPhase.toShopUiState()
        assertEquals(buyPhase.shop.map { it.cout }, broke.cards.map { it.cost })
        assertTrue(broke.cards.none { it.canBuy })
        assertFalse(broke.canSweep)

        val rich = buyPhase.copy(players = buyPhase.players.map { if (it.isBot) it else it.copy(energy = 99) }).toShopUiState()
        assertTrue(rich.cards.all { it.canBuy })
        assertTrue(rich.canSweep)
        assertEquals(99, rich.energy)
    }

    @Test
    fun `game over ranking puts survivors first`() {
        val over = game.copy(
            phase = TurnPhase.FIN_PARTIE,
            winnerId = 2,
            players = game.players.map { if (it.id == 0) it.copy(isAlive = false, hp = 0, vp = 19) else it },
        ).toGameOverUiState()

        assertEquals(game.players[2].monster.displayName, over.winnerName)
        assertEquals(7L, over.seed)
        assertEquals(0, over.ranking.last().id)
    }
}
