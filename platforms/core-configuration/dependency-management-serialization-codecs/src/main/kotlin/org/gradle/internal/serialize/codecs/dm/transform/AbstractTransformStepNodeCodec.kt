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
import org.gradle.api.internal.artifacts.transform.ComponentVariantIdentifier
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.api.internal.artifacts.transform.TransformStepNodeFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readNonNull


abstract class AbstractTransformStepNodeCodec<T : TransformStepNode>(
    protected val transformStepNodeFactory: TransformStepNodeFactory,
    protected val buildOperationRunner: BuildOperationRunner,
    protected val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<T> {

    override suspend fun WriteContext.encode(value: T) {
        encodePreservingSharedIdentityOf(value) {
            writeLong(value.transformStepNodeId)
            write(value.targetComponentVariant)
            write(value.sourceAttributes)
            write(unpackTransformStep(value))
            encodeSourceArtifact(value)
            writeBoolean(value.wasScheduled())
        }
    }

    override suspend fun ReadContext.decode(): T =
        decodePreservingSharedIdentity {
            val transformStepNodeId = readLong()
            val targetComponentVariant = readNonNull<ComponentVariantIdentifier>()
            val sourceAttributes = readNonNull<AttributeContainer>()
            val transformStepSpec = readNonNull<TransformStepSpec>()
            val node = recreate(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStepSpec)
            if (readBoolean()) {
                node.markScheduled()
            }
            node
        }

    /**
     * Writes the type-specific source artifact field of [value] to the stream.
     *
     * <p>For an initial step this is the source [org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact];
     * for a chained step it is the previous [TransformStepNode]. Called between the shared prefix
     * and the trailing scheduled-flag byte; subclasses MUST NOT write any other state here.
     */
    protected abstract suspend fun WriteContext.encodeSourceArtifact(value: T)

    /**
     * Reads the type-specific source artifact field and constructs the recreated node.
     *
     * <p>Implementations read exactly one source-artifact field from the stream (mirroring
     * {@link #encodeSourceArtifact}) and call the matching {@code TransformStepNodeFactory.recreate*}
     * method. They MUST NOT read or write any other state — the trailing scheduled-flag byte is
     * handled by {@link #decode}.
     */
    protected abstract suspend fun ReadContext.recreate(
        transformStepNodeId: Long,
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        transformStepSpec: TransformStepSpec
    ): T
}
