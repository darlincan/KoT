package com.neonkaiju.kot.engine

import com.neonkaiju.kot.content.CatalogData
import com.neonkaiju.kot.content.EffectInterpreter
import com.neonkaiju.kot.model.*

class IllegalActionException(message: String) : IllegalArgumentException(message)

object GameEngine {

    fun createGame(
        seed: Long,
        humanMonster: MonsterId,
        catalog: CatalogData
    ): GameState {
        var rng = DeterministicRng(seed)
        val remainingMonsters = MonsterId.entries.filter { it != humanMonster }
        val (shuffledMonsters, rngAfterMonsters) = rng.shuffle(remainingMonsters)
        rng = rngAfterMonsters

        val players = listOf(
            PlayerState(id = 0, monster = humanMonster, isBot = false),
            PlayerState(id = 1, monster = shuffledMonsters[0], isBot = true),
            PlayerState(id = 2, monster = shuffledMonsters[1], isBot = true),
            PlayerState(id = 3, monster = shuffledMonsters[2], isBot = true)
        )

        val (shuffledDeck, rngAfterDeck) = rng.shuffle(catalog.cards)
        rng = rngAfterDeck

        val shop = shuffledDeck.take(3)
        val deck = shuffledDeck.drop(3)

        val initialDice = mutableListOf<Die>()
        for (i in 0 until 6) {
            val (face, nextRng) = rng.nextDieFace()
            rng = nextRng
            initialDice.add(Die(id = i, face = face, isKept = false))
        }

        val initialState = GameState(
            rng = rng,
            players = players,
            activePlayerIndex = 0,
            tokyoOccupantId = null,
            phase = TurnPhase.LANCER,
            rollsLeft = 2,
            dice = initialDice,
            deck = deck,
            shop = shop,
            availableTiles = catalog.tiles,
            actionLog = listOf("Partie initialisée (Graine: $seed)")
        )

        return EffectInterpreter.applyTrigger(initialState, EffectTrigger.AVANT_LANCER, 0)
    }

    fun legalActions(state: GameState): List<GameAction> {
        if (state.phase == TurnPhase.FIN_PARTIE) return emptyList()

        if (state.pendingMaliceLevels.isNotEmpty()) {
            val level = state.pendingMaliceLevels.first()
            val tilesForLevel = state.availableTiles.filter { it.niveau == level }
            if (tilesForLevel.isNotEmpty()) {
                return tilesForLevel.map { GameAction.PickMaliceTile(it.id) }
            }
        }

        return when (state.phase) {
            TurnPhase.LANCER -> {
                val actions = mutableListOf<GameAction>()
                if (state.rollsLeft > 0) {
                    state.dice.forEach { actions.add(GameAction.ToggleKeep(it.id)) }
                    if (state.dice.any { !it.isKept }) {
                        actions.add(GameAction.Reroll)
                    }
                }
                actions.add(GameAction.StopRolling)
                actions
            }
            TurnPhase.RESOUDRE -> {
                state.unresolvedSymbols.map { GameAction.ResolveSymbols(it) }
            }
            TurnPhase.DECISION_FUITE -> {
                listOf(GameAction.Flee, GameAction.Stay)
            }
            TurnPhase.ACHETER -> {
                val actions = mutableListOf<GameAction>()
                val active = state.activePlayer
                state.shop.forEach { card ->
                    val cost = EffectInterpreter.computeCardCost(state, active.id, card)
                    if (active.energy >= cost) {
                        actions.add(GameAction.BuyCard(card.id))
                    }
                }
                if (active.energy >= 2 && state.deck.size + state.discard.size >= 3) {
                    actions.add(GameAction.SweepShop)
                }
                actions.add(GameAction.EndTurn)
                actions
            }
            TurnPhase.ENTRER, TurnPhase.FIN_TOUR, TurnPhase.FIN_PARTIE -> emptyList()
        }
    }

