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
import org.gradle.instantexecution.serialization.beans.SerializableReadReplaceReader
import org.gradle.instantexecution.serialization.beans.SerializableWriteReplaceWriter
import org.gradle.internal.reflect.ClassInspector
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.Serializable


internal
class DefaultWriteContext(

    private
    val encodings: EncodingProvider,

    private
    val encoder: Encoder,

    override val logger: Logger,

    private
    val problemHandler: (PropertyProblem) -> Unit

) : AbstractIsolateContext<WriteIsolate>(), MutableWriteContext, Encoder by encoder {

    private
    val beanPropertyWriters = hashMapOf<Class<*>, BeanStateWriter>()

    private
    val classes = hashMapOf<Class<*>, Int>()

    override fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter =
        beanPropertyWriters.computeIfAbsent(beanType, this::createWriterFor)

    private
    fun createWriterFor(beanType: Class<*>): BeanStateWriter {
        // When the type is serializable and has a writeReplace() method, then use this method to unpack the state of the object and serialize the result
        if (Serializable::class.java.isAssignableFrom(beanType)) {
            val details = ClassInspector.inspect(beanType)
            val method = details.allMethods.find { it.name == "writeReplace" && it.parameters.isEmpty() }
            if (method != null) {
                return SerializableWriteReplaceWriter(method)
            }
        }
        // Otherwise, serialize the fields of the bean
        return BeanPropertyWriter(beanType)
    }

    override val isolate: WriteIsolate
        get() = getIsolate()

    override suspend fun write(value: Any?) {
        encodings.run {
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


internal
interface EncodingProvider {
    suspend fun WriteContext.encode(candidate: Any?)
}


internal
class DefaultReadContext(

    private
    val decoding: DecodingProvider,

    private
    val decoder: Decoder,

    override val logger: Logger,

    private
    val beanPropertyReaderFactory: (Class<*>) -> BeanPropertyReader

) : AbstractIsolateContext<ReadIsolate>(), MutableReadContext, Decoder by decoder {

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

    override suspend fun read(): Any? = decoding.run {
        decode()
    }

    override val isolate: ReadIsolate
        get() = getIsolate()

    override fun beanStateReaderFor(beanType: Class<*>): BeanStateReader =
        beanStateReaders.computeIfAbsent(beanType, this::createReaderFor)

    private
    fun createReaderFor(beanType: Class<*>): BeanStateReader {
        // When the type is serializable and has a writeReplace() method, then use the corresponding readReplace() method from the placeholder
        if (Serializable::class.java.isAssignableFrom(beanType)) {
            val details = ClassInspector.inspect(beanType)
            val method = details.allMethods.find { it.name == "writeReplace" && it.parameters.isEmpty() }
            if (method != null) {
                return SerializableReadReplaceReader()
            }
        }
        // Otherwise, serialize the fields of the bean
        return beanPropertyReaderFactory(beanType)
    }

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


internal
interface DecodingProvider {
    suspend fun ReadContext.decode(): Any?
}


internal
typealias ProjectProvider = (String) -> ProjectInternal


internal
abstract class AbstractIsolateContext<T> : MutableIsolateContext {

    private
    var currentIsolate: T? = null

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

    override fun enterIsolate(owner: IsolateOwner) {
        require(currentIsolate === null)
        currentIsolate = newIsolate(owner)
    }

    override fun leaveIsolate() {
        require(currentIsolate !== null)
        currentIsolate = null
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
