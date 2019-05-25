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

package org.gradle.instantexecution.serialization

import org.gradle.internal.serialize.Serializer
import kotlin.reflect.KClass


internal
inline fun bindings(block: BindingsBuilder.() -> Unit): List<Binding> =
    BindingsBuilder().apply(block).build()


internal
data class Binding(
    val tag: Byte,
    val type: Class<*>,
    val codec: Codec<Any>
)


internal
class BindingsBuilder {

    private
    val bindings = mutableListOf<Binding>()

    fun build(): List<Binding> = bindings.toList()

    fun bind(type: Class<*>, codec: Codec<*>) {
        require(bindings.none { it.type === type })
        val tag = bindings.size
        require(tag < Byte.MAX_VALUE)
        @Suppress("unchecked_cast")
        bindings.add(
            Binding(tag.toByte(), type, codec as Codec<Any>)
        )
    }

    inline fun <reified T> bind(codec: Codec<T>) =
        bind(T::class.java, codec)

    inline fun <reified T> bind(serializer: Serializer<T>) =
        bind(T::class.java, serializer)

    fun bind(type: KClass<*>, codec: Codec<*>) =
        bind(type.java, codec)

    fun bind(type: KClass<*>, serializer: Serializer<*>) =
        bind(type.java, serializer)

    fun bind(type: Class<*>, serializer: Serializer<*>) =
        bind(type, SerializerCodec(serializer))
}
