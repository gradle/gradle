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

package org.gradle.internal.serialize.graph

import org.gradle.api.logging.Logger
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder


/**
 * Binary encoding for type [T].
 */
interface Codec<T> : EncodingProvider<T>, DecodingProvider<T>


interface EncodingProvider<T> {
    suspend fun WriteContext.encode(value: T)
}


interface DecodingProvider<T> {
    suspend fun ReadContext.decode(): T?

    val displayName: String
        get() = this::class.simpleName ?: "Unknown"
}


interface WriteContext : MutableIsolateContext, Encoder {

    val tracer: Tracer?

    val sharedIdentities: WriteIdentities

    val circularReferences: CircularReferences

    override val isolate: WriteIsolate

    fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter

    suspend fun write(value: Any?)

    suspend fun <T : Any> writeSharedObject(value: T, encode: suspend WriteContext.(T) -> Unit)

    fun writeClass(type: Class<*>)

    /**
     * @see ClassEncoder.encodeClassLoader
     */
    fun writeClassLoader(classLoader: ClassLoader?) = Unit
}


interface CloseableWriteContext : WriteContext, AutoCloseable


fun <I, R> CloseableWriteContext.writeWith(
    argument: I,
    writeOperation: suspend WriteContext.(I) -> R
): R =
    useToRun {
        runWriteOperation {
            writeOperation(argument)
        }
    }


interface Tracer {

    fun open(frame: String, instance: Any?)

    fun close(frame: String, instance: Any?)
}


interface ReadContext : IsolateContext, MutableIsolateContext, Decoder {

    val sharedIdentities: ReadIdentities

    override val isolate: ReadIsolate

    fun beanStateReaderFor(beanType: Class<*>): BeanStateReader

    /**
     * When in immediate mode, [read] calls are NOT suspending.
     * Useful for bridging with non-suspending serialization protocols such as [java.io.Serializable].
     */
    var immediateMode: Boolean // TODO:configuration-cache prevent StackOverflowErrors when crossing protocols

    suspend fun read(): Any?

    suspend fun <T : Any> readSharedObject(decode: suspend ReadContext.() -> T): T

    fun readClass(): Class<*>

    /**
     * @see ClassDecoder.decodeClassLoader
     */
    fun readClassLoader(): ClassLoader? = null

    /**
     * Defers the given [action] until all objects have been read.
     */
    fun onFinish(action: () -> Unit)

    fun <T : Any> getSingletonProperty(propertyType: Class<T>): T
}


interface MutableReadContext : ReadContext {
    /**
     * Sets a client specific property value that can be queried via [getSingletonProperty].
     */
    fun <T : Any> setSingletonProperty(singletonProperty: T)
}


interface CloseableReadContext : MutableReadContext, AutoCloseable {
    fun finish()

}


fun <I, R> CloseableReadContext.readWith(argument: I, readOperation: suspend MutableReadContext.(I) -> R) =
    useToRun {
        runReadOperation {
            readOperation(argument)
        }.also {
            finish()
        }
    }


inline
fun <reified T : Any> ReadContext.getSingletonProperty(): T =
    getSingletonProperty(T::class.java)


suspend fun <T : Any> ReadContext.readNonNull() = read()!!.uncheckedCast<T>()


interface IsolateContext {
    val isIntegrityCheckEnabled: Boolean

    val logger: Logger

    val isolate: Isolate

    val trace: PropertyTrace

    val problemsListener: ProblemsListener

    fun onProblem(problem: PropertyProblem)

    fun onError(error: Exception, message: StructuredMessageBuilder)

    val name: String
        get() = this::class.simpleName!!
}


interface IsolateOwner {
    val delegate: Any
    fun <T : Any> service(type: Class<T>): T
}


inline fun <reified T : Any> IsolateOwner.serviceOf() = service(T::class.java)


interface Isolate {

    val owner: IsolateOwner
}


interface WriteIsolate : Isolate {

    /**
     * Identities of objects that are shared within this isolate only.
     */
    val identities: WriteIdentities
}


interface ReadIsolate : Isolate {

    /**
     * Identities of objects that are shared within this isolate only.
     */
    val identities: ReadIdentities
}


interface MutableIsolateContext : IsolateContext {
    override var trace: PropertyTrace

    fun push(codec: Codec<Any?>)
    fun push(owner: IsolateOwner)
    fun push(owner: IsolateOwner, codec: Codec<Any?>)
    fun pop()

    suspend fun forIncompatibleTask(trace: PropertyTrace, reason: String, action: suspend () -> Unit)
}


inline fun <T : ReadContext, R> T.withImmediateMode(block: T.() -> R): R {
    val immediateMode = this.immediateMode
    try {
        this.immediateMode = true
        return block()
    } finally {
        this.immediateMode = immediateMode
    }
}


