package com.neonkaiju.kot.model

import kotlinx.serialization.Serializable

@Serializable
enum class MonsterId(val displayName: String, val bio: String, val accentHex: Long) {
    VOLTRAX("Voltrax", "Reptile blindé branché sur la haute tension", 0xFF28E0FF),
    MECHA_LYNX("Mecha-Lynx", "Prédateur félin à griffes laser", 0xFFFF3D9A),
    HYDROKOR("Hydrokor", "Léviathan abyssal sous pression", 0xFF00F5D4),
    SPECTRA("Spectra", "Entité holographique de surcharge", 0xFFB5179E),
    FERRODON("Ferrodon", "Colosse d'acier lourdement blindé", 0xFFF72585),
    KILOWATT("Kilowatt", "Insectoïde voltaïque ultra-rapide", 0xFFFEE440)
}

@Serializable
enum class DieFace {
    ONE, TWO, THREE, ENERGY, CLAW, HEART
}

@Serializable
data class Die(
    val id: Int,
    val face: DieFace,
    val isKept: Boolean = false
)

@Serializable
enum class CardType { POUVOIR, ACTION }

@Serializable
enum class EffectTrigger {
    ACHAT, DEBUT_TOUR, AVANT_LANCER, AVANT_RESOLUTION, APRES_RESOLUTION,
    AVANT_DEGAT_SUBI, APRES_DEGAT_INFLIGE, ENTREE_TOKYO, FIN_TOUR, PERMANENT
}

@Serializable
enum class EffectTarget {
    SOI, ACTIF, TOUS, AUTRES, OCCUPANT, HORS_TOKYO, VOISINS
}

@Serializable
enum class EffectOperation {
    gainVp, gainEnergy, gainHp, dealDamage, annulerDegat,
    modifyCost, rerollDice, changerFace, setFlag
}

@Serializable
data class EffectDef(
    val declencheur: EffectTrigger,
    val condition: String? = null,
    val cible: EffectTarget,
    val operation: EffectOperation,
    val valeur: Int = 0
)

@Serializable
data class CardDef(
    val id: String,
    val nom: String,
    val cout: Int,
    val type: CardType,
    val texte: String,
    val effets: List<EffectDef>
)

@Serializable
data class MaliceTileDef(
    val id: String,
    val nom: String,
    val niveau: Int,
    val texte: String,
    val effets: List<EffectDef>
)

@Serializable
enum class TurnPhase {
    LANCER,
    RESOUDRE,
    DECISION_FUITE,
    ENTRER,
    ACHETER,
    FIN_TOUR,
    FIN_PARTIE
}

@Serializable
data class PendingFleeDecision(
    val attackerId: Int,
    val occupantId: Int,
    val damageTaken: Int
)

@Serializable
data class PlayerState(
    val id: Int,
    val monster: MonsterId,
    val isBot: Boolean,
    val hp: Int = 10,
    val maxHp: Int = 10,
    val vp: Int = 0,
    val energy: Int = 0,
    val malice: Int = 0,
    val isAlive: Boolean = true,
    val cards: List<CardDef> = emptyList(),
    val tiles: List<MaliceTileDef> = emptyList(),
    val unlockedMaliceLevels: Set<Int> = emptySet(),
    val damageTakenThisTurn: Int = 0,
    val damagePreventedThisTurn: Int = 0,
    val flags: Set<String> = emptySet()
)

@Serializable
data class DeterministicRng(
    val seed: Long,
    val cursor: Int = 0
) {
    fun nextDieFace(): Pair<DieFace, DeterministicRng> {
        val hash = randomBits(cursor)
        val faces = DieFace.entries
        return faces[(hash % faces.size).toInt()] to copy(cursor = cursor + 1)
    }

    // SplitMix64: each cursor yields independent bits, including for nearby seeds.
    private fun randomBits(index: Int): Long {
        var z = seed + (index.toLong() + 1) * -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return (z xor (z ushr 31)) ushr 1
    }

    fun <T> shuffle(list: List<T>): Pair<List<T>, DeterministicRng> {
        val mutable = list.toMutableList()
        var currentRng = this
        for (i in mutable.lastIndex downTo 1) {
            val j = (currentRng.randomBits(currentRng.cursor) % (i + 1)).toInt()
            val tmp = mutable[i]
            mutable[i] = mutable[j]
            mutable[j] = tmp
            currentRng = currentRng.copy(cursor = currentRng.cursor + 1)
        }
        return mutable to currentRng
    }
}

