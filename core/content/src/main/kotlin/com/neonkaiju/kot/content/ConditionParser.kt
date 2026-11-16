package com.neonkaiju.kot.content

import com.neonkaiju.kot.model.GameState
import com.neonkaiju.kot.model.PlayerState

class ConditionSyntaxException(message: String) : IllegalArgumentException(message)

/**
 * Expression de condition parsée une fois pour toutes : aucune évaluation dynamique de texte.
 *
 * Grammaire (sous-ensemble figé, §8) :
 *   ou          := et ( "||" et )*
 *   et          := non ( "&&" non )*
 *   non         := "!" non | comparaison
 *   comparaison := terme ( ("==" | "!=" | ">=" | "<=" | ">" | "<") terme )?
 *   terme       := ENTIER | "true" | "false" | VARIABLE | "(" ou ")"
 */
sealed interface Condition {
    fun evaluate(state: GameState, ownerId: Int): Boolean
}

enum class ConditionType { INT, BOOL }

/** Variables lisibles par une condition, toujours du point de vue du propriétaire de l'effet. */
enum class ConditionVariable(val key: String, val type: ConditionType, val read: (GameState, PlayerState) -> Any) {
    DEGATS_SUBIS("degatsSubisCeTour", ConditionType.INT, { _, p -> p.damageTakenThisTurn }),
    DANS_TOKYO("estDansTokyo", ConditionType.BOOL, { s, p -> s.tokyoOccupantId == p.id }),
    ENERGIE("energie", ConditionType.INT, { _, p -> p.energy }),
    HP("hp", ConditionType.INT, { _, p -> p.hp }),
    VP("vp", ConditionType.INT, { _, p -> p.vp }),
    SURCHARGE("surcharge", ConditionType.INT, { _, p -> p.malice }),
    NB_CARTES("nbCartes", ConditionType.INT, { _, p -> p.cards.size }),
    EST_ACTIF("estActif", ConditionType.BOOL, { s, p -> s.activePlayer.id == p.id });

    companion object {
        fun fromKey(key: String): ConditionVariable? = entries.firstOrNull { it.key == key }
    }
}

private sealed interface Operand {
    val type: ConditionType
    fun value(state: GameState, owner: PlayerState): Any

    data class Literal(val raw: Any, override val type: ConditionType) : Operand {
        override fun value(state: GameState, owner: PlayerState) = raw
    }

    data class Variable(val variable: ConditionVariable) : Operand {
        override val type get() = variable.type
        override fun value(state: GameState, owner: PlayerState) = variable.read(state, owner)
    }
}

private enum class Comparator(val symbol: String) {
    EQ("=="), NE("!="), GE(">="), LE("<="), GT(">"), LT("<");

    companion object {
        fun fromSymbol(symbol: String) = entries.firstOrNull { it.symbol == symbol }
    }
}

private data class Comparison(val left: Operand, val op: Comparator, val right: Operand) : Condition {
    override fun evaluate(state: GameState, ownerId: Int): Boolean {
        val owner = state.players.first { it.id == ownerId }
        val l = left.value(state, owner)
        val r = right.value(state, owner)
        return when (op) {
            Comparator.EQ -> l == r
            Comparator.NE -> l != r
            Comparator.GE -> (l as Int) >= (r as Int)
            Comparator.LE -> (l as Int) <= (r as Int)
            Comparator.GT -> (l as Int) > (r as Int)
            Comparator.LT -> (l as Int) < (r as Int)
        }
    }
}

private data class BoolTerm(val operand: Operand) : Condition {
    override fun evaluate(state: GameState, ownerId: Int): Boolean =
        operand.value(state, state.players.first { it.id == ownerId }) as Boolean
}

private data class Not(val inner: Condition) : Condition {
    override fun evaluate(state: GameState, ownerId: Int) = !inner.evaluate(state, ownerId)
}

private data class And(val left: Condition, val right: Condition) : Condition {
    override fun evaluate(state: GameState, ownerId: Int) =
        left.evaluate(state, ownerId) && right.evaluate(state, ownerId)
}

