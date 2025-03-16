/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.configurationcache

import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.impl.CheckedFingerprint
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configurationcache.ConfigurationCacheLoadBuildOperationType
import org.gradle.internal.configurationcache.ConfigurationCacheStoreBuildOperationType
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.operations.configuration.ConfigurationCacheCheckFingerprintBuildOperationType
import org.gradle.operations.configuration.ConfigurationCacheCheckFingerprintBuildOperationType.BuildInvalidationReasons
import org.gradle.operations.configuration.ConfigurationCacheCheckFingerprintBuildOperationType.CheckStatus
import org.gradle.operations.configuration.ConfigurationCacheCheckFingerprintBuildOperationType.FingerprintInvalidationReason
import org.gradle.operations.configuration.ConfigurationCacheCheckFingerprintBuildOperationType.ProjectInvalidationReasons
import org.gradle.util.Path
import java.io.File


internal
fun BuildOperationRunner.withWorkGraphLoadOperation(block: () -> Pair<WorkGraphLoadResult, BuildTreeWorkGraph.FinalizedGraph>): BuildTreeWorkGraph.FinalizedGraph =
    call(object : CallableBuildOperation<BuildTreeWorkGraph.FinalizedGraph> {
        override fun description(): BuildOperationDescriptor.Builder = BuildOperationDescriptor
            .displayName("Load configuration cache state")
            .progressDisplayName("Loading configuration cache state")
            .details(WorkGraphLoadDetails)

        override fun call(context: BuildOperationContext): BuildTreeWorkGraph.FinalizedGraph =
            block().let { (opResult, returnValue) ->
                context.setResult(opResult)
                returnValue
            }
    })


internal
fun BuildOperationRunner.withWorkGraphStoreOperation(@Suppress("UNUSED_PARAMETER") cacheKey: String, block: () -> WorkGraphStoreResult): Unit =
    run(object : RunnableBuildOperation {
        override fun description(): BuildOperationDescriptor.Builder = BuildOperationDescriptor
            .displayName("Store configuration cache state")
            .progressDisplayName("Storing configuration cache state")
            .details(WorkGraphStoreDetails)

        override fun run(context: BuildOperationContext) {
            block().let {
                context.setResult(it)
                it.storeFailure?.let { failure -> context.failed(failure) }
            }
        }
    })


private
object WorkGraphLoadDetails : ConfigurationCacheLoadBuildOperationType.Details


internal
data class WorkGraphLoadResult(val stateFiles: List<File>, val originInvocationId: String? = null) : ConfigurationCacheLoadBuildOperationType.Result {
    override fun getCacheEntrySize(): Long = stateFiles.asSequence()
        .filter { it.isFile }
        .sumOf { it.length() }

    override fun getOriginBuildInvocationId(): String? = originInvocationId
}


private
object WorkGraphStoreDetails : ConfigurationCacheStoreBuildOperationType.Details


internal
data class WorkGraphStoreResult(val stateFiles: List<File>, val storeFailure: Throwable?) : ConfigurationCacheStoreBuildOperationType.Result {
    override fun getCacheEntrySize(): Long = stateFiles.asSequence()
        .filter { it.isFile }
        .sumOf { it.length() }
}


internal
fun BuildOperationRunner.withModelStoreOperation(block: () -> ModelStoreResult): Unit =
    run(object : RunnableBuildOperation {
        override fun description(): BuildOperationDescriptor.Builder = BuildOperationDescriptor
            .displayName("Store model in configuration cache")
            .progressDisplayName("Storing model in configuration cache")

        override fun run(context: BuildOperationContext) {
            block().let {
                context.setResult(it)
                it.storeFailure?.let { failure -> context.failed(failure) }
            }
        }
    })


internal
fun <T : Any> BuildOperationRunner.withModelLoadOperation(block: () -> T): T =
    call(object : CallableBuildOperation<T> {
        override fun description(): BuildOperationDescriptor.Builder = BuildOperationDescriptor
            .displayName("Load model from configuration cache")
            .progressDisplayName("Loading model from configuration cache")

        override fun call(context: BuildOperationContext): T = block()
    })


