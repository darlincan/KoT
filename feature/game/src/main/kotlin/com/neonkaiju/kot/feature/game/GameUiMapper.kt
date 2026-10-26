package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.content.EffectInterpreter
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.PlayerState
import com.neonkaiju.kot.model.TurnPhase

internal fun PlayerState.toUi(state: GameState) = PlayerUi(
    id = id,
    monster = monster,
    hp = hp,
    vp = vp,
    energy = energy,
    malice = malice,
    isAlive = isAlive,
    isInTokyo = state.tokyoOccupantId == id,
    isActive = state.activePlayer.id == id,
)

internal fun GameState.toHomeUiState(version: String) = HomeUiState(
    canResume = phase != TurnPhase.FIN_PARTIE,
    version = version,
)

internal fun GameState.toGameUiState(isBotFastForward: Boolean): GameUiState {
    val legal = GameEngine.legalActions(this)
    val human = players.first { !it.isBot }
    val active = activePlayer

    val reroll = when {
        GameAction.Reroll in legal -> CommandUi(enabled = true)
        phase != TurnPhase.LANCER -> CommandUi(false, BlockedReason.ROLLING_OVER)
        rollsLeft == 0 -> CommandUi(false, BlockedReason.NO_ROLLS_LEFT)
        else -> CommandUi(false, BlockedReason.ALL_DICE_KEPT)
    }
    val stopRolling =
        if (GameAction.StopRolling in legal) CommandUi(enabled = true) else CommandUi(false, BlockedReason.ROLLING_OVER)
    val openShop =
        if (phase == TurnPhase.ACHETER && !active.isBot) CommandUi(enabled = true) else CommandUi(false, BlockedReason.NOT_BUY_PHASE)
    val endTurn =
        if (GameAction.EndTurn in legal) CommandUi(enabled = true) else CommandUi(false, BlockedReason.RESOLVE_FIRST)

    return GameUiState(
        me = human.toUi(this),
        opponents = players.filter { it.isBot }.map { it.toUi(this) },
        tokyoOccupant = players.firstOrNull { it.id == tokyoOccupantId }?.toUi(this),
        dice = dice.map { die ->
            DieUi(die.id, die.face, die.isKept, canToggle = GameAction.ToggleKeep(die.id) in legal)
        },
        rollsLeft = rollsLeft,
        isResolving = phase == TurnPhase.RESOUDRE,
        symbolsToResolve = unresolvedSymbols.toList(),
        reroll = reroll,
        stopRolling = stopRolling,
        openShop = openShop,
        endTurn = endTurn,
        lastLog = actionLog.lastOrNull().orEmpty(),
        botTurn = if (active.isBot && phase != TurnPhase.DECISION_FUITE) {
            BotTurnUi(active.monster.displayName, actionLog.lastOrNull().orEmpty())
        } else null,
        flee = pendingFlee
            ?.takeIf { it.occupantId == human.id }
            ?.let { pending ->
                FleeUi(
                    attackerName = players.first { it.id == pending.attackerId }.monster.displayName,
                    damageTaken = pending.damageTaken,
                    remainingHp = human.hp,
                )
            },
        malicePick = pendingMaliceLevels.firstOrNull()
            ?.takeIf { !active.isBot }
            ?.let { level ->
                MalicePickUi(
                    level = level,
                    tiles = availableTiles.filter { it.niveau == level }.map { TileUi(it.id, it.nom, it.texte) },
                )
            },
        isBotFastForward = isBotFastForward,
    )
}

internal fun GameState.toShopUiState(): ShopUiState {
    val legal = GameEngine.legalActions(this)
    val buyer = activePlayer
    return ShopUiState(
        energy = buyer.energy,
        cards = shop.map { card ->
            ShopCardUi(
                id = card.id,
                name = card.nom,
                type = card.type,
                text = card.texte,
                cost = EffectInterpreter.computeCardCost(this, buyer.id, card),
                canBuy = GameAction.BuyCard(card.id) in legal,
            )
        },
        canSweep = GameAction.SweepShop in legal,
    )
}

internal fun GameState.toGameOverUiState() = GameOverUiState(
    winnerName = players.firstOrNull { it.id == winnerId }?.monster?.displayName,
    seed = rng.seed,
    ranking = players
        .sortedWith(compareByDescending<PlayerState> { it.isAlive }.thenByDescending { it.vp }.thenByDescending { it.hp })
        .map { it.toUi(this) },
)
