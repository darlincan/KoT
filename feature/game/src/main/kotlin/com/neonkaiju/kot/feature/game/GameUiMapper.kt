package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.content.EffectInterpreter
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MaliceTileDef
import com.neonkaiju.kot.model.PlayerState
import com.neonkaiju.kot.model.TurnPhase

private fun MaliceTileDef.toUi() = TileUi(id, nom, texte)

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
    gains = state.lastResolutionGains[id],
)

/** Pendant la décision d'un bot, le joueur humain ne dispose d'aucune action. */
private fun GameState.humanLegalActions(): List<GameAction> =
    if (decisionPlayer.isBot) emptyList() else GameEngine.legalActions(this)

/** La décision n'est affichée que si l'occupant de la Tour est le joueur humain. */
internal fun GameState.toFleeUi(): FleeUi? {
    val human = players.first { !it.isBot }
    return pendingFlee
        ?.takeIf { it.occupantId == human.id }
        ?.let { pending ->
            FleeUi(
                attackerName = players.first { it.id == pending.attackerId }.monster.displayName,
                damageTaken = pending.damageTaken,
                remainingHp = human.hp,
            )
        }
}

internal fun GameState.toMalicePickUi(): MalicePickUi? {
    val human = players.first { !it.isBot }
    return pendingMaliceLevels.firstOrNull()
        ?.takeIf { !activePlayer.isBot }
        ?.let { level ->
            MalicePickUi(
                level = level,
                tiles = availableTiles.filter { it.niveau == level }.map { it.toUi() },
                ownedTiles = human.tiles.map { it.toUi() },
            )
        }
}

private fun GameState.toBotTurnUi(explanation: String?): BotTurnUi? {
    val decision = decisionPlayer
    if (!decision.isBot || phase == TurnPhase.FIN_PARTIE) return null
    val result = lastResolutionGains[activePlayer.id]
    val text = if (phase == TurnPhase.ACHETER && result != null) {
        "Résultat : +${result.vp} ★, +${result.energy} ⚡, +${result.healing} ♥, +${result.malice} Surcharge.\n${explanation.orEmpty()}"
    } else {
        explanation.orEmpty()
    }
    return BotTurnUi(decision.monster.displayName, text)
}

internal fun GameState.toHomeUiState(version: String) = HomeUiState(
    canResume = phase != TurnPhase.FIN_PARTIE,
    version = version,
)

internal fun GameState.toGameUiState(isBotFastForward: Boolean, botExplanation: String? = null): GameUiState {
    val legal = humanLegalActions()
    val human = players.first { !it.isBot }
    val botDecides = decisionPlayer.isBot

    val reroll = when {
        GameAction.Reroll in legal -> CommandUi(enabled = true)
        botDecides -> CommandUi(false, BlockedReason.BOT_PLAYING)
        phase != TurnPhase.LANCER -> CommandUi(false, BlockedReason.ROLLING_OVER)
        rollsLeft == 0 -> CommandUi(false, BlockedReason.NO_ROLLS_LEFT)
        else -> CommandUi(false, BlockedReason.ALL_DICE_KEPT)
    }
    val stopRolling = when {
        GameAction.StopRolling in legal -> CommandUi(enabled = true)
        botDecides -> CommandUi(false, BlockedReason.BOT_PLAYING)
        else -> CommandUi(false, BlockedReason.ROLLING_OVER)
    }
    val openShop = when {
        botDecides -> CommandUi(false, BlockedReason.BOT_PLAYING)
        phase == TurnPhase.ACHETER -> CommandUi(enabled = true)
        else -> CommandUi(false, BlockedReason.NOT_BUY_PHASE)
    }
    val endTurn = when {
        GameAction.EndTurn in legal -> CommandUi(enabled = true)
        botDecides -> CommandUi(false, BlockedReason.BOT_PLAYING)
        else -> CommandUi(false, BlockedReason.RESOLVE_FIRST)
    }

    return GameUiState(
        me = human.toUi(this),
        opponents = players.filter { it.isBot }.map { it.toUi(this) },
        tokyoOccupant = players.firstOrNull { it.id == tokyoOccupantId }?.toUi(this),
        activePlayerName = activePlayer.monster.displayName,
        phase = phase,
        isChoosingPower = pendingMaliceLevels.isNotEmpty(),
        dice = dice.map { die ->
            DieUi(die.id, die.face, die.isKept, canToggle = GameAction.ToggleKeep(die.id) in legal)
        },
        rollsMade = rollsMade,
        maxRolls = maxRolls,
        rollsLeft = rollsLeft,
        resolutionOrder = resolutionOrder,
        canChangeOrder = phase == TurnPhase.LANCER && !botDecides && pendingMaliceLevels.isEmpty(),
        reroll = reroll,
        stopRolling = stopRolling,
        openShop = openShop,
        endTurn = endTurn,
        summary = resolutionSummary,
        botTurn = toBotTurnUi(botExplanation),
        flee = toFleeUi(),
        malicePick = toMalicePickUi(),
        isBotFastForward = isBotFastForward,
    )
}

internal fun GameState.toShopUiState(): ShopUiState {
    val legal = humanLegalActions()
    val buyer = activePlayer
    return ShopUiState(
        buyerId = buyer.id,
        isBotTurn = decisionPlayer.isBot,
        energy = buyer.energy,
        cards = shop.map { card ->
            ShopCardUi(
                id = card.id,
                name = card.nom,
                type = card.type,
                text = card.texte,
                cost = EffectInterpreter.computeCardCost(this, buyer.id, card),
                baseCost = card.cout,
                canBuy = GameAction.BuyCard(card.id) in legal,
            )
        },
        canSweep = GameAction.SweepShop in legal,
        revision = actionHistory.size,
        lastReceipt = lastMarketReceipts[buyer.id]?.let { receipt ->
            val effect = when {
                receipt.effectEnergyChange > 0 -> " + ${receipt.effectEnergyChange} (effet)"
                receipt.effectEnergyChange < 0 -> " − ${-receipt.effectEnergyChange} (effet)"
                else -> ""
            }
            "${receipt.itemName} : ${receipt.energyBefore} − ${receipt.paid}$effect = ${receipt.energyAfter} Cellules"
        },
    )
}

internal fun GameState.toGameOverUiState() = GameOverUiState(
    winnerName = players.firstOrNull { it.id == winnerId }?.monster?.displayName,
    // Même priorité que le moteur : le dernier survivant l'emporte avant le test des 20 ★.
    endReason = when {
        winnerId == null -> GameEndReason.ALL_ELIMINATED
        players.count { it.isAlive } == 1 -> GameEndReason.LAST_SURVIVOR
        else -> GameEndReason.TWENTY_STARS
    },
    humanId = players.first { !it.isBot }.id,
    seed = rng.seed,
    ranking = players
        .sortedWith(compareByDescending<PlayerState> { it.isAlive }.thenByDescending { it.vp }.thenByDescending { it.hp })
        .map { it.toUi(this) },
)
