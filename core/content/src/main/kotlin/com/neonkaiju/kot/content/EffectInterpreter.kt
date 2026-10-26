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
        return json.decodeFromString<CatalogData>(text)
    }
}

object ConditionParser {
    fun evaluate(condition: String?, state: GameState, ownerId: Int): Boolean {
        if (condition.isNullOrBlank()) return true
        val player = state.players.first { it.id == ownerId }
        val parts = condition.trim().split(" ")
        require(parts.size == 3) { "Condition invalide : $condition" }
        val (lhs, op, rhs) = parts

        val leftVal: Any = when (lhs) {
            "degatsSubisCeTour" -> player.damageTakenThisTurn
            "estDansTokyo" -> (state.tokyoOccupantId == ownerId)
            "energie" -> player.energy
            "hp" -> player.hp
            "vp" -> player.vp
            else -> error("Variable de condition inconnue : $lhs")
        }

        return when (leftVal) {
            is Boolean -> {
                val rightBool = rhs.toBooleanStrict()
                when (op) {
                    "==" -> leftVal == rightBool
                    "!=" -> leftVal != rightBool
                    else -> error("Opérateur booléen inconnu : $op")
                }
            }
            is Int -> {
                val rightInt = rhs.toInt()
                when (op) {
                    "==" -> leftVal == rightInt
                    "!=" -> leftVal != rightInt
                    ">=" -> leftVal >= rightInt
                    "<=" -> leftVal <= rightInt
                    ">" -> leftVal > rightInt
                    "<" -> leftVal < rightInt
                    else -> error("Opérateur entier inconnu : $op")
                }
            }
            else -> false
        }
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

    fun applyTrigger(state: GameState, trigger: EffectTrigger, ownerId: Int, explicitEffects: List<EffectDef>? = null): GameState {
        val owner = state.players.firstOrNull { it.id == ownerId } ?: return state
        if (!owner.isAlive) return state

        val effects = explicitEffects ?: (owner.cards.flatMap { it.effets } + owner.tiles.flatMap { it.effets })
            .filter { it.declencheur == trigger }

        var current = state
        for (effect in effects) {
            if (!ConditionParser.evaluate(effect.condition, current, ownerId)) continue
            val targetIds = resolveTargets(current, ownerId, effect.cible)
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
            EffectTarget.HORS_TOKYO -> alivePlayers.filter { it.id != state.tokyoOccupantId }.map { it.id }
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
                val idx = state.dice.indexOfFirst { it.face == DieFace.ONE }
                if (idx != -1) {
                    val updated = state.dice.toMutableList()
                    updated[idx] = updated[idx].copy(face = DieFace.CLAW)
                    state.copy(dice = updated)
                } else state
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
