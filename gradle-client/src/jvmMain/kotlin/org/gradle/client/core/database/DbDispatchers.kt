package org.gradle.client.core.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
object DbDispatchers {

    /**
     * Coroutine context for database READ operations.
     *
     * SQLite supports multithreaded reads.
     * This is effectively [Dispatchers.IO].
     */
    val READ: CoroutineDispatcher =
        Dispatchers.IO

    /**
     * Coroutine context for database WRITE operations.
     *
     * SQLite requires serial write operations.
     * This context enforces no parallelism, reusing threads from [Dispatchers.IO].
     */
    val WRITE: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1)
}