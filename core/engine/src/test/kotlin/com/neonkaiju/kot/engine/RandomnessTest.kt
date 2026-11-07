package com.neonkaiju.kot.engine

import com.neonkaiju.kot.model.DeterministicRng
import com.neonkaiju.kot.model.DieFace
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RandomnessTest {
    private fun sequence(seed: Long, size: Int): List<DieFace> {
        var rng = DeterministicRng(seed)
        return List(size) { val (face, next) = rng.nextDieFace(); rng = next; face }
    }

    @Test fun fixed_seed_repeats_and_different_seeds_vary_over_many_rolls() {
        assertEquals(sequence(42, 120), sequence(42, 120))
        assertNotEquals(sequence(42, 120), sequence(43, 120))
        for (seed in 0L..9L) assertEquals(DieFace.entries.toSet(), sequence(seed, 120).toSet())
    }

    @Test fun large_sample_six_faces_distribution_has_no_detected_bias() {
        for (seed in listOf(0L, 42L, 2026L)) {
            val size = 120_000
            val counts = sequence(seed, size).groupingBy { it }.eachCount()
            assertEquals(6, counts.size)
            val expected = size / 6.0
            val chiSquare = counts.values.sumOf { val difference = it - expected; difference * difference / expected }
            // Deterministic broad anomaly detector, not a proof of randomness or perfect equality.
            assertTrue(chiSquare < 30.0, "seed=$seed counts=$counts chi²=$chiSquare")
            println("seed=$seed n=$size counts=$counts chi²=$chiSquare")
        }
    }

    @Test fun saved_cursor_replays_the_remaining_sequence_and_shuffle_is_a_permutation() {
        var rng = DeterministicRng(42)
        repeat(17) { rng = rng.nextDieFace().second }
        assertEquals(rng.nextDieFace(), DeterministicRng(42, 17).nextDieFace())
        val original = (0..19).toList()
        val (shuffled, next) = rng.shuffle(original)
        assertEquals(original.toSet(), shuffled.toSet())
        assertEquals(20, shuffled.size)
        assertEquals(rng.cursor + 19, next.cursor)
        assertEquals(rng.shuffle(original), DeterministicRng(42, 17).shuffle(original))
    }
}
