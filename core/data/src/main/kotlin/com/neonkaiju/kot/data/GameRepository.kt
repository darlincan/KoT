package com.neonkaiju.kot.data

import com.neonkaiju.kot.model.GameState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

// partie en cours et  une seule sauvegarde, reprise au lancement de l'application.
interface GameRepository {
    suspend fun load(): GameState?
    suspend fun save(state: GameState)
    suspend fun clear()
}

/**
 * on save dans un fichier json
 *
 * Les writes passent par un [Mutex] : elles sont appliquées dans l'ordre des appels,
 * donc la dernière action jouée est toujours celle qui reste sur disque.
 * Chaque écriture se fait dans un fichier temporaire renommé ensuite donc une application tuée en pleine écriture laisse
 * l'ancienne sauvegarde intacte au lieu d'un JSON tronqué.
 */
class FileGameRepository(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : GameRepository {

    private val mutex = Mutex()

    override suspend fun load(): GameState? = mutex.withLock {
        withContext(ioDispatcher) {
            runCatching {
                if (file.exists()) json.decodeFromString<GameState>(file.readText()) else null
            }.getOrNull()
        }
    }

    override suspend fun save(state: GameState) = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(GameState.serializer(), state))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    override suspend fun clear() = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            file.delete()
            Unit
        }
    }
}
