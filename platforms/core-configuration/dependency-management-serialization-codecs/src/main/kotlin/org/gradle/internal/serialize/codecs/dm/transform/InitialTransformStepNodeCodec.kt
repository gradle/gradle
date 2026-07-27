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

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.transform.ComponentVariantIdentifier
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.api.internal.artifacts.transform.TransformStepNodeFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readNonNull


class InitialTransformStepNodeCodec(
    transformStepNodeFactory: TransformStepNodeFactory,
    buildOperationRunner: BuildOperationRunner,
    calculatedValueContainerFactory: CalculatedValueContainerFactory
) : AbstractTransformStepNodeCodec<TransformStepNode.InitialTransformStepNode>(
    transformStepNodeFactory,
    buildOperationRunner,
    calculatedValueContainerFactory
) {

    override suspend fun WriteContext.encodeSourceArtifact(value: TransformStepNode.InitialTransformStepNode) {
        write(value.inputArtifact)
    }

    override suspend fun ReadContext.recreate(
        transformStepNodeId: Long,
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        transformStepSpec: TransformStepSpec
    ): TransformStepNode.InitialTransformStepNode {
        val artifacts = readNonNull<ResolvableArtifact>()
        return transformStepNodeFactory.recreateInitial(
            transformStepNodeId,
            targetComponentVariant,
            sourceAttributes,
            transformStepSpec.transformStep,
            artifacts,
            transformStepSpec.recreateDependencies(),
            buildOperationRunner,
            calculatedValueContainerFactory
        )
    }
}
