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

import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.internal.artifacts.transform.ArtifactTransformActionScheme
import org.gradle.api.internal.artifacts.transform.ArtifactTransformParameterScheme
import org.gradle.api.internal.artifacts.transform.DefaultTransformer
import org.gradle.api.internal.artifacts.transform.LegacyTransformer
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileLookup
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.withCodec
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.isolation.Isolatable
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.internal.snapshot.impl.IsolatedArray


internal
class DefaultTransformerCodec(
    private val userTypesCodec: Codec<Any?>,
    private val buildOperationExecutor: BuildOperationExecutor,
    private val classLoaderHierarchyHasher: ClassLoaderHierarchyHasher,
    private val isolatableFactory: IsolatableFactory,
    private val valueSnapshotter: ValueSnapshotter,
    private val fileCollectionFactory: FileCollectionFactory,
    private val fileLookup: FileLookup,
    private val parameterScheme: ArtifactTransformParameterScheme,
    private val actionScheme: ArtifactTransformActionScheme
) : Codec<DefaultTransformer> {
    override suspend fun WriteContext.encode(value: DefaultTransformer) {
        writeClass(value.implementationClass)

        // TODO - isolate now and discard node, if isolation is scheduled and has no dependencies
        // Write isolated parameters, if available, and discard the parameters
        if (value.isIsolated) {
            writeBoolean(true)
            writeBinary(value.isolatedParameters.secondaryInputsHash.toByteArray())
            write(value.isolatedParameters.isolatedParameterObject)
        } else {
            writeBoolean(false)
            withCodec(userTypesCodec) { write(value.parameterObject) }
        }

        // TODO - write more state
    }

    override suspend fun ReadContext.decode(): DefaultTransformer? {
        val implementationClass = readClass().asSubclass(TransformAction::class.java)

        val isolated = readBoolean()
        val parametersObject: TransformParameters?
        val isolatedParametersObject: DefaultTransformer.IsolatedParameters?
        if (isolated) {
            parametersObject = null
            val secondaryInputsHash = HashCode.fromBytes(readBinary())
            val isolatedParameters = read() as Isolatable<TransformParameters>
            isolatedParametersObject = DefaultTransformer.IsolatedParameters(isolatedParameters, secondaryInputsHash)
        } else {
            parametersObject = withCodec(userTypesCodec) { read() as TransformParameters? }
            isolatedParametersObject = null
        }
        return DefaultTransformer(
            implementationClass,
            parametersObject,
            isolatedParametersObject,
            ImmutableAttributes.EMPTY,
            AbsolutePathInputNormalizer::class.java,
            AbsolutePathInputNormalizer::class.java,
            false,
            buildOperationExecutor,
            classLoaderHierarchyHasher,
            isolatableFactory,
            valueSnapshotter,
            fileCollectionFactory,
            fileLookup,
            parameterScheme.inspectionScheme.propertyWalker,
            actionScheme.instantiationScheme,
            isolate.owner.service(ServiceRegistry::class.java)
        )
    }
}


internal
class LegacyTransformerCodec(
    private val actionScheme: ArtifactTransformActionScheme
) : Codec<LegacyTransformer> {
    override suspend fun WriteContext.encode(value: LegacyTransformer) {
        writeClass(value.implementationClass)
        writeBinary(value.secondaryInputsHash.toByteArray())
        // TODO - write more state, eg parameters
    }

    override suspend fun ReadContext.decode(): LegacyTransformer? {
        val implementationClass = readClass().asSubclass(ArtifactTransform::class.java)
        val secondaryInputsHash = HashCode.fromBytes(readBinary())
        return LegacyTransformer(
            implementationClass,
            IsolatedArray.EMPTY,
            secondaryInputsHash,
            actionScheme.instantiationScheme,
            ImmutableAttributes.EMPTY
        )
    }
}