inline fun <T : MutableIsolateContext, R> T.withIsolate(owner: IsolateOwner, codec: Codec<Any?>, block: T.() -> R): R {
    push(owner, codec)
    try {
        return block()
    } finally {
        pop()
    }
}


inline fun <T : MutableIsolateContext, R> T.withIsolate(owner: IsolateOwner, block: T.() -> R): R {
    push(owner)
    try {
        return block()
    } finally {
        pop()
    }
}


inline fun <T : MutableIsolateContext, R> T.withCodec(codec: Codec<Any?>, block: T.() -> R): R {
    push(codec)
    try {
        return block()
    } finally {
        pop()
    }
}


inline fun <T : MutableIsolateContext, R> T.withBeanTrace(beanType: Class<*>, action: () -> R): R =
    withPropertyTrace(PropertyTrace.Bean(beanType, trace)) {
        action()
    }


inline fun <T : MutableIsolateContext, R> T.withPropertyTrace(kind: PropertyKind, name: String, action: () -> R): R =
    withPropertyTrace(PropertyTrace.Property(kind, name, trace)) {
        action()
    }


inline fun <T : MutableIsolateContext, R> T.withPropertyTrace(trace: PropertyTrace, block: T.() -> R): R {
    val previousTrace = this.trace
    this.trace = trace
    try {
        return block()
    } finally {
        this.trace = previousTrace
    }
}


/**
 * Encodes [reference] preserving its identity within the current isolate.
 *
 * If [reference] has been seen before in this isolate, only its id is written and [encode] is NOT
 * invoked; otherwise a fresh id is assigned and [encode] is called to write the value's contents.
 * The matching read side is [ReadContext.decodePreservingIdentity].
 *
 * See the explicit-identities overload of [encodePreservingIdentityOf] for proper-usage notes.
 */
inline fun <T : Any> WriteContext.encodePreservingIdentityOf(reference: T, encode: WriteContext.(T) -> Unit) =
    encodePreservingIdentityOf(isolate.identities, reference, encode)


/**
 * Like [WriteContext.encodePreservingIdentityOf] but uses [WriteContext.sharedIdentities] — identity is
 * preserved across all isolates.
 * Use for values that may legitimately appear in multiple isolates (e.g. classes, classloader scopes).
 */
inline fun <T : Any> WriteContext.encodePreservingSharedIdentityOf(reference: T, encode: WriteContext.(T) -> Unit) =
    encodePreservingIdentityOf(sharedIdentities, reference, encode)


/**
 * Encodes [reference] preserving its identity in the given [identities] map.
 *
 * On first encounter a new id is assigned, [reference] is registered immediately (so a cycle
 * inside [encode] resolves to the same id rather than a duplicate), and [encode] writes the
 * contents. On subsequent encounters only the id is written.
 *
 * **Proper usage:** the matching decoder must call [ReadIdentities.putInstance] *before* reading
 * any sub-graph that could transitively reference the value being decoded. Decoders that read
 * non-trivial state before registering the partial instance break under self-references and
 * produce corrupted streams. See [ReadContext.decodePreservingIdentity] for the read-side contract.
 *
 * When the receiving context has [WriteContext.isIntegrityCheckEnabled] set, an extra boolean is
 * written after the id indicating whether this encounter is a back-reference (`true`) or a fresh
 * write (`false`). The matching decoder verifies the flag and fails fast with a clear message if
 * the writer's view of the identity state disagrees with the reader's.
 */
inline fun <T : Any> WriteContext.encodePreservingIdentityOf(
    identities: WriteIdentities,
    reference: T,
    encode: WriteContext.(T) -> Unit
) {
    val id = identities.getId(reference)
    if (id >= 0) {
        writeSmallInt(id)
        if (isIntegrityCheckEnabled) writeBoolean(true)
    } else {
        val newId = identities.putInstance(reference)
        writeSmallInt(newId)
        if (isIntegrityCheckEnabled) writeBoolean(false)
        circularReferences.enter(reference)
        try {
            encode(reference)
        } finally {
            circularReferences.leave(reference)
        }
    }
}


/**
 * Decodes a value previously written with [encodePreservingIdentityOf] within the current
 * isolate's identities.
 *
 * See the explicit-identities overload of [decodePreservingIdentity] for the proper-usage contract.
 */
inline fun <T> ReadContext.decodePreservingIdentity(decode: ReadContext.(Int) -> T): T =
    decodePreservingIdentity(isolate.identities, decode)


