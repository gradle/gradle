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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.instantexecution.serialization.beans.BeanPropertyReader
import org.gradle.instantexecution.serialization.beans.BeanPropertyWriter
import org.gradle.instantexecution.serialization.beans.BeanStateReader
import org.gradle.instantexecution.serialization.beans.BeanStateWriter
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder


internal
class DefaultWriteContext(
    codec: Codec<Any?>,

    private
    val encoder: Encoder,

    override val logger: Logger,

    private
    val problemHandler: (PropertyProblem) -> Unit

) : AbstractIsolateContext<WriteIsolate>(codec), WriteContext, Encoder by encoder {
    override val sharedIdentities = WriteIdentities()

    private
    val beanPropertyWriters = hashMapOf<Class<*>, BeanStateWriter>()

    private
    val classes = hashMapOf<Class<*>, Int>()

    override fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter =
        beanPropertyWriters.computeIfAbsent(beanType, ::BeanPropertyWriter)


    override val isolate: WriteIsolate
        get() = getIsolate()

    override suspend fun write(value: Any?) {
        getCodec().run {
            encode(value)
        }
    }

    override fun writeClass(type: Class<*>) {
        val id = classes[type]
        if (id != null) {
            writeSmallInt(id)
        } else {
            val newId = classes.size
            classes[type] = newId
            writeSmallInt(newId)
            writeString(type.name)
        }
    }

    // TODO: consider interning strings
    override fun writeString(string: CharSequence) =
        encoder.writeString(string)

    override fun newIsolate(owner: IsolateOwner): WriteIsolate =
        DefaultWriteIsolate(owner)

    override fun onProblem(problem: PropertyProblem) {
        problemHandler(problem)
    }
}


interface EncodingProvider<T> {
    suspend fun WriteContext.encode(value: T)
}


internal
class DefaultReadContext(
    codec: Codec<Any?>,

    private
    val decoder: Decoder,

    override val logger: Logger
) : AbstractIsolateContext<ReadIsolate>(codec), ReadContext, Decoder by decoder {

    override val sharedIdentities = ReadIdentities()

    private
    val beanStateReaders = hashMapOf<Class<*>, BeanStateReader>()

    private
    val classes = hashMapOf<Int, Class<*>>()

    private
    lateinit var projectProvider: ProjectProvider

    override lateinit var classLoader: ClassLoader

    internal
    fun initClassLoader(classLoader: ClassLoader) {
        this.classLoader = classLoader
    }

    internal
    fun initProjectProvider(projectProvider: ProjectProvider) {
        this.projectProvider = projectProvider
    }

    override var immediateMode: Boolean = false

    override suspend fun read(): Any? = getCodec().run {
        decode()
    }

    override val isolate: ReadIsolate
        get() = getIsolate()

    override fun beanStateReaderFor(beanType: Class<*>): BeanStateReader =
        beanStateReaders.computeIfAbsent(beanType) { type -> BeanPropertyReader(type) }

    override fun readClass(): Class<*> {
        val id = readSmallInt()
        val type = classes[id]
        if (type != null) {
            return type
        }
        val newType = Class.forName(readString(), false, classLoader)
        classes[id] = newType
        return newType
    }

    override fun getProject(path: String): ProjectInternal =
        projectProvider(path)

    override fun newIsolate(owner: IsolateOwner): ReadIsolate =
        DefaultReadIsolate(owner)

    override fun onProblem(problem: PropertyProblem) {
        // ignore problems
    }
}


interface DecodingProvider<T> {
    suspend fun ReadContext.decode(): T?
}


internal
typealias ProjectProvider = (String) -> ProjectInternal


internal
abstract class AbstractIsolateContext<T>(codec: Codec<Any?>) : MutableIsolateContext {

    private
    var currentIsolate: T? = null

    private
    var currentCodec = codec

    var trace: PropertyTrace = PropertyTrace.Unknown

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

    override fun push(owner: IsolateOwner, codec: Codec<Any?>) {
        contexts.add(0, Pair(currentIsolate, currentCodec))
        currentIsolate = newIsolate(owner)
        currentCodec = codec
    }

    override fun pop() {
        val previousValues = contexts.removeAt(0)
        currentIsolate = previousValues.first
        currentCodec = previousValues.second
    }
}


internal
class DefaultWriteIsolate(override val owner: IsolateOwner) : WriteIsolate {

    override val identities: WriteIdentities = WriteIdentities()
}


internal
class DefaultReadIsolate(override val owner: IsolateOwner) : ReadIsolate {

    override val identities: ReadIdentities = ReadIdentities()
}