    fun reduce(state: GameState, action: GameAction): GameState {
        val allowed = legalActions(state)
        if (action !in allowed) {
            throw IllegalActionException("Action $action illégale dans la phase ${state.phase}. Actions permises : $allowed")
        }

        val next = when (action) {
            is GameAction.PickMaliceTile -> handlePickMaliceTile(state, action.tileId)
            is GameAction.ToggleKeep -> {
                val newDice = state.dice.map {
                    if (it.id == action.dieId) it.copy(isKept = !it.isKept) else it
                }
                state.copy(dice = newDice)
            }
            is GameAction.Reroll -> {
                var rng = state.rng
                val newDice = state.dice.map { die ->
                    if (die.isKept) die
                    else {
                        val (newFace, nextRng) = rng.nextDieFace()
                        rng = nextRng
                        die.copy(face = newFace)
                    }
                }
                val newRollsLeft = state.rollsLeft - 1
                val updated = state.copy(
                    rng = rng,
                    dice = newDice,
                    rollsLeft = newRollsLeft,
                    actionLog = state.actionLog + "${state.activePlayer.monster.displayName} relance (${2 - newRollsLeft}/2)"
                )
                if (newRollsLeft == 0) transitionToResolve(updated) else updated
            }
            is GameAction.StopRolling -> transitionToResolve(state)
            is GameAction.ResolveSymbols -> handleResolveSymbol(state, action.symbolType)
            is GameAction.Flee -> handleFleeDecision(state, flee = true)
            is GameAction.Stay -> handleFleeDecision(state, flee = false)
            is GameAction.BuyCard -> handleBuyCard(state, action.cardId)
            is GameAction.SweepShop -> handleSweepShop(state)
            is GameAction.EndTurn -> handleEndTurn(state)
        }
        return next.copy(history = state.history + action)
    }

    fun journalOf(state: GameState): GameJournal = GameJournal(
        seed = state.rng.seed,
        humanMonster = state.players.first { !it.isBot }.monster,
        actions = state.history
    )

    /**
     * Rejoue une partie depuis sa graine. Chaque action repasse par [reduce] :
     * un journal falsifié ou issu d'une autre version des règles lève [IllegalActionException].
     */
    fun replay(journal: GameJournal, catalog: CatalogData): GameState =
        journal.actions.fold(createGame(journal.seed, journal.humanMonster, catalog), ::reduce)

    private fun transitionToResolve(rolledState: GameState): GameState {
        val state = EffectInterpreter.applyFaceChanges(rolledState, rolledState.activePlayer.id)
        val presentTypes = mutableSetOf<SymbolResolutionType>()
        val counts = state.dice.groupingBy { it.face }.eachCount()
        if ((counts[DieFace.ONE] ?: 0) >= 3 || (counts[DieFace.TWO] ?: 0) >= 3 || (counts[DieFace.THREE] ?: 0) >= 3) {
            presentTypes.add(SymbolResolutionType.NUMBERS)
        }
        if ((counts[DieFace.ENERGY] ?: 0) > 0) presentTypes.add(SymbolResolutionType.ENERGY)
        if ((counts[DieFace.CLAW] ?: 0) > 0) presentTypes.add(SymbolResolutionType.CLAWS)
        if ((counts[DieFace.HEART] ?: 0) > 0) presentTypes.add(SymbolResolutionType.HEARTS)

        val resolvedState = state.copy(
            phase = TurnPhase.RESOUDRE,
            unresolvedSymbols = presentTypes
        )
        return if (presentTypes.isEmpty()) advanceAfterResolution(resolvedState) else resolvedState
    }

