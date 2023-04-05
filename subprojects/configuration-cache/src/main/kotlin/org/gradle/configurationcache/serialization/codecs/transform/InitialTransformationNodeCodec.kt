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

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.transform.ComponentVariantIdentifier
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.artifacts.transform.TransformationNodeFactory
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationExecutor


internal
class InitialTransformationNodeCodec(
    private val transformationNodeFactory: TransformationNodeFactory,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : AbstractTransformationNodeCodec<TransformationNode.InitialTransformationNode>() {

    override suspend fun WriteContext.doEncode(value: TransformationNode.InitialTransformationNode) {
        writeLong(value.transformationNodeId)
        write(value.targetComponentVariant)
        write(value.sourceAttributes)
        write(unpackTransformationStep(value))
        write(value.inputArtifact)
    }

    override suspend fun ReadContext.doDecode(): TransformationNode.InitialTransformationNode {
        val transformationNodeId = readLong()
        val targetComponentVariant = readNonNull<ComponentVariantIdentifier>()
        val sourceAttributes = readNonNull<AttributeContainer>()
        val transformationStep = readNonNull<TransformStepSpec>()
        val artifacts = readNonNull<ResolvableArtifact>()
        return transformationNodeFactory.recreateInitial(transformationNodeId, targetComponentVariant, sourceAttributes, transformationStep.transformation, artifacts, transformationStep.recreateDependencies(), buildOperationExecutor, calculatedValueContainerFactory)
    }
}
