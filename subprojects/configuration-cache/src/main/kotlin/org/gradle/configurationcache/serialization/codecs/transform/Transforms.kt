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
import org.gradle.api.internal.artifacts.transform.BoundTransformationStep
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependencies
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.internal.Try
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity


sealed class TransformStepSpec(val transformation: TransformationStep) {
    abstract fun recreateDependencies(): TransformUpstreamDependencies

    class NoDependencies(transformation: TransformationStep) : TransformStepSpec(transformation) {
        override fun recreateDependencies(): TransformUpstreamDependencies {
            return DefaultTransformUpstreamDependenciesResolver.NO_DEPENDENCIES
        }
    }

    class FileDependencies(transformation: TransformationStep, val files: FileCollection, val configurationIdentity: ConfigurationIdentity) : TransformStepSpec(transformation) {
        override fun recreateDependencies(): TransformUpstreamDependencies {
            return FixedUpstreamDependencies(DefaultArtifactTransformDependencies(files), configurationIdentity)
        }
    }
}


object TransformStepSpecCodec : Codec<TransformStepSpec> {
    override suspend fun WriteContext.encode(value: TransformStepSpec) {
        write(value.transformation)
        if (value is TransformStepSpec.FileDependencies) {
            writeBoolean(true)
            write(value.files)
            write(value.configurationIdentity)
        } else {
            writeBoolean(false)
        }
    }

    override suspend fun ReadContext.decode(): TransformStepSpec {
        val transformation = readNonNull<TransformationStep>()
        return if (readBoolean()) {
            return TransformStepSpec.FileDependencies(transformation, read() as FileCollection, read() as ConfigurationIdentity)
        } else {
            TransformStepSpec.NoDependencies(transformation)
        }
    }
}


fun unpackTransformationSteps(steps: List<BoundTransformationStep>): List<TransformStepSpec> {
    return steps.map { unpackTransformationStep(it.transformation, it.upstreamDependencies) }
}


fun unpackTransformationStep(node: TransformationNode): TransformStepSpec {
    return unpackTransformationStep(node.transformationStep, node.upstreamDependencies)
}


fun unpackTransformationStep(transformationStep: TransformationStep, upstreamDependencies: TransformUpstreamDependencies): TransformStepSpec {
    return if (transformationStep.requiresDependencies()) {
        TransformStepSpec.FileDependencies(transformationStep, upstreamDependencies.selectedArtifacts(), upstreamDependencies.configurationIdentity!!)
    } else {
        TransformStepSpec.NoDependencies(transformationStep)
    }
}


class FixedUpstreamDependencies(private val dependencies: ArtifactTransformDependencies, private val configurationIdentity: ConfigurationIdentity) : TransformUpstreamDependencies {

    override fun getConfigurationIdentity(): ConfigurationIdentity {
        return configurationIdentity
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        throw IllegalStateException("Should not be called")
    }

    override fun selectedArtifacts(): FileCollection {
        return dependencies.files.orElseThrow { IllegalStateException("Transform does not use artifact dependencies.") }
    }

    override fun finalizeIfNotAlready() {
    }

    override fun computeArtifacts(): Try<ArtifactTransformDependencies> {
        return Try.successful(dependencies)
    }
}
