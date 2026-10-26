import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TmpTest {
    private val catalog = CatalogLoader.loadCatalog()

    @Test
    fun `RULE_24_elimination_discards_cards_and_frees_tokyo`() {
        var state = com.neonkaiju.kot.engine.GameEngine.createGame(42L, MonsterId.VOLTRAX, catalog)
        
        // Put player 1 (bot) in Tokyo
        state = state.copy(tokyoOccupantId = 1)
        
        // Give player 1 some cards and energy
        state = state.copy(players = state.players.map { 
            if (it.id == 1) it.copy(hp = 2, energy = 5, cards = listOf(catalog.cards[0])) else it
        })
        
        // Player 0 rolls 2 claws
        state = state.copy(dice = listOf(Die(0, DieFace.CLAW), Die(1, DieFace.CLAW)))
        state = com.neonkaiju.kot.engine.GameEngine.reduce(state, GameAction.StopRolling)
        state = com.neonkaiju.kot.engine.GameEngine.reduce(state, GameAction.ResolveSymbols(SymbolResolutionType.CLAWS))
        
        // Player 1 should be eliminated, Tokyo free, cards discarded
        val p1 = state.players[1]
        assertFalse(p1.isAlive)
        assertEquals(0, p1.hp)
        assertEquals(0, p1.energy)
        assertEquals(0, p1.cards.size)
        assertNull(state.tokyoOccupantId)
        
        // Discard pile should contain the card
        assertTrue(state.discard.contains(catalog.cards[0]))
    }
}
