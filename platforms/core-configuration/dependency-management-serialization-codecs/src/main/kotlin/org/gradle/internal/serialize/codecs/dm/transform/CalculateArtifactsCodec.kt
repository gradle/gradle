/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.dm.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.transform.AbstractTransformedArtifactSet
import org.gradle.api.internal.artifacts.transform.BoundTransformStep
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import java.io.File


class CalculateArtifactsCodec(
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<AbstractTransformedArtifactSet.CalculateArtifacts> {
    override suspend fun WriteContext.encode(value: AbstractTransformedArtifactSet.CalculateArtifacts) {
        write(value.ownerId)
        write(value.sourceVariantId)
        write(value.targetVariantAttributes)
        writeCollection(value.capabilities.asSet())
        val files = mutableListOf<Artifact>()
        // Capture rather than rethrow input-resolution failures so the cache store does not abort.
        // The failure is replayed at visit time, when the existing failure-handling path takes effect.
        // Note: if the delegate is a composite and a failing sub-set is visited before a good one,
        // the good sub-set's artifacts are lost — same iteration-order behavior as the pre-catch code.
        val failure: Throwable? = try {
            value.delegate.visitExternalArtifacts { files.add(Artifact(file, artifactName.classifier)) } // TODO: Serialize the whole ResolvableArtifact, not just the files.
            null
        } catch (e: RuntimeException) {
            e
        }
        write(files)
        write(failure)
        val steps = unpackTransformSteps(value.steps)
        writeCollection(steps)
    }

    override suspend fun ReadContext.decode(): AbstractTransformedArtifactSet.CalculateArtifacts {
        val ownerId = readNonNull<ComponentIdentifier>()
        val sourceVariantId = readNonNull<VariantIdentifier>()
        val targetAttributes = readNonNull<ImmutableAttributes>()
        val capabilities: List<Capability> = readList().uncheckedCast()
        val files = readNonNull<List<Artifact>>()
        val failure = read() as Throwable?
        val steps: List<TransformStepSpec> = readList().uncheckedCast()
        val fixed: ResolvedArtifactSet = FixedFilesArtifactSet(ownerId, sourceVariantId, files, calculatedValueContainerFactory)
        val delegate: ResolvedArtifactSet = when {
            failure == null -> fixed
            files.isEmpty() -> BrokenArtifactSet(failure)
            else -> CompositeArtifactSet(listOf(fixed, BrokenArtifactSet(failure)))
        }
        return AbstractTransformedArtifactSet.CalculateArtifacts(
            ownerId,
            sourceVariantId,
            delegate,
            targetAttributes,
            ImmutableCapabilities.of(capabilities),
            ImmutableList.copyOf(steps.map { BoundTransformStep(it.transformStep, it.recreateDependencies()) })
        )
    }

    private
    class Artifact(val file: File, val classifier: String?)

    private
    class FixedFilesArtifactSet(
        private val ownerId: ComponentIdentifier,
        private val sourceVariantId: VariantIdentifier,
        private val files: List<Artifact>,
        private val calculatedValueContainerFactory: CalculatedValueContainerFactory
    ) : ResolvedArtifactSet, ResolvedArtifactSet.Artifacts {
        override fun visitDependencies(context: TaskDependencyResolveContext) {
            throw UnsupportedOperationException("should not be called")
        }

        override fun visit(visitor: ResolvedArtifactSet.Visitor) {
            visitor.visitArtifacts(this)
        }

        override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation>, requireFiles: Boolean) = Unit

        override fun visit(visitor: ArtifactVisitor) {
            val artifactSetName = Describables.of(ownerId)
            for (artifact in artifacts) {
                visitor.visitArtifact(artifactSetName, sourceVariantId, ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, artifact)
            }
        }

        override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
            throw UnsupportedOperationException("should not be called")
        }

        override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
            for (artifact in artifacts) {
                visitor.execute(artifact)
            }
        }

        private
        val artifacts by lazy {
            files.map { file ->
                val artifactId = ComponentFileArtifactIdentifier(ownerId, file.file.name)
                PreResolvedResolvableArtifact(null, DefaultIvyArtifactName.forFile(file.file, file.classifier), artifactId, file.file, TaskDependencyContainer.EMPTY, calculatedValueContainerFactory)
            }
        }
    }

    private
    class BrokenArtifactSet(private val failure: Throwable) : ResolvedArtifactSet, ResolvedArtifactSet.Artifacts {
        override fun visitDependencies(context: TaskDependencyResolveContext) = context.visitFailure(failure)

        override fun visit(visitor: ResolvedArtifactSet.Visitor) = visitor.visitArtifacts(this)

        override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation>, requireFiles: Boolean) = Unit

        override fun visit(visitor: ArtifactVisitor) = visitor.visitFailure(failure)

        override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor): Nothing = throw failure

        // Throws so a re-encoding round-trip is recaught by CalculateArtifactsCodec.encode.
        override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>): Nothing = throw failure
    }

    private
    class CompositeArtifactSet(private val sets: List<ResolvedArtifactSet>) : ResolvedArtifactSet {
        override fun visitDependencies(context: TaskDependencyResolveContext) {
            for (set in sets) set.visitDependencies(context)
        }

        override fun visit(visitor: ResolvedArtifactSet.Visitor) {
            for (set in sets) set.visit(visitor)
        }

        override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
            for (set in sets) set.visitTransformSources(visitor)
        }

        override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
            for (set in sets) set.visitExternalArtifacts(visitor)
        }
    }
}
