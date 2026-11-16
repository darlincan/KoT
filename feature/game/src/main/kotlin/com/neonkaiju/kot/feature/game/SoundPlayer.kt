package com.neonkaiju.kot.feature.game

enum class SoundEffect { DICE, DAMAGE, PURCHASE }

/** Sons du jeu. L'implémentation Android est fournie par `:app` ; les tests utilisent un faux. */
interface SoundPlayer {
    fun play(effect: SoundEffect)
    fun setMusicEnabled(enabled: Boolean)
    /** L'application est visible : la musique peut jouer si elle est activée. */
    fun onForeground()
    /** L'application passe en arrière-plan : la musique se coupe. */
    fun onBackground()
}
