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
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.DisplayName
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
        val (files, failures) = extractFilesAndFailures(value)
        write(files)
        writeCollection(failures)
        val steps = unpackTransformSteps(value.steps)
        writeCollection(steps)
    }

    override suspend fun ReadContext.decode(): AbstractTransformedArtifactSet.CalculateArtifacts {
        val ownerId = readNonNull<ComponentIdentifier>()
        val sourceVariantId = readNonNull<VariantIdentifier>()
        val targetAttributes = readNonNull<ImmutableAttributes>()
        val capabilities: List<Capability> = readList().uncheckedCast()
        val files = readNonNull<List<Artifact>>()
        val failures: List<Throwable> = readList().uncheckedCast()
        val steps: List<TransformStepSpec> = readList().uncheckedCast()
        val delegate: ResolvedArtifactSet = buildDelegate(ownerId, sourceVariantId, files, failures)
        return AbstractTransformedArtifactSet.CalculateArtifacts(
            ownerId,
            sourceVariantId,
            delegate,
            targetAttributes,
            ImmutableCapabilities.of(capabilities),
            ImmutableList.copyOf(steps.map { BoundTransformStep(it.transformStep, it.recreateDependencies()) })
        )
    }

    /**
     * Walks the transform's input artifact set, separating the artifacts that resolved successfully
     * from any failures encountered along the way.
     * <p>
     * The traversal is failure-tolerant: a broken artifact does not abort the iteration, so siblings
     * in the same set are still captured. `requireArtifactFiles = false` avoids `SingleArtifactSet`'s
     * pre-check that calls `getFileSource().getValue()` — which throws "Value has not been calculated"
     * when the file source has not been finalized yet (the typical encode-time state). Forcing
     * materialization via `artifact.file` inside the callback finalizes it lazily and lets us catch
     * per-artifact resolution failures (e.g., broken downloads) per element.
     *
     * @return a pair of (resolved artifacts, collected failures); either list may be empty.
     */
    private
    fun extractFilesAndFailures(value: AbstractTransformedArtifactSet.CalculateArtifacts): Pair<MutableList<Artifact>, MutableList<Throwable>> {
        // TODO: Serialize the whole ResolvableArtifact, not just the files.
        val files = mutableListOf<Artifact>()
        val failures = mutableListOf<Throwable>()
        val artifactVisitor = object : ArtifactVisitor {
            override fun visitArtifact(
                artifactSetName: DisplayName,
                sourceVariantId: VariantIdentifier,
                attributes: ImmutableAttributes,
                capabilities: ImmutableCapabilities,
                artifact: ResolvableArtifact
            ) {
                try {
                    files.add(Artifact(artifact.file, artifact.artifactName.classifier))
                } catch (e: RuntimeException) {
                    failures.add(e)
                }
            }

            override fun requireArtifactFiles(): Boolean = false

            override fun visitFailure(failure: Throwable) {
                failures.add(failure)
            }
        }
        value.delegate.visit(object : ResolvedArtifactSet.Visitor {
            override fun prepareForVisit(source: FileCollectionInternal.Source) = FileCollectionStructureVisitor.VisitType.Visit
            override fun visitArtifacts(artifacts: ResolvedArtifactSet.Artifacts) {
                try {
                    artifacts.visit(artifactVisitor)
                } catch (e: RuntimeException) {
                    failures.add(e)
                }
            }
        })
        return Pair(files, failures)
    }

    private
    fun buildDelegate(
        ownerId: ComponentIdentifier,
        sourceVariantId: VariantIdentifier,
        files: List<Artifact>,
        failures: List<Throwable>
    ): ResolvedArtifactSet {
        if (files.isEmpty() && failures.isEmpty()) {
            // no failures and no files
            return ResolvedArtifactSet.EMPTY
        } else if (failures.isEmpty()) {
            // no failures, but some files
            return FixedFilesArtifactSet(ownerId, sourceVariantId, files, calculatedValueContainerFactory)
        } else {
            // some failures, possibly with files
            val sets = mutableListOf<ResolvedArtifactSet>()
            if (files.isNotEmpty()) {
                sets.add(FixedFilesArtifactSet(ownerId, sourceVariantId, files, calculatedValueContainerFactory))
            }
            failures.forEach { sets.add(BrokenArtifactSet(it)) }
            return CompositeArtifactSet(sets)
        }
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
