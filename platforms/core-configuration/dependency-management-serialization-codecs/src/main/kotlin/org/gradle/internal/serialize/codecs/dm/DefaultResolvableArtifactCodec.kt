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
            emitReplayOperation(artifactId, file, fileSize)
        }
        return DefaultResolvableArtifact(null, artifactName, artifactId, TaskDependencyContainer.EMPTY, calculatedValueContainerFactory.create(Describables.of(artifactId), file), calculatedValueContainerFactory)
    }

    private
    fun ReadContext.emitReplayOperation(artifactId: ComponentArtifactIdentifier, file: File, fileSize: Long) {
        val buildOperationRunner = isolate.owner.serviceOf<BuildOperationRunner>()
        buildOperationRunner.run(ReplayDownloadArtifactOperation(artifactId, file, fileSize))
    }

    /**
     * Emits a [DownloadArtifactBuildOperationType] synthetically during Configuration Cache replay.
     *
     * On CC hit the original resolution pipeline is skipped, so neither [DownloadArtifactBuildOperationType]
     * nor any [org.gradle.internal.resource.ExternalResourceReadBuildOperationType] /
     * [org.gradle.internal.resource.ExternalResourceAlreadyPresentBuildOperationType] is emitted for the
     * artifact. This synthetic emission surfaces the resolved artifact to build operation consumers
     * (e.g. the Develocity plugin) with the same path/size payload. The absence of a child
     * [org.gradle.internal.resource.ExternalResourceReadBuildOperationType] signals that no download
     * occurred — on CC replay, every artifact is cache-resolved by definition.
     */
    private
    class ReplayDownloadArtifactOperation(
        private val artifactId: ComponentArtifactIdentifier,
        private val file: File,
        private val fileSize: Long
    ) : RunnableBuildOperation {
        override fun run(context: BuildOperationContext) {
            context.setResult(DownloadArtifactBuildOperationType.ResultImpl(file.absolutePath, fileSize))
        }

        override fun description(): BuildOperationDescriptor.Builder {
            val displayName = artifactId.displayName
            return BuildOperationDescriptor
                .displayName("Resolve $displayName")
                .details(DownloadArtifactBuildOperationType.DetailsImpl(displayName))
        }
    }
}
