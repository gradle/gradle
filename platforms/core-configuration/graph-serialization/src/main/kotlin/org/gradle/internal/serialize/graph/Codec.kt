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
}


interface WriteContext : MutableIsolateContext, Encoder {

    val tracer: Tracer?

    val sharedIdentities: WriteIdentities

    val circularReferences: CircularReferences

    override val isolate: WriteIsolate

    fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter

    suspend fun write(value: Any?)

    fun writeClass(type: Class<*>)

    /**
     * @see ClassEncoder.encodeClassLoader
     */
    fun writeClassLoader(classLoader: ClassLoader?): Boolean = false
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

    fun open(frame: String)

    fun close(frame: String)
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

    val logger: Logger

    val isolate: Isolate

    val trace: PropertyTrace

    fun onProblem(problem: PropertyProblem)

    fun onError(error: Exception, message: StructuredMessageBuilder)
}


interface IsolateOwner {
    val delegate: Any
    fun <T> service(type: Class<T>): T
}


inline fun <reified T> IsolateOwner.serviceOf() = service(T::class.java)


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


inline fun <T : Any> WriteContext.encodePreservingIdentityOf(reference: T, encode: WriteContext.(T) -> Unit) {
    encodePreservingIdentityOf(isolate.identities, reference, encode)
}


inline fun <T : Any> WriteContext.encodePreservingSharedIdentityOf(reference: T, encode: WriteContext.(T) -> Unit) =
    encodePreservingIdentityOf(sharedIdentities, reference, encode)


inline fun <T : Any> WriteContext.encodePreservingIdentityOf(identities: WriteIdentities, reference: T, encode: WriteContext.(T) -> Unit) {
    val id = identities.getId(reference)
    if (id != null) {
        writeSmallInt(id)
    } else {
        val newId = identities.putInstance(reference)
        writeSmallInt(newId)
        circularReferences.enter(reference)
        try {
            encode(reference)
        } finally {
            circularReferences.leave(reference)
        }
    }
}


inline fun <T> ReadContext.decodePreservingIdentity(decode: ReadContext.(Int) -> T): T =
    decodePreservingIdentity(isolate.identities, decode)


inline fun <T : Any> ReadContext.decodePreservingSharedIdentity(decode: ReadContext.(Int) -> T): T =
    decodePreservingIdentity(sharedIdentities) { id ->
        decode(id).also {
            sharedIdentities.putInstance(id, it)
        }
    }


inline fun <T> ReadContext.decodePreservingIdentity(identities: ReadIdentities, decode: ReadContext.(Int) -> T): T {
    val id = readSmallInt()
    val previousValue = identities.getInstance(id)
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
            newBean(false).also {
                readStateOf(it)
            }
        }
    }
}


interface BeanStateWriter {
    suspend fun WriteContext.writeStateOf(bean: Any)
}


interface BeanStateReader {

    fun ReadContext.newBeanWithId(generated: Boolean, id: Int) =
        newBean(generated).also {
            isolate.identities.putInstance(id, it)
        }

    fun ReadContext.newBean(generated: Boolean): Any

    suspend fun ReadContext.readStateOf(bean: Any)
}
