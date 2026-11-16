package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.DieFace
import com.neonkaiju.kot.model.MarketReceipt
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.PendingFleeDecision
import com.neonkaiju.kot.model.ResolutionGains
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

        val flee = pending.toFleeUi()

        assertEquals(FleeUi(attacker.monster.displayName, damageTaken = 2, remainingHp = human.hp), flee)
    }

    @Test
    fun `flee dialog states both consequences and is hidden when a bot occupies Tokyo`() {
        val human = game.players.first { !it.isBot }
        val attacker = game.players[1]
        val humanInTokyo = game.copy(
            phase = TurnPhase.DECISION_FUITE,
            tokyoOccupantId = human.id,
            pendingFlee = PendingFleeDecision(attackerId = attacker.id, occupantId = human.id, damageTaken = 2),
        )

        assertEquals(attacker.monster.displayName, humanInTokyo.toFleeUi()!!.attackerName)

        val botInTokyo = game.copy(
            phase = TurnPhase.DECISION_FUITE,
            tokyoOccupantId = attacker.id,
            pendingFlee = PendingFleeDecision(attackerId = human.id, occupantId = attacker.id, damageTaken = 2),
        )
        assertNull(botInTokyo.toFleeUi())
    }

    @Test
    fun `malice picker offers the tiles still available at the level and shows the owned ones`() {
        val owned = game.availableTiles.first { it.niveau == 3 }
        val takenByABot = game.availableTiles.first { it.niveau == 6 }
        val pending = game.copy(
            pendingMaliceLevels = listOf(6),
            availableTiles = game.availableTiles - owned - takenByABot,
            players = game.players.map { player ->
                when {
                    !player.isBot -> player.copy(tiles = listOf(owned))
                    player.id == 1 -> player.copy(tiles = listOf(takenByABot))
                    else -> player
                }
            },
        )

        val pick = pending.toMalicePickUi()!!

        assertEquals(6, pick.level)
        assertEquals(
            game.availableTiles.filter { it.niveau == 6 && it != takenByABot }.map { it.id },
            pick.tiles.map { it.id },
        )
        assertEquals(listOf(TileUi(owned.id, owned.nom, owned.texte)), pick.ownedTiles)
    }

    @Test
    fun `malice picker is hidden during a bot turn`() {
        val botTurn = game.copy(activePlayerIndex = 1, pendingMaliceLevels = listOf(3))

        assertNull(botTurn.toMalicePickUi())
    }

    @Test
    fun `flee and malice picker reach the game screen state`() {
        val human = game.players.first { !it.isBot }
        val attacker = game.players[1]
        val pending = game.copy(
            phase = TurnPhase.DECISION_FUITE,
            tokyoOccupantId = human.id,
            pendingFlee = PendingFleeDecision(attackerId = attacker.id, occupantId = human.id, damageTaken = 2),
        ).toGameUiState(false)

        assertEquals(FleeUi(attacker.monster.displayName, damageTaken = 2, remainingHp = human.hp), pending.flee)
        assertTrue(pending.me.isInTokyo)
        assertEquals(3, game.copy(pendingMaliceLevels = listOf(3)).toGameUiState(false).malicePick?.level)
    }

    @Test
    fun `game screen state describes the phase without exposing it`() {
        val rolling = game.toGameUiState(false)
        assertTrue(rolling.isRolling)
        assertFalse(rolling.isBuyPhase)
        assertEquals(PhaseUi.ROLL, rolling.phase)
        assertEquals(GameEngine.MALICE_TIERS, rolling.maliceTiers)

        val buying = game.copy(phase = TurnPhase.ACHETER).toGameUiState(false)
        assertTrue(buying.isBuyPhase)
        assertFalse(buying.isRolling)

        val choosing = game.copy(pendingMaliceLevels = listOf(3)).toGameUiState(false)
        assertEquals(PhaseUi.CHOOSE_POWER, choosing.phase)
        assertEquals(GameEngine.MALICE_TIERS, choosing.malicePick?.tiers)
    }

    @Test
    fun `bot overlay names the occupant when a bot decides to flee`() {
        val human = game.players.first { !it.isBot }
        val occupant = game.players[2]
        val ui = game.copy(
            phase = TurnPhase.DECISION_FUITE,
            tokyoOccupantId = occupant.id,
            pendingFlee = PendingFleeDecision(attackerId = human.id, occupantId = occupant.id, damageTaken = 1),
        ).toGameUiState(false, botExplanation = "fuit la Tour")

        assertEquals(BotTurnUi(occupant.monster.displayName, "fuit la Tour"), ui.botTurn)
        assertNull(ui.flee)
    }

    @Test
    fun `shop gives the remaining cells after each purchase and sweep`() {
        val shop = game.copy(phase = TurnPhase.ACHETER, players = game.players.map { if (it.isBot) it else it.copy(energy = 5) }).toShopUiState()

        shop.cards.forEach {
            assertEquals(5 - it.cost, it.energyAfterPurchase)
            assertEquals(it.cost, it.baseCost, "aucune réduction sans carte ni tuile")
        }
        assertEquals(GameEngine.SWEEP_COST, shop.sweepCost)
        assertEquals(5 - GameEngine.SWEEP_COST, shop.energyAfterSweep)
    }

    @Test
    fun `player state gives the number of cards held`() {
        val card = catalog.cards.first()
        val ui = game.copy(players = game.players.map { if (it.isBot) it else it.copy(cards = listOf(card)) }).toGameUiState(false)

        assertEquals(1, ui.me.cardCount)
        assertEquals(0, ui.opponents.first().cardCount)
    }

    @Test
    fun `bot overlay recalls the gains only when the bot moves on to buying`() {
        val gains = ResolutionGains(vp = 2, energy = 1)
        val state = game.copy(activePlayerIndex = 1, lastResolutionGains = mapOf(game.players[1].id to gains))

        assertNull(state.toGameUiState(false).botTurn?.result)
        assertEquals(gains, state.copy(phase = TurnPhase.ACHETER).toGameUiState(false).botTurn?.result)
    }

    @Test
    fun `shop keeps the last receipt as values`() {
        val human = game.players.first { !it.isBot }
        val receipt = MarketReceipt("Bombe", energyBefore = 8, paid = 3, energyAfter = 6, effectEnergyChange = 1)
        val shop = game.copy(phase = TurnPhase.ACHETER, lastMarketReceipts = mapOf(human.id to receipt)).toShopUiState()

        assertEquals(ReceiptUi("Bombe", energyBefore = 8, paid = 3, effectChange = 1, energyAfter = 6), shop.lastReceipt)
    }

    @Test
    fun `finished game cannot be resumed`() {
        assertTrue(game.toHomeUiState("1.0").canResume)
        assertFalse(game.copy(phase = TurnPhase.FIN_PARTIE).toHomeUiState("1.0").canResume)
    }

    @Test
    fun `countdown stays off unless the setting enables it`() {
        assertFalse(game.toGameUiState(false).countdownEnabled)
        assertTrue(game.toGameUiState(false, countdownEnabled = true).countdownEnabled)
    }

    @Test
    fun `human commands stay blocked while a bot decides`() {
        val ui = game.copy(activePlayerIndex = 1).toGameUiState(isBotFastForward = false, botExplanation = "garde un dé")

        assertEquals("garde un dé", ui.botTurn?.lastAction)
        assertEquals(CommandUi(false, BlockedReason.BOT_PLAYING), ui.reroll)
        assertEquals(CommandUi(false, BlockedReason.BOT_PLAYING), ui.endTurn)
        assertTrue(ui.dice.none { it.canToggle })
        assertFalse(ui.canChangeOrder)
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

    @Test
    fun `game over states why the game ended`() {
        val twentyStars = game.copy(
            phase = TurnPhase.FIN_PARTIE,
            winnerId = 0,
            players = game.players.map { if (it.id == 0) it.copy(vp = 20) else it },
        ).toGameOverUiState()
        assertEquals(GameEndReason.TWENTY_STARS, twentyStars.endReason)
        assertEquals(0, twentyStars.humanId)

        val lastSurvivor = game.copy(
            phase = TurnPhase.FIN_PARTIE,
            winnerId = 2,
            players = game.players.map { if (it.id == 2) it else it.copy(isAlive = false, hp = 0) },
        ).toGameOverUiState()
        assertEquals(GameEndReason.LAST_SURVIVOR, lastSurvivor.endReason)

        val nobody = game.copy(
            phase = TurnPhase.FIN_PARTIE,
            winnerId = null,
            players = game.players.map { it.copy(isAlive = false, hp = 0) },
        ).toGameOverUiState()
        assertEquals(GameEndReason.ALL_ELIMINATED, nobody.endReason)
        assertNull(nobody.winnerName)
    }

    @Test
    fun `game over ranking keeps the statistics of each player`() {
        val over = game.copy(
            phase = TurnPhase.FIN_PARTIE,
            winnerId = 0,
            players = game.players.map {
                when (it.id) {
                    0 -> it.copy(vp = 20, hp = 7, energy = 3)
                    3 -> it.copy(vp = 9, hp = 0, energy = 0, isAlive = false)
                    else -> it
                }
            },
        ).toGameOverUiState()

        val first = over.ranking.first()
        assertEquals(listOf(20, 7, 3), listOf(first.vp, first.hp, first.energy))
        assertFalse(over.ranking.last().isAlive)
        assertEquals(3, over.ranking.last().id)
    }
}
