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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant
import org.gradle.api.internal.artifacts.transform.ArtifactTransformListener
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.withCodec
import org.gradle.internal.operations.BuildOperationExecutor


internal
class InitialTransformationNodeCodec(
    private val userTypesCodec: Codec<Any?>,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val transformListener: ArtifactTransformListener
) : AbstractTransformationNodeCodec<TransformationNode.InitialTransformationNode>() {

    override suspend fun WriteContext.doEncode(value: TransformationNode.InitialTransformationNode) {
        withCodec(userTypesCodec) {
            write(value.transformationStep)
            write(transformDependencies(value))
        }
        write((value.inputArtifacts as ArtifactBackedResolvedVariant.SingleLocalArtifactSet).artifact)
    }

    override suspend fun ReadContext.doDecode(): TransformationNode.InitialTransformationNode {
        val transformationStep = withCodec(userTypesCodec) {
            readNonNull<TransformationStep>()
        }
        val resolver = withCodec(userTypesCodec) {
            (read() as TransformDependencies).recreate()
        }
        val artifacts = ArtifactBackedResolvedVariant.SingleLocalArtifactSet(readNonNull())
        return TransformationNode.initial(transformationStep, artifacts, resolver, buildOperationExecutor, transformListener)
    }
}
