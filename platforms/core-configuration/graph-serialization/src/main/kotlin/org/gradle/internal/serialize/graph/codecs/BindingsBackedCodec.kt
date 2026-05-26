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
import org.gradle.internal.serialize.graph.CodecLookup
import org.gradle.internal.serialize.graph.DecodingProvider
import org.gradle.internal.serialize.graph.EncodingProvider
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SerializerCodec
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.withDebugFrame
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


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
class BindingsBackedCodec(private val bindings: List<Binding>) : Codec<Any?>, CodecLookup {

    internal
    companion object {
        private
        const val NULL_VALUE: Int = -1

        private
        const val SENTINEL_VALUE: Byte = 0
    }

    // Shared cache for taggedEncodingFor and encodingForType. Hits store the matching
    // TaggedEncoding; misses store the noMatch sentinel so subsequent lookups for the
    // same type stay O(1). taggedEncodingFor turns an observed noMatch into a thrown
    // IllegalArgumentException; encodingForType turns it into a null return.
    private
    val encodings = ConcurrentHashMap<Class<*>, TaggedEncoding>()

    private
    val noMatch: TaggedEncoding = TaggedEncoding(-1, object : Encoding {
        override suspend fun WriteContext.encode(value: Any) = Unit
    })

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
                    onError(e) {
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
                        onError(IllegalArgumentException(
                            "Tag guard mismatch for ${decoding.displayName}: expected <$tag><$SENTINEL_VALUE>, found <$tagGuard><$sentinel>")
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
    fun taggedEncodingFor(type: Class<*>): TaggedEncoding {
        val tagged = encodings.computeIfAbsent(type, ::computeEncoding)
        require(tagged !== noMatch) { "Don't know how to serialize an object of type ${type.name}." }
        return tagged
    }

    /**
     * Returns the encoding registered for the given runtime [type], or null if
     * no binding matches. The lookup mirrors the dispatch used by [encode] —
     * both hits and misses are cached per-type via `computeIfAbsent`, so
     * concurrent callers see consistent, idempotent results.
     *
     * Intended for store-time diagnostics (for example, checking whether the
     * codec that will handle a value declares a [WideningCodec.decodedType]
     * incompatible with the field receiving it). Calling code must cast the
     * returned encoding to `WideningCodec<*>` (or similar) to inspect metadata.
     *
     * Returns [Encoding] rather than [TaggedEncoding] because the tag is a
     * serialization-internal concern — only [encode] needs it, to write the
     * discriminator byte before delegating. Diagnostic callers don't write
     * to the encoder, so the tag would be dead information.
     */
    override fun encodingForType(type: Class<*>): Encoding? {
        val tagged = encodings.computeIfAbsent(type, ::computeEncoding)
        return if (tagged === noMatch) null else tagged.encoding
    }

    private
    fun computeEncoding(type: Class<*>): TaggedEncoding {
        for (binding in bindings) {
            val encoding = binding.encodingForType(type)
            if (encoding != null) {
                return TaggedEncoding(binding.tag, encoding)
            }
        }
        return noMatch
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
