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

import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.internal.artifacts.transform.DefaultTransform
import org.gradle.api.internal.artifacts.transform.TransformActionScheme
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileLookup
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readClassOf
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeEnum
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.service.ServiceRegistry


internal
class DefaultTransformCodec(
    private val fileLookup: FileLookup,
    private val actionScheme: TransformActionScheme
) : Codec<DefaultTransform> {

    override suspend fun WriteContext.encode(value: DefaultTransform) {
        encodePreservingSharedIdentityOf(value) {
            writeClass(value.implementationClass)
            write(value.fromAttributes)
            write(value.toAttributes)
            writeEnum(value.inputArtifactNormalizer as InputNormalizer)
            writeEnum(value.inputArtifactDependenciesNormalizer as InputNormalizer)
            writeBoolean(value.isCacheable)
            writeEnum(value.inputArtifactDirectorySensitivity)
            writeEnum(value.inputArtifactDependenciesDirectorySensitivity)
            writeEnum(value.inputArtifactLineEndingNormalization)
            writeEnum(value.inputArtifactDependenciesLineEndingNormalization)
            write(value.isolatedParameters)
            // TODO - isolate now and discard node, if isolation is scheduled but has no dependencies
        }
    }

    override suspend fun ReadContext.decode(): DefaultTransform {
        return decodePreservingSharedIdentity {
            val implementationClass = readClassOf<TransformAction<*>>()
            val fromAttributes = readNonNull<ImmutableAttributes>()
            val toAttributes = readNonNull<ImmutableAttributes>()
            val inputArtifactNormalizer = readEnum<InputNormalizer>()
            val inputArtifactDependenciesNormalizer = readEnum<InputNormalizer>()
            val isCacheable = readBoolean()
            val inputArtifactDirectorySensitivity = readEnum<DirectorySensitivity>()
            val inputArtifactDependenciesDirectorySensitivity = readEnum<DirectorySensitivity>()
            val inputArtifactLineEndingNormalization = readEnum<LineEndingSensitivity>()
            val inputArtifactDependenciesLineEndingNormalization = readEnum<LineEndingSensitivity>()
            val isolatedParameters = readNonNull<CalculatedValueContainer<DefaultTransform.IsolatedParameters, DefaultTransform.IsolateTransformParameters>>()
            DefaultTransform(
                implementationClass,
                isolatedParameters,
                fromAttributes,
                toAttributes,
                inputArtifactNormalizer,
                inputArtifactDependenciesNormalizer,
                isCacheable,
                fileLookup,
                actionScheme.instantiationScheme,
                isolate.owner.service(ServiceRegistry::class.java),
                inputArtifactDirectorySensitivity,
                inputArtifactDependenciesDirectorySensitivity,
                inputArtifactLineEndingNormalization,
                inputArtifactDependenciesLineEndingNormalization
            )
        }
    }
}
