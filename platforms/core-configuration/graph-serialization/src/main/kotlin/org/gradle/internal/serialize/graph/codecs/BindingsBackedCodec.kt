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

package org.gradle.internal.serialize.graph.codecs

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.DecodingProvider
import org.gradle.internal.serialize.graph.EncodingProvider
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SerializerCodec
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.singleton
import org.gradle.internal.serialize.graph.withDebugFrame
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


/**
 * An implementation of the Codec protocol that (based on a [Binding.tag]) chooses and delegates
 * to the proper binding (if one is found).
 *
 * The binding (a tagged codec) is chosen based on the availability of a [Binding.encoding] for the value being encoded.
 * This is basically implemented as a predicate dispatching on the value type, first available [Binding.encoding] wins
 * and its [Binding.tag] is recorded in the output stream so decoding can be implemented via a fast array lookup.
 *
 * @see Binding.tag
 */
class BindingsBackedCodec(private val bindings: List<Binding>) : Codec<Any?> {

    internal
    companion object {
        private
        const val NULL_VALUE: Int = 0
    }

    private
    val nonSingletonBindings = bindings.filterNot(Binding::isSingleton)

    private
    val encodings = ConcurrentHashMap<Class<*>, TaggedEncoding>()

    private
    val singletonEncodings: Map<Any, TaggedEncoding> =
        bindings.filter { it.isSingleton }.map { binding ->
            binding.singleton!! to TaggedEncoding(binding.tag, binding.encodingForSingleton())
        }.toMap(IdentityHashMap())

    override suspend fun WriteContext.encode(value: Any?) = when (value) {
        null -> writeSmallInt(NULL_VALUE)
        else -> taggedEncodingFor(value).run {
            writeSmallInt(tag + 1)
            withDebugFrame({
                // TODO:configuration-cache evaluate whether we need to unpack the type here
                // GeneratedSubclasses.unpackType(value).typeName
                value.javaClass.typeName
            }) {
                encoding.run { encode(value) }
            }
        }
    }

    override suspend fun ReadContext.decode() = when (val tag = readSmallInt()) {
        NULL_VALUE -> null
        else -> bindings[tag - 1].decoding.run { decode() }
    }

    private
    fun taggedEncodingFor(value: Any): TaggedEncoding {
        return taggedEncodingForSingleton(value)
            ?: taggedEncodingForType(value::class.java)
    }

    private fun taggedEncodingForType(type: Class<*>) = encodings.computeIfAbsent(type, ::computeEncoding)

    private fun taggedEncodingForSingleton(value: Any) = singletonEncodings.get(value)?.also {
        println("Encoding for $value: $it")
    }

    private
    fun computeEncoding(type: Class<*>): TaggedEncoding {
        for (binding in nonSingletonBindings) {
            val encoding = binding.encodingFor(type)
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
    val decoding: Decoding,
    val singleton: Any? = null
) {
    fun encodingFor(type: Class<*>) = encoding.encodingFor(type)

    fun encodingForSingleton() = encoding.encodingForValue(singleton!!)!!

    fun withTag(newTag: Int): Binding =
        Binding(newTag, encoding, decoding)

    val isSingleton: Boolean
        get() = singleton != null

    companion object {
        fun forSingleton(tag: Int, instance: Any): Binding =
            singleton(instance).let { singletonCodec ->
                Binding(tag, SingletonEncodingProducer(instance, singletonCodec), singletonCodec, instance)
            }
    }
}


/**
 * An object that can determine, for a given type, whether it can encode instances of that type, and which specific encoding to use.
 */
interface EncodingProducer {

    /**
     * Returns the encoding to use for that type, or null, if not supported.
     */
    fun encodingForType(type: Class<*>): Encoding?

    fun encodingFor(type: Class<*>, value: Any? = null): Encoding? =
        encodingForType(type)

    fun encodingForValue(value: Any): Encoding? =
        encodingForType(value.javaClass)
}

class SingletonEncodingProducer(val singleton: Any, val encoding: Encoding): EncodingProducer {
    override fun encodingForType(type: Class<*>): Encoding = error("Unsupported")

    override fun encodingFor(type: Class<*>, value: Any?): Encoding? =
        value?.let (::encodingForValue)

    override fun encodingForValue(value: Any): Encoding? =
        if (value === singleton) encoding else null
}


typealias Encoding = EncodingProvider<Any>


typealias Decoding = DecodingProvider<Any>


/**
 * An immutable set of bindings, from which a [Codec] can be created.
 */
class Bindings(
    private val bindings: ImmutableList<Binding>
) {
    companion object {
        fun of(builder: BindingsBuilder.() -> Unit) = BindingsBuilder(emptyList()).apply(builder).build()
    }

    /**
     * Builds a new set of bindings based on the current bindings plus any bindings created via the given builder.
     */
    fun append(builder: BindingsBuilder.() -> Unit) = BindingsBuilder(bindings).apply(builder).build()

    fun build() = BindingsBackedCodec(bindings)
}


class BindingsBuilder(initialBindings: List<Binding>) {

    private
    val bindings = ArrayList(initialBindings)

    private
    val singletons = ArrayList<Any>()

    fun build() = singletons.run {
        ImmutableList.copyOf(singletonBindings() + bindings.mapIndexed { index, binding -> binding.withTag(index + size) })
    }.let { Bindings(it) }

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
        require(bindings.all { it.encodingFor(type) == null }) {
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

    fun withSingleton(singleton: Any) {
        singletons.add(singleton)
    }

    private
    fun List<Any>.singletonBindings(): List<Binding> = mapIndexed { index, singleton ->
        Binding.forSingleton(index + bindings.size, singleton)
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
