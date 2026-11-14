package com.neonkaiju.kot.content

import com.neonkaiju.kot.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CatalogData(
    val cards: List<CardDef>,
    val tiles: List<MaliceTileDef>
)

object CatalogLoader {
    private val json = Json { ignoreUnknownKeys = false }

    fun loadCatalog(): CatalogData {
        val stream = CatalogLoader::class.java.getResourceAsStream("/catalog.json")
            ?: error("catalog.json introuvable")
        val text = stream.bufferedReader().use { it.readText() }
        return parse(text)
    }

    /** Décode puis valide : un catalogue invalide ne peut pas être chargé. */
    fun parse(text: String): CatalogData {
        val catalog = json.decodeFromString<CatalogData>(text)
        val errors = CatalogValidator.validate(catalog)
        if (errors.isNotEmpty()) throw InvalidCatalogException(errors)
        return catalog
    }
}

object EffectInterpreter {
    fun computeCardCost(state: GameState, buyerId: Int, card: CardDef): Int {
        val buyer = state.players.first { it.id == buyerId }
        val allEffects = buyer.cards.flatMap { it.effets } + buyer.tiles.flatMap { it.effets }
        val modifier = allEffects
            .filter { it.declencheur == EffectTrigger.PERMANENT && it.operation == EffectOperation.modifyCost }
            .sumOf { it.valeur }
        return (card.cout + modifier).coerceAtLeast(1)
    }

    fun computeDamageReduction(state: GameState, victimId: Int): Int {
        val victim = state.players.first { it.id == victimId }
        val allEffects = victim.cards.flatMap { it.effets } + victim.tiles.flatMap { it.effets }
        return allEffects
            .filter {
                it.declencheur == EffectTrigger.AVANT_DEGAT_SUBI &&
                    it.operation == EffectOperation.annulerDegat &&
                    ConditionParser.evaluate(it.condition, state, victimId)
            }
            .sumOf { it.valeur }
    }

    /**
     * Les changements de face s'appliquent quand les dés sont figés, avant le décompte des symboles :
     * appliqués après la résolution, ils n'auraient aucun effet.
     */
    fun applyFaceChanges(state: GameState, ownerId: Int): GameState {
        val owner = state.players.firstOrNull { it.id == ownerId } ?: return state
        val effects = (owner.cards.flatMap { it.effets } + owner.tiles.flatMap { it.effets })
            .filter { it.operation == EffectOperation.changerFace }
        return if (effects.isEmpty()) state else applyTrigger(state, EffectTrigger.APRES_RESOLUTION, ownerId, effects)
    }

    /**
     * Applique les effets [trigger] du propriétaire. [restrictTo], s'il est fourni, limite les cibles
     * (par exemple aux monstres réellement blessés pour APRES_DEGAT_INFLIGE).
     */
    fun applyTrigger(
        state: GameState,
        trigger: EffectTrigger,
        ownerId: Int,
        explicitEffects: List<EffectDef>? = null,
        restrictTo: Set<Int>? = null
    ): GameState {
        val owner = state.players.firstOrNull { it.id == ownerId } ?: return state
        if (!owner.isAlive) return state

        val effects = explicitEffects ?: (owner.cards.flatMap { it.effets } + owner.tiles.flatMap { it.effets })
            .filter { it.declencheur == trigger && it.operation != EffectOperation.changerFace }

        var current = state
        for (effect in effects) {
            if (!ConditionParser.evaluate(effect.condition, current, ownerId)) continue
            val targetIds = resolveTargets(current, ownerId, effect.cible)
                .filter { restrictTo == null || it in restrictTo }
            for (targetId in targetIds) {
                current = executeOperation(current, ownerId, targetId, effect)
            }
        }
        return current
    }

