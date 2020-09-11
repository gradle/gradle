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

import org.gradle.api.internal.artifacts.transform.ArtifactTransformActionScheme
import org.gradle.api.internal.artifacts.transform.LegacyTransformer
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readClassOf
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.internal.hash.HashCode
import org.gradle.internal.isolation.Isolatable


internal
class LegacyTransformerCodec(
    private val actionScheme: ArtifactTransformActionScheme
) : Codec<LegacyTransformer> {

    override suspend fun WriteContext.encode(value: LegacyTransformer) {
        writeClass(value.implementationClass)
        writeBinary(value.secondaryInputsHash.toByteArray())
        write(value.fromAttributes)
        write(value.isolatableParameters)
    }

    override suspend fun ReadContext.decode(): LegacyTransformer? {
        @Suppress("deprecation")
        val implementationClass = readClassOf<org.gradle.api.artifacts.transform.ArtifactTransform>()
        val secondaryInputsHash = HashCode.fromBytes(readBinary())
        val fromAttributes = readNonNull<ImmutableAttributes>()
        val parameters = readNonNull<Isolatable<Array<Any>>>()
        return LegacyTransformer(
            implementationClass,
            parameters,
            secondaryInputsHash,
            actionScheme.instantiationScheme,
            fromAttributes
        )
    }
}
