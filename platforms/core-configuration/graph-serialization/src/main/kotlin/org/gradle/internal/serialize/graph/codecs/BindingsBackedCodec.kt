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
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SerializerCodec
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.withDebugFrame
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass


private
const val CORRUPTED_CACHE_ENTRY = "Corrupted cache entry"


/**
 * An implementation of the Codec protocol that (based on a [Binding.tag]) chooses and delegates
 * to the proper binding (if one is found).
 *
 * The binding (a tagged codec) is chosen by the type of the value being encoded.
 * This is basically implemented as a predicate dispatching on the value type, the first binding that [accepts][Binding.accepts]
 * the type wins and its [Binding.tag] is recorded in the output stream so decoding can be implemented via a fast array lookup.
 * The chosen binding is cached per type.
 *
 * @see Binding.tag
 */
class BindingsBackedCodec internal constructor(
    private val bindings: List<Binding>,
    private val bindingTagsCache: ConcurrentHashMap<Class<*>, Int>
) : Codec<Any?> {

    internal
    companion object {
        private
        const val NULL_VALUE: Int = -1

        private
        const val SENTINEL_VALUE: Byte = 0
    }

    override suspend fun WriteContext.encode(value: Any?) = when (value) {
        null -> writeSmallInt(NULL_VALUE)
        else -> bindingFor(value.javaClass).run {
            writeSmallInt(tag)
            withDebugFrame({
                // TODO:configuration-cache evaluate whether we need to unpack the type here
                // GeneratedSubclasses.unpackType(value).typeName
                value.javaClass.typeName
            }, value) {
                codec.run { encode(value) }
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

                val codec = binding.codec
                val result = codec.run { decode() }
                if (isIntegrityCheckEnabled) {
                    val tagGuard = readSmallInt()
                    val sentinel = readByte()

                    if (tag != tagGuard || sentinel != SENTINEL_VALUE) {
                        onError(
                            CORRUPTED_CACHE_ENTRY,
                            IllegalArgumentException(
                                "Tag guard mismatch for ${codec.displayName}: expected <$tag><$SENTINEL_VALUE>, found <$tagGuard><$sentinel>"
                            )
                        ) {
                            text("The value cannot be decoded properly with ")
                            reference(codec.displayName)
                            text(". It may have been written incorrectly or its data is corrupted.")
                        }
                    }
                }
                return result
            }
        }
    }

    private
    fun bindingFor(type: Class<*>): Binding =
        bindingTagsCache.get(type)?.let { tag -> bindings.get(tag) }
            ?: findBinding(type).also { bindingTagsCache.put(type, it.tag) }

    private
    fun findBinding(type: Class<*>): Binding =
        bindings.firstOrNull { it.accepts(type) }
            ?: throw IllegalArgumentException("Don't know how to serialize an object of type ${type.name}.")
}


data class Binding(
    val tag: Int,
    val accepts: (Class<*>) -> Boolean,
    val codec: Codec<Any>
) : BindingsBlueprint.Entry


/**
 * A codec that handles only some types, as determined by [accepts].
 *
 * The decision must depend only on the type, never on the value or on the context,
 * and must not change during the lifetime of the codec, so it can be cached per type.
 */
interface SelectiveCodec : Codec<Any> {

    /**
     * Returns whether this codec can encode instances of [type].
     */
    fun accepts(type: Class<*>): Boolean
}


/**
 * A typed placeholder in a [BindingsBlueprint] for a codec that is provided anew for every [BindingsBackedCodec].
 *
 * The slot determines which types its binding encodes, so the blueprint can be validated once,
 * while the codec instance, which may be stateful, is only provided when the [BindingsBackedCodec] is built.
 *
 * @see BindingsBlueprint.build
 */
class BindingSlot<T : Any>(val type: Class<T>) : BindingsBlueprint.Entry {
    override fun toString(): String = "slot for ${type.name}"
}


inline fun <reified T : Any> bindingSlot(): BindingSlot<T> = BindingSlot(T::class.java)