    private fun handleResolveSymbol(state: GameState, symbolType: SymbolResolutionType): GameState {
        val active = state.activePlayer
        val counts = state.dice.groupingBy { it.face }.eachCount()
        var current = state.copy(unresolvedSymbols = state.unresolvedSymbols - symbolType)

        when (symbolType) {
            SymbolResolutionType.NUMBERS -> {
                var vpGain = 0
                var maliceGain = 0
                listOf(DieFace.ONE to 1, DieFace.TWO to 2, DieFace.THREE to 3).forEach { (face, baseVp) ->
                    val count = counts[face] ?: 0
                    if (count >= 3) {
                        vpGain += baseVp + (count - 3)
                        if (face == DieFace.ONE) maliceGain += 2
                        if (face == DieFace.TWO) maliceGain += 1
                    }
                }
                val oldMalice = active.malice
                val newMalice = (oldMalice + maliceGain).coerceAtMost(10)
                val newlyCrossed = listOf(3, 6, 10).filter { level ->
                    oldMalice < level && newMalice >= level && level !in active.unlockedMaliceLevels &&
                        current.availableTiles.any { it.niveau == level }
                }
                current = updatePlayer(current, active.id) {
                    it.copy(
                        vp = (it.vp + vpGain).coerceAtMost(20),
                        malice = newMalice
                    )
                }.copy(
                    pendingMaliceLevels = current.pendingMaliceLevels + newlyCrossed,
                    actionLog = current.actionLog + "${active.monster.displayName} gagne +$vpGain ★ et +$maliceGain Surcharge"
                )
            }
            SymbolResolutionType.ENERGY -> {
                val eGain = counts[DieFace.ENERGY] ?: 0
                current = updatePlayer(current, active.id) {
                    it.copy(energy = it.energy + eGain)
                }.copy(actionLog = current.actionLog + "${active.monster.displayName} gagne +$eGain ⚡")
            }
            SymbolResolutionType.HEARTS -> {
                val hCount = counts[DieFace.HEART] ?: 0
                if (current.tokyoOccupantId != active.id) {
                    current = updatePlayer(current, active.id) {
                        it.copy(hp = (it.hp + hCount).coerceAtMost(it.maxHp))
                    }.copy(actionLog = current.actionLog + "${active.monster.displayName} récupère +$hCount ♥")
                } else {
                    current = current.copy(actionLog = current.actionLog + "${active.monster.displayName} est dans la Tour : les ♥ des dés ne soignent pas")
                }
            }
            SymbolResolutionType.CLAWS -> {
                val claws = counts[DieFace.CLAW] ?: 0
                if (claws > 0) {
                    if (current.tokyoOccupantId == active.id) {
                        val targets = current.players.filter { it.isAlive && it.id != active.id }.map { it.id }
                        val wounded = mutableSetOf<Int>()
                        for (tid in targets) {
                            val reduction = EffectInterpreter.computeDamageReduction(current, tid)
                            val dmg = (claws - reduction).coerceAtLeast(0)
                            if (dmg > 0) {
                                current = EffectInterpreter.applyDirectDamage(current, tid, dmg)
                                wounded += tid
                            }
                        }
                        if (wounded.isNotEmpty()) {
                            current = EffectInterpreter.applyTrigger(
                                current, EffectTrigger.APRES_DEGAT_INFLIGE, active.id, restrictTo = wounded
                            )
                        }
                        current = current.copy(actionLog = current.actionLog + "${active.monster.displayName} frappe toute la ville ($claws 🐾)")
                    } else {
                        val occupantId = current.tokyoOccupantId
                        if (occupantId != null) {
                            val reduction = EffectInterpreter.computeDamageReduction(current, occupantId)
                            val dmg = (claws - reduction).coerceAtLeast(0)
                            if (dmg > 0) {
                                current = EffectInterpreter.applyDirectDamage(current, occupantId, dmg)
                                current = EffectInterpreter.applyTrigger(
                                    current, EffectTrigger.APRES_DEGAT_INFLIGE, active.id, restrictTo = setOf(occupantId)
                                )
                                val occupantAfter = current.players.first { it.id == occupantId }
                                if (occupantAfter.isAlive) {
                                    return current.copy(
                                        phase = TurnPhase.DECISION_FUITE,
                                        pendingFlee = PendingFleeDecision(
                                            attackerId = active.id,
                                            occupantId = occupantId,
                                            damageTaken = dmg
                                        ),
                                        actionLog = current.actionLog + "${active.monster.displayName} inflige $dmg dégât(s) à ${occupantAfter.monster.displayName}"
                                    )
                                } else {
                                    current = current.copy(
                                        actionLog = current.actionLog + "${active.monster.displayName} inflige $dmg dégât(s) à ${occupantAfter.monster.displayName} et l'élimine !"
                                    )
                                }
                            }
                        } else {
                            current = current.copy(actionLog = current.actionLog + "La Tour est vide : les $claws 🐾 ne touchent personne")
                        }
                    }
                }
            }
        }

        val alive = current.players.filter { it.isAlive }
        if (alive.size <= 1) {
            return current.copy(
                phase = TurnPhase.FIN_PARTIE,
                winnerId = alive.firstOrNull()?.id
            )
        }

        return if (current.unresolvedSymbols.isEmpty() && current.pendingMaliceLevels.isEmpty()) {
            advanceAfterResolution(current)
        } else current
    }

