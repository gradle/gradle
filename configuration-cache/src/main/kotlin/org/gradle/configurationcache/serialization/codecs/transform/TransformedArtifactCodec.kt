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

package org.gradle.configurationcache.serialization.codecs.transform

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact
import org.gradle.api.internal.artifacts.transform.BoundTransformationStep
import org.gradle.api.internal.artifacts.transform.TransformingAsyncArtifactListener
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.model.CalculatedValueContainerFactory
import java.io.File


class TransformedArtifactCodec(
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<TransformingAsyncArtifactListener.TransformedArtifact> {
    override suspend fun WriteContext.encode(value: TransformingAsyncArtifactListener.TransformedArtifact) {
        write(value.variantName)
        write(value.target)
        writeCollection(value.capabilities)
        write(value.artifact.id.componentIdentifier)
        write(value.artifact.file)
        write(unpackTransformationSteps(value.transformationSteps))
    }

    override suspend fun ReadContext.decode(): TransformingAsyncArtifactListener.TransformedArtifact? {
        val variantName = readNonNull<DisplayName>()
        val target = readNonNull<ImmutableAttributes>()
        val capabilities: List<Capability> = readList().uncheckedCast()
        val ownerId = readNonNull<ComponentIdentifier>()
        val file = readNonNull<File>()
        val artifactId = ComponentFileArtifactIdentifier(ownerId, file.name)
        val artifact = PreResolvedResolvableArtifact(null, DefaultIvyArtifactName.forFile(file, null), artifactId, calculatedValueContainerFactory.create(Describables.of(artifactId), file), TaskDependencyContainer.EMPTY, calculatedValueContainerFactory)
        val steps = readNonNull<List<TransformStepSpec>>().map { BoundTransformationStep(it.transformation, it.recreate()) }
        return TransformingAsyncArtifactListener.TransformedArtifact(variantName, target, capabilities, artifact, steps)
    }
}
