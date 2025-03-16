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

import it.unimi.dsi.fastutil.objects.ReferenceArrayList
import org.gradle.api.logging.Logger
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.BuildTree::class)
interface BeanStateWriterLookup {
    fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter
}


@ServiceScope(Scope.BuildTree::class)
interface BeanStateReaderLookup {
    fun beanStateReaderFor(beanType: Class<*>): BeanStateReader
}


data class SpecialEncoders(
    val stringEncoder: StringEncoder = InlineStringEncoder,
    val sharedObjectEncoder: SharedObjectEncoder = InlineSharedObjectEncoder
)


data class SpecialDecoders(
    val stringDecoder: StringDecoder = InlineStringDecoder,
    val sharedObjectDecoder: SharedObjectDecoder = InlineSharedObjectDecoder
)


class DefaultWriteContext(
    name: String? = null,

    codec: Codec<Any?>,

    private
    val encoder: Encoder,

    private
    val beanStateWriterLookup: BeanStateWriterLookup,

    override val logger: Logger,

    override val tracer: Tracer?,

    problemsListener: ProblemsListener,

    private
    val classEncoder: ClassEncoder,

    specialEncoders: SpecialEncoders = SpecialEncoders()

) : AbstractIsolateContext<WriteIsolate>(codec, problemsListener, name), CloseableWriteContext, Encoder by encoder {

    val stringEncoder = specialEncoders.stringEncoder

    val sharedObjectEncoder = specialEncoders.sharedObjectEncoder

    override val sharedIdentities = WriteIdentities()

    override val circularReferences = CircularReferences()

    /**
     * Closes the given [encoder] if it is [AutoCloseable].
     */
    override fun close() {
        (encoder as? AutoCloseable)?.close()
    }

    override fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter =
        beanStateWriterLookup.beanStateWriterFor(beanType)

    override val isolate: WriteIsolate
        get() = getIsolate()

    override fun writeString(value: CharSequence) =
        stringEncoder.writeString(encoder, value)

    override fun writeNullableString(value: CharSequence?) =
        stringEncoder.writeNullableString(encoder, value)

    override suspend fun write(value: Any?) {
        getCodec().run {
            encode(value)
        }
    }

    override suspend fun <T : Any> writeSharedObject(value: T, encode: suspend WriteContext.(T) -> Unit) {
        sharedObjectEncoder.run {
            write(this@DefaultWriteContext, value, encode)
        }
    }

    override fun writeClass(type: Class<*>) {
        classEncoder.run {
            encodeClass(type)
        }
    }

    override fun writeClassLoader(classLoader: ClassLoader?) = classEncoder.run {
        encodeClassLoader(classLoader)
    }

    override fun newIsolate(owner: IsolateOwner): WriteIsolate =
        DefaultWriteIsolate(owner)
}


@JvmInline
value class ClassLoaderRole(val local: Boolean)


interface ClassEncoder {
    fun WriteContext.encodeClass(type: Class<*>)

    /**
     * Tries to encode the given [classLoader].
     */
    fun WriteContext.encodeClassLoader(classLoader: ClassLoader?) = Unit
}


interface ClassDecoder {
    fun Decoder.decodeClass(): Class<*>

    /**
     * Decodes a [ClassLoader] previously encoded via [ClassEncoder.encodeClassLoader].
     *
     * @return the previously encoded [ClassLoader] or `null` when [ClassEncoder.encodeClassLoader] returns `false`
     */
    fun Decoder.decodeClassLoader(): ClassLoader? = null
}


interface StringEncoder : AutoCloseable {
    fun writeNullableString(encoder: Encoder, string: CharSequence?)
    fun writeString(encoder: Encoder, string: CharSequence)
}


object InlineStringEncoder : StringEncoder {
    override fun writeNullableString(encoder: Encoder, string: CharSequence?) {
        encoder.writeNullableString(string)
    }

    override fun writeString(encoder: Encoder, string: CharSequence) {
        encoder.writeString(string)
    }

    override fun close() = Unit
}


interface StringDecoder : AutoCloseable {
    fun readNullableString(decoder: Decoder): String?
    fun readString(decoder: Decoder): String
}


object InlineStringDecoder : StringDecoder {
    override fun readNullableString(decoder: Decoder): String? =
        decoder.readNullableString()

    override fun readString(decoder: Decoder): String =
        decoder.readString()

    override fun close() = Unit
}

//TODO-RC consider making the implementations auto-closeable
interface SharedObjectEncoder : AutoCloseable {
    suspend fun <T: Any> write(writeContext: WriteContext, value: T, encode: suspend WriteContext.(T) -> Unit)
}


interface SharedObjectDecoder : AutoCloseable {
    suspend fun <T: Any> read(readContext: ReadContext, decode: suspend ReadContext.() -> T): T
}


object InlineSharedObjectDecoder : SharedObjectDecoder {
    override suspend fun <T: Any> read(readContext: ReadContext, decode: suspend ReadContext.() -> T): T =
        readContext.decode()

    override fun close() = Unit
}


