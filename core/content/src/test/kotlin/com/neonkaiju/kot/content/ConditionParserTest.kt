package com.neonkaiju.kot.content

import com.neonkaiju.kot.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConditionParserTest {

    private fun state(
        hp: Int = 10,
        energy: Int = 0,
        vp: Int = 0,
        malice: Int = 0,
        damage: Int = 0,
        inTokyo: Boolean = false
    ) = GameState(
        rng = DeterministicRng(1L),
        players = listOf(
            PlayerState(0, MonsterId.VOLTRAX, false, hp = hp, energy = energy, vp = vp, malice = malice, damageTakenThisTurn = damage),
            PlayerState(1, MonsterId.HYDROKOR, true)
        ),
        tokyoOccupantId = if (inTokyo) 0 else 1
    )

    private fun eval(condition: String?, s: GameState = state()) = ConditionParser.evaluate(condition, s, 0)

    @Test
    fun `une condition absente ou vide est toujours vraie`() {
        assertTrue(eval(null))
        assertTrue(eval("   "))
    }

    @Test
    fun `comparaisons entières`() {
        val s = state(hp = 8, energy = 3, vp = 5, malice = 6, damage = 2)
        assertTrue(eval("hp >= 8", s))
        assertFalse(eval("hp > 8", s))
        assertTrue(eval("energie == 3", s))
        assertTrue(eval("vp != 4", s))
        assertTrue(eval("surcharge <= 6", s))
        assertTrue(eval("degatsSubisCeTour < 3", s))
        assertTrue(eval("3 == energie", s))
    }

    @Test
    fun `booléens, négation et variable seule`() {
        assertTrue(eval("estDansTokyo == true", state(inTokyo = true)))
        assertTrue(eval("estDansTokyo", state(inTokyo = true)))
        assertTrue(eval("!estDansTokyo", state(inTokyo = false)))
        assertTrue(eval("estDansTokyo != true", state(inTokyo = false)))
    }

    @Test
    fun `priorité de et sur ou, parenthèses`() {
        val s = state(hp = 3, energy = 0)
        // hp < 5 || (energie > 2 && hp > 9)
        assertTrue(eval("hp < 5 || energie > 2 && hp > 9", s))
        assertFalse(eval("(hp < 5 || energie > 2) && hp > 9", s))
        assertTrue(eval("!(hp > 5) && energie == 0", s))
    }

    @Test
    fun `entiers négatifs et espaces optionnels`() {
        assertTrue(eval("hp>-1&&energie==0"))
    }

    @Test
    fun `les expressions invalides sont rejetées au parsing`() {
        listOf(
            "pv >= 3",                    // variable inconnue
            "hp >=",                      // opérande manquant
            "hp >= 3 4",                  // jeton en trop
            "hp",                         // un entier n'est pas une condition
            "hp == true",                 // types incompatibles
            "estDansTokyo > false",       // ordre sur un booléen
            "(hp > 3",                    // parenthèse non fermée
            "hp > 3 ; System.exit(0)",    // aucune évaluation dynamique
            "Runtime.getRuntime()"
        ).forEach { source ->
            assertThrows(ConditionSyntaxException::class.java, { ConditionParser.parse(source) }, source)
        }
    }

    @Test
    fun `le parsing est mis en cache`() {
        assertSame(ConditionParser.parse("hp >= 8"), ConditionParser.parse("hp >= 8"))
    }
}