    private fun handlePickMaliceTile(state: GameState, tileId: String): GameState {
        val level = state.pendingMaliceLevels.first()
        val tile = state.availableTiles.firstOrNull { it.id == tileId && it.niveau == level }
            ?: throw IllegalActionException("Tuile invalide : $tileId")

        val active = state.activePlayer
        val updated = updatePlayer(state, active.id) {
            it.copy(
                tiles = it.tiles + tile,
                unlockedMaliceLevels = it.unlockedMaliceLevels + level
            )
        }.copy(
            availableTiles = state.availableTiles.filter { it.id != tileId },
            pendingMaliceLevels = state.pendingMaliceLevels.drop(1),
            actionLog = state.actionLog + "${active.monster.displayName} débloque le pouvoir de Surcharge : ${tile.nom}"
        )

        return if (updated.phase == TurnPhase.RESOUDRE && updated.unresolvedSymbols.isEmpty() && updated.pendingMaliceLevels.isEmpty()) {
            advanceAfterResolution(updated)
        } else updated
    }

    private fun handleFleeDecision(state: GameState, flee: Boolean): GameState {
        val decision = state.pendingFlee ?: throw IllegalActionException("Aucune décision de fuite en attente")
        val occupant = state.players.first { it.id == decision.occupantId }
        val updated = if (flee) {
            state.copy(
                tokyoOccupantId = null,
                pendingFlee = null,
                phase = TurnPhase.RESOUDRE,
                actionLog = state.actionLog + "${occupant.monster.displayName} fuit la Tour centrale !"
            )
        } else {
            state.copy(
                pendingFlee = null,
                phase = TurnPhase.RESOUDRE,
                actionLog = state.actionLog + "${occupant.monster.displayName} reste dans la Tour centrale !"
            )
        }

        return if (updated.unresolvedSymbols.isEmpty() && updated.pendingMaliceLevels.isEmpty()) {
            advanceAfterResolution(updated)
        } else updated
    }

    private fun advanceAfterResolution(state: GameState): GameState {
        val activeId = state.activePlayer.id
        var current = EffectInterpreter.applyTrigger(state, EffectTrigger.APRES_RESOLUTION, activeId)

        current = current.copy(phase = TurnPhase.ENTRER)
        if (current.tokyoOccupantId == null && current.activePlayer.isAlive) {
            current = updatePlayer(current, activeId) {
                it.copy(vp = (it.vp + 1).coerceAtMost(20))
            }.copy(
                tokyoOccupantId = activeId,
                actionLog = current.actionLog + "${current.activePlayer.monster.displayName} entre dans la Tour (+1 ★)"
            )
            current = EffectInterpreter.applyTrigger(current, EffectTrigger.ENTREE_TOKYO, activeId)
        }

        return checkImmediateElimination(current.copy(phase = TurnPhase.ACHETER))
    }

    private fun handleBuyCard(state: GameState, cardId: String): GameState {
        val active = state.activePlayer
        val card = state.shop.first { it.id == cardId }
        val cost = EffectInterpreter.computeCardCost(state, active.id, card)

        var deck = state.deck
        var discard = state.discard
        var rng = state.rng
        if (deck.isEmpty() && discard.isNotEmpty()) {
            val (reshuffled, nextRng) = rng.shuffle(discard)
            deck = reshuffled
            discard = emptyList()
            rng = nextRng
        }
        val replacement = deck.firstOrNull()
        val remainingDeck = if (replacement != null) deck.drop(1) else deck
        val newShop = state.shop.filter { it.id != cardId } + listOfNotNull(replacement)

        var current = updatePlayer(state, active.id) {
            it.copy(
                energy = it.energy - cost,
                cards = if (card.type == CardType.POUVOIR) it.cards + card else it.cards
            )
        }.copy(
            rng = rng,
            shop = newShop,
            deck = remainingDeck,
            discard = if (card.type == CardType.ACTION) discard + card else discard,
            actionLog = state.actionLog + "${active.monster.displayName} achète ${card.nom} (-$cost ⚡)"
        )

        if (card.type == CardType.ACTION) {
            current = EffectInterpreter.applyTrigger(current, EffectTrigger.ACHAT, active.id, card.effets)
        }

        return checkImmediateElimination(current)
    }

