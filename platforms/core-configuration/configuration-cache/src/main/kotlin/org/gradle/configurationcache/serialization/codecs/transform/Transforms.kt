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
import org.gradle.api.internal.artifacts.transform.BoundTransformStep
import org.gradle.api.internal.artifacts.transform.DefaultTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformDependencies
import org.gradle.api.internal.artifacts.transform.TransformStep
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependencies
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.Try
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity


sealed class TransformStepSpec(val transformStep: TransformStep) {
    abstract fun recreateDependencies(): TransformUpstreamDependencies

    class NoDependencies(transformStep: TransformStep) : TransformStepSpec(transformStep) {
        override fun recreateDependencies(): TransformUpstreamDependencies {
            return DefaultTransformUpstreamDependenciesResolver.NO_DEPENDENCIES
        }
    }

    class FileDependencies(transformStep: TransformStep, val files: FileCollection, val configurationIdentity: ConfigurationIdentity) : TransformStepSpec(transformStep) {
        override fun recreateDependencies(): TransformUpstreamDependencies {
            return FixedUpstreamDependencies(DefaultTransformDependencies(files), configurationIdentity)
        }
    }
}


object TransformStepSpecCodec : Codec<TransformStepSpec> {
    override suspend fun WriteContext.encode(value: TransformStepSpec) {
        write(value.transformStep)
        if (value is TransformStepSpec.FileDependencies) {
            writeBoolean(true)
            write(value.files)
            write(value.configurationIdentity)
        } else {
            writeBoolean(false)
        }
    }

    override suspend fun ReadContext.decode(): TransformStepSpec {
        val transformStep = readNonNull<TransformStep>()
        return if (readBoolean()) {
            return TransformStepSpec.FileDependencies(transformStep, read() as FileCollection, read() as ConfigurationIdentity)
        } else {
            TransformStepSpec.NoDependencies(transformStep)
        }
    }
}


fun unpackTransformSteps(steps: List<BoundTransformStep>): List<TransformStepSpec> {
    return steps.map { unpackTransformStep(it.transformStep, it.upstreamDependencies) }
}


fun unpackTransformStep(node: TransformStepNode): TransformStepSpec {
    return unpackTransformStep(node.transformStep, node.upstreamDependencies)
}


fun unpackTransformStep(transformStep: TransformStep, upstreamDependencies: TransformUpstreamDependencies): TransformStepSpec {
    return if (transformStep.requiresDependencies()) {
        TransformStepSpec.FileDependencies(transformStep, upstreamDependencies.selectedArtifacts(), upstreamDependencies.configurationIdentity!!)
    } else {
        TransformStepSpec.NoDependencies(transformStep)
    }
}


class FixedUpstreamDependencies(private val dependencies: TransformDependencies, private val configurationIdentity: ConfigurationIdentity) : TransformUpstreamDependencies {

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

    override fun computeArtifacts(): Try<TransformDependencies> {
        return Try.successful(dependencies)
    }
}
