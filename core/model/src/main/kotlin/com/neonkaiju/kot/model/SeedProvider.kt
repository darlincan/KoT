package com.neonkaiju.kot.model

/**
 * Source de la graine d'une nouvelle partie.
 * Injectée, pour qu'un test puisse fixer la graine et rejouer une partie à l'identique.
 */
fun interface SeedProvider {
    fun newSeed(): Long
}
