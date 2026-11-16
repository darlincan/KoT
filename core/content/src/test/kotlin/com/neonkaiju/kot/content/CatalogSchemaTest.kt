package com.neonkaiju.kot.content

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.neonkaiju.kot.model.CardType
import com.neonkaiju.kot.model.EffectOperation
import com.neonkaiju.kot.model.EffectTarget
import com.neonkaiju.kot.model.EffectTrigger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * §8 « Validation au build » : catalog.json respecte catalog.schema.json, et le schéma reste
 * aligné sur les énumérations Kotlin et sur les constantes de [CatalogValidator].
 */
class CatalogSchemaTest {

    private val mapper = ObjectMapper()

    private fun resource(name: String): JsonNode =
        CatalogSchemaTest::class.java.getResourceAsStream("/$name")!!.use { mapper.readTree(it) }

    private val schemaNode = resource("catalog.schema.json")
    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode)
    private val catalog = resource("catalog.json")

    private fun errorsOf(transform: (ObjectNode) -> Unit): List<String> {
        val copy = catalog.deepCopy<ObjectNode>().also(transform)
        return schema.validate(copy).map { it.message }
    }

    private fun ObjectNode.card(index: Int) = (get("cards") as ArrayNode)[index] as ObjectNode
    private fun ObjectNode.tile(index: Int) = (get("tiles") as ArrayNode)[index] as ObjectNode
    private fun ObjectNode.firstEffect() = (get("effets") as ArrayNode)[0] as ObjectNode

    private fun schemaEnum(vararg path: String): List<String> =
        path.fold(schemaNode) { node, key -> node.get(key) }.get("enum").map { it.asText() }

    @Test
    fun `catalog_json respecte le schéma`() {
        assertEquals(emptyList<String>(), schema.validate(catalog).map { it.message })
    }

    @Test
    fun `le schéma reprend les énumérations du modèle`() {
        assertEquals(EffectTrigger.entries.map { it.name }, schemaEnum("\$defs", "effect", "properties", "declencheur"))
        assertEquals(EffectTarget.entries.map { it.name }, schemaEnum("\$defs", "effect", "properties", "cible"))
        assertEquals(EffectOperation.entries.map { it.name }, schemaEnum("\$defs", "effect", "properties", "operation"))
        assertEquals(CardType.entries.map { it.name }, schemaEnum("\$defs", "card", "properties", "type"))
    }

    @Test
    fun `le schéma reprend les quantités de CatalogValidator`() {
        val props = schemaNode.get("properties")
        listOf("cards" to CatalogValidator.CARD_COUNT, "tiles" to CatalogValidator.TILE_COUNT).forEach { (key, count) ->
            assertEquals(count, props.get(key).get("minItems").asInt(), key)
            assertEquals(count, props.get(key).get("maxItems").asInt(), key)
        }
        assertEquals(
            CatalogValidator.TILES_PER_LEVEL.keys.map { it.toString() },
            schemaEnum("\$defs", "tile", "properties", "niveau")
        )
    }

    @Test
    fun `une carte en trop ou en moins est refusée`() {
        assertFalse(errorsOf { (it.get("cards") as ArrayNode).remove(0) }.isEmpty())
        assertFalse(errorsOf { (it.get("tiles") as ArrayNode).add(it.tile(0).deepCopy()) }.isEmpty())
    }

    @Test
    fun `un champ inconnu ou manquant est refusé`() {
        assertFalse(errorsOf { it.card(0).put("rarete", "commune") }.isEmpty())
        assertFalse(errorsOf { it.card(0).remove("cout") }.isEmpty())
        assertFalse(errorsOf { it.tile(0).remove("niveau") }.isEmpty())
        assertFalse(errorsOf { it.card(0).firstEffect().remove("cible") }.isEmpty())
    }

    @Test
    fun `une valeur hors grammaire est refusée`() {
        assertFalse(errorsOf { it.card(0).put("id", "Blindage-Réactif") }.isEmpty())
        assertFalse(errorsOf { it.card(0).put("cout", 0) }.isEmpty())
        assertFalse(errorsOf { it.card(0).put("nom", "  ") }.isEmpty())
        assertFalse(errorsOf { it.card(0).put("type", "EQUIPEMENT") }.isEmpty())
        assertFalse(errorsOf { it.tile(0).put("niveau", 5) }.isEmpty())
        assertFalse(errorsOf { it.card(0).firstEffect().put("operation", "addToken") }.isEmpty())
        assertFalse(errorsOf { it.card(0).firstEffect().put("valeur", "deux") }.isEmpty())
        assertFalse(errorsOf { (it.card(0).get("effets") as ArrayNode).removeAll() }.isEmpty())
    }
}
