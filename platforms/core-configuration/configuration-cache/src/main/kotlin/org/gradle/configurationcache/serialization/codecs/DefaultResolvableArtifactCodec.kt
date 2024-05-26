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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.writeFile
import org.gradle.internal.Describables
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.model.CalculatedValueContainerFactory


class DefaultResolvableArtifactCodec(
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<DefaultResolvableArtifact> {
    private
    val componentIdSerializer = ComponentIdentifierSerializer()

    override suspend fun WriteContext.encode(value: DefaultResolvableArtifact) {
        // Write the source artifact
        writeFile(value.file)
        IvyArtifactNameSerializer.INSTANCE.write(this, value.artifactName)
        // TODO - preserve the artifact id implementation instead of unpacking the component id
        componentIdSerializer.write(this, value.id.componentIdentifier)
        // TODO - preserve the artifact's owner id (or get rid of it as it's not used for transforms)
    }

    override suspend fun ReadContext.decode(): DefaultResolvableArtifact {
        val file = readFile()
        val artifactName = IvyArtifactNameSerializer.INSTANCE.read(this)
        val componentId = componentIdSerializer.read(this)
        val artifactId = ComponentFileArtifactIdentifier(componentId, file.name)
        return DefaultResolvableArtifact(null, artifactName, artifactId, TaskDependencyContainer.EMPTY, calculatedValueContainerFactory.create(Describables.of(artifactId), file), calculatedValueContainerFactory)
    }
}
