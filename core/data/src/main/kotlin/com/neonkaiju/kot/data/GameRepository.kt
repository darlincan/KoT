package com.neonkaiju.kot.data

import com.neonkaiju.kot.model.GameState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /**
     * La version est lue avant le décodage : une sauvegarde plus ancienne passe par [migrate],
     * une sauvegarde plus récente (app rétrogradée) ou illisible est supprimée plutôt que mal interprétée.
     */
    override suspend fun load(): GameState? = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            if (!file.exists()) return@withContext null
            val state = runCatching {
                val raw = json.parseToJsonElement(file.readText()).jsonObject
                // les sauvegardes v1 écrites avant @EncodeDefault n'ont pas de champ version.
                val version = raw["version"]?.jsonPrimitive?.int ?: 1
                if (version > GameState.CURRENT_VERSION) null
                else json.decodeFromJsonElement(GameState.serializer(), migrate(raw, version))
            }.getOrNull()
            if (state == null) file.delete()
            state
        }
    }

    // fait monter le json d'une version à la suivante jusqu'à CURRENT_VERSION.
    private fun migrate(raw: JsonObject, from: Int): JsonObject {
        var current = raw
        for (version in from until GameState.CURRENT_VERSION) {
            current = when (version) {
                1 -> migrateV1ToV2(current)
                else -> error("migration manquante depuis la v$version")
            }
        }
        return current
    }

    /**
     * v1 → v2 : les nouveaux champs ont tous une valeur par défaut ; on impose 3 lancers et on borne les relances
     * à 2. Idempotente, car les sauvegardes v2 écrites avant @EncodeDefault n'ont pas non plus de version.
     */
    private fun migrateV1ToV2(raw: JsonObject): JsonObject {
        val rollsLeft = (raw["rollsLeft"]?.jsonPrimitive?.int ?: 2).coerceIn(0, 2)
        return JsonObject(
            raw + mapOf("version" to JsonPrimitive(2), "maxRolls" to JsonPrimitive(3), "rollsLeft" to JsonPrimitive(rollsLeft))
        )
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
