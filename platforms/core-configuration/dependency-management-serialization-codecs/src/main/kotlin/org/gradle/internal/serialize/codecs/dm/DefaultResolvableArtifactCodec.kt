/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.serialize.codecs.dm

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.DownloadArtifactBuildOperationType
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.internal.Describables
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.resource.ExternalResourceAlreadyPresentBuildOperationType
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.serialize.graph.writeFile

import java.io.File


class DefaultResolvableArtifactCodec(
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<DefaultResolvableArtifact> {
    private
    val componentIdSerializer = ComponentIdentifierSerializer()

    override suspend fun WriteContext.encode(value: DefaultResolvableArtifact) {
        // Write the source artifact
        val file = value.file
        writeFile(file)
        writeLong(file.length())
        IvyArtifactNameSerializer.INSTANCE.write(this, value.artifactName)
        // TODO - preserve the artifact id implementation instead of unpacking the component id
        componentIdSerializer.write(this, value.id.componentIdentifier)
        // TODO - preserve the artifact's owner id (or get rid of it as it's not used for transforms)
    }

    override suspend fun ReadContext.decode(): DefaultResolvableArtifact {
        val file = readFile()
        val fileSize = readLong()
        val artifactName = IvyArtifactNameSerializer.INSTANCE.read(this)
        val componentId = componentIdSerializer.read(this)
        val artifactId = ComponentFileArtifactIdentifier(componentId, file.name)
        if (componentId is ModuleComponentIdentifier) {
            markAccessedInArtifactCache(file)
            emitReplayOperation(artifactId, file, fileSize)
        }
        return DefaultResolvableArtifact(null, artifactName, artifactId, TaskDependencyContainer.EMPTY, calculatedValueContainerFactory.create(Describables.of(artifactId), file), calculatedValueContainerFactory)
    }

    /**
     * Mark a CC-replayed artifact file as accessed in the Gradle user home artifact cache journal.
     *
     * Without this, LRU cache cleanup would not see the file as in-use and could evict it despite
     * it being actively used by builds that consistently hit the Configuration Cache. On CC miss,
     * this bookkeeping is done by `AbstractCachedIndex.lookup()`; on CC hit the resolution pipeline
     * is bypassed, leaving the journal un-updated.
     */
    private
    fun ReadContext.markAccessedInArtifactCache(file: File) {
        val fileStoreProvider = isolate.owner.serviceOf<FileStoreAndIndexProvider>()
        fileStoreProvider.artifactIdentifierFileStore.fileAccessTracker.markAccessed(file)
    }

    private
    fun ReadContext.emitReplayOperation(artifactId: ComponentArtifactIdentifier, file: File, fileSize: Long) {
        val buildOperationRunner = isolate.owner.serviceOf<BuildOperationRunner>()
        buildOperationRunner.run(ReplayDownloadArtifactOperation(artifactId, file, fileSize, buildOperationRunner))
    }

    /**
     * Emits a [DownloadArtifactBuildOperationType] synthetically during Configuration Cache replay,
     * with a nested [ExternalResourceAlreadyPresentBuildOperationType] child that signals the artifact
     * was served from the local cache (by definition on CC hit). The child carries the same
     * file path and size payload as would fire during a CC-miss cache-hit resolution.
     *
     * Consumers see a consistent parent-child structure on both CC miss and CC hit:
     *   DownloadArtifactBuildOperationType
     *     └── ExternalResourceAlreadyPresentBuildOperationType   (cache-resolved)
     *
     * The child's `location` is empty because the original request URI is not persisted in the
     * configuration cache — consumers that need it should correlate via the artifact identifier
     * on the parent op. Replay for related module metadata files (POM, `.module`) is intentionally
     * not included here and is tracked as a follow-up.
     */
    private
    class ReplayDownloadArtifactOperation(
        private val artifactId: ComponentArtifactIdentifier,
        private val file: File,
        private val fileSize: Long,
        private val buildOperationRunner: BuildOperationRunner
    ) : RunnableBuildOperation {
        override fun run(context: BuildOperationContext) {
            buildOperationRunner.run(ReplayAlreadyPresentOperation(file, fileSize))
            context.setResult(DownloadArtifactBuildOperationType.ResultImpl(file.absolutePath, fileSize))
        }

        override fun description(): BuildOperationDescriptor.Builder {
            val displayName = artifactId.displayName
            return BuildOperationDescriptor
                .displayName("Resolve $displayName")
                .details(DownloadArtifactBuildOperationType.DetailsImpl(displayName))
        }
    }

    private
    class ReplayAlreadyPresentOperation(
        private val file: File,
        private val fileSize: Long
    ) : RunnableBuildOperation {
        override fun run(context: BuildOperationContext) {
            context.setResult(ReplayAlreadyPresentResult(file.absolutePath, fileSize))
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName("Cache hit for " + file.absolutePath)
                .details(ReplayAlreadyPresentDetails)
        }
    }

    private
    object ReplayAlreadyPresentDetails : ExternalResourceAlreadyPresentBuildOperationType.Details {
        // Original request URI is not persisted in the Configuration Cache.
        override fun getLocation(): String = ""
    }

    private
    class ReplayAlreadyPresentResult(
        private val resolvedFilePath: String,
        private val fileSize: Long
    ) : ExternalResourceAlreadyPresentBuildOperationType.Result {
        override fun getResolvedFilePath(): String = resolvedFilePath
        override fun getFileSize(): Long = fileSize
    }
}