private data class Or(val left: Condition, val right: Condition) : Condition {
    override fun evaluate(state: GameState, ownerId: Int) =
        left.evaluate(state, ownerId) || right.evaluate(state, ownerId)
}

private data object Always : Condition {
    override fun evaluate(state: GameState, ownerId: Int) = true
}

object ConditionParser {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Condition>()

    /** Parse et vérifie le typage ; lève [ConditionSyntaxException] si l'expression est invalide. */
    fun parse(condition: String?): Condition {
        if (condition.isNullOrBlank()) return Always
        return cache.getOrPut(condition) { Parser(tokenize(condition), condition).parseAll() }
    }

    fun evaluate(condition: String?, state: GameState, ownerId: Int): Boolean =
        parse(condition).evaluate(state, ownerId)

    private val SYMBOLS = listOf("&&", "||", "==", "!=", ">=", "<=", ">", "<", "!", "(", ")")

    private fun tokenize(source: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c.isWhitespace() -> i++
                c.isLetter() -> {
                    val start = i
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    tokens += source.substring(start, i)
                }
                c.isDigit() || (c == '-' && i + 1 < source.length && source[i + 1].isDigit()) -> {
                    val start = i
                    i++
                    while (i < source.length && source[i].isDigit()) i++
                    tokens += source.substring(start, i)
                }
                else -> {
                    val symbol = SYMBOLS.firstOrNull { source.startsWith(it, i) }
                        ?: throw ConditionSyntaxException("Caractère inattendu '$c' dans « $source »")
                    tokens += symbol
                    i += symbol.length
                }
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<String>, private val source: String) {
        private var pos = 0

        fun parseAll(): Condition {
            if (tokens.isEmpty()) throw error("expression vide")
            val result = parseOr()
            if (pos != tokens.size) throw error("jeton inattendu '${tokens[pos]}'")
            return result
        }

        private fun peek() = tokens.getOrNull(pos)
        private fun next() = tokens.getOrNull(pos++) ?: throw error("fin d'expression inattendue")
        private fun error(msg: String) = ConditionSyntaxException("Condition invalide « $source » : $msg")

        private fun parseOr(): Condition {
            var left = parseAnd()
            while (peek() == "||") { pos++; left = Or(left, parseAnd()) }
            return left
        }

        private fun parseAnd(): Condition {
            var left = parseNot()
            while (peek() == "&&") { pos++; left = And(left, parseNot()) }
            return left
        }

        private fun parseNot(): Condition {
            if (peek() == "!") { pos++; return Not(parseNot()) }
            return parseComparison()
        }

        private fun parseComparison(): Condition {
            if (peek() == "(") {
                pos++
                val inner = parseOr()
                if (next() != ")") throw error("')' attendue")
                return inner
            }
            val left = parseOperand()
            val op = peek()?.let { Comparator.fromSymbol(it) }
            if (op == null) {
                if (left.type != ConditionType.BOOL) throw error("une valeur entière n'est pas une condition")
                return BoolTerm(left)
            }
            pos++
            val right = parseOperand()
            if (left.type != right.type) throw error("types incompatibles ${left.type} ${op.symbol} ${right.type}")
            if (left.type == ConditionType.BOOL && op != Comparator.EQ && op != Comparator.NE) {
                throw error("l'opérateur ${op.symbol} ne s'applique pas à un booléen")
            }
            return Comparison(left, op, right)
        }

        private fun parseOperand(): Operand {
            val token = next()
            return when {
                token == "true" -> Operand.Literal(true, ConditionType.BOOL)
                token == "false" -> Operand.Literal(false, ConditionType.BOOL)
                token.toIntOrNull() != null -> Operand.Literal(token.toInt(), ConditionType.INT)
                else -> ConditionVariable.fromKey(token)?.let { Operand.Variable(it) }
                    ?: throw error("variable inconnue '$token'")
            }
        }
    }
}
