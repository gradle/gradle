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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.ArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultExecutionGraphDependenciesResolver
import org.gradle.api.internal.artifacts.transform.ExecutionGraphDependenciesResolver
import org.gradle.api.internal.artifacts.transform.Transformation
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.artifacts.transform.Transformer
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.internal.Try


sealed class TransformDependencies {
    abstract fun recreate(): ExecutionGraphDependenciesResolver

    object NotRequired : TransformDependencies() {
        override fun recreate(): ExecutionGraphDependenciesResolver {
            return FixedDependenciesResolver(DefaultExecutionGraphDependenciesResolver.MISSING_DEPENDENCIES)
        }
    }

    class FileDependencies(val files: FileCollection) : TransformDependencies() {
        override fun recreate(): ExecutionGraphDependenciesResolver {
            return FixedDependenciesResolver(DefaultArtifactTransformDependencies(files))
        }
    }
}


object TransformDependenciesCodec : Codec<TransformDependencies> {
    override suspend fun WriteContext.encode(value: TransformDependencies) {
        if (value is TransformDependencies.FileDependencies) {
            writeBoolean(true)
            write(value.files)
        } else {
            writeBoolean(false)
        }
    }

    override suspend fun ReadContext.decode(): TransformDependencies {
        return if (readBoolean()) {
            return TransformDependencies.FileDependencies(read() as FileCollection)
        } else {
            TransformDependencies.NotRequired
        }
    }
}


class TransformationSpec(val transformation: Transformation, val dependencies: List<TransformDependencies>)


fun unpackTransformation(transformation: Transformation, dependenciesResolver: ExecutionGraphDependenciesResolver): TransformationSpec {
    val dependencies = mutableListOf<TransformDependencies>()
    transformation.visitTransformationSteps {
        dependencies.add(transformDependencies(it.transformer, dependenciesResolver))
    }
    return TransformationSpec(transformation, dependencies)
}


fun transformDependencies(transformer: Transformer, dependenciesResolver: ExecutionGraphDependenciesResolver): TransformDependencies {
    return if (transformer.requiresDependencies()) {
        TransformDependencies.FileDependencies(dependenciesResolver.selectedArtifacts(transformer))
    } else {
        TransformDependencies.NotRequired
    }
}


fun transformDependencies(node: TransformationNode): TransformDependencies {
    return transformDependencies(node.transformationStep.transformer, node.dependenciesResolver)
}


class FixedDependenciesResolver(private val dependencies: ArtifactTransformDependencies) : ExecutionGraphDependenciesResolver {
    override fun computeDependencyNodes(transformationStep: TransformationStep): TaskDependencyContainer {
        throw IllegalStateException()
    }

    override fun selectedArtifacts(transformer: Transformer): FileCollection {
        return dependencies.files
    }

    override fun computeArtifacts(transformer: Transformer): Try<ArtifactTransformDependencies> {
        return Try.successful(dependencies)
    }
}
