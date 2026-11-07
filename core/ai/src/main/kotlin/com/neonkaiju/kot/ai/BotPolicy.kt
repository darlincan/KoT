package com.neonkaiju.kot.ai

import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.*

interface BotPolicy {
    fun chooseAction(state: GameState): GameAction
}

/** Griffes qu'un adversaire inflige en moyenne en un tour, sur les 6 qu'il peut obtenir au maximum. */
private const val EXPECTED_CLAWS_NUMERATOR = 3
private const val EXPECTED_CLAWS_DENOMINATOR = 2

/** One policy for all bots; priorities depend on health, opponents, and the current dice. */
class HeuristicBotPolicy : BotPolicy {
    override fun chooseAction(state: GameState): GameAction {
        val legal = GameEngine.legalActions(state)
        require(legal.isNotEmpty()) { "Aucune action légale disponible pour le bot" }
        val bot = state.decisionPlayer
        val tilePicks = legal.filterIsInstance<GameAction.PickMaliceTile>()
        if (tilePicks.isNotEmpty()) return bestByEvaluation(state, tilePicks)

        return when (state.phase) {
            TurnPhase.DECISION_FUITE -> chooseFleeOrStay(state, legal)
            TurnPhase.LANCER -> chooseRollAction(state, bot, legal)
            TurnPhase.RESOUDRE -> legal.filterIsInstance<GameAction.ResolveSymbols>()
                .minByOrNull { state.resolutionOrder.indexOf(it.symbolType) } ?: legal.first()
            TurnPhase.ACHETER -> bestByEvaluation(state, legal.filterIsInstance<GameAction.BuyCard>() + GameAction.EndTurn)
            else -> legal.first()
        }.also { require(it in legal) }
    }

    /** Explanations describe the same action that was selected, without consuming randomness. */
    fun explainAction(state: GameState, action: GameAction): String {
        val name = state.decisionPlayer.monster.displayName
        val reason = when (action) {
            is GameAction.ToggleKeep -> {
                val die = state.dice.first { it.id == action.dieId }
                if (die.isKept) "libère le dé ${die.id + 1} pour chercher un résultat plus utile"
                else when (die.face) {
                    DieFace.HEART -> "conserve un cœur pour récupérer des PV hors de la Tour"
                    DieFace.ENERGY -> "conserve un éclair pour financer un achat"
                    DieFace.CLAW -> if (state.tokyoOccupantId == state.activePlayer.id)
                        "conserve une griffe pour attaquer les monstres hors de la Tour"
                        else "conserve une griffe pour attaquer l’occupant de la Tour"
                    else -> "conserve un chiffre pour compléter ou renforcer un brelan"
                }
            }
            GameAction.Reroll -> "relance les ${state.dice.count { !it.isKept }} dés non conservés pour améliorer son résultat"
            GameAction.StopRolling -> "termine ses lancers et résout les dés conservés"
            GameAction.Flee -> "fuit la Tour pour limiter le risque d’élimination"
            GameAction.Stay -> "reste dans la Tour pour défendre sa position et gagner des points au prochain tour"
            is GameAction.BuyCard -> "achète ${state.shop.first { it.id == action.cardId }.nom}, évaluée utile selon ses ressources et ses effets"
            is GameAction.PickMaliceTile -> "choisit ${state.availableTiles.first { it.id == action.tileId }.nom} selon sa santé et ses points"
            GameAction.EndTurn -> "termine son tour : aucun achat supplémentaire n’est jugé assez utile"
            is GameAction.ResolveSymbols -> "résout automatiquement ses résultats"
            GameAction.SweepShop -> "renouvelle les cartes du marché"
            is GameAction.SetResolutionOrder -> "prépare l’ordre de résolution de ses résultats"
        }
        return "$name $reason."
    }

