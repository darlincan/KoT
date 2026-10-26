package com.neonkaiju.kot.data

import com.neonkaiju.kot.content.CatalogData
import com.neonkaiju.kot.engine.GameEngine
import com.neonkaiju.kot.model.GameAction
import com.neonkaiju.kot.model.MonsterId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RepositoriesTest {

    @TempDir
    lateinit var dir: File

    private fun repository() = FileGameRepository(File(dir, "saved_game_v1.json"), Dispatchers.Unconfined)

    private fun newGame(catalog: CatalogData) =
        GameEngine.createGame(seed = 42L, humanMonster = MonsterId.VOLTRAX, catalog = catalog)

    @Test
    fun `load returns null when nothing is saved`() = runBlocking {
        assertNull(repository().load())
    }

    @Test
    fun `saved game is restored identically, seed included`() = runBlocking {
        val repository = repository()
        val state = newGame(ResourceCatalogRepository().catalog())

        repository.save(state)

        assertEquals(state, repository.load())
    }

    @Test
    fun `last saved state wins when saves are queued`() = runBlocking {
        val repository = repository()
        val first = newGame(ResourceCatalogRepository().catalog())
        val second = GameEngine.reduce(first, GameAction.StopRolling)

        launch { repository.save(first) }
        launch { repository.save(second) }.join()

        assertEquals(second, repository.load())
    }

    @Test
    fun `clear removes the saved game`() = runBlocking {
        val repository = repository()
        repository.save(newGame(ResourceCatalogRepository().catalog()))

        repository.clear()

        assertNull(repository.load())
    }

    @Test
    fun `corrupted save is ignored instead of crashing`() = runBlocking {
        File(dir, "saved_game_v1.json").writeText("{ pas du json")

        assertNull(repository().load())
    }

    @Test
    fun `catalog is loaded only once`() = runBlocking {
        var loads = 0
        val repository = ResourceCatalogRepository(Dispatchers.Unconfined) {
            loads++
            CatalogData(cards = emptyList(), tiles = emptyList())
        }

        repository.catalog()
        repository.catalog()

        assertEquals(1, loads)
    }
}
