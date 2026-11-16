package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.model.CardType
import com.neonkaiju.kot.model.DieFace
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.ResolutionGains
import com.neonkaiju.kot.model.SymbolResolutionType


data class HomeUiState(
    val canResume: Boolean = false,
    val version: String = "",
)

data class MonsterPickerUiState(
    val monsters: List<MonsterId> = MonsterId.entries,
    val selected: MonsterId = MonsterId.VOLTRAX,
)

data class PlayerUi(
    val id: Int,
    val monster: MonsterId,
    val hp: Int,
    val vp: Int,
    val energy: Int,
    val malice: Int,
    val isAlive: Boolean,
    val isInTokyo: Boolean,
    val isActive: Boolean,
    val gains: ResolutionGains? = null,
    val cardCount: Int = 0,
) {
    val name: String get() = monster.displayName
}

data class DieUi(
    val id: Int,
    val face: DieFace,
    val isKept: Boolean,
    val canToggle: Boolean,
)

/** raison pour une commande est grisée qu'on affiche au toucher long (rule transverse n2). */
enum class BlockedReason {
    NO_ROLLS_LEFT,
    ALL_DICE_KEPT,
    ROLLING_OVER,
    NOT_BUY_PHASE,
    RESOLVE_FIRST,
    BOT_PLAYING,
}

/** Une commande de l'ecran : active, ou grivee avec sa raison. */
data class CommandUi(val enabled: Boolean, val blockedReason: BlockedReason? = null)

/** Moment du tour affiché au joueur ; le libellé est dans les ressources. */
enum class PhaseUi { ROLL, RESOLVE, FLEE, ENTER, BUY, END_TURN, GAME_OVER, CHOOSE_POWER }

/**
 * [lastAction] est l'explication produite par la politique du bot ([com.neonkaiju.kot.ai.BotPolicy]).
 * [result] : gains à rappeler quand le bot passe aux achats.
 */
data class BotTurnUi(val botName: String, val lastAction: String, val result: ResolutionGains? = null)

data class FleeUi(val attackerName: String, val damageTaken: Int, val remainingHp: Int)

data class TileUi(val id: String, val name: String, val text: String)

data class MalicePickUi(
    val level: Int,
    val tiles: List<TileUi>,
    val ownedTiles: List<TileUi> = emptyList(),
    val tiers: List<Int> = emptyList(),
)

data class GameUiState(
    val me: PlayerUi,
    val opponents: List<PlayerUi>,
    val tokyoOccupant: PlayerUi?,
    val activePlayerName: String,
    val phase: PhaseUi,
    val isRolling: Boolean,
    val isBuyPhase: Boolean,
    val maliceTiers: List<Int>,
    val dice: List<DieUi>,
    val rollsMade: Int,
    val maxRolls: Int,
    val rollsLeft: Int,
    val resolutionOrder: List<SymbolResolutionType>,
    val canChangeOrder: Boolean,
    val reroll: CommandUi,
    val stopRolling: CommandUi,
    val openShop: CommandUi,
    val endTurn: CommandUi,
    val summary: List<String>,
    val botTurn: BotTurnUi?,
    val flee: FleeUi?,
    val malicePick: MalicePickUi?,
    val isBotFastForward: Boolean,
    val countdownEnabled: Boolean = false,
)

data class ShopCardUi(
    val id: String,
    val name: String,
    val type: CardType,
    val text: String,
    /** Prix reel, remise des cartes et tuiles deja applique. */
    val cost: Int,
    val baseCost: Int,
    /** Négatif s'il manque des Cellules. */
    val energyAfterPurchase: Int,
    val canBuy: Boolean,
)

/** Dernier paiement du joueur ; [effectChange] est le gain ou la perte de Cellules dus à l'effet. */
data class ReceiptUi(
    val itemName: String,
    val energyBefore: Int,
    val paid: Int,
    val effectChange: Int,
    val energyAfter: Int,
)

data class ShopUiState(
    val buyerId: Int,
    val isBotTurn: Boolean,
    val energy: Int,
    val cards: List<ShopCardUi>,
    val canSweep: Boolean,
    val sweepCost: Int,
    val energyAfterSweep: Int,
    /** Une confirmation n'est valable que pour l'état de partie où elle a été ouverte. */
    val revision: Int,
    val lastReceipt: ReceiptUi?,
)

enum class GameEndReason { TWENTY_STARS, LAST_SURVIVOR, ALL_ELIMINATED }

data class GameOverUiState(
    val winnerName: String?,
    val endReason: GameEndReason,
    val humanId: Int,
    val seed: Long,
    val ranking: List<PlayerUi>,
)
