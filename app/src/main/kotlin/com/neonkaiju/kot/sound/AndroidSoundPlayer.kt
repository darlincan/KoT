package com.neonkaiju.kot.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.neonkaiju.kot.feature.game.R
import com.neonkaiju.kot.feature.game.SoundEffect
import com.neonkaiju.kot.feature.game.SoundPlayer

/** [SoundPool] pour les effets courts, [MediaPlayer] en boucle pour la musique. Une seule instance. */
class AndroidSoundPlayer(private val context: Context) : SoundPlayer {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
    private val effects = mapOf(
        SoundEffect.DICE to pool.load(context, R.raw.sfx_dice, 1),
        SoundEffect.DAMAGE to pool.load(context, R.raw.sfx_damage, 1),
        SoundEffect.PURCHASE to pool.load(context, R.raw.sfx_purchase, 1),
    )

    private var music: MediaPlayer? = null
    private var musicEnabled = false
    private var inForeground = false

    override fun play(effect: SoundEffect) {
        effects[effect]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    override fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        syncMusic()
    }

    override fun onForeground() {
        inForeground = true
        syncMusic()
    }

    override fun onBackground() {
        inForeground = false
        syncMusic()
    }

    private fun syncMusic() {
        if (musicEnabled && inForeground) {
            val player = music ?: MediaPlayer.create(context, R.raw.music_loop)?.apply {
                isLooping = true
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            }?.also { music = it }
            if (player?.isPlaying == false) player.start()
        } else if (music?.isPlaying == true) {
            music?.pause()
        }
    }

    private companion object {
        const val MUSIC_VOLUME = 0.4f
    }
}
