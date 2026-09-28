/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.serialization.codecs

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.component.Artifact
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact
import org.gradle.api.internal.artifacts.NamedVariantIdentifier
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.api.internal.artifacts.transform.AbstractTransformedArtifactSet
import org.gradle.api.internal.artifacts.transform.TransformingAsyncArtifactListener
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.MissingLocalArtifactMetadata
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.local.model.TransformedArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.serialize.graph.Codec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.Path
import org.gradle.util.TestUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.Test
import java.io.File
import java.util.Date


class ComponentArtifactIdentifierCodecTest : AbstractUserTypeCodecTest() {

    private
    val calculatedValueContainerFactory = TestUtil.calculatedValueContainerFactory()

    private
    val codec: Codec<Any?> = codecs(
        attributesFactory = AttributeTestUtil.attributesFactory(),
        calculatedValueContainerFactory = calculatedValueContainerFactory
    ).userTypesCodec()

    private
    val moduleId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "lib"), "1.0")

    private
    val projectId = DefaultProjectComponentIdentifier(ProjectIdentity.forSubproject(Path.path(":"), Path.path(":sub")))

    private
    val rootProjectId = DefaultProjectComponentIdentifier(ProjectIdentity.forRootProject(Path.path(":"), "root"))

    private
    val artifactName = DefaultIvyArtifactName("lib", "jar", "jar", "sources")

    private
    val file = File("lib-1.0.jar").absoluteFile

    private
    val componentIds: List<ComponentIdentifier> = listOf(
        moduleId,
        projectId,
        rootProjectId
    )

    private
    val immutableArtifactIds: List<ComponentArtifactIdentifier> =
        listOf(
            DefaultModuleComponentArtifactIdentifier(moduleId, artifactName),
            ModuleComponentFileArtifactIdentifier(moduleId, "lib-1.0.jar"),
            OpaqueComponentArtifactIdentifier(file)
        ) + componentIds.flatMap { componentId ->
            listOf(
                ComponentFileArtifactIdentifier(componentId, "lib-1.0.jar"),
                MissingLocalArtifactMetadata(componentId, artifactName),
                PublishArtifactLocalArtifactMetadata(componentId, immutablePublishArtifact())
            )
        }

    @Test
    fun `can roundtrip immutable artifact ids`() {
        immutableArtifactIds.forEach(::assertRoundtrips)
    }

    @Test
    fun `can roundtrip transformed artifact ids wrapping any artifact id`() {
        immutableArtifactIds.forEach { inputArtifactId ->
            assertRoundtrips(TransformedArtifactIdentifier(inputArtifactId, "transformed.jar"))
        }
    }

    @Test
    fun `can roundtrip nested transformed artifact ids`() {
        immutableArtifactIds.forEach { inputArtifactId ->
            assertRoundtrips(
                TransformedArtifactIdentifier(
                    TransformedArtifactIdentifier(inputArtifactId, "transformed.jar"),
                    "transformed-again.jar"
                )
            )
        }
    }

    @Test
    fun `publish artifact ids are stored without live publish artifact state`() {
        componentIds.forEach { componentId ->
            val id = PublishArtifactLocalArtifactMetadata(componentId, livePublishArtifact())

            assertThat(serializationProblemsOf(id, codec), empty())
            val read = configurationCacheRoundtripOf(id, codec)

            assertThat(read.publishArtifact, instanceOf(ImmutablePublishArtifact::class.java))
            assertThat(read, equalTo(PublishArtifactLocalArtifactMetadata(componentId, immutablePublishArtifact())))
        }
    }

    @Test
    fun `transformed artifact ids wrapping publish artifact ids are stored without live publish artifact state`() {
        componentIds.forEach { componentId ->
            val id = TransformedArtifactIdentifier(PublishArtifactLocalArtifactMetadata(componentId, livePublishArtifact()), "transformed.jar")

            assertThat(serializationProblemsOf(id, codec), empty())
            val read = configurationCacheRoundtripOf(id, codec)

            assertThat(read, equalTo(TransformedArtifactIdentifier(PublishArtifactLocalArtifactMetadata(componentId, immutablePublishArtifact()), "transformed.jar")))
        }
    }

    @Test
    fun `can roundtrip artifact ids of resolved artifact results`() {
        allArtifactIds().forEach { (id, expectedId) ->
            val result = DefaultResolvedArtifactResult(
                id,
                ImmutableAttributes.EMPTY,
                ImmutableCapabilities.EMPTY,
                Describables.of("variant"),
                Artifact::class.java,
                file
            )

            assertThat(serializationProblemsOf(result, codec), empty())
            val read = configurationCacheRoundtripOf(result, codec)

            assertThat(read.id, equalTo(expectedId))
            assertThat(read.file, equalTo(file))
        }
    }

    @Test
    fun `can roundtrip artifact ids of resolvable artifacts`() {
        allArtifactIds().forEach { (id, expectedId) ->
            val artifact = DefaultResolvableArtifact(
                null,
                artifactName,
                id,
                TaskDependencyContainer.EMPTY,
                calculatedValueContainerFactory.create(Describables.of("file"), file),
                calculatedValueContainerFactory
            )

            assertThat(serializationProblemsOf(artifact, codec), empty())
            val read = configurationCacheRoundtripOf(artifact, codec)

            assertThat(read.id, equalTo(expectedId))
            assertThat(read.file, equalTo(file))
        }
    }

    @Test
    fun `can roundtrip input artifact ids of transformed artifacts`() {
        allArtifactIds().forEach { (id, expectedId) ->
            val transformedArtifact = TransformingAsyncArtifactListener.TransformedArtifact(
                Describables.of("artifact set"),
                NamedVariantIdentifier(id.componentIdentifier, "variant"),
                ImmutableAttributes.EMPTY,
                ImmutableCapabilities.EMPTY,
                preResolvedArtifact(id),
                emptyList()
            )

            assertThat(serializationProblemsOf(transformedArtifact, codec), empty())
            val read = configurationCacheRoundtripOf(transformedArtifact, codec)

            assertThat(read.artifact.id, equalTo(expectedId))
            assertThat(read.artifact.file, equalTo(file))
        }
    }

    @Test
    fun `can roundtrip input artifact ids of transformed artifact calculations`() {
        allArtifactIds().forEach { (id, expectedId) ->
            val calculateArtifacts = AbstractTransformedArtifactSet.CalculateArtifacts(
                id.componentIdentifier,
                NamedVariantIdentifier(id.componentIdentifier, "variant"),
                SingleArtifactSet(preResolvedArtifact(id)),
                ImmutableAttributes.EMPTY,
                ImmutableCapabilities.EMPTY,
                ImmutableList.of()
            )

            assertThat(serializationProblemsOf(calculateArtifacts, codec), empty())
            val read = configurationCacheRoundtripOf(calculateArtifacts, codec)

            val readArtifacts = mutableListOf<ResolvableArtifact>()
            read.delegate.visitExternalArtifacts { readArtifacts.add(this) }
            assertThat(readArtifacts.map { it.id }, equalTo(listOf(expectedId)))
            assertThat(readArtifacts.map { it.file }, equalTo(listOf(file)))
        }
    }

    private
    fun preResolvedArtifact(id: ComponentArtifactIdentifier) =
        PreResolvedResolvableArtifact(
            null,
            artifactName,
            id,
            file,
            TaskDependencyContainer.EMPTY,
            calculatedValueContainerFactory
        )

    private
    class SingleArtifactSet(private val artifact: ResolvableArtifact) : ResolvedArtifactSet, ResolvedArtifactSet.Artifacts {
        override fun visitDependencies(context: TaskDependencyResolveContext) = Unit

        override fun visit(visitor: ResolvedArtifactSet.Visitor) = visitor.visitArtifacts(this)

        override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation>, requireFiles: Boolean) = Unit

        override fun visit(visitor: ArtifactVisitor) =
            visitor.visitArtifact(
                Describables.of("artifact set"),
                NamedVariantIdentifier(artifact.id.componentIdentifier, "variant"),
                ImmutableAttributes.EMPTY,
                ImmutableCapabilities.EMPTY,
                artifact
            )

        override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) = Unit

        override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) = visitor.execute(artifact)
    }

    private
    fun allArtifactIds(): List<Pair<ComponentArtifactIdentifier, ComponentArtifactIdentifier>> =
        immutableArtifactIds.flatMap { id ->
            listOf(
                id to id,
                TransformedArtifactIdentifier(id, "transformed.jar") to TransformedArtifactIdentifier(id, "transformed.jar")
            )
        } + componentIds.map { componentId ->
            TransformedArtifactIdentifier(
                PublishArtifactLocalArtifactMetadata(componentId, livePublishArtifact()),
                "transformed.jar"
            ) to TransformedArtifactIdentifier(
                PublishArtifactLocalArtifactMetadata(componentId, immutablePublishArtifact()),
                "transformed.jar"
            )
        }

    private
    fun assertRoundtrips(id: ComponentArtifactIdentifier) {
        assertThat(serializationProblemsOf(id, codec), empty())
        val read = configurationCacheRoundtripOf(id, codec)
        assertThat(read, equalTo(id))
    }

    private
    fun immutablePublishArtifact() =
        ImmutablePublishArtifact("lib", "jar", "jar", "sources", file)

    private
    fun livePublishArtifact() = LivePublishArtifact(file)

    class LivePublishArtifact(private val file: File) : PublishArtifact {
        val unserializableState = Thread()

        override fun getName() = "lib"
        override fun getExtension() = "jar"
        override fun getType() = "jar"
        override fun getClassifier() = "sources"
        override fun getFile() = file
        override fun getDate(): Date? = null
        override fun getBuildDependencies(): TaskDependency = TaskDependency { emptySet() }
    }
}
