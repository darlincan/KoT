package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.content.ConditionParser
import com.neonkaiju.kot.content.EffectInterpreter
import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ContentEffectsTest {
    private val catalog = CatalogLoader.loadCatalog()
    private fun fixture() = GameEngine.createGame(42, MonsterId.VOLTRAX, catalog).copy(
        activePlayerIndex = 0, tokyoOccupantId = 1,
        players = MonsterId.entries.take(4).mapIndexed { id, monster -> PlayerState(id, monster, id != 0, hp = 5, vp = 4, energy = 8) },
        dice = List(6) { Die(it, DieFace.ONE) }
    )

    @Test fun CATALOG_exact_counts_and_unique_ids_and_invalid_conditions_rejected() {
        assertEquals(20, catalog.cards.size); assertEquals(10, catalog.tiles.size)
        val ids = catalog.cards.map { it.id } + catalog.tiles.map { it.id }
        assertEquals(30, ids.distinct().size)
        assertThrows(IllegalArgumentException::class.java) { ConditionParser.evaluate("hp", fixture(), 0) }
        assertThrows(IllegalStateException::class.java) { ConditionParser.evaluate("unknown == 1", fixture(), 0) }
    }

    @TestFactory fun every_card_and_tile_has_verified_behavior() =
        (catalog.cards.map { Triple(it.id, it.effets, it as Any) } + catalog.tiles.map { Triple(it.id, it.effets, it as Any) }).map { (id, effects, item) ->
            dynamicTest(id) {
                var before = fixture()
                before = before.copy(players = before.players.map { player ->
                    if (player.id == 0) player.copy(
                        cards = if (item is CardDef) listOf(item) else emptyList(),
                        tiles = if (item is MaliceTileDef) listOf(item) else emptyList(),
                        energy = if (id == "condensateur_flux") 0 else 8,
                        hp = if (id == "balise_neon") 8 else 5
                    ) else player
                })
                if (id in listOf("piratage_neural", "drones_sentinelles", "protocole_berserk", "tile_3_impulsion", "tile_6_predateur_neon")) before = before.copy(tokyoOccupantId = 0)
                val trigger = effects.first().declencheur
                val after = EffectInterpreter.applyTrigger(before, trigger, 0)
                when (id) {
                    "blindage_reactif" -> {
                        assertEquals(1, EffectInterpreter.computeDamageReduction(before, 0))
                        val damaged = before.copy(players = before.players.map { if (it.id == 0) it.copy(damageTakenThisTurn = 1) else it })
                        assertEquals(0, EffectInterpreter.computeDamageReduction(damaged, 0))
                    }
                    "griffes_plasma" -> assertEquals(listOf(5, 4, 4, 4), after.players.map { it.hp })
                    "coeur_nucleaire", "tile_3_court_circuit" -> assertEquals(9, after.activePlayer.energy)
                    "marche_clandestin", "tile_3_escompte" -> assertEquals(3, EffectInterpreter.computeCardCost(before, 0, catalog.cards.first()))
                    "recalibrage_optique", "implant_cybernetique" -> {
                        assertEquals(DieFace.CLAW, after.dice[0].face)
                        assertEquals(5, after.dice.count { it.face == DieFace.ONE })
                        assertEquals(2, after.rollsLeft)
                    }
                    "piratage_neural", "drones_sentinelles", "tile_6_arc_electrique", "tile_6_predateur_neon", "balise_neon" -> assertEquals(5, after.activePlayer.vp)
                    "bombe_iem" -> { assertEquals(listOf(5, 3, 3, 3), after.players.map { it.hp }); assertEquals(5, after.activePlayer.vp) }
                    "surcharge_reseau" -> assertEquals(13, after.activePlayer.energy)
                    "nanobots_reparateurs" -> assertEquals(8, after.activePlayer.hp)
                    "frappe_orbitale" -> { assertEquals(6, after.activePlayer.vp); assertEquals(listOf(5, 2, 5, 5), after.players.map { it.hp }) }
                    "exosquelette_titane" -> {
                        assertEquals(1, EffectInterpreter.computeDamageReduction(before, 0))
                        assertEquals(0, EffectInterpreter.computeDamageReduction(before.copy(tokyoOccupantId = 0), 0))
                    }
                    "condensateur_flux" -> assertEquals(1, after.activePlayer.energy)
                    "decharge_voisinage" -> assertEquals(listOf(5, 3, 5, 3), after.players.map { it.hp })
                    "protocole_berserk" -> { assertEquals(listOf(5, 4, 4, 4), after.players.map { it.hp }); assertTrue("FLAG_1" in after.activePlayer.flags) }
                    "extraction_miniere" -> { assertEquals(5, after.activePlayer.vp); assertEquals(9, after.activePlayer.energy) }
                    "regenerateur_cellulaire", "tile_3_blindage_leger" -> assertEquals(6, after.activePlayer.hp)
                    "canon_railgun" -> { assertEquals(7, after.activePlayer.vp); assertEquals(listOf(3, 5, 3, 3), after.players.map { it.hp }) }
                    "tile_3_impulsion" -> assertEquals(listOf(5, 4, 4, 4), after.players.map { it.hp })
                    "tile_6_bouclier_deflecteur" -> assertEquals(1, EffectInterpreter.computeDamageReduction(before, 0))
                    "tile_6_reacteur_fusion" -> assertEquals(10, after.activePlayer.energy)
                    "tile_10_apocalypse_cyber" -> { assertEquals(6, after.activePlayer.vp); assertEquals(listOf(5, 4, 4, 4), after.players.map { it.hp }) }
                    "tile_10_immortalite_synthetique" -> { assertEquals(7, after.activePlayer.hp); assertEquals(6, after.activePlayer.vp) }
                    else -> fail<Unit>("Contenu sans vérification : $id")
                }
            }
        }

    @Test fun no_heal_can_resurrect_a_dead_player_and_negative_hp_eliminates() {
        val base = fixture()
        val killed = EffectInterpreter.applyDirectDamage(base, 0, 8)
        val heal = EffectDef(EffectTrigger.ACHAT, cible = EffectTarget.ACTIF, operation = EffectOperation.gainHp, valeur = 9)
        val healed = EffectInterpreter.applyTrigger(killed, EffectTrigger.ACHAT, 1, listOf(heal))
        assertFalse(healed.players[0].isAlive); assertEquals(0, healed.players[0].hp)
        val harm = heal.copy(operation = EffectOperation.gainHp, valeur = -8)
        val eliminated = EffectInterpreter.applyTrigger(base, EffectTrigger.ACHAT, 1, listOf(harm))
        assertFalse(eliminated.players[0].isAlive)
    }

    @Test fun entry_effect_last_survivor_finishes_immediately_and_self_elimination_skips_dead_turn() {
        var base = fixture()
        val impulse = catalog.tiles.first { it.id == "tile_3_impulsion" }
        base = base.copy(tokyoOccupantId = null, players = base.players.map { if (it.id == 0) it.copy(tiles = listOf(impulse)) else it.copy(hp = 1) }, dice = List(6) { Die(it, DieFace.ENERGY) })
        val win = GameEngine.reduce(base, GameAction.StopRolling)
        assertEquals(TurnPhase.FIN_PARTIE, win.phase); assertEquals(0, win.winnerId)
        val railgun = catalog.cards.first { it.id == "canon_railgun" }
        val self = fixture().copy(phase = TurnPhase.ACHETER, shop = listOf(railgun), players = fixture().players.map { if (it.id == 0) it.copy(hp = 2) else it })
        val result = GameEngine.reduce(self, GameAction.BuyCard(railgun.id))
        assertFalse(result.players[0].isAlive); assertEquals(1, result.activePlayer.id)
        assertEquals(TurnPhase.LANCER, result.phase)
    }
    @Test fun first_damage_shield_is_consumed_even_when_hit_is_fully_blocked() {
        val shield = catalog.cards.first { it.id == "blindage_reactif" }
        val initial = fixture().copy(players = fixture().players.map { if (it.id == 0) it.copy(cards = listOf(shield)) else it })
        val first = EffectInterpreter.applyDamage(initial, 0, 1)
        assertEquals(5, first.activePlayer.hp)
        val second = EffectInterpreter.applyDamage(first, 0, 1)
        assertEquals(4, second.activePlayer.hp)
        val nextTurn = GameEngine.reduce(second.copy(phase = TurnPhase.ACHETER), GameAction.EndTurn)
        assertEquals(1, EffectInterpreter.computeDamageReduction(nextTurn, 0))
    }

}
