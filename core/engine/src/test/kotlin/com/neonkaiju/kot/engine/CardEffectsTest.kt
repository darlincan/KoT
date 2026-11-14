package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.content.EffectInterpreter
import com.neonkaiju.kot.model.*
import com.neonkaiju.kot.model.DieFace.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Un test par carte et par tuile, nommé d'après son identifiant (§8 « Validation au build »).
 * Le joueur 0 est actif sauf mention contraire ; ses voisins sont 1 et 3.
 */
class CardEffectsTest {

    private val catalog = CatalogLoader.loadCatalog()
    private val noSymbols = arrayOf(ONE, ONE, TWO, TWO, THREE, THREE)

    // --- Préparation ---------------------------------------------------------------------------

    private fun game(occupant: Int? = null) =
        GameEngine.createGame(42L, MonsterId.VOLTRAX, catalog).copy(tokyoOccupantId = occupant)

    private fun card(id: String) = catalog.cards.first { it.id == id }
    private fun tile(id: String) = catalog.tiles.first { it.id == id }

    private fun GameState.player(id: Int) = players.first { it.id == id }

    private fun GameState.withPlayer(id: Int, block: (PlayerState) -> PlayerState) =
        copy(players = players.map { if (it.id == id) block(it) else it })

    private fun GameState.giveCard(playerId: Int, cardId: String) =
        withPlayer(playerId) { it.copy(cards = it.cards + card(cardId)) }

    private fun GameState.giveTile(playerId: Int, tileId: String) =
        withPlayer(playerId) { it.copy(tiles = it.tiles + tile(tileId)) }

    private fun GameState.activate(playerIndex: Int) = copy(activePlayerIndex = playerIndex)

    /** Fige les dés, arrête de lancer et résout tous les symboles (s'arrête sur une décision de fuite). */
    private fun GameState.rollAndResolve(vararg faces: DieFace): GameState {
        var s = GameEngine.reduce(copy(dice = faces.mapIndexed { i, f -> Die(i, f) }), GameAction.StopRolling)
        while (s.phase == TurnPhase.RESOUDRE) s = GameEngine.reduce(s, GameEngine.legalActions(s).first())
        return s
    }

    /** Le joueur actif achète [cardId] avec [energy] Cellules en poche. */
    private fun GameState.buy(cardId: String, energy: Int): GameState {
        val c = card(cardId)
        val s = copy(
            phase = TurnPhase.ACHETER,
            shop = listOf(c) + shop.filter { it.id != cardId },
            deck = deck.filter { it.id != cardId }
        ).withPlayer(activePlayer.id) { it.copy(energy = energy) }
        return GameEngine.reduce(s, GameAction.BuyCard(cardId))
    }

    private fun GameState.endTurn() = GameEngine.reduce(copy(phase = TurnPhase.ACHETER), GameAction.EndTurn)

    // --- Couverture -----------------------------------------------------------------------------

