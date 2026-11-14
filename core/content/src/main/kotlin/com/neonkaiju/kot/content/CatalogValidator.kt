package com.neonkaiju.kot.content

import com.neonkaiju.kot.model.CardType
import com.neonkaiju.kot.model.EffectDef
import com.neonkaiju.kot.model.EffectOperation
import com.neonkaiju.kot.model.EffectTrigger

class InvalidCatalogException(val errors: List<String>) :
    IllegalStateException("Catalogue invalide :\n" + errors.joinToString("\n") { "- $it" })

/**
 * Règles du §8 « Validation au build ». Les opérations, cibles et déclencheurs inconnus sont déjà
 * rejetés par le décodage JSON strict (énumérations) ; on vérifie ici tout le reste.
 */
object CatalogValidator {
    const val CARD_COUNT = 20
    const val TILE_COUNT = 10
    val TILES_PER_LEVEL = mapOf(3 to 4, 6 to 4, 10 to 2)

    /** Opérations dont le moment d'application est imposé par le moteur. */
    private val REQUIRED_TRIGGER = mapOf(
        EffectOperation.annulerDegat to EffectTrigger.AVANT_DEGAT_SUBI,
        EffectOperation.modifyCost to EffectTrigger.PERMANENT,
        EffectOperation.changerFace to EffectTrigger.APRES_RESOLUTION
    )

    private val ID_FORMAT = Regex("[a-z0-9_]+")

    fun validate(catalog: CatalogData): List<String> {
        val errors = mutableListOf<String>()

        if (catalog.cards.size != CARD_COUNT) {
            errors += "le catalogue compte ${catalog.cards.size} cartes au lieu de $CARD_COUNT"
        }
        if (catalog.tiles.size != TILE_COUNT) {
            errors += "le catalogue compte ${catalog.tiles.size} tuiles au lieu de $TILE_COUNT"
        }

        val allIds = catalog.cards.map { it.id } + catalog.tiles.map { it.id }
        allIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            errors += "identifiant dupliqué : $it"
        }
        allIds.filterNot { ID_FORMAT.matches(it) }.forEach {
            errors += "identifiant mal formé (attendu [a-z0-9_]+) : $it"
        }

        catalog.cards.forEach { card ->
            if (card.cout < 1) errors += "${card.id} : coût ${card.cout} invalide"
            if (card.nom.isBlank() || card.texte.isBlank()) errors += "${card.id} : nom ou texte vide"
            val immediate = card.effets.map { it.declencheur == EffectTrigger.ACHAT }
            if (card.type == CardType.ACTION && !immediate.all { it }) {
                errors += "${card.id} : une carte ACTION n'a que des effets ACHAT"
            }
            if (card.type == CardType.POUVOIR && immediate.any { it }) {
                errors += "${card.id} : une carte POUVOIR n'a pas d'effet ACHAT"
            }
            errors += validateEffects(card.id, card.effets)
        }

        val counts = catalog.tiles.groupingBy { it.niveau }.eachCount()
        catalog.tiles.forEach { tile ->
            if (tile.niveau !in TILES_PER_LEVEL) errors += "${tile.id} : niveau ${tile.niveau} invalide (3, 6 ou 10)"
            if (tile.nom.isBlank() || tile.texte.isBlank()) errors += "${tile.id} : nom ou texte vide"
            if (tile.effets.any { it.declencheur == EffectTrigger.ACHAT }) {
                errors += "${tile.id} : une tuile ne s'achète pas, déclencheur ACHAT interdit"
            }
            errors += validateEffects(tile.id, tile.effets)
        }
        TILES_PER_LEVEL.forEach { (level, expected) ->
            val actual = counts[level] ?: 0
            if (actual != expected) errors += "$actual tuile(s) de niveau $level au lieu de $expected"
        }

        return errors
    }

    private fun validateEffects(ownerId: String, effects: List<EffectDef>): List<String> {
        if (effects.isEmpty()) return listOf("$ownerId : aucun effet")
        return effects.flatMapIndexed { index, effect ->
            val where = "$ownerId, effet #$index"
            buildList {
                try {
                    ConditionParser.parse(effect.condition)
                } catch (e: ConditionSyntaxException) {
                    add("$where : ${e.message}")
                }
                val required = REQUIRED_TRIGGER[effect.operation]
                if (required != null && effect.declencheur != required) {
                    add("$where : ${effect.operation} exige le déclencheur $required")
                }
                if (effect.valeur == 0 && effect.operation != EffectOperation.setFlag) {
                    add("$where : valeur nulle, l'effet ne fait rien")
                }
            }
        }
    }
}
