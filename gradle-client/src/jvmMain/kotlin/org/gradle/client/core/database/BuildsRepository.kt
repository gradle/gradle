package org.gradle.client.core.database

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.client.core.database.sqldelight.generated.queries.BuildsQueries
import org.gradle.client.core.gradle.GradleDistribution
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
                javaHomeDir = build.javaHomeDir?.absolutePath,
                gradleUserHomeDir = build.gradleUserHomeDir?.absolutePath,
                gradleDistributionJson = Json.encodeToString(build.gradleDistribution)
            )
        }

    suspend fun delete(build: Build) =
        withContext(writeDispatcher) {
            queries.delete(build.id)
        }

    suspend fun update(build: Build) =
        withContext(writeDispatcher) {
            queries.update(
                id = build.id,
                rootDir = build.rootDir.absolutePath,
                javaHomeDir = build.javaHomeDir?.absolutePath,
                gradleUserHomeDir = build.gradleUserHomeDir?.absolutePath,
                gradleDistributionJson = Json.encodeToString(build.gradleDistribution)
            )
        }

    object DbBuildMapper : (String, String, String?, String?, String?) -> Build {
        override fun invoke(
            id: String,
            rootDir: String,
            javaHomeDir: String?,
            gradleUserHomeDir: String?,
            gradleDistributionJson: String?,
        ) = Build(
            id = id,
            rootDir = File(rootDir),
            javaHomeDir = javaHomeDir?.let(::File),
            gradleUserHomeDir = gradleUserHomeDir?.let(::File),
            gradleDistribution = gradleDistributionJson
                ?.let { Json.decodeFromString(it) }
                ?: GradleDistribution.Default,
        )
    }
}
