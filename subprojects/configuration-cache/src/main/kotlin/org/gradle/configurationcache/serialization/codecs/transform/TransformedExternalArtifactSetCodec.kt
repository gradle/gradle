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

import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.transform.ArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.ExecutionGraphDependenciesResolver
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory
import org.gradle.api.internal.artifacts.transform.Transformation
import org.gradle.api.internal.artifacts.transform.TransformationNodeRegistry
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.artifacts.transform.TransformedExternalArtifactSet
import org.gradle.api.internal.artifacts.transform.Transformer
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingSharedIdentity
import org.gradle.configurationcache.serialization.encodePreservingSharedIdentityOf
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.internal.Try
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import java.io.File


class TransformedExternalArtifactSetCodec(
    private val transformationNodeRegistry: TransformationNodeRegistry
) : Codec<TransformedExternalArtifactSet> {
    override suspend fun WriteContext.encode(value: TransformedExternalArtifactSet) {
        encodePreservingSharedIdentityOf(value) {
            write(value.ownerId)
            write(value.targetVariantAttributes)
            val files = mutableListOf<File>()
            value.visitArtifacts { files.add(it.file) }
            write(files)
            write(value.transformation)
            val unpacked = unpackTransformation(value.transformation, value.dependenciesResolver)
            write(unpacked.dependencies)
        }
    }

    override suspend fun ReadContext.decode(): TransformedExternalArtifactSet {
        return decodePreservingSharedIdentity {
            val ownerId = readNonNull<ComponentIdentifier>()
            val targetAttributes = readNonNull<ImmutableAttributes>()
            val files = readNonNull<List<File>>()
            val transformation = readNonNull<Transformation>()
            val dependencies = readNonNull<List<TransformDependencies>>()
            val dependenciesPerTransformer = mutableMapOf<Transformer, TransformDependencies>()
            transformation.visitTransformationSteps {
                dependenciesPerTransformer.put(it.transformer, dependencies[dependenciesPerTransformer.size])
            }
            TransformedExternalArtifactSet(ownerId, FixedFilesArtifactSet(ownerId, files), targetAttributes, transformation, FixedDependenciesResolverFactory(dependenciesPerTransformer), transformationNodeRegistry)
        }
    }
}


private
class FixedDependenciesResolverFactory(private val dependencies: Map<Transformer, TransformDependencies>) : ExtraExecutionGraphDependenciesResolverFactory, ExecutionGraphDependenciesResolver {
    override fun create(componentIdentifier: ComponentIdentifier): ExecutionGraphDependenciesResolver {
        return this
    }

    override fun computeDependencyNodes(transformationStep: TransformationStep): TaskDependencyContainer {
        throw UnsupportedOperationException("should not be called")
    }

    override fun selectedArtifacts(transformer: Transformer): FileCollection {
        throw UnsupportedOperationException("should not be called")
    }

    override fun computeArtifacts(transformer: Transformer): Try<ArtifactTransformDependencies> {
        return dependencies.getValue(transformer).recreate().computeArtifacts(transformer)
    }
}


private
class FixedFilesArtifactSet(private val ownerId: ComponentIdentifier, private val files: List<File>) : ResolvedArtifactSet {
    override fun visitDependencies(context: TaskDependencyResolveContext) {
        throw UnsupportedOperationException("should not be called")
    }

    override fun startVisit(actions: BuildOperationQueue<RunnableBuildOperation>, listener: ResolvedArtifactSet.AsyncArtifactListener): ResolvedArtifactSet.Completion {
        throw UnsupportedOperationException("should not be called")
    }

    override fun visitLocalArtifacts(visitor: ResolvedArtifactSet.LocalArtifactVisitor) {
        throw UnsupportedOperationException("should not be called")
    }

    override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
        for (file in files) {
            visitor.execute(PreResolvedResolvableArtifact(null, DefaultIvyArtifactName.forFile(file, null), ComponentFileArtifactIdentifier(ownerId, file.name), file, TaskDependencyContainer.EMPTY))
        }
    }
}