    private fun handleSweepShop(state: GameState): GameState {
        val active = state.activePlayer
        var deck = state.deck
        var discard = state.discard + state.shop
        var rng = state.rng

        if (deck.size < 3) {
            val (reshuffled, nextRng) = rng.shuffle(discard)
            deck = deck + reshuffled
            discard = emptyList()
            rng = nextRng
        }

        val newShop = deck.take(3)
        val remainingDeck = deck.drop(3)

        return updatePlayer(state, active.id) {
            it.copy(energy = it.energy - 2)
        }.copy(
            rng = rng,
            shop = newShop,
            deck = remainingDeck,
            discard = discard,
            actionLog = state.actionLog + "${active.monster.displayName} renouvelle le Marché noir (-2 ⚡)"
        )
    }

    private fun handleEndTurn(state: GameState): GameState {
        val activeId = state.activePlayer.id
        var current = state.copy(phase = TurnPhase.FIN_TOUR)
        current = EffectInterpreter.applyTrigger(current, EffectTrigger.FIN_TOUR, activeId)

        val alivePlayers = current.players.filter { it.isAlive }
        if (alivePlayers.isEmpty()) {
            return current.copy(phase = TurnPhase.FIN_PARTIE, winnerId = null)
        }
        if (alivePlayers.size == 1) {
            return current.copy(phase = TurnPhase.FIN_PARTIE, winnerId = alivePlayers.first().id)
        }
        val vpWinner = alivePlayers.firstOrNull { it.vp >= 20 }
        if (vpWinner != null) {
            return current.copy(phase = TurnPhase.FIN_PARTIE, winnerId = vpWinner.id)
        }

        var nextIndex = (current.activePlayerIndex + 1) % current.players.size
        while (!current.players[nextIndex].isAlive) {
            nextIndex = (nextIndex + 1) % current.players.size
        }
        val nextPlayerId = current.players[nextIndex].id

        current = current.copy(
            activePlayerIndex = nextIndex,
            players = current.players.map { it.copy(damageTakenThisTurn = 0) }
        )

        if (current.tokyoOccupantId == nextPlayerId) {
            current = updatePlayer(current, nextPlayerId) {
                it.copy(vp = (it.vp + 2).coerceAtMost(20))
            }.copy(actionLog = current.actionLog + "${current.players[nextIndex].monster.displayName} débute dans la Tour (+2 ★)")
        }

        current = EffectInterpreter.applyTrigger(current, EffectTrigger.DEBUT_TOUR, nextPlayerId)
        current = checkImmediateElimination(current)
        if (current.phase == TurnPhase.FIN_PARTIE) return current

        var rng = current.rng
        val freshDice = (0 until 6).map { id ->
            val (face, nextRng) = rng.nextDieFace()
            rng = nextRng
            Die(id = id, face = face, isKept = false)
        }

        current = current.copy(
            rng = rng,
            phase = TurnPhase.LANCER,
            rollsLeft = 2,
            dice = freshDice
        )
        return EffectInterpreter.applyTrigger(current, EffectTrigger.AVANT_LANCER, nextPlayerId)
    }

    private fun checkImmediateElimination(state: GameState): GameState {
        val alive = state.players.filter { it.isAlive }
        return if (alive.size <= 1) {
            state.copy(phase = TurnPhase.FIN_PARTIE, winnerId = alive.firstOrNull()?.id)
        } else state
    }

    private fun updatePlayer(state: GameState, playerId: Int, block: (PlayerState) -> PlayerState): GameState {
        return state.copy(players = state.players.map { if (it.id == playerId) block(it) else it })
    }
}
