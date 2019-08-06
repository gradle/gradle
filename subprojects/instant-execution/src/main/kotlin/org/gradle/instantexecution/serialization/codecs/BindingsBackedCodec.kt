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

import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext


class BindingsBackedCodec(private val bindings: List<Binding>) : Codec<Any?> {
    private
    val encodings = HashMap<Class<*>, Encoding>()

    override suspend fun WriteContext.encode(value: Any?) = when (value) {
        null -> writeByte(NULL_VALUE)
        else -> {
            val encoding = encodings.computeIfAbsent(value.javaClass, ::computeEncoding)
            encoding(value)
        }
    }

    override suspend fun ReadContext.decode() = when (val tag = readByte()) {
        NULL_VALUE -> null
        else -> bindings[tag.toInt()].codec.run { decode() }
    }

    private
    fun encoding(e: Encoding) = e

    private
    fun computeEncoding(type: Class<*>): Encoding =
        bindings.find { it.type.isAssignableFrom(type) }?.run {
            encoding { value ->
                writeByte(tag)
                codec.run { encode(value) }
            }
        }!!

    internal
    companion object {
        const val NULL_VALUE: Byte = -1
    }
}


typealias Encoding = suspend WriteContext.(value: Any) -> Unit


data class Binding(
    val tag: Byte,
    val type: Class<*>,
    val codec: Codec<Any>
)