    /**
     * Simule chaque candidate jusqu'à la fin du tour et garde la mieux notée. Passer par la fin du tour
     * compare un achat et [GameAction.EndTurn] à égalité, avec les mêmes effets de fin de tour.
     */
    private fun bestByEvaluation(state: GameState, candidates: List<GameAction>): GameAction {
        val botId = state.activePlayer.id
        return candidates.maxBy { action ->
            evaluate(playOutTurn(GameEngine.reduce(state, action)), botId)
        }
    }

    private fun playOutTurn(state: GameState): GameState =
        if (GameAction.EndTurn in GameEngine.legalActions(state)) GameEngine.reduce(state, GameAction.EndTurn) else state

    /**
     * Compare deux projections du prochain tour plutôt que [GameEngine.reduce], qui lancerait les dés du
     * joueur suivant. Le bot concerné est l'occupant, pas le joueur actif.
     */
    private fun chooseFleeOrStay(state: GameState, legal: List<GameAction>): GameAction {
        val decision = state.pendingFlee ?: return legal.first()
        val botId = decision.occupantId
        val opponents = state.players.count { it.id != botId && it.isAlive }
        val expectedClaws = opponents * EXPECTED_CLAWS_NUMERATOR / EXPECTED_CLAWS_DENOMINATOR

        val ifStays = state.projected(botId, damage = expectedClaws, stars = 2, occupantId = botId)
        val ifFlees = state.projected(botId, damage = 1, stars = 0, occupantId = decision.attackerId)

        return if (evaluate(ifStays, botId) > evaluate(ifFlees, botId)) GameAction.Stay else GameAction.Flee
    }

    private fun GameState.projected(playerId: Int, damage: Int, stars: Int, occupantId: Int): GameState = copy(
        tokyoOccupantId = occupantId,
        players = players.map { player ->
            if (player.id != playerId) player
            else player.copy(hp = (player.hp - damage).coerceAtLeast(0), vp = (player.vp + stars).coerceAtMost(20))
        },
    )

    private fun chooseRollAction(state: GameState, bot: PlayerState, legal: List<GameAction>): GameAction {
        val inTokyo = state.tokyoOccupantId == bot.id
        val counts = state.dice.groupingBy { it.face }.eachCount()
        val number = listOf(DieFace.ONE, DieFace.TWO, DieFace.THREE).maxBy { face ->
            (counts[face] ?: 0) * 10 + when (face) {
                DieFace.ONE -> if (bot.malice in listOf(1, 2, 4, 5, 8, 9)) 4 else 0
                DieFace.TWO -> if (bot.malice in listOf(2, 5, 9)) 3 else 1
                else -> 2
            }
        }
        val healingNeeded = if (inTokyo) 0 else bot.maxHp - bot.hp
        val canAttack = inTokyo || state.tokyoOccupantId != null
        val desiredEnergy = (state.shop.minOfOrNull { it.cout } ?: 3) - bot.energy
        val priority = ((state.rng.seed xor bot.id.toLong() xor state.rng.cursor.toLong()) and 1L).toInt()
        val desired = state.dice.filter { die ->
            when (die.face) {
                DieFace.HEART -> healingNeeded >= 3 && state.dice.filter { it.face == DieFace.HEART }.indexOf(die) < healingNeeded
                DieFace.CLAW -> canAttack && (inTokyo || bot.vp < 16 || (counts[DieFace.CLAW] ?: 0) >= 2)
                DieFace.ENERGY -> desiredEnergy > 0 && (priority == 0 || (counts[number] ?: 0) < 2) && healingNeeded < 6
                else -> die.face == number && (counts[number] ?: 0) >= 2 && healingNeeded < 6
            }
        }.map { it.id }.toSet()

        state.dice.firstOrNull { it.isKept != (it.id in desired) }?.let {
            val toggle = GameAction.ToggleKeep(it.id)
            if (toggle in legal) return toggle
        }
        return if (GameAction.Reroll in legal) GameAction.Reroll else GameAction.StopRolling
    }
}
