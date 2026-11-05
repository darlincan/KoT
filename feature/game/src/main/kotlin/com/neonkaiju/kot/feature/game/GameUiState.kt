package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.model.MonsterId

data class PlayerUi(
    val id: Int,
    val monster: MonsterId,
    val hp: Int,
    val vp: Int,
    val energy: Int,
    val isAlive: Boolean,
) {
    val name: String get() = monster.displayName
}

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

enum class GameEndReason { TWENTY_STARS, LAST_SURVIVOR, ALL_ELIMINATED }

data class GameOverUiState(
    val winnerName: String?,
    val endReason: GameEndReason,
    val humanId: Int,
    val seed: Long,
    val ranking: List<PlayerUi>,
)
