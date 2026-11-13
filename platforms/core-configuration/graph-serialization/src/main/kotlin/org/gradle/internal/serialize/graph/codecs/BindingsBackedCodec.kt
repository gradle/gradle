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
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.DecodingProvider
import org.gradle.internal.serialize.graph.EncodingProvider
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SerializerCodec
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.withDebugFrame
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass


private
const val CORRUPTED_CACHE_ENTRY = "Corrupted cache entry"


/**
 * An implementation of the Codec protocol that (based on a [Binding.tag]) chooses and delegates
 * to the proper binding (if one is found).
 *
 * The binding (a tagged codec) is chosen based on the availability of a [Binding.encoding] for the value being encoded.
 * This is basically implemented as a predicate dispatching on the value type, first available Binding.encoding wins
 * and its [Binding.tag] is recorded in the output stream so decoding can be implemented via a fast array lookup.
 *
 * @see Binding.tag
 */
class BindingsBackedCodec(
    private val bindings: List<Binding>,
    private val tags: BindingTags = BindingTags()
) : Codec<Any?> {

    init {
        tags.checkCompatibleWith(bindings)
    }

    internal
    companion object {
        private
        const val NULL_VALUE: Int = -1

        private
        const val SENTINEL_VALUE: Byte = 0
    }

    private
    val encodings = ConcurrentHashMap<Class<*>, TaggedEncoding>()

    override suspend fun WriteContext.encode(value: Any?) = when (value) {
        null -> writeSmallInt(NULL_VALUE)
        else -> taggedEncodingFor(value.javaClass).run {
            writeSmallInt(tag)
            withDebugFrame({
                // TODO:configuration-cache evaluate whether we need to unpack the type here
                // GeneratedSubclasses.unpackType(value).typeName
                value.javaClass.typeName
            }, value) {
                encoding.run { encode(value) }
            }
            if (isIntegrityCheckEnabled) {
                writeSmallInt(tag)
                writeByte(SENTINEL_VALUE)
            }
        }
    }

    override suspend fun ReadContext.decode(): Any? {
        val tag = readSmallInt()
        when (tag) {
            NULL_VALUE -> return null
            else -> {
                val binding = try {
                    bindings[tag]
                } catch (e: IndexOutOfBoundsException) {
                    onError(CORRUPTED_CACHE_ENTRY, e) {
                        text("Cannot deserialize the value because the type tag $tag is not in the valid range [-1..${bindings.size}). ")
                        text("The value may have been written incorrectly or its data is corrupted.")
                    }
                    // We may end up here if the errors are suppressed.
                    return null
                }

                val decoding = binding.decoding
                val result = decoding.run { decode() }
                if (isIntegrityCheckEnabled) {
                    val tagGuard = readSmallInt()
                    val sentinel = readByte()

                    if (tag != tagGuard || sentinel != SENTINEL_VALUE) {
                        onError(
                            CORRUPTED_CACHE_ENTRY,
                            IllegalArgumentException(
                                "Tag guard mismatch for ${decoding.displayName}: expected <$tag><$SENTINEL_VALUE>, found <$tagGuard><$sentinel>"
                            )
                        ) {
                            text("The value cannot be decoded properly with ")
                            reference(decoding.displayName)
                            text(". It may have been written incorrectly or its data is corrupted.")
                        }
                    }
                }
                return result
            }
        }
    }

    private
    fun taggedEncodingFor(type: Class<*>): TaggedEncoding =
        encodings.computeIfAbsent(type, ::computeEncoding)

    private
    fun computeEncoding(type: Class<*>): TaggedEncoding {
        val tag = tags.tagFor(type, bindings)
        if (tag == BindingTags.NO_TAG) {
            throw IllegalArgumentException("Don't know how to serialize an object of type ${type.name}.")
        }
        val encoding = checkNotNull(bindings[tag].encodingForType(type)) {
            "Binding $tag was selected for type ${type.name} but does not provide an encoding for it."
        }
        return TaggedEncoding(tag, encoding)
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


/**
 * An object that can determine, for a given type, whether it can encode instances of that type, and which specific encoding to use.
 */
interface EncodingProducer {

    /**
     * Returns the encoding to use for that type, or null, if not supported.
     */
    fun encodingForType(type: Class<*>): Encoding?
}


typealias Encoding = EncodingProvider<Any>


typealias Decoding = DecodingProvider<Any>


/**
 * Remembers, for each type, the [tag][Binding.tag] of the first binding that can encode it.
 *
 * Finding that binding means asking every binding in turn, some of which use reflection, so the result
 * is worth sharing between codecs. A single instance can be shared by codecs whose bindings select the
 * same binding for every type, for example, bindings created by the same code that differ only in
 * the instances of stateful codecs they hold. Only the tag is shared: each codec still gets the
 * [Encoding] from its own binding.
 *
 * Mixing bindings of different structure is detected on a best-effort basis, see [checkCompatibleWith].
 */
class BindingTags {

    internal
    companion object {
        const val NO_TAG: Int = -2
    }

    private
    val tags = ConcurrentHashMap<Class<*>, Int>()

    private
    val structure = AtomicReference<Array<Class<*>>?>()

    /**
     * Ensures the given bindings have the same structure as the bindings this instance was first used with.
     * The structure is approximated by the number of bindings and the types of their decodings.
     */
    internal
    fun checkCompatibleWith(bindings: List<Binding>) {
        val expected = structure.get()
            ?: structureOf(bindings).let { if (structure.compareAndSet(null, it)) it else structure.get()!! }
        require(expected.size == bindings.size && bindings.indices.all { expected[it] === bindings[it].decoding.javaClass }) {
            "Cannot share binding tags between bindings of different structure."
        }
    }

    /**
     * Returns the tag of the first binding in [bindings] that can encode [type], or [NO_TAG] if there is none.
     */
    internal
    fun tagFor(type: Class<*>, bindings: List<Binding>): Int =
        // Not using computeIfAbsent, as encoding producers may need to look up encodings themselves.
        tags[type] ?: computeTag(type, bindings).let { tags.putIfAbsent(type, it) ?: it }

    private
    fun computeTag(type: Class<*>, bindings: List<Binding>): Int {
        for (binding in bindings) {
            if (binding.encodingForType(type) != null) {
                return binding.tag
            }
        }
        return NO_TAG
    }

    private
    fun structureOf(bindings: List<Binding>): Array<Class<*>> =
        Array<Class<*>>(bindings.size) { bindings[it].decoding.javaClass }
}


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
     * Shared by all codecs built from this instance, and used to validate the bindings appended to it.
     */
    private
    val tags = BindingTags()

    /**
     * Builds a new set of bindings based on the current bindings plus any bindings created via the given builder.
     */
    fun append(builder: BindingsBuilder.() -> Unit) = BindingsBuilder(bindings, tags).apply(builder).build()

    fun build() = BindingsBackedCodec(bindings, tags)

    /**
     * Builds a codec that shares the given [tags] with other codecs.
     *
     * Use this when creating equivalent bindings repeatedly, for example, to get fresh instances of stateful codecs,
     * so that the binding for a type is selected only once.
     */
    fun build(tags: BindingTags) = BindingsBackedCodec(bindings, tags)
}


class BindingsBuilder internal constructor(
    initialBindings: List<Binding>,
    private val initialTags: BindingTags?
) {

    constructor(initialBindings: List<Binding>) : this(initialBindings, null)

    private
    val bindings = ArrayList(initialBindings)

    private
    val initialSize = initialBindings.size

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
        require(!hasEncodingFor(type)) {
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
    fun hasEncodingFor(type: Class<*>): Boolean {
        if (initialTags == null) {
            return bindings.any { it.encodingForType(type) != null }
        }
        // The initial bindings are shared by every builder appending to them, so look them up through the shared tags.
        return initialTags.tagFor(type, bindings.subList(0, initialSize)) != BindingTags.NO_TAG ||
            (initialSize until bindings.size).any { bindings[it].encodingForType(type) != null }
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
