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

package org.gradle.configurationcache.serialization.codecs

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.configurationcache.extensions.uncheckedCast

import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.DecodingProvider
import org.gradle.configurationcache.serialization.EncodingProvider
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.SerializerCodec
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.withDebugFrame

import org.gradle.internal.serialize.Serializer

import kotlin.reflect.KClass


class BindingsBackedCodec(private val bindings: List<Binding>) : Codec<Any?> {

    internal
    companion object {
        private
        const val NULL_VALUE: Int = -1
    }

    private
    val encodings = HashMap<Class<*>, TaggedEncoding>()

    override suspend fun WriteContext.encode(value: Any?) = when (value) {
        null -> writeSmallInt(NULL_VALUE)
        else -> taggedEncodingFor(value.javaClass).run {
            writeSmallInt(tag)
            withDebugFrame({ GeneratedSubclasses.unpackType(value).typeName }) {
                encoding.run { encode(value) }
            }
        }
    }

    override suspend fun ReadContext.decode() = when (val tag = readSmallInt()) {
        NULL_VALUE -> null
        else -> bindings[tag].decoding.run { decode() }
    }

    private
    fun taggedEncodingFor(type: Class<*>): TaggedEncoding =
        encodings.computeIfAbsent(type, ::computeEncoding)

    private
    fun computeEncoding(type: Class<*>): TaggedEncoding {
        for (binding in bindings) {
            val encoding = binding.encodingForType(type)
            if (encoding != null) {
                return TaggedEncoding(binding.tag, encoding)
            }
        }
        throw IllegalArgumentException("Don't know how to serialize an object of type ${type.name}.")
    }

    private
    data class TaggedEncoding(
        val tag: Int,
        val encoding: Encoding
    )
}


data class Binding(
    val tag: Int,
    val encoding: EncodingProducer,
    val decoding: Decoding
) {
    fun encodingForType(type: Class<*>) = encoding.encodingForType(type)
}


interface EncodingProducer {

    fun encodingForType(type: Class<*>): Encoding?
}


typealias Encoding = EncodingProvider<Any>


typealias Decoding = DecodingProvider<Any>


/**
 * An immutable set of bindings, from which a [Codec] can be created.
 */
internal
class Bindings(
    private val bindings: ImmutableList<Binding>
) {
    internal
    companion object {
        fun of(builder: BindingsBuilder.() -> Unit) = BindingsBuilder(emptyList()).apply(builder).build()
    }

    fun append(builder: BindingsBuilder.() -> Unit) = BindingsBuilder(bindings).apply(builder).build()

    fun build() = BindingsBackedCodec(bindings)
}


internal
class BindingsBuilder(initialBindings: List<Binding>) {

    private
    val bindings = ArrayList(initialBindings)

    fun build() = Bindings(ImmutableList.copyOf(bindings))

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

    fun bind(type: Class<*>, codec: Codec<*>) {
        require(bindings.all { it.encodingForType(type) == null }) {
            "There's already an encoding for type '$type'"
        }
        val codecForAny = codec.uncheckedCast<Codec<Any>>()
        val encodingProducer = producerForSubtypesOf(type, codecForAny)
        bind(encodingProducer, codecForAny)
    }

    fun <T> bind(codec: T) where T : EncodingProducer, T : Decoding =
        bind(codec, codec)

    fun bind(encodingProducer: EncodingProducer, decoding: Decoding) {
        bindings.add(
            Binding(
                tag = bindings.size,
                encoding = encodingProducer,
                decoding = decoding
            )
        )
    }

    private
    fun producerForSubtypesOf(
        superType: Class<*>,
        codec: Codec<Any>
    ): EncodingProducer = object : EncodingProducer {

        override fun encodingForType(type: Class<*>) =
            codec.takeIf { superType.isAssignableFrom(type) }
    }
}
