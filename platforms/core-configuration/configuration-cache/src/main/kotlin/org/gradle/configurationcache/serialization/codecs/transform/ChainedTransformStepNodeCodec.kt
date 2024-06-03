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
import org.gradle.api.internal.artifacts.transform.ComponentVariantIdentifier
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.api.internal.artifacts.transform.TransformStepNodeFactory
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner


internal
class ChainedTransformStepNodeCodec(
    private val transformStepNodeFactory: TransformStepNodeFactory,
    private val buildOperationRunner: BuildOperationRunner,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : AbstractTransformStepNodeCodec<TransformStepNode.ChainedTransformStepNode>() {

    override suspend fun WriteContext.doEncode(value: TransformStepNode.ChainedTransformStepNode) {
        writeLong(value.transformStepNodeId)
        write(value.targetComponentVariant)
        write(value.sourceAttributes)
        write(unpackTransformStep(value))
        write(value.previousTransformStepNode)
    }

    override suspend fun ReadContext.doDecode(): TransformStepNode.ChainedTransformStepNode {
        val transformStepNodeId = readLong()
        val targetComponentVariant = readNonNull<ComponentVariantIdentifier>()
        val sourceAttributes = readNonNull<AttributeContainer>()
        val transformStepSpec = readNonNull<TransformStepSpec>()
        val previousStep = readNonNull<TransformStepNode>()
        return transformStepNodeFactory.recreateChained(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStepSpec.transformStep, previousStep, transformStepSpec.recreateDependencies(), buildOperationRunner, calculatedValueContainerFactory)
    }
}
