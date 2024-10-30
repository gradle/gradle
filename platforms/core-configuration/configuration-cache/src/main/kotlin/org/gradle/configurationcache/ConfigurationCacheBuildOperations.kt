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
fun <T : Any> BuildOperationRunner.withLoadOperation(block: () -> Pair<LoadResult, T>) =
    call(object : CallableBuildOperation<T> {
        override fun description(): BuildOperationDescriptor.Builder = BuildOperationDescriptor
            .displayName("Load configuration cache state")
            .progressDisplayName("Loading configuration cache state")
            .details(LoadDetails)

        override fun call(context: BuildOperationContext): T =
            block().let { (opResult, returnValue) ->
                context.setResult(opResult)
                returnValue
            }
    })


internal
fun BuildOperationRunner.withStoreOperation(@Suppress("UNUSED_PARAMETER") cacheKey: String, block: () -> StoreResult) =
    run(object : RunnableBuildOperation {
        override fun description(): BuildOperationDescriptor.Builder = BuildOperationDescriptor
            .displayName("Store configuration cache state")
            .progressDisplayName("Storing configuration cache state")
            .details(StoreDetails)

        override fun run(context: BuildOperationContext) {
            block().let {
                context.setResult(it)
                it.storeFailure?.let { failure -> context.failed(failure) }
            }
        }
    })


private
object LoadDetails : ConfigurationCacheLoadBuildOperationType.Details


internal
data class LoadResult(val stateFiles: List<File>, val originInvocationId: String? = null) : ConfigurationCacheLoadBuildOperationType.Result {
    override fun getCacheEntrySize(): Long = stateFiles.asSequence()
        .filter { it.isFile }
        .sumOf { it.length() }

    override fun getOriginBuildInvocationId(): String? = originInvocationId
}


private
object StoreDetails : ConfigurationCacheStoreBuildOperationType.Details


internal
data class StoreResult(val stateFiles: List<File>, val storeFailure: Throwable?) : ConfigurationCacheStoreBuildOperationType.Result {
    override fun getCacheEntrySize(): Long = stateFiles.asSequence()
        .filter { it.isFile }
        .sumOf { it.length() }
}


internal
fun BuildOperationRunner.withFingerprintCheckOperations(block: () -> CheckedFingerprint): CheckedFingerprint {
    return call(object : CallableBuildOperation<CheckedFingerprint> {
        override fun description() = BuildOperationDescriptor
            .displayName("Check configuration cache fingerprint")
            .details(FingerprintCheckDetails)

        override fun call(context: BuildOperationContext): CheckedFingerprint {
            return block().also {
                context.setResult(FingerprintCheckResult(it))
            }
        }
    })
}


private
object FingerprintCheckDetails : ConfigurationCacheCheckFingerprintBuildOperationType.Details


private
class FingerprintCheckResult(
    private val checkResult: CheckedFingerprint
) : ConfigurationCacheCheckFingerprintBuildOperationType.Result {

    override fun getStatus(): CheckStatus = when (checkResult) {
        is CheckedFingerprint.NotFound -> CheckStatus.NOT_FOUND
        is CheckedFingerprint.Valid -> CheckStatus.VALID
        is CheckedFingerprint.Found -> CheckStatus.VALID
        is CheckedFingerprint.EntryInvalid -> CheckStatus.INVALID
        is CheckedFingerprint.ProjectsInvalid -> CheckStatus.PARTIAL
    }

    override fun getBuildInvalidationReasons(): List<BuildInvalidationReasons> {
        return when (checkResult) {
            is CheckedFingerprint.EntryInvalid -> listOf(BuildInvalidationReasonsImpl(checkResult.buildPath, checkResult.reason))
            else -> emptyList()
        }
    }

    override fun getProjectInvalidationReasons(): List<ProjectInvalidationReasons> {
        return when (checkResult) {
            is CheckedFingerprint.ProjectsInvalid -> {
                buildList(checkResult.invalidProjects.size) {
                    // First reason is shown to the user.
                    add(ProjectInvalidationReasonsImpl(checkResult.invalidProjects.getValue(checkResult.firstInvalidated)))
                    // The rest is in alphabetical order.
                    checkResult.invalidProjects.asSequence()
                        .filterNot { it.key == checkResult.firstInvalidated }
                        .map { ProjectInvalidationReasonsImpl(it.value) }
                        .sortedWith(compareBy({ it.buildPath }, { it.projectPath }))
                        .forEach { add(it) }
                }
            }

            else -> emptyList()
        }
    }

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

        constructor(invalidation: CheckedFingerprint.ProjectInvalidationData) : this(
            invalidation.buildPath.path,
            invalidation.projectPath.path,
            listOf(FingerprintInvalidationReasonImpl(invalidation.message.toString()))
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
