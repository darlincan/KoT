package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogLoader
import com.neonkaiju.kot.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MarketTest {
    private val catalog = CatalogLoader.loadCatalog()
    private fun market(energy: Int, vararg ids: String): GameState {
        val cards = ids.map { id -> catalog.cards.first { it.id == id } }
        val initial = GameEngine.createGame(42, MonsterId.VOLTRAX, catalog)
        return initial.copy(activePlayerIndex = 0, phase = TurnPhase.ACHETER,
            players = initial.players.map { if (it.id == 0) it.copy(energy = energy) else it },
            shop = cards, deck = catalog.cards.filter { c -> c.id !in ids }, discard = emptyList())
    }
    private fun buy(state: GameState, id: String) = GameEngine.reduceIfCurrent(state, GameAction.BuyCard(id), state.actionHistory.size)

    @Test fun four_cells_normal_purchase_costing_three_leaves_one_with_matching_receipt() {
        val before = market(4, "coeur_nucleaire", "blindage_reactif", "griffes_plasma")
        val after = buy(before, "coeur_nucleaire")
        assertEquals(1, after.activePlayer.energy)
        assertEquals(1, after.activePlayer.cards.count { it.id == "coeur_nucleaire" })
        assertEquals(3, after.shop.size)
        assertFalse(after.shop.any { it.id == "coeur_nucleaire" })
        assertEquals(MarketReceipt("Cœur nucléaire", 4, 3, 1), after.lastMarketReceipts[0])
    }

    @Test fun exact_cost_is_allowed_and_insufficient_energy_is_rejected_without_changes() {
        val exact = market(3, "coeur_nucleaire")
        assertTrue(GameEngine.quoteCard(exact, "coeur_nucleaire").canAfford)
        assertEquals(0, buy(exact, "coeur_nucleaire").activePlayer.energy)
        val insufficient = market(2, "coeur_nucleaire")
        val saved = Json.encodeToString(insufficient)
        assertFalse(GameEngine.quoteCard(insufficient, "coeur_nucleaire").canAfford)
        assertFalse(GameAction.BuyCard("coeur_nucleaire") in GameEngine.legalActions(insufficient))
        assertThrows(IllegalActionException::class.java) { buy(insufficient, "coeur_nucleaire") }
        assertEquals(saved, Json.encodeToString(insufficient))
    }

    @Test fun surcharge_reseau_reproduces_four_minus_three_plus_five_equals_six() {
        val before = market(4, "surcharge_reseau")
        val after = buy(before, "surcharge_reseau")
        assertEquals(6, after.activePlayer.energy)
        val receipt = after.lastMarketReceipts.getValue(0)
        assertEquals(4, receipt.energyBefore); assertEquals(3, receipt.paid)
        assertEquals(5, receipt.effectEnergyChange); assertEquals(6, receipt.energyAfter)
        assertTrue(receipt.effects.any { "+5 Cellule" in it })
        assertTrue(after.discard.any { it.id == "surcharge_reseau" })
        assertFalse(after.activePlayer.cards.any { it.id == "surcharge_reseau" })
    }

    @Test fun repeated_confirmations_pay_and_apply_effect_only_once_even_after_card_returns() {
        val before = market(4, "surcharge_reseau")
        val revision = before.actionHistory.size
        val after = buy(before, "surcharge_reseau")
        repeat(5) { assertEquals(after, GameEngine.reduceIfCurrent(after, GameAction.BuyCard("surcharge_reseau"), revision)) }
        val returned = after.copy(shop = listOf(catalog.cards.first { it.id == "surcharge_reseau" }))
        assertEquals(returned, GameEngine.reduceIfCurrent(returned, GameAction.BuyCard("surcharge_reseau"), revision))
        assertEquals(6, returned.activePlayer.energy)
    }

    @Test fun selecting_different_quotes_neither_buys_nor_applies_energy_effects() {
        val before = market(4, "surcharge_reseau", "coeur_nucleaire", "nanobots_reparateurs")
        val original = Json.encodeToString(before)
        val first = GameEngine.quoteCard(before, "surcharge_reseau")
        val second = GameEngine.quoteCard(before, "coeur_nucleaire")
        assertEquals(3, first.cost); assertEquals(1, first.afterPayment)
        assertEquals(3, second.cost); assertEquals(first.revision, second.revision)
        assertEquals(original, Json.encodeToString(before))
        val bought = GameEngine.reduceIfCurrent(before, GameAction.BuyCard(second.card.id), second.revision)
        assertEquals(1, bought.activePlayer.energy)
        assertTrue(bought.activePlayer.cards.any { it.id == second.card.id })
        assertFalse(bought.discard.any { it.id == first.card.id })
    }

    @Test fun renewal_costs_two_once_and_replaces_three_visible_cards() {
        val before = market(4, "coeur_nucleaire", "blindage_reactif", "griffes_plasma")
        val revision = before.actionHistory.size
        val after = GameEngine.reduceIfCurrent(before, GameAction.SweepShop, revision)
        assertEquals(2, after.activePlayer.energy); assertEquals(3, after.shop.size)
        assertTrue(before.shop.none { old -> after.shop.any { it.id == old.id } })
        assertEquals(after, GameEngine.reduceIfCurrent(after, GameAction.SweepShop, revision))
        assertEquals(MarketReceipt("Renouvellement du marché", 4, 2, 2, isSweep = true), after.lastMarketReceipts[0])
    }

    @Test fun displayed_quote_and_paid_cost_include_discounts_and_receipt_survives_save() {
        val original = market(4, "blindage_reactif")
        val before = original.copy(players = original.players.map {
            if (it.id == 0) it.copy(cards = listOf(catalog.cards.first { c -> c.id == "marche_clandestin" })) else it
        })
        val quote = GameEngine.quoteCard(before, "blindage_reactif")
        assertEquals(3, quote.cost)
        val after = buy(before, quote.card.id)
        assertEquals(quote.afterPayment, after.activePlayer.energy)
        assertEquals(quote.cost, after.lastMarketReceipts.getValue(0).paid)
        assertEquals(after, Json.decodeFromString<GameState>(Json.encodeToString(after)))
    }

    @Test fun absent_cards_and_purchases_outside_market_phase_are_illegal() {
        val before = market(4, "coeur_nucleaire")
        assertThrows(IllegalActionException::class.java) { GameEngine.quoteCard(before, "surcharge_reseau") }
        assertThrows(IllegalActionException::class.java) { GameEngine.quoteCard(before.copy(phase = TurnPhase.LANCER), "coeur_nucleaire") }
        assertThrows(IllegalActionException::class.java) { buy(before, "surcharge_reseau") }
    }
}