object InlineSharedObjectEncoder : SharedObjectEncoder {
    override suspend fun <T : Any> write(writeContext: WriteContext, value: T, encode: suspend WriteContext.(T) -> Unit) {
        writeContext.encode(value)
    }

    override fun close() = Unit
}


class DefaultReadContext(
    name: String? = null,
    codec: Codec<Any?>,

    private
    val decoder: Decoder,

    private
    val beanStateReaderLookup: BeanStateReaderLookup,

    override val logger: Logger,

    problemsListener: ProblemsListener,

    private
    val classDecoder: ClassDecoder,

    specialDecoders: SpecialDecoders = SpecialDecoders()
) : AbstractIsolateContext<ReadIsolate>(codec, problemsListener, name), CloseableReadContext, Decoder by decoder {

    override val sharedIdentities = ReadIdentities()

    val stringDecoder = specialDecoders.stringDecoder

    val sharedObjectDecoder = specialDecoders.sharedObjectDecoder

    private
    var singletonProperty: Any? = null

    override fun onFinish(action: () -> Unit) {
        pendingOperations.add(action)
    }

    override fun finish() {
        for (op in pendingOperations) {
            op()
        }
        pendingOperations.clear()
    }

    private
    var pendingOperations = ReferenceArrayList<() -> Unit>()

    override var immediateMode: Boolean = false

    override fun close() {
        (decoder as? AutoCloseable)?.close()
    }

    override fun readNullableString(): String? =
        stringDecoder.readNullableString(decoder)

    override fun readString(): String =
        stringDecoder.readString(decoder)

    override suspend fun read(): Any? = getCodec().run {
        decode()
    }

    override suspend fun <T : Any> readSharedObject(decode: suspend ReadContext.() -> T): T =
        sharedObjectDecoder.run {
            read(this@DefaultReadContext, decode)
        }

    override fun readClass(): Class<*> = classDecoder.run {
        decodeClass()
    }

    override fun readClassLoader(): ClassLoader? = classDecoder.run {
        decodeClassLoader()
    }

    override val isolate: ReadIsolate
        get() = getIsolate()

    override fun beanStateReaderFor(beanType: Class<*>): BeanStateReader =
        beanStateReaderLookup.beanStateReaderFor(beanType)

    override fun <T : Any> setSingletonProperty(singletonProperty: T) {
        this.singletonProperty = singletonProperty
    }

    override fun <T : Any> getSingletonProperty(propertyType: Class<T>): T {
        val propertyValue = singletonProperty
        require(propertyValue != null && propertyType.isInstance(propertyValue)) {
            "A singleton property of type $propertyType has not been registered!"
        }
        return propertyValue.uncheckedCast()
    }

    override fun newIsolate(owner: IsolateOwner): ReadIsolate =
        DefaultReadIsolate(owner)
}


abstract class AbstractIsolateContext<T>(
    codec: Codec<Any?>,
    problemsListener: ProblemsListener,
    private val explicitName: String? = null
) : MutableIsolateContext {

    private
    var currentProblemsListener: ProblemsListener = problemsListener

    private
    var currentIsolate: T? = null

    private
    var currentCodec = codec

    override val name: String
        get() = explicitName ?: ""

    override var trace: PropertyTrace = PropertyTrace.Gradle

    protected
    abstract fun newIsolate(owner: IsolateOwner): T

    protected
    fun getIsolate(): T = currentIsolate.let { isolate ->
        require(isolate != null) {
            "`isolate` is only available during Task serialization."
        }
        isolate
    }

    protected
    fun getCodec() = currentCodec

    private
    val contexts = ArrayList<Pair<T?, Codec<Any?>>>()

    override fun push(codec: Codec<Any?>) {
        contexts.add(0, Pair(currentIsolate, currentCodec))
        currentCodec = codec
    }

    override fun push(owner: IsolateOwner) {
        push(owner, currentCodec)
    }

    override fun push(owner: IsolateOwner, codec: Codec<Any?>) {
        push(codec)
        currentIsolate = newIsolate(owner)
    }

    override fun pop() {
        val previousValues = contexts.removeAt(0)
        currentIsolate = previousValues.first
        currentCodec = previousValues.second
    }

    override fun onProblem(problem: PropertyProblem) {
        currentProblemsListener.onProblem(problem)
    }

    override fun onError(error: Exception, message: StructuredMessageBuilder) {
        currentProblemsListener.onError(trace, error, message)
    }

    override suspend fun forIncompatibleTask(trace: PropertyTrace, reason: String, action: suspend () -> Unit) {
        val previousListener = currentProblemsListener
        currentProblemsListener = previousListener.forIncompatibleTask(trace, reason)
        try {
            action()
        } finally {
            currentProblemsListener = previousListener
        }
    }

    override fun toString(): String {
        return "$name ${this::class.simpleName}"
    }
}


class DefaultWriteIsolate(override val owner: IsolateOwner) : WriteIsolate {

    override val identities: WriteIdentities = WriteIdentities()
}


class DefaultReadIsolate(override val owner: IsolateOwner) : ReadIsolate {

    override val identities: ReadIdentities = ReadIdentities()
}
