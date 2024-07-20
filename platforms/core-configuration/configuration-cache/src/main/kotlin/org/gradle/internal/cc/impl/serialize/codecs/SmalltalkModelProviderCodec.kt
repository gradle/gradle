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

package org.gradle.internal.cc.impl.serialize.codecs

import org.gradle.internal.cc.impl.smalltalk.SmalltalkModelProvider
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf


class SmalltalkModelProviderCodec(

) : Codec<SmalltalkModelProvider<*>> {

    override suspend fun WriteContext.encode(value: SmalltalkModelProvider<*>) {
        if (!value.hasBeenObtained()) {
            // source has **NOT** been used as build logic input:
            // serialize the source
            writeBoolean(true)
            encodeValueSource(value)
        } else {
            // source has been used as build logic input:
            // serialize the value directly as it will be part of the
            // cached state fingerprint.
            // Currently not necessary due to the unpacking that happens
            // to the TypeSanitizingProvider put around the ValueSourceProvider.
            error("build logic input")
        }
    }

    override suspend fun ReadContext.decode(): SmalltalkModelProvider<*>? {
        TODO("Not yet implemented")
    }

    private
    suspend fun WriteContext.encodeValueSource(value: SmalltalkModelProvider<*>) {
        encodePreservingSharedIdentityOf(value) {
            TODO("Not yet implemented")
        }
    }
}

//class SmalltalkModelProviderCodec(
//    private val valueSourceProviderFactory: ValueSourceProviderFactory
//) : Codec<SmalllProvider<*, *>> {
//
//    override suspend fun WriteContext.encode(value: ValueSourceProvider<*, *>) {
//        if (!value.hasBeenObtained()) {
//            // source has **NOT** been used as build logic input:
//            // serialize the source
//            writeBoolean(true)
//            encodeValueSource(value)
//        } else {
//            // source has been used as build logic input:
//            // serialize the value directly as it will be part of the
//            // cached state fingerprint.
//            // Currently not necessary due to the unpacking that happens
//            // to the TypeSanitizingProvider put around the ValueSourceProvider.
//            error("build logic input")
//        }
//    }
//
//    override suspend fun ReadContext.decode(): ValueSourceProvider<*, *> =
//        when (readBoolean()) {
//            true -> decodeValueSource()
//            false -> error("Unexpected boolean value (false) while decoding")
//        }
//
//    private
//    suspend fun WriteContext.encodeValueSource(value: ValueSourceProvider<*, *>) {
//        encodePreservingSharedIdentityOf(value) {
//            value.run {
//                val hasParameters = parametersType != null
//                writeClass(valueSourceType)
//                writeBoolean(hasParameters)
//                if (hasParameters) {
//                    writeClass(parametersType as Class<*>)
//                    write(parameters)
//                }
//            }
//        }
//    }
//
//    private
//    suspend fun ReadContext.decodeValueSource(): ValueSourceProvider<*, *> =
//        decodePreservingSharedIdentity {
//            val valueSourceType = readClass()
//            val hasParameters = readBoolean()
//            val parametersType = if (hasParameters) readClass() else null
//            val parameters = if (hasParameters) read()!! else null
//
//            val provider =
//                valueSourceProviderFactory.instantiateValueSourceProvider<Any, ValueSourceParameters>(
//                    valueSourceType.uncheckedCast(),
//                    parametersType?.uncheckedCast(),
//                    parameters?.uncheckedCast()
//                )
//            provider.uncheckedCast()
//        }
//}
