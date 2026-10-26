package com.neonkaiju.kot.data

import com.neonkaiju.kot.content.CatalogData
import com.neonkaiju.kot.content.CatalogLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Catalogue des cartes et des tuiles, immuable pendant toute la vie de l'application.
interface CatalogRepository {
    suspend fun catalog(): CatalogData
}

// on Lit catalog.json une seule fois, hors du thread principal, puis le garde en mémoire.
class ResourceCatalogRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loader: () -> CatalogData = CatalogLoader::loadCatalog,
) : CatalogRepository {

    private val mutex = Mutex()
    private var cached: CatalogData? = null

    override suspend fun catalog(): CatalogData = mutex.withLock {
        cached ?: withContext(ioDispatcher) { loader() }.also { cached = it }
    }
}
