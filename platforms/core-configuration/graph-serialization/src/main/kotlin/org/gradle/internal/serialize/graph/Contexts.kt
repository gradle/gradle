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
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder


interface BeanStateWriterLookup {
    fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter
}


interface BeanStateReaderLookup {
    fun beanStateReaderFor(beanType: Class<*>): BeanStateReader
}


class DefaultWriteContext(

    codec: Codec<Any?>,

    private
    val encoder: Encoder,

    private
    val beanStateWriterLookup: BeanStateWriterLookup,

    override val logger: Logger,

    override val tracer: Tracer?,

    problemsListener: ProblemsListener,

    private
    val classEncoder: ClassEncoder

) : AbstractIsolateContext<WriteIsolate>(codec, problemsListener), WriteContext, Encoder by encoder, AutoCloseable {

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

    override suspend fun write(value: Any?) {
        getCodec().run {
            encode(value)
        }
    }

    override fun writeClass(type: Class<*>) {
        classEncoder.run {
            encodeClass(type)
        }
    }

    // TODO: consider interning strings
    override fun writeString(string: CharSequence) =
        encoder.writeString(string)

    override fun newIsolate(owner: IsolateOwner): WriteIsolate =
        DefaultWriteIsolate(owner)
}


@JvmInline
value class ClassLoaderRole(val local: Boolean)


interface ClassEncoder {
    fun WriteContext.encodeClass(type: Class<*>)
}


interface ClassDecoder {
    fun ReadContext.decodeClass(): Class<*>
}


class DefaultReadContext(
    codec: Codec<Any?>,

    private
    val decoder: Decoder,

    private
    val beanStateReaderLookup: BeanStateReaderLookup,

    override val logger: Logger,

    problemsListener: ProblemsListener,

    private
    val classDecoder: ClassDecoder

) : AbstractIsolateContext<ReadIsolate>(codec, problemsListener), ReadContext, Decoder by decoder, AutoCloseable {

    override val sharedIdentities = ReadIdentities()

    private
    var singletonProperty: Any? = null

    override lateinit var classLoader: ClassLoader

    override fun onFinish(action: () -> Unit) {
        pendingOperations.add(action)
    }

    fun finish() {
        for (op in pendingOperations) {
            op()
        }
        pendingOperations.clear()
    }

    private
    var pendingOperations = ReferenceArrayList<() -> Unit>()

    fun initClassLoader(classLoader: ClassLoader) {
        this.classLoader = classLoader
    }

    override var immediateMode: Boolean = false

    override fun close() {
        (decoder as? AutoCloseable)?.close()
    }

    override suspend fun read(): Any? = getCodec().run {
        decode()
    }

    override fun readClass(): Class<*> = classDecoder.run {
        decodeClass()
    }

    override val isolate: ReadIsolate
        get() = getIsolate()

    override fun beanStateReaderFor(beanType: Class<*>): BeanStateReader =
        beanStateReaderLookup.beanStateReaderFor(beanType)

    /**
     * Sets a client specific property value that can be queried via [getSingletonProperty].
     */
    fun <T : Any> setSingletonProperty(singletonProperty: T) {
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
    problemsListener: ProblemsListener
) : MutableIsolateContext {

    private
    var currentProblemsListener: ProblemsListener = problemsListener

    private
    var currentIsolate: T? = null

    private
    var currentCodec = codec

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

    override suspend fun forIncompatibleType(path: String, action: suspend () -> Unit) {
        val previousListener = currentProblemsListener
        currentProblemsListener = previousListener.forIncompatibleTask(path)
        try {
            action()
        } finally {
            currentProblemsListener = previousListener
        }
    }
}


class DefaultWriteIsolate(override val owner: IsolateOwner) : WriteIsolate {

    override val identities: WriteIdentities = WriteIdentities()
}


class DefaultReadIsolate(override val owner: IsolateOwner) : ReadIsolate {

    override val identities: ReadIdentities = ReadIdentities()
}
