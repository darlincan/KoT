package com.neonkaiju.kot.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun `defaults apply before anything is saved, then changes are stored`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val file = File(dir, "settings.preferences_pb")
        val repository = DataStoreSettingsRepository.create(file, scope)

        assertEquals(Settings(fastBots = false), repository.settings.first())

        repository.setFastBots(true)

        assertTrue(repository.settings.first().fastBots)
        assertTrue(file.length() > 0, "le réglage est écrit sur disque")
        scope.cancel()
    }
}
