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

package org.gradle.instantexecution.serialization.codecs

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.transform.ArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.CacheableInvocation
import org.gradle.api.internal.artifacts.transform.DomainObjectProjectStateHandler
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.artifacts.transform.TransformationSubject
import org.gradle.api.internal.artifacts.transform.Transformer
import org.gradle.api.internal.artifacts.transform.TransformerInvocationFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.FileNormalizer
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.Try
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry
import org.gradle.internal.hash.HashCode
import org.gradle.util.Path
import org.gradle.work.InputChanges
import java.io.File
import java.util.Optional


internal
class NoOpTransformationStepCodec(private val projectStateRegistry: ProjectStateRegistry, private val fingerprinterRegistry: FileCollectionFingerprinterRegistry) : Codec<TransformationStep> {
    override suspend fun WriteContext.encode(value: TransformationStep) {
        // Ignore
    }

    override suspend fun ReadContext.decode(): TransformationStep {
        val stateHandler = DomainObjectProjectStateHandler(projectStateRegistry, NoOpDomainObjectContext, NoOpProjectFinder)
        return TransformationStep(NoOpTransformer, NoOpTransformerInvocationFactory, stateHandler, fingerprinterRegistry)
    }
}


private
object NoOpProjectFinder : ProjectFinder {
    override fun getProject(path: String?): ProjectInternal = TODO("not implemented")

    override fun findProject(path: String?): ProjectInternal? = TODO("not implemented")

    override fun findProject(build: BuildIdentifier?, path: String?): ProjectInternal? = TODO("not implemented")
}


private
object NoOpDomainObjectContext : DomainObjectContext {
    override fun getBuildPath(): Path = TODO("not implemented")

    override fun isScript(): Boolean = TODO("not implemented")

    override fun getProjectPath() = null

    override fun identityPath(name: String): Path = TODO("not implemented")

    override fun projectPath(name: String): Path = TODO("not implemented")
}


private
object NoOpTransformer : Transformer {
    override fun getDisplayName(): String {
        return "does nothing"
    }

    override fun requiresInputChanges(): Boolean {
        return false
    }

    override fun requiresDependencies(): Boolean {
        return false
    }

    override fun isCacheable(): Boolean {
        return false
    }

    override fun isIsolated(): Boolean {
        return true
    }

    override fun getInputArtifactNormalizer(): Class<out FileNormalizer> {
        TODO("not implemented")
    }

    override fun getInputArtifactDependenciesNormalizer(): Class<out FileNormalizer> {
        TODO("not implemented")
    }

    override fun getImplementationClass(): Class<*> {
        TODO("not implemented")
    }

    override fun getFromAttributes(): ImmutableAttributes {
        TODO("not implemented")
    }

    override fun getSecondaryInputHash(): HashCode {
        TODO("not implemented")
    }

    override fun isolateParameters(fingerprinterRegistry: FileCollectionFingerprinterRegistry) {
        TODO("not implemented")
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        TODO("not implemented")
    }

    override fun transform(inputArtifactProvider: Provider<FileSystemLocation>, outputDir: File, dependencies: ArtifactTransformDependencies, inputChanges: InputChanges?): ImmutableList<File> {
        println("Would run transform")
        return ImmutableList.of()
    }
}


private
object EmptyResult : CacheableInvocation<ImmutableList<File>> {
    override fun invoke(): Try<ImmutableList<File>> {
        return cachedResult.get()
    }

    override fun getCachedResult(): Optional<Try<ImmutableList<File>>> {
        return Optional.of(Try.successful(ImmutableList.of()))
    }
}


private
object NoOpTransformerInvocationFactory : TransformerInvocationFactory {
    override fun createInvocation(transformer: Transformer, inputArtifact: File, dependencies: ArtifactTransformDependencies, subject: TransformationSubject, fingerprinterRegistry: FileCollectionFingerprinterRegistry): CacheableInvocation<ImmutableList<File>> {
        return EmptyResult
    }
}