/**
 * Decodes a value previously written with [WriteContext.encodePreservingSharedIdentityOf]. The decoded
 * instance is registered with [ReadContext.sharedIdentities] automatically, so callers do not
 * need to call `putInstance` themselves.
 *
 * **Limitation:** registration happens *after* [decode] returns, so this helper cannot handle
 * self-cycles — a sub-read inside [decode] that back-references the value being decoded will
 * trip the circular-reference detection under the integrity check. Use [decodePreservingIdentity]
 * with manual `putInstance` if the decoded graph may reference itself.
 */
inline fun <T : Any> ReadContext.decodePreservingSharedIdentity(decode: ReadContext.(Int) -> T): T =
    decodePreservingIdentity(sharedIdentities) { id ->
        decode(id).also {
            sharedIdentities.putInstance(id, it)
        }
    }


/**
 * Decodes a value previously written with [encodePreservingIdentityOf], looking up identities in
 * the given [identities] map.
 *
 * If the id has already been decoded, the previously-registered instance is returned and [decode]
 * is NOT invoked. Otherwise [decode] is called with the id; it must produce the value AND register
 * it via `identities.putInstance(id, instance)` — the registration is asserted on return.
 *
 * **Proper usage of [decode]:** call `putInstance(id, partialInstance)` *before* reading any
 * sub-graph that could transitively reference the value being decoded. The typical pattern is:
 *
 * ```
 * decodePreservingIdentity { id ->
 *     val partial = createEmptyInstance()
 *     identities.putInstance(id, partial)   // register first
 *     populate(partial)                     // then read children that may reference `partial`
 *     partial
 * }
 * ```
 *
 * Reading children before `putInstance` corrupts the stream when a self-reference is encountered.
 *
 * When the receiving context has [ReadContext.isIntegrityCheckEnabled] set, the encoder has
 * written a boolean flag indicating whether the id is a back-reference. The decoder verifies that
 * the flag matches the current identity state and fails fast with [IllegalStateException]
 * pointing at the buggy codec — instead of letting a stream-corruption error surface later.
 * Note that the writer side must have been running with the integrity check enabled too;
 * mismatched modes corrupt the stream.
 *
 * @throws IllegalArgumentException if [decode] hasn't called `putInstance`
 * @throws IllegalStateException if integrity checks are enabled and the writer's view of the
 *      identity state disagrees with the reader's (back-reference to a not-yet-registered id, or
 *      fresh write to an already-bound id)
 */
inline fun <T> ReadContext.decodePreservingIdentity(
    identities: ReadIdentities,
    decode: ReadContext.(Int) -> T
): T {
    val id = readSmallInt()
    val previousValue = identities.getInstance(id)
    if (isIntegrityCheckEnabled) {
        val reusedExpected = readBoolean()
        if (reusedExpected) {
            // Let's check if the current state of the identities is valid according to what is written in the stream.
            // Our codecs should handle everything users throw at them properly. Invalid identity state is our fault.
            // Let's point the user in a right direction.
            check(previousValue != null) {
                // We have a circular reference and must backfill it, but the reference is not available.
                "Unresolvable circular reference detected when decoding id=$id. An existing instance is expected but none available. " +
                    "This is likely a bug in a Gradle codec. " +
                    "Please report the issue to Gradle's bug tracker."
            }
        } else {
            check(previousValue == null) {
                // We have a fresh id that is followed by a definition, but the id is already used for something.
                "Unexpected reuse of id=$id. The id is already bound to `${previousValue!!.javaClass.name}`. " +
                    "This is likely a bug in a Gradle codec. " +
                    "Please report the issue to Gradle's bug tracker."
            }
        }
    }

    return when {
        previousValue != null -> previousValue.uncheckedCast()
        else -> {
            decode(id).also {
                require(identities.getInstance(id) === it) {
                    "`decode(id)` should register the decoded instance"
                }
            }
        }
    }
}


suspend fun WriteContext.encodeBean(value: Any) {
    val beanType = value.javaClass
    withBeanTrace(beanType) {
        writeClass(beanType)
        beanStateWriterFor(beanType).run {
            writeStateOf(value)
        }
    }
}


suspend fun ReadContext.decodeBean(): Any {
    val beanType = readClass()
    return withBeanTrace(beanType) {
        beanStateReaderFor(beanType).run {
            newBean().also {
                readStateOf(it)
            }
        }
    }
}


interface BeanStateWriter {
    suspend fun WriteContext.writeStateOf(bean: Any)
}


interface BeanStateReader {

    fun ReadContext.newBeanWithId(id: Int) =
        newBean().also {
            isolate.identities.putInstance(id, it)
        }

    fun ReadContext.newBean(): Any

    suspend fun ReadContext.readStateOf(bean: Any)
}
