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

package org.gradle.instantexecution.serialization.codecs.transform

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.ArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultExecutionGraphDependenciesResolver
import org.gradle.api.internal.artifacts.transform.ExecutionGraphDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.artifacts.transform.Transformer
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.decodePreservingSharedIdentity
import org.gradle.instantexecution.serialization.encodePreservingSharedIdentityOf
import org.gradle.internal.Try


internal
abstract class AbstractTransformationNodeCodec<T : TransformationNode> : Codec<T> {

    override suspend fun WriteContext.encode(value: T) {
        encodePreservingSharedIdentityOf(value) { doEncode(value) }
    }

    override suspend fun ReadContext.decode(): T =
        decodePreservingSharedIdentity { doDecode() }

    protected
    suspend fun WriteContext.writeDependenciesResolver(value: TransformationNode) {
        if (value.transformationStep.transformer.requiresDependencies()) {
            writeBoolean(true)
            write(value.dependenciesResolver.forTransformer(value.transformationStep.transformer).get().files)
        } else {
            writeBoolean(false)
        }
    }

    protected
    suspend fun ReadContext.readDependenciesResolver(): ExecutionGraphDependenciesResolver {
        val requiresDependencies = readBoolean()
        val dependencies = if (requiresDependencies) {
            val files = read() as FileCollection
            DefaultArtifactTransformDependencies(files)
        } else {
            DefaultExecutionGraphDependenciesResolver.MISSING_DEPENDENCIES
        }
        return FixedDependenciesResolver(dependencies)
    }

    protected
    abstract suspend fun WriteContext.doEncode(value: T)

    protected
    abstract suspend fun ReadContext.doDecode(): T
}


private
class FixedDependenciesResolver(private val dependencies: ArtifactTransformDependencies) : ExecutionGraphDependenciesResolver {
    override fun computeDependencyNodes(transformationStep: TransformationStep): TaskDependencyContainer {
        throw IllegalStateException()
    }

    override fun forTransformer(transformer: Transformer): Try<ArtifactTransformDependencies> {
        return Try.successful(dependencies)
    }
}
