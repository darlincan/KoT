package com.neonkaiju.kot.feature.game

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource

/**
 * Les textes sont en français, mais Android choisit la forme plurielle selon la langue du téléphone :
 * en anglais, 0 serait au pluriel (« 0 étoiles »). On applique la règle française : 0 et 1 au singulier.
 */
@Composable
fun frenchPlural(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    pluralStringResource(id, if (count <= 1) 1 else 2, *formatArgs)
