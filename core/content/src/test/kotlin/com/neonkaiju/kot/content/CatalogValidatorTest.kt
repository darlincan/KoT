package com.neonkaiju.kot.content

import com.neonkaiju.kot.model.*
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CatalogValidatorTest {

    private val catalog = CatalogLoader.loadCatalog()

    private fun errorsOf(transform: (CatalogData) -> CatalogData) = CatalogValidator.validate(transform(catalog))

    @Test
    fun `le catalogue livré est valide`() {
        assertEquals(emptyList<String>(), CatalogValidator.validate(catalog))
        assertEquals(20, catalog.cards.size)
        assertEquals(10, catalog.tiles.size)
    }

    @Test
    fun `il faut exactement 20 cartes et 10 tuiles`() {
        assertTrue(errorsOf { it.copy(cards = it.cards.drop(1)) }.any { "19 cartes" in it })
        assertTrue(errorsOf { it.copy(tiles = it.tiles.drop(1)) }.any { "9 tuiles" in it })
    }

    @Test
    fun `un identifiant dupliqué est refusé, y compris entre carte et tuile`() {
        val dup = catalog.cards[0].copy()
        assertTrue(errorsOf { it.copy(cards = it.cards.dropLast(1) + dup) }.any { "dupliqué" in it })
        val clash = catalog.tiles[0].copy(id = catalog.cards[0].id)
        assertTrue(errorsOf { it.copy(tiles = listOf(clash) + it.tiles.drop(1)) }.any { "dupliqué" in it })
    }

    @Test
    fun `la répartition des tuiles par niveau est 4, 4, 2`() {
        val wrongLevel = catalog.tiles[0].copy(niveau = 5)
        val errors = errorsOf { it.copy(tiles = listOf(wrongLevel) + it.tiles.drop(1)) }
        assertTrue(errors.any { "niveau 5 invalide" in it })
        assertTrue(errors.any { "de niveau 3 au lieu de 4" in it })
    }

    @Test
    fun `une condition invalide est refusée au chargement`() {
        val card = catalog.cards[0]
        val broken = card.copy(effets = listOf(card.effets[0].copy(condition = "pv >= 3")))
        assertTrue(errorsOf { it.copy(cards = listOf(broken) + it.cards.drop(1)) }.any { "variable inconnue" in it })
    }

    @Test
    fun `une opération à moment imposé doit utiliser le bon déclencheur`() {
        val card = catalog.cards.first { it.id == "marche_clandestin" }
        val broken = card.copy(effets = listOf(card.effets[0].copy(declencheur = EffectTrigger.FIN_TOUR)))
        val errors = errorsOf { c -> c.copy(cards = c.cards.map { if (it.id == card.id) broken else it }) }
        assertTrue(errors.any { "modifyCost exige le déclencheur PERMANENT" in it })
    }

    @Test
    fun `une carte ACTION ne porte que des effets ACHAT`() {
        val card = catalog.cards.first { it.id == "surcharge_reseau" }
        val broken = card.copy(effets = listOf(card.effets[0].copy(declencheur = EffectTrigger.FIN_TOUR)))
        val errors = errorsOf { c -> c.copy(cards = c.cards.map { if (it.id == card.id) broken else it }) }
        assertTrue(errors.any { "ACTION" in it })
    }

    @Test
    fun `une opération, une cible ou un déclencheur inconnu est rejeté au décodage`() {
        val text = CatalogLoader::class.java.getResourceAsStream("/catalog.json")!!.bufferedReader().readText()
        listOf(
            "\"operation\": \"gainEnergy\"" to "\"operation\": \"addToken\"",
            "\"cible\": \"VOISINS\"" to "\"cible\": \"MOI\"",
            "\"declencheur\": \"FIN_TOUR\"" to "\"declencheur\": \"MILIEU_TOUR\""
        ).forEach { (from, to) ->
            assertTrue(from in text, from)
            assertThrows(SerializationException::class.java) { CatalogLoader.parse(text.replaceFirst(from, to)) }
        }
    }

    @Test
    fun `le chargeur refuse un catalogue invalide`() {
        val text = CatalogLoader::class.java.getResourceAsStream("/catalog.json")!!.bufferedReader().readText()
        val broken = text.replaceFirst("\"condition\": \"hp >= 8\"", "\"condition\": \"hp >= huit\"")
        assertNotEquals(text, broken)
        assertThrows(InvalidCatalogException::class.java) { CatalogLoader.parse(broken) }
    }
}