/**
 * An immutable set of bindings, from which a [Codec] can be created.
 *
 * The blueprint may contain [slots][BindingSlot] that must be filled with codec instances to build a codec.
 */
class BindingsBlueprint internal constructor(
    private val entries: ImmutableList<Entry>
) {
    companion object {
        fun of(builder: BindingsBuilder.() -> Unit) = BindingsBuilder().apply(builder).build()
    }

    private val bindingTagsCache = ConcurrentHashMap<Class<*>, Int>()

    /**
     * An entry of the blueprint: either a complete [Binding] or a [BindingSlot] to be filled.
     */
    sealed interface Entry

    /**
     * Builds a codec from a blueprint without slots.
     */
    fun build(): BindingsBackedCodec = build {}

    /**
     * Builds a codec, filling all slots of this blueprint with the codecs provided by [filler].
     */
    fun build(filler: BindingSlotFiller.() -> Unit): BindingsBackedCodec {
        val slotCodecs = BindingSlotFiller(HashMap()).apply(filler).codecs
        var filledSlots = 0
        val bindings = entries.mapIndexed { tag, entry ->
            when (entry) {
                is Binding -> entry
                is BindingSlot<*> -> {
                    val codec = requireNotNull(slotCodecs[entry]) {
                        "Cannot build a codec with an unfilled $entry"
                    }
                    filledSlots++
                    Binding(tag, entry.type::isAssignableFrom, codec)
                }
            }
        }
        require(filledSlots == slotCodecs.size) {
            "Slots are not part of the blueprint: ${slotCodecs.keys.filter { it !in entries }}"
        }
        return BindingsBackedCodec(bindings, bindingTagsCache)
    }
}


/**
 * Fills [slots][BindingSlot] of a [BindingsBlueprint] with codec instances.
 */
@JvmInline
value class BindingSlotFiller internal constructor(
    internal val codecs: MutableMap<BindingSlot<*>, Codec<Any>>
) {
    fun <T : Any> fill(slot: BindingSlot<T>, codec: Codec<T>) {
        require(codec !is SelectiveCodec) {
            "Codec '$codec' for the $slot is a SelectiveCodec, but the slot type alone determines which values it encodes"
        }
        require(codecs.putIfAbsent(slot, codec.uncheckedCast()) == null) {
            "The $slot is already filled"
        }
    }
}


class BindingsBuilder internal constructor() {

    private
    val entries = ArrayList<BindingsBlueprint.Entry>()

    fun build() = BindingsBlueprint(ImmutableList.copyOf(entries))

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
        require(codec !is SelectiveCodec) {
            "Codec '$codec' for type '$type' is a SelectiveCodec, bind it with bind(SelectiveCodec) instead"
        }
        requireNoEncodingFor(type)
        bind(type::isAssignableFrom, codec.uncheckedCast())
    }

    /**
     * Reserves a binding for the [slot]'s type, to be filled with a codec instance when building a codec.
     *
     * @see BindingsBlueprint.build
     */
    fun bind(slot: BindingSlot<*>) {
        require(slot !in entries) {
            "The $slot is already bound"
        }
        requireNoEncodingFor(slot.type)
        entries.add(slot)
    }

    fun bind(codec: SelectiveCodec) =
        bind(codec::accepts, codec)

    private
    fun bind(accepts: (Class<*>) -> Boolean, codec: Codec<Any>) {
        entries.add(
            Binding(
                tag = entries.size,
                accepts = accepts,
                codec = codec
            )
        )
    }

    private
    fun requireNoEncodingFor(type: Class<*>) {
        require(entries.none { it.encodes(type) }) {
            "There's already an encoding for type '$type'"
        }
    }

    private
    fun BindingsBlueprint.Entry.encodes(type: Class<*>): Boolean = when (this) {
        is Binding -> accepts(type)
        is BindingSlot<*> -> this.type.isAssignableFrom(type)
    }
}
