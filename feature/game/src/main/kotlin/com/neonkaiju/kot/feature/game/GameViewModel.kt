package com.neonkaiju.kot.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neonkaiju.kot.ai.BotPolicy
import com.neonkaiju.kot.ai.BotTurnPresenter
import com.neonkaiju.kot.data.CatalogRepository
import com.neonkaiju.kot.data.GameRepository
import com.neonkaiju.kot.data.SettingsRepository
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.engine.IllegalActionException
import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.MonsterId
import com.neonkaiju.kot.model.SeedProvider
import com.neonkaiju.kot.model.TurnPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GameEffect {
    data object GameStarted : GameEffect
    data object GameOver : GameEffect
}

/**
 * partagée par les écrans Choix du monstre, Jeu, Marché noir et Fin de partie.
 *
 * Toute modification passe par [GameEngine.reduce] puis est sauvegardée. Les bots jouent ici, dans
 * [viewModelScope], et seulement quand l'écran de jeu est affiché ([setBotsActive]).
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val botPolicy: BotPolicy,
    private val seedProvider: SeedProvider,
    @Named(APP_VERSION) private val appVersion: String,
) : ViewModel() {

    private val _game = MutableStateFlow<GameState?>(null)
    internal val game: StateFlow<GameState?> = _game.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val hasGame: Boolean get() = _game.value != null

    val selectedMonster: StateFlow<MonsterId> = savedStateHandle.getStateFlow(KEY_SELECTED_MONSTER, MonsterId.VOLTRAX)

    val fastBots: StateFlow<Boolean> = settingsRepository.settings
        .map { it.fastBots }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val fleeCountdown: StateFlow<Boolean> = settingsRepository.settings
        .map { it.fleeCountdown }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val botsActive = MutableStateFlow(false)
    private val botPresenter = BotTurnPresenter(botPolicy)

    private val _effects = Channel<GameEffect>(Channel.BUFFERED)
    val effects: Flow<GameEffect> = _effects.receiveAsFlow()

    val homeUiState: StateFlow<HomeUiState> = _game
        .map { it?.toHomeUiState(appVersion) ?: HomeUiState(canResume = false, version = appVersion) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState(version = appVersion))

    val pickerUiState: StateFlow<MonsterPickerUiState> = selectedMonster
        .map { MonsterPickerUiState(selected = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MonsterPickerUiState(selected = selectedMonster.value))

    /** a une valeur egale a null tant qu'aucune partie n'est start. */
    val gameUiState: StateFlow<GameUiState?> = combine(_game, fastBots, fleeCountdown) { game, fast, countdown ->
        game?.toGameUiState(fast, game.botExplanation(), countdown)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val shopUiState: StateFlow<ShopUiState?> = _game
        .map { it?.toShopUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val gameOverUiState: StateFlow<GameOverUiState?> = _game
        .map { it?.toGameOverUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            val saved = gameRepository.load()?.resumed()
            _game.compareAndSet(null, saved)
            _isLoaded.value = true
        }
        viewModelScope.launch { playBots() }
    }

    fun pickMonster(monster: MonsterId) {
        savedStateHandle[KEY_SELECTED_MONSTER] = monster
    }

    fun startGame() {
        viewModelScope.launch {
            val fresh = GameEngine.createGame(
                seed = seedProvider.newSeed(),
                humanMonster = selectedMonster.value,
                catalog = catalogRepository.catalog(),
            )
            update(fresh)
            _effects.send(GameEffect.GameStarted)
        }
    }

    /**
     * Action du joueur humain. Elle est ignorée pendant la décision d'un bot, et aussi quand
     * [expectedRevision] ne correspond plus à la partie : un double appui ne paie pas deux fois.
     */
    fun dispatch(action: GameAction, expectedRevision: Int? = null) {
        val current = _game.value ?: return
        if (current.decisionPlayer.isBot) return
        if (expectedRevision != null && current.actionHistory.size != expectedRevision) return
        apply(current, action)
    }

    fun toggleFastBots() {
        viewModelScope.launch { settingsRepository.setFastBots(!fastBots.value) }
    }

    /** ecran de jeu l'active quand il est visible. */
    fun setBotsActive(active: Boolean) {
        botsActive.value = active
    }

    private suspend fun playBots() {
        combine(_game, botsActive, fastBots, ::Triple).collectLatest { (state, active, fast) ->
            if (state == null || !active || !state.awaitsBot()) return@collectLatest
            // rule transverse 1 : chaque action de bot reste visible au moins 400 ms.
            val action = botPresenter.nextAction(state, fast) { true } ?: return@collectLatest
            apply(state, action)
        }
    }

    private fun apply(state: GameState, action: GameAction) {
        val next = try {
            GameEngine.reduce(state, action)
        } catch (e: IllegalActionException) {
            return
        }
        update(next)
    }

    private fun GameState.botExplanation(): String? =
        if (awaitsBot()) botPolicy.explainAction(this, botPolicy.chooseAction(this)) else null

    private fun update(state: GameState) {
        _game.value = state
        val isOver = state.phase == TurnPhase.FIN_PARTIE
        viewModelScope.launch {
            if (isOver) gameRepository.clear() else gameRepository.save(state)
        }
        if (isOver) _effects.trySend(GameEffect.GameOver)
    }

    companion object {
        const val APP_VERSION = "appVersion"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val KEY_SELECTED_MONSTER = "selected_monster"
    }
}

/** Vrai quand la prochaine décision revient à un bot, y compris Fuir ou Rester pour un bot dans la Tour. */
internal fun GameState.awaitsBot(): Boolean = phase != TurnPhase.FIN_PARTIE && decisionPlayer.isBot

/** Termine la résolution automatique en attente ; la migration du format est faite par le GameRepository. */
private fun GameState.resumed(): GameState = GameEngine.advanceAutomatically(this)
