package com.neonkaiju.kot.feature.game

import com.neonkaiju.kot.model.CardType
import com.neonkaiju.kot.model.DieFace
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.ResolutionGains
import com.neonkaiju.kot.model.SymbolResolutionType
import com.neonkaiju.kot.model.TurnPhase

/** États d'écran construits à la main pour les captures Paparazzi. */
internal object PreviewData {
    private val human = PlayerUi(0, MonsterId.VOLTRAX, hp = 7, vp = 12, energy = 5, malice = 3, isAlive = true, isInTokyo = true, isActive = true,
        gains = ResolutionGains(vp = 3, energy = 2, isComplete = true, numbersVp = 2, tokyoVp = 1))
    private val bots = listOf(
        PlayerUi(1, MonsterId.KILOWATT, hp = 10, vp = 9, energy = 2, malice = 1, isAlive = true, isInTokyo = false, isActive = false),
        PlayerUi(2, MonsterId.HYDROKOR, hp = 4, vp = 15, energy = 0, malice = 6, isAlive = true, isInTokyo = false, isActive = false,
            gains = ResolutionGains(vp = 1, healing = 1)),
        PlayerUi(3, MonsterId.SPECTRA, hp = 0, vp = 6, energy = 0, malice = 0, isAlive = false, isInTokyo = false, isActive = false),
    )

    val home = HomeUiState(canResume = true, version = "1.0.0")
    val picker = MonsterPickerUiState(selected = MonsterId.VOLTRAX)

    val game = GameUiState(
        me = human,
        opponents = bots,
        tokyoOccupant = human,
        activePlayerName = human.name,
        phase = TurnPhase.LANCER,
        isChoosingPower = false,
        dice = listOf(
            DieUi(0, DieFace.ONE, isKept = true, canToggle = true),
            DieUi(1, DieFace.ONE, isKept = true, canToggle = true),
            DieUi(2, DieFace.CLAW, isKept = false, canToggle = true),
            DieUi(3, DieFace.HEART, isKept = false, canToggle = true),
            DieUi(4, DieFace.ENERGY, isKept = false, canToggle = true),
            DieUi(5, DieFace.THREE, isKept = false, canToggle = true),
        ),
        rollsMade = 2,
        maxRolls = 3,
        rollsLeft = 1,
        resolutionOrder = SymbolResolutionType.entries,
        canChangeOrder = true,
        reroll = CommandUi(enabled = true),
        stopRolling = CommandUi(enabled = true),
        openShop = CommandUi(false, BlockedReason.NOT_BUY_PHASE),
        endTurn = CommandUi(false, BlockedReason.RESOLVE_FIRST),
        summary = listOf("Voltrax gagne +2 ★", "Voltrax gagne +2 ⚡"),
        botTurn = null,
        flee = null,
        malicePick = null,
        isBotFastForward = false,
    )

    val botTurn = game.copy(
        activePlayerName = bots[0].name,
        me = human.copy(isActive = false),
        opponents = listOf(bots[0].copy(isActive = true), bots[1], bots[2]),
        dice = game.dice.map { it.copy(canToggle = false) },
        canChangeOrder = false,
        reroll = CommandUi(false, BlockedReason.BOT_PLAYING),
        stopRolling = CommandUi(false, BlockedReason.BOT_PLAYING),
        botTurn = BotTurnUi(bots[0].name, "Kilowatt conserve une griffe pour attaquer l'occupant de la Tour."),
    )

    val flee = game.copy(
        phase = TurnPhase.DECISION_FUITE,
        flee = FleeUi(attackerName = bots[0].name, damageTaken = 3, remainingHp = human.hp),
    )

    val malicePick = game.copy(
        isChoosingPower = true,
        malicePick = MalicePickUi(
            level = 6,
            tiles = listOf(
                TileUi("arc", "Arc électrique", "Gagnez 1 ★ au début de votre tour."),
                TileUi("bouclier", "Bouclier déflecteur", "Annule 1 dégât à chaque attaque subie."),
                TileUi("reacteur", "Réacteur à fusion", "Gagnez 2 Cellules au début de votre tour."),
            ),
            ownedTiles = listOf(TileUi("court", "Court-circuit", "Gagnez 1 Cellule au début de chaque tour.")),
        ),
    )

    val shop = ShopUiState(
        buyerId = human.id,
        isBotTurn = false,
        energy = human.energy,
        cards = listOf(
            ShopCardUi("piratage", "Piratage neural", CardType.POUVOIR, "Après résolution, gagnez 1 ★ si vous êtes dans la Tour centrale.", cost = 4, baseCost = 4, canBuy = true),
            ShopCardUi("frappe", "Frappe orbitale", CardType.ACTION, "Gagnez 2 ★ et infligez 3 dégâts à l'occupant de la Tour.", cost = 6, baseCost = 6, canBuy = false),
            ShopCardUi("bombe", "Bombe IEM", CardType.ACTION, "Infligez 2 dégâts à tous les autres monstres et gagnez 1 ★.", cost = 3, baseCost = 4, canBuy = true),
        ),
        canSweep = true,
        revision = 12,
        lastReceipt = "Cœur nucléaire : 8 − 3 = 5 Cellules",
    )

    val gameOver = GameOverUiState(
        winnerName = human.name,
        endReason = GameEndReason.TWENTY_STARS,
        humanId = human.id,
        seed = 42L,
        ranking = listOf(human.copy(vp = 20), bots[1], bots[0], bots[2]),
    )

    val settings = SettingsUiState(fastBots = true, fleeCountdown = false)
}