    @Test
    fun `chaque carte et chaque tuile a un test nommé d'après son identifiant`() {
        val testNames = javaClass.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) }
            .map { it.name.substringBefore(' ') }
            .toSet()
        val missing = (catalog.cards.map { it.id } + catalog.tiles.map { it.id }).filterNot { it in testNames }
        assertEquals(emptyList<String>(), missing, "Identifiants sans test")
    }

    // --- Cartes ---------------------------------------------------------------------------------

    @Test
    fun `blindage_reactif ignore le premier dégât subi dans le tour`() {
        val s = game(occupant = 0).giveCard(0, "blindage_reactif").activate(1)
            .rollAndResolve(CLAW, CLAW, CLAW, ONE, TWO, THREE)
        assertEquals(8, s.player(0).hp)
        assertEquals(TurnPhase.DECISION_FUITE, s.phase)
        assertEquals(0, EffectInterpreter.computeDamageReduction(s, 0), "déjà blessé ce tour")
    }

    @Test
    fun `griffes_plasma ajoute 1 dégât aux seuls monstres blessés`() {
        val outside = game(occupant = 1).giveCard(0, "griffes_plasma")
            .rollAndResolve(CLAW, CLAW, ONE, TWO, THREE, ENERGY)
        assertEquals(7, outside.player(1).hp)
        assertEquals(10, outside.player(2).hp)
        assertEquals(10, outside.player(3).hp)

        val inside = game(occupant = 0).giveCard(0, "griffes_plasma").rollAndResolve(CLAW, ONE, ONE, TWO, TWO, THREE)
        listOf(1, 2, 3).forEach { assertEquals(8, inside.player(it).hp) }
    }

    @Test
    fun `coeur_nucleaire donne 1 Cellule au début du tour`() {
        val s = game().giveCard(1, "coeur_nucleaire").endTurn()
        assertEquals(1, s.activePlayer.id)
        assertEquals(1, s.player(1).energy)
    }

    @Test
    fun `marche_clandestin réduit le coût des cartes de son seul propriétaire`() {
        val s = game().giveCard(0, "marche_clandestin")
        assertEquals(6, EffectInterpreter.computeCardCost(s, 0, card("canon_railgun")))
        assertEquals(7, EffectInterpreter.computeCardCost(s, 1, card("canon_railgun")))
    }

    @Test
    fun `recalibrage_optique donne une relance de plus`() {
        var s = game().giveCard(1, "recalibrage_optique").endTurn()
        assertEquals(3, s.rollsLeft)
        s = GameEngine.reduce(GameEngine.reduce(s, GameAction.Reroll), GameAction.Reroll)
        assertEquals(TurnPhase.LANCER, s.phase)
        assertTrue(GameAction.Reroll in GameEngine.legalActions(s))
    }

    @Test
    fun `piratage_neural donne 1 étoile après résolution dans la Tour seulement`() {
        assertEquals(1, game(occupant = 0).giveCard(0, "piratage_neural").rollAndResolve(*noSymbols).player(0).vp)
        assertEquals(0, game(occupant = 1).giveCard(0, "piratage_neural").rollAndResolve(*noSymbols).player(0).vp)
    }

    @Test
    fun `bombe_iem blesse les autres de 2 et rapporte 1 étoile`() {
        val s = game().buy("bombe_iem", energy = 4)
        listOf(1, 2, 3).forEach { assertEquals(8, s.player(it).hp) }
        assertEquals(10, s.player(0).hp)
        assertEquals(1, s.player(0).vp)
        assertEquals(0, s.player(0).energy)
        assertTrue(s.player(0).cards.isEmpty(), "une ACTION est défaussée")
        assertTrue(card("bombe_iem") in s.discard)
    }

    @Test
    fun `surcharge_reseau donne 5 Cellules`() {
        assertEquals(5, game().buy("surcharge_reseau", energy = 3).player(0).energy)
    }

    @Test
    fun `nanobots_reparateurs soigne 3 même dans la Tour, sans dépasser 10`() {
        val inTokyo = game(occupant = 0).withPlayer(0) { it.copy(hp = 5) }.buy("nanobots_reparateurs", energy = 3)
        assertEquals(8, inTokyo.player(0).hp)
        val nearlyFull = game().withPlayer(0) { it.copy(hp = 9) }.buy("nanobots_reparateurs", energy = 3)
        assertEquals(10, nearlyFull.player(0).hp)
    }

    @Test
    fun `frappe_orbitale donne 2 étoiles et blesse l'occupant de 3`() {
        val s = game(occupant = 1).buy("frappe_orbitale", energy = 6)
        assertEquals(2, s.player(0).vp)
        assertEquals(7, s.player(1).hp)
        assertEquals(10, s.player(2).hp)
    }

    @Test
    fun `drones_sentinelles donne 1 étoile de plus en entrant dans la Tour`() {
        val s = game().giveCard(0, "drones_sentinelles").rollAndResolve(*noSymbols)
        assertEquals(0, s.tokyoOccupantId)
        assertEquals(2, s.player(0).vp)
    }

    @Test
    fun `exosquelette_titane réduit de 1 les dégâts hors de la Tour seulement`() {
        val outside = game(occupant = 1).giveCard(0, "exosquelette_titane").activate(1)
            .rollAndResolve(CLAW, CLAW, ONE, TWO, THREE, ENERGY)
        assertEquals(9, outside.player(0).hp)
        assertEquals(8, outside.player(2).hp)

        val inside = game(occupant = 0).giveCard(0, "exosquelette_titane").activate(1)
            .rollAndResolve(CLAW, CLAW, ONE, TWO, THREE, ENERGY)
        assertEquals(8, inside.player(0).hp)
    }

    @Test
    fun `condensateur_flux donne 1 Cellule en fin de tour si la réserve est vide`() {
        assertEquals(1, game().giveCard(0, "condensateur_flux").endTurn().player(0).energy)
        val rich = game().giveCard(0, "condensateur_flux").withPlayer(0) { it.copy(energy = 2) }.endTurn()
        assertEquals(2, rich.player(0).energy)
    }

    @Test
    fun `decharge_voisinage blesse les deux voisins, même dans la Tour`() {
        val s = game(occupant = 1).buy("decharge_voisinage", energy = 4)
        assertEquals(8, s.player(1).hp)
        assertEquals(8, s.player(3).hp)
        assertEquals(10, s.player(2).hp)
        assertEquals(10, s.player(0).hp)
    }

    @Test
    fun `implant_cybernetique change un 1 en griffe avant le décompte`() {
        val stopped = GameEngine.reduce(
            game(occupant = 1).giveCard(0, "implant_cybernetique")
                .copy(dice = listOf(ONE, ONE, CLAW, TWO, THREE, ENERGY).mapIndexed { i, f -> Die(i, f) }),
            GameAction.StopRolling
        )
        assertEquals(1, stopped.dice.count { it.face == ONE }, "un seul dé changé (valeur 1)")
        assertEquals(2, stopped.dice.count { it.face == CLAW })

        val s = game(occupant = 1).giveCard(0, "implant_cybernetique")
            .rollAndResolve(ONE, ONE, CLAW, TWO, THREE, ENERGY)
        assertEquals(8, s.player(1).hp)
    }

    @Test
    fun `balise_neon donne 1 étoile en fin de tour à partir de 8 PV`() {
        assertEquals(1, game().giveCard(0, "balise_neon").withPlayer(0) { it.copy(hp = 8) }.endTurn().player(0).vp)
        assertEquals(0, game().giveCard(0, "balise_neon").withPlayer(0) { it.copy(hp = 7) }.endTurn().player(0).vp)
    }

    @Test
    fun `protocole_berserk blesse les autres et active le mode furie en entrant dans la Tour`() {
        val s = game().giveCard(0, "protocole_berserk").rollAndResolve(*noSymbols)
        assertEquals(0, s.tokyoOccupantId)
        listOf(1, 2, 3).forEach { assertEquals(9, s.player(it).hp) }
        assertTrue("FLAG_1" in s.player(0).flags)
    }

    @Test
    fun `extraction_miniere donne 1 étoile et 1 Cellule`() {
        val s = game().buy("extraction_miniere", energy = 2)
        assertEquals(1, s.player(0).vp)
        assertEquals(1, s.player(0).energy)
    }

    @Test
    fun `regenerateur_cellulaire soigne 1 en fin de tour hors de la Tour`() {
        val outside = game(occupant = 1).giveCard(0, "regenerateur_cellulaire").withPlayer(0) { it.copy(hp = 6) }
        assertEquals(7, outside.endTurn().player(0).hp)
        val inside = game(occupant = 0).giveCard(0, "regenerateur_cellulaire").withPlayer(0) { it.copy(hp = 6) }
        assertEquals(6, inside.endTurn().player(0).hp)
    }

    @Test
    fun `canon_railgun donne 3 étoiles et blesse les autres monstres hors de la Tour`() {
        val s = game(occupant = 1).buy("canon_railgun", energy = 7)
        assertEquals(3, s.player(0).vp)
        assertEquals(10, s.player(0).hp, "l'acheteur n'est pas touché")
        assertEquals(10, s.player(1).hp, "l'occupant n'est pas touché")
        assertEquals(8, s.player(2).hp)
        assertEquals(8, s.player(3).hp)
    }

    // --- Tuiles de Surcharge --------------------------------------------------------------------

    @Test
    fun `tile_3_court_circuit donne 1 Cellule au début du tour`() {
        assertEquals(1, game().giveTile(1, "tile_3_court_circuit").endTurn().player(1).energy)
    }

    @Test
    fun `tile_3_blindage_leger soigne 1 en fin de tour hors de la Tour`() {
        val outside = game(occupant = 1).giveTile(0, "tile_3_blindage_leger").withPlayer(0) { it.copy(hp = 4) }
        assertEquals(5, outside.endTurn().player(0).hp)
        val inside = game(occupant = 0).giveTile(0, "tile_3_blindage_leger").withPlayer(0) { it.copy(hp = 4) }
        assertEquals(4, inside.endTurn().player(0).hp)
    }

    @Test
    fun `tile_3_impulsion blesse les autres de 1 en entrant dans la Tour`() {
        val s = game().giveTile(0, "tile_3_impulsion").rollAndResolve(*noSymbols)
        listOf(1, 2, 3).forEach { assertEquals(9, s.player(it).hp) }
        assertEquals(10, s.player(0).hp)
    }

    @Test
    fun `tile_3_escompte réduit le coût de 1, jamais sous 1 Cellule`() {
        val s = game().giveTile(0, "tile_3_escompte")
        assertEquals(3, EffectInterpreter.computeCardCost(s, 0, card("bombe_iem")))
        val stacked = s.giveCard(0, "marche_clandestin")
        assertEquals(1, EffectInterpreter.computeCardCost(stacked, 0, card("extraction_miniere")))
        assertTrue(GameAction.BuyCard("extraction_miniere") in GameEngine.legalActions(
            stacked.copy(phase = TurnPhase.ACHETER, shop = listOf(card("extraction_miniere")))
                .withPlayer(0) { it.copy(energy = 1) }
        ))
    }

    @Test
    fun `tile_6_arc_electrique donne 1 étoile au début du tour`() {
        assertEquals(1, game().giveTile(1, "tile_6_arc_electrique").endTurn().player(1).vp)
    }

    @Test
    fun `tile_6_bouclier_deflecteur annule 1 dégât à chaque attaque`() {
        val hit = game(occupant = 0).giveTile(0, "tile_6_bouclier_deflecteur").activate(1)
            .rollAndResolve(CLAW, CLAW, CLAW, ONE, TWO, THREE)
        assertEquals(8, hit.player(0).hp)

        val absorbed = game(occupant = 0).giveTile(0, "tile_6_bouclier_deflecteur").activate(1)
            .rollAndResolve(CLAW, ONE, ONE, TWO, TWO, THREE)
        assertEquals(10, absorbed.player(0).hp)
        assertNull(absorbed.pendingFlee, "aucun dégât, donc pas de décision de fuite")
    }

    @Test
    fun `tile_6_reacteur_fusion donne 2 Cellules au début du tour`() {
        assertEquals(2, game().giveTile(1, "tile_6_reacteur_fusion").endTurn().player(1).energy)
    }

    @Test
    fun `tile_6_predateur_neon donne 1 étoile de plus en entrant dans la Tour`() {
        assertEquals(2, game().giveTile(0, "tile_6_predateur_neon").rollAndResolve(*noSymbols).player(0).vp)
    }

    @Test
    fun `tile_10_apocalypse_cyber donne 2 étoiles et blesse les autres au début du tour`() {
        val s = game().giveTile(1, "tile_10_apocalypse_cyber").withPlayer(2) { it.copy(hp = 1) }.endTurn()
        assertEquals(2, s.player(1).vp)
        assertEquals(9, s.player(0).hp)
        assertFalse(s.player(2).isAlive)
        assertEquals(9, s.player(3).hp)

        val lastStanding = game().giveTile(1, "tile_10_apocalypse_cyber")
            .withPlayer(0) { it.copy(hp = 1) }.withPlayer(2) { it.copy(hp = 1) }.withPlayer(3) { it.copy(hp = 1) }
            .endTurn()
        assertEquals(TurnPhase.FIN_PARTIE, lastStanding.phase)
        assertEquals(1, lastStanding.winnerId)
    }

    @Test
    fun `tile_10_immortalite_synthetique soigne 2 et donne 2 étoiles en fin de tour`() {
        val s = game().giveTile(0, "tile_10_immortalite_synthetique").withPlayer(0) { it.copy(hp = 5) }.endTurn()
        assertEquals(7, s.player(0).hp)
        assertEquals(2, s.player(0).vp)
    }
}