internal
data class ModelStoreResult(val storeFailure: Throwable?)


internal
fun BuildOperationRunner.withFingerprintCheckOperations(block: () -> EntrySearchResult): CheckedFingerprint {
    return call(object : CallableBuildOperation<CheckedFingerprint> {
        override fun description() = BuildOperationDescriptor
            .displayName("Check configuration cache fingerprint")
            .details(FingerprintCheckDetails)

        override fun call(context: BuildOperationContext): CheckedFingerprint {
            return block().also {
                context.setResult(FingerprintCheckResult(it))
            }.checkedFingerprint
        }
    })
}

internal
data class EntrySearchResult(val originInvocationId: String?, val checkedFingerprint: CheckedFingerprint)


private
object FingerprintCheckDetails : ConfigurationCacheCheckFingerprintBuildOperationType.Details


private
class FingerprintCheckResult(
    entrySearchResult: EntrySearchResult
) : ConfigurationCacheCheckFingerprintBuildOperationType.Result {

    private
    val checkedFingerprint = entrySearchResult.checkedFingerprint

    private
    val originInvocationId = entrySearchResult.originInvocationId

    override fun getStatus(): CheckStatus = when (checkedFingerprint) {
        is CheckedFingerprint.NotFound -> CheckStatus.NOT_FOUND
        is CheckedFingerprint.Invalid -> CheckStatus.INVALID
        is CheckedFingerprint.Valid -> {
            when (checkedFingerprint.invalidProjects) {
                null -> CheckStatus.VALID
                else -> CheckStatus.PARTIAL
            }
        }
    }

    override fun getBuildInvalidationReasons(): List<BuildInvalidationReasons> = when (checkedFingerprint) {
        is CheckedFingerprint.Invalid -> listOf(
            BuildInvalidationReasonsImpl(checkedFingerprint.buildPath, checkedFingerprint.reason)
        )

        else -> emptyList()
    }

    override fun getProjectInvalidationReasons(): List<ProjectInvalidationReasons> = when {
        checkedFingerprint is CheckedFingerprint.Valid && checkedFingerprint.invalidProjects != null -> {
            val invalidProjects = checkedFingerprint.invalidProjects
            buildList(invalidProjects.size) {
                // First reason is shown to the user.
                add(ProjectInvalidationReasonsImpl(invalidProjects.first))
                // The rest is in alphabetical order.
                invalidProjects.all.asSequence()
                    .filterNot { it.key == invalidProjects.firstProjectPath }
                    .map { ProjectInvalidationReasonsImpl(it.value) }
                    .sortedWith(compareBy({ it.buildPath }, { it.projectPath }))
                    .forEach { add(it) }
            }
        }

        else -> emptyList()
    }

    override fun getOriginBuildInvocationId(): String? =
        originInvocationId

    private
    data class BuildInvalidationReasonsImpl(
        private val buildPath: String,
        private val invalidationReasons: List<FingerprintInvalidationReason>
    ) : BuildInvalidationReasons {

        constructor(buildPath: Path, invalidationReason: StructuredMessage) : this(buildPath.toString(), listOf(FingerprintInvalidationReasonImpl(invalidationReason)))

        override fun getBuildPath() = buildPath

        override fun getInvalidationReasons() = invalidationReasons
    }

    private
    data class ProjectInvalidationReasonsImpl(
        private val buildPath: String,
        private val projectPath: String,
        private val invalidationReasons: List<FingerprintInvalidationReason>
    ) : ProjectInvalidationReasons {

        constructor(invalidation: CheckedFingerprint.InvalidProject) : this(
            invalidation.buildPath.path,
            invalidation.projectPath.path,
            listOf(FingerprintInvalidationReasonImpl(invalidation.reason.toString()))
        )

        override fun getBuildPath() = buildPath

        override fun getProjectPath() = projectPath

        override fun getInvalidationReasons() = invalidationReasons
    }

    private
    data class FingerprintInvalidationReasonImpl(private val message: String) : FingerprintInvalidationReason {
        constructor(reason: StructuredMessage) : this(reason.toString())

        override fun getMessage() = message
    }
}
