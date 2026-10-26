package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.model.CardType
import com.neonkaiju.kot.model.DieFace
import com.neonkaiju.kot.model.MonsterId
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
}

/** Une commande de l'ecran : active, ou grivee avec sa raison. */
data class CommandUi(val enabled: Boolean, val blockedReason: BlockedReason? = null)

data class BotTurnUi(val botName: String, val lastAction: String)

data class FleeUi(val attackerName: String, val damageTaken: Int, val remainingHp: Int)

data class TileUi(val id: String, val name: String, val text: String)

data class MalicePickUi(val level: Int, val tiles: List<TileUi>)

data class GameUiState(
    val me: PlayerUi,
    val opponents: List<PlayerUi>,
    val tokyoOccupant: PlayerUi?,
    val dice: List<DieUi>,
    val rollsLeft: Int,
    val isResolving: Boolean,
    val symbolsToResolve: List<SymbolResolutionType>,
    val reroll: CommandUi,
    val stopRolling: CommandUi,
    val openShop: CommandUi,
    val endTurn: CommandUi,
    val lastLog: String,
    val botTurn: BotTurnUi?,
    val flee: FleeUi?,
    val malicePick: MalicePickUi?,
    val isBotFastForward: Boolean,
)

data class ShopCardUi(
    val id: String,
    val name: String,
    val type: CardType,
    val text: String,
    /** Prix reel, remise des cartes et tuiles deja applique. */
    val cost: Int,
    val canBuy: Boolean,
)

data class ShopUiState(
    val energy: Int,
    val cards: List<ShopCardUi>,
    val canSweep: Boolean,
)

data class GameOverUiState(
    val winnerName: String?,
    val seed: Long,
    val ranking: List<PlayerUi>,
)
