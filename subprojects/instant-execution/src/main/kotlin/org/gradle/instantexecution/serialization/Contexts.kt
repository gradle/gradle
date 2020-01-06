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

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.instantexecution.ClassLoaderScopeSpec
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.instantexecution.serialization.beans.BeanPropertyReader
import org.gradle.instantexecution.serialization.beans.BeanPropertyWriter
import org.gradle.instantexecution.serialization.beans.BeanStateReader
import org.gradle.instantexecution.serialization.beans.BeanStateWriter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder


internal
class DefaultWriteContext(
    codec: Codec<Any?>,

    private
    val encoder: Encoder,

    private
    val scopeLookup: ScopeLookup,

    override val logger: Logger,

    private
    val problemHandler: (PropertyProblem) -> Unit

) : AbstractIsolateContext<WriteIsolate>(codec), WriteContext, Encoder by encoder {
    override val sharedIdentities = WriteIdentities()

    private
    val beanPropertyWriters = hashMapOf<Class<*>, BeanStateWriter>()

    private
    val classes = WriteIdentities()

    private
    val scopes = WriteIdentities()

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
        val id = classes.getId(type)
        if (id != null) {
            writeSmallInt(id)
        } else {
            val scope = scopeLookup.scopeFor(type.classLoader)
            val newId = classes.putInstance(type)
            writeSmallInt(newId)
            writeString(type.name)
            if (scope == null) {
                writeBoolean(false)
            } else {
                writeBoolean(true)
                writeScope(scope.first)
                writeBoolean(scope.second.local)
            }
        }
    }

    private
    fun writeScope(scope: ClassLoaderScopeSpec) {
        val id = scopes.getId(scope)
        if (id != null) {
            writeSmallInt(id)
        } else {
            val newId = scopes.putInstance(scope)
            writeSmallInt(newId)
            if (scope.parent != null) {
                writeBoolean(true)
                writeScope(scope.parent)
                writeString(scope.name)
                writeClassPath(scope.localClassPath)
                writeHashCode(scope.localImplementationHash)
                writeClassPath(scope.exportClassPath)
            } else {
                writeBoolean(false)
            }
        }
    }

    private
    fun writeHashCode(hashCode: HashCode?) {
        if (hashCode == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeBinary(hashCode.toByteArray())
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


@Suppress("experimental_feature_warning")
inline class ClassLoaderRole(val local: Boolean)


internal
interface ScopeLookup {
    fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>?
}


internal
class DefaultReadContext(
    codec: Codec<Any?>,

    private
    val decoder: Decoder,

    private
    val instantiatorFactory: InstantiatorFactory,

    private
    val constructors: BeanConstructors,

    override val logger: Logger
) : AbstractIsolateContext<ReadIsolate>(codec), ReadContext, Decoder by decoder {

    override val sharedIdentities = ReadIdentities()

    private
    val beanStateReaders = hashMapOf<Class<*>, BeanStateReader>()

    private
    val classes = ReadIdentities()

    private
    val scopes = ReadIdentities()

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
        beanStateReaders.computeIfAbsent(beanType) { type -> BeanPropertyReader(type, constructors, instantiatorFactory) }

    override fun readClass(): Class<*> {
        val id = readSmallInt()
        val type = classes.getInstance(id)
        if (type != null) {
            return type as Class<*>
        }
        val name = readString()
        val classLoader = if (readBoolean()) {
            val scope = readScope()
            if (readBoolean()) {
                scope.localClassLoader
            } else {
                scope.exportClassLoader
            }
        } else {
            this.classLoader
        }
        val newType = Class.forName(name, false, classLoader)
        classes.putInstance(id, newType)
        return newType
    }

    private
    fun readScope(): ClassLoaderScope {
        val id = readSmallInt()
        val scope = scopes.getInstance(id)
        if (scope != null) {
            return scope as ClassLoaderScope
        }
        val newScope = if (readBoolean()) {
            val parent = readScope()
            val name = readString()
            val localClassPath = readClassPath()
            val localImplementationHash = readHashCode()
            val exportClassPath = readClassPath()
            if (localImplementationHash != null && exportClassPath.isEmpty) {
                parent.createLockedChild(name, localClassPath, localImplementationHash, null)
            } else {
                parent.createChild(name).local(localClassPath).export(exportClassPath).lock()
            }
        } else {
            isolate.owner.service(ClassLoaderScopeRegistry::class.java).coreAndPluginsScope
        }
        Workarounds.maybeSetDefaultStaticStateIn(newScope)
        scopes.putInstance(id, newScope)
        return newScope
    }

    private
    fun readHashCode() = if (readBoolean()) {
        HashCode.fromBytes(readBinary())
    } else {
        null
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

    var trace: PropertyTrace = PropertyTrace.Gradle

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