    private fun resolveTargets(state: GameState, ownerId: Int, cible: EffectTarget): List<Int> {
        val alivePlayers = state.players.filter { it.isAlive }
        return when (cible) {
            EffectTarget.SOI -> listOf(ownerId)
            EffectTarget.ACTIF -> listOf(state.activePlayer.id)
            EffectTarget.TOUS -> alivePlayers.map { it.id }
            EffectTarget.AUTRES -> alivePlayers.filter { it.id != ownerId }.map { it.id }
            EffectTarget.OCCUPANT -> listOfNotNull(state.tokyoOccupantId)
            // Comme AUTRES, HORS_TOKYO exclut le propriétaire : une carte ne se retourne pas contre son acheteur.
            EffectTarget.HORS_TOKYO -> alivePlayers.filter { it.id != state.tokyoOccupantId && it.id != ownerId }.map { it.id }
            EffectTarget.VOISINS -> {
                val aliveIds = alivePlayers.map { it.id }
                val idx = aliveIds.indexOf(ownerId)
                if (idx == -1 || aliveIds.size <= 1) emptyList()
                else {
                    val left = aliveIds[(idx - 1 + aliveIds.size) % aliveIds.size]
                    val right = aliveIds[(idx + 1) % aliveIds.size]
                    listOf(left, right).distinct().filter { it != ownerId }
                }
            }
        }
    }

    private fun executeOperation(state: GameState, ownerId: Int, targetId: Int, effect: EffectDef): GameState {
        return when (effect.operation) {
            EffectOperation.gainVp -> updatePlayer(state, targetId) {
                it.copy(vp = (it.vp + effect.valeur).coerceIn(0, 20))
            }
            EffectOperation.gainEnergy -> updatePlayer(state, targetId) {
                it.copy(energy = (it.energy + effect.valeur).coerceAtLeast(0))
            }
            EffectOperation.gainHp -> updatePlayer(state, targetId) {
                it.copy(hp = (it.hp + effect.valeur).coerceIn(0, it.maxHp))
            }
            EffectOperation.dealDamage -> {
                val reduction = computeDamageReduction(state, targetId)
                val finalDamage = (effect.valeur - reduction).coerceAtLeast(0)
                if (finalDamage == 0) state
                else applyDirectDamage(state, targetId, finalDamage)
            }
            EffectOperation.rerollDice -> {
                if (targetId == state.activePlayer.id) {
                    state.copy(rollsLeft = state.rollsLeft + effect.valeur)
                } else state
            }
            EffectOperation.changerFace -> {
                if (targetId != state.activePlayer.id) return state
                var remaining = effect.valeur
                state.copy(dice = state.dice.map { die ->
                    if (remaining > 0 && die.face == DieFace.ONE) {
                        remaining--
                        die.copy(face = DieFace.CLAW)
                    } else die
                })
            }
            EffectOperation.setFlag -> updatePlayer(state, targetId) {
                it.copy(flags = it.flags + "FLAG_${effect.valeur}")
            }
            EffectOperation.annulerDegat, EffectOperation.modifyCost -> state
        }
    }

    fun applyDirectDamage(state: GameState, victimId: Int, amount: Int): GameState {
        var updatedOccupant = state.tokyoOccupantId
        var discardedCards = emptyList<CardDef>()
        val newPlayers = state.players.map { p ->
            if (p.id != victimId || !p.isAlive) p
            else {
                val newHp = (p.hp - amount).coerceAtLeast(0)
                if (newHp == 0) {
                    if (updatedOccupant == p.id) updatedOccupant = null
                    discardedCards = discardedCards + p.cards
                    p.copy(
                        hp = 0,
                        isAlive = false,
                        energy = 0,
                        cards = emptyList(),
                        tiles = emptyList(),
                        damageTakenThisTurn = p.damageTakenThisTurn + amount
                    )
                } else {
                    p.copy(hp = newHp, damageTakenThisTurn = p.damageTakenThisTurn + amount)
                }
            }
        }
        return state.copy(
            players = newPlayers, 
            tokyoOccupantId = updatedOccupant,
            discard = state.discard + discardedCards
        )
    }

    private fun updatePlayer(state: GameState, playerId: Int, block: (PlayerState) -> PlayerState): GameState {
        return state.copy(players = state.players.map { if (it.id == playerId) block(it) else it })
    }
}
