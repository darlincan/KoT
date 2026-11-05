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
        val catalog = json.decodeFromString<CatalogData>(text)
        require(catalog.cards.size == 20 && catalog.tiles.size == 10)
        val ids = catalog.cards.map { it.id } + catalog.tiles.map { it.id }
        require(ids.distinct().size == ids.size) { "Identifiant de contenu dupliqué" }
        require(catalog.tiles.groupingBy { it.niveau }.eachCount() == mapOf(3 to 4, 6 to 4, 10 to 2))
        val sample = GameState(rng = DeterministicRng(0), players = listOf(PlayerState(0, MonsterId.VOLTRAX, false)))
        (catalog.cards.flatMap { it.effets } + catalog.tiles.flatMap { it.effets }).forEach {
            ConditionParser.evaluate(it.condition, sample, 0)
        }
        return catalog
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
            "degatsSubisCeTour" -> player.damageTakenThisTurn + player.damagePreventedThisTurn
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
            .filter { it.declencheur == EffectTrigger.PERMANENT && it.operation == EffectOperation.modifyCost &&
                ConditionParser.evaluate(it.condition, state, buyerId) }
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

    fun applyTrigger(state: GameState, trigger: EffectTrigger, ownerId: Int, explicitEffects: List<EffectDef>? = null, damagedPlayerIds: Set<Int>? = null): GameState {
        val owner = state.players.firstOrNull { it.id == ownerId } ?: return state
        if (!owner.isAlive) return state

        val effects = explicitEffects?.map { "Action" to it } ?: (
            owner.cards.flatMap { card -> card.effets.map { card.nom to it } } +
                owner.tiles.flatMap { tile -> tile.effets.map { tile.nom to it } }
            ).filter { it.second.declencheur == trigger }

        var current = state
        for ((source, effect) in effects) {
            if (!ConditionParser.evaluate(effect.condition, current, ownerId)) continue
            val targetIds = resolveTargets(current, ownerId, effect.cible).filter {
                trigger != EffectTrigger.APRES_DEGAT_INFLIGE || effect.cible != EffectTarget.AUTRES ||
                    damagedPlayerIds == null || it in damagedPlayerIds
            }
            for (targetId in targetIds) {
                val previous = current
                val before = current.players.first { it.id == targetId }
                current = executeOperation(current, ownerId, targetId, effect)
                val after = current.players.first { it.id == targetId }
                val description = when (effect.operation) {
                    EffectOperation.gainVp -> "+${after.vp - before.vp} ★"
                    EffectOperation.gainEnergy -> "+${after.energy - before.energy} Cellule(s)"
                    EffectOperation.gainHp -> "+${after.hp - before.hp} ♥"
                    EffectOperation.changerFace -> "un dé 1 devient une griffe"
                    EffectOperation.setFlag -> "mode furie activé"
                    EffectOperation.rerollDice -> "lancers disponibles : ${current.rollsLeft}"
                    else -> null
                }
                if (current != previous && description != null) {
                    current = current.copy(actionLog = current.actionLog + "${owner.monster.displayName} — $source : $description pour ${after.monster.displayName}")
                }
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
            EffectOperation.gainHp -> if (effect.valeur < 0) applyDirectDamage(state, targetId, -effect.valeur)
                else updatePlayer(state, targetId) {
                    if (it.isAlive) it.copy(hp = (it.hp + effect.valeur).coerceAtMost(it.maxHp)) else it
                }
            EffectOperation.dealDamage -> applyDamage(state, targetId, effect.valeur)
            EffectOperation.rerollDice -> {
                if (targetId == state.activePlayer.id) {
                    state.copy(rollsLeft = (state.rollsLeft + effect.valeur).coerceIn(0, 3 - state.rollsMade))
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

    /** Apply a single attack, consuming a first-damage shield even if it absorbs all damage. */
    fun applyDamage(state: GameState, victimId: Int, amount: Int): GameState {
        val prevented = minOf(amount.coerceAtLeast(0), computeDamageReduction(state, victimId))
        var current = applyDirectDamage(state, victimId, (amount - prevented).coerceAtLeast(0))
        if (prevented > 0) {
            current = updatePlayer(current, victimId) { it.copy(damagePreventedThisTurn = it.damagePreventedThisTurn + prevented) }
            val victim = current.players.first { it.id == victimId }
            current = current.copy(actionLog = current.actionLog + "${victim.monster.displayName} : $prevented dégât(s) annulé(s) par ses protections")
        }
        return current
    }

    fun applyDirectDamage(state: GameState, victimId: Int, amount: Int): GameState {
        if (amount <= 0) return state
        val victim = state.players.first { it.id == victimId }
        if (!victim.isAlive) return state
        var updatedOccupant = state.tokyoOccupantId
        val newPlayers = state.players.map { p ->
            if (p.id != victimId || !p.isAlive) p
            else {
                val newHp = (p.hp - amount).coerceAtLeast(0)
                if (newHp == 0) {
                    if (updatedOccupant == p.id) updatedOccupant = null
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
        val after = newPlayers.first { it.id == victimId }
        return state.copy(
            players = newPlayers,
            tokyoOccupantId = updatedOccupant,
            discard = state.discard + if (!after.isAlive) victim.cards else emptyList(),
            actionLog = state.actionLog + "${victim.monster.displayName} subit ${victim.hp - after.hp} dégât(s) : ${after.hp} ♥${if (!after.isAlive) " — éliminé" else ""}"
        )
    }

    private fun updatePlayer(state: GameState, playerId: Int, block: (PlayerState) -> PlayerState): GameState {
        return state.copy(players = state.players.map { if (it.id == playerId) block(it) else it })
    }
}
