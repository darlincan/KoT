package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.model.CardType
import com.neonkaiju.kot.model.DieFace
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.ResolutionGains
import com.neonkaiju.kot.model.SymbolResolutionType
import com.neonkaiju.kot.model.TurnPhase


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

data class BotTurnUi(val botName: String, val lastAction: String)

data class FleeUi(val attackerName: String, val damageTaken: Int, val remainingHp: Int) {
    val fleeConsequence: String
        get() = "$attackerName prend la Tour. Vous pourrez de nouveau vous soigner avec les dés."
    val stayConsequence: String
        get() = "+2 ★ au début de votre prochain tour si vous survivez, mais aucun soin par les dés."
}

data class TileUi(val id: String, val name: String, val text: String)

data class MalicePickUi(
    val level: Int,
    val tiles: List<TileUi>,
    val ownedTiles: List<TileUi> = emptyList(),
)

data class GameUiState(
    val me: PlayerUi,
    val opponents: List<PlayerUi>,
    val tokyoOccupant: PlayerUi?,
    val activePlayerName: String,
    val phase: TurnPhase,
    val isChoosingPower: Boolean,
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
)

data class ShopCardUi(
    val id: String,
    val name: String,
    val type: CardType,
    val text: String,
    /** Prix reel, remise des cartes et tuiles deja applique. */
    val cost: Int,
    val baseCost: Int,
    val canBuy: Boolean,
)

data class ShopUiState(
    val buyerId: Int,
    val isBotTurn: Boolean,
    val energy: Int,
    val cards: List<ShopCardUi>,
    val canSweep: Boolean,
    /** Une confirmation n'est valable que pour l'état de partie où elle a été ouverte. */
    val revision: Int,
    val lastReceipt: String?,
)

enum class GameEndReason { TWENTY_STARS, LAST_SURVIVOR, ALL_ELIMINATED }

data class GameOverUiState(
    val winnerName: String?,
    val endReason: GameEndReason,
    val humanId: Int,
    val seed: Long,
    val ranking: List<PlayerUi>,
)