@Serializable
enum class SymbolResolutionType {
    NUMBERS, ENERGY, CLAWS, HEARTS
}

@Serializable
sealed interface GameAction {
    @Serializable data class ToggleKeep(val dieId: Int) : GameAction
    @Serializable data class SetResolutionOrder(val order: List<SymbolResolutionType>) : GameAction
    @Serializable data object Reroll : GameAction
    @Serializable data object StopRolling : GameAction
    @Serializable data class ResolveSymbols(val symbolType: SymbolResolutionType) : GameAction
    @Serializable data object Flee : GameAction
    @Serializable data object Stay : GameAction
    @Serializable data class PickMaliceTile(val tileId: String) : GameAction
    @Serializable data class BuyCard(val cardId: String) : GameAction
    @Serializable data object SweepShop : GameAction
    @Serializable data object EndTurn : GameAction
}

@Serializable
data class MarketReceipt(
    val itemName: String,
    val energyBefore: Int,
    val paid: Int,
    val energyAfter: Int,
    val effectEnergyChange: Int = 0,
    val effects: List<String> = emptyList(),
    val isSweep: Boolean = false
)

/** Actual resource increases already applied by the engine, retained per player. */
@Serializable
data class ResolutionGains(
    val vp: Int = 0,
    val energy: Int = 0,
    val healing: Int = 0,
    val malice: Int = 0,
    val isComplete: Boolean = false,
    val isStartOfTurn: Boolean = false,
    val numbersVp: Int = 0,
    val tokyoVp: Int = 0
)

@Serializable
data class GameState(
    val version: Int = 2,
    val rng: DeterministicRng,
    val players: List<PlayerState>,
    val activePlayerIndex: Int = 0,
    val tokyoOccupantId: Int? = null,
    val phase: TurnPhase = TurnPhase.LANCER,
    val rollsLeft: Int = 2,
    val maxRolls: Int = 3,
    val resolutionOrder: List<SymbolResolutionType> = listOf(SymbolResolutionType.NUMBERS, SymbolResolutionType.ENERGY, SymbolResolutionType.HEARTS, SymbolResolutionType.CLAWS),
    val resolutionLogStart: Int = 0,
    val dice: List<Die> = emptyList(),
    val unresolvedSymbols: Set<SymbolResolutionType> = emptySet(),
    val pendingFlee: PendingFleeDecision? = null,
    val pendingMaliceLevels: List<Int> = emptyList(),
    val deck: List<CardDef> = emptyList(),
    val discard: List<CardDef> = emptyList(),
    val shop: List<CardDef> = emptyList(),
    val availableTiles: List<MaliceTileDef> = emptyList(),
    val winnerId: Int? = null,
    val lastMarketReceipts: Map<Int, MarketReceipt> = emptyMap(),
    val lastResolutionGains: Map<Int, ResolutionGains> = emptyMap(),
    val actionHistory: List<GameAction> = emptyList(),
    val actionLog: List<String> = emptyList()
) {
    val rollsMade: Int get() = maxRolls - rollsLeft
    val decisionPlayer: PlayerState get() = if (pendingMaliceLevels.isNotEmpty()) activePlayer
        else pendingFlee?.let { decision -> players.first { it.id == decision.occupantId } } ?: activePlayer
    val resolutionSummary: List<String> get() = actionLog.drop(resolutionLogStart)
    val activePlayer: PlayerState get() = players[activePlayerIndex]
}
