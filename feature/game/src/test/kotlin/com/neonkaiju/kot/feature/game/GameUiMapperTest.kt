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

        val flee = humanInTokyo.toFleeUi()!!
        assertTrue(flee.fleeConsequence.startsWith("${attacker.monster.displayName} prend la Tour"))
        assertTrue(flee.stayConsequence.startsWith("+2 ★ au début de votre prochain tour si vous survivez"))

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
