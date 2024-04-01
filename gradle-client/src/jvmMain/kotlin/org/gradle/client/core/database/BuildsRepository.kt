package org.gradle.client.core.database

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import org.gradle.client.core.database.sqldelight.generated.queries.BuildsQueries
import java.io.File

class BuildsRepository(
    private val queries: BuildsQueries,
    private val readDispatcher: CoroutineDispatcher = DbDispatchers.READ,
    private val writeDispatcher: CoroutineDispatcher = DbDispatchers.WRITE,
) {
    suspend fun fetchAll(): Flow<List<Build>> =
        withContext(readDispatcher) {
            queries.selectAll(DbBuildMapper).asFlow().map { it.executeAsList() }
        }

    suspend fun fetch(id: String): Flow<Build> =
        withContext(readDispatcher) {
            queries.select(id, DbBuildMapper).asFlow().mapNotNull { it.executeAsOneOrNull() }
        }

    suspend fun insert(build: Build) =
        withContext(writeDispatcher) {
            queries.insert(
                id = build.id,
                rootDir = build.rootDir.absolutePath,
            )
        }

    suspend fun delete(build: Build) =
        withContext(writeDispatcher) {
            queries.delete(build.id)
        }

    object DbBuildMapper : (String, String) -> Build {
        override fun invoke(id: String, rootDir: String): Build =
            Build(id, File(rootDir))
    }
}
