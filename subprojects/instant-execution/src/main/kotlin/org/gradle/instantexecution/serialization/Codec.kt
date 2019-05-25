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

import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder


/**
 * Binary encoding for type [T].
 */
interface Codec<T> {

    fun WriteContext.encode(value: T)

    fun ReadContext.decode(): T?
}


interface WriteContext : IsolateContext, Encoder {

    override val isolate: WriteIsolate

    fun writeActionFor(value: Any?): Encoding?

    fun write(value: Any?) {
        writeActionFor(value)!!(value)
    }
}


typealias Encoding = WriteContext.(value: Any?) -> Unit


interface ReadContext : IsolateContext, Decoder {

    override val isolate: ReadIsolate

    val taskClassLoader: ClassLoader

    fun getProject(path: String): ProjectInternal

    fun read(): Any?
}


interface IsolateContext {

    val logger: Logger

    val isolate: Isolate
}


interface Isolate {

    val owner: Task
}


interface WriteIsolate : Isolate {

    val identities: WriteIdentities
}


interface ReadIsolate : Isolate {

    val identities: ReadIdentities
}


internal
interface MutableIsolateContext {

    fun enterIsolate(owner: Task)

    fun leaveIsolate()
}


internal
inline fun <T : MutableIsolateContext> T.withIsolate(owner: Task, block: T.() -> Unit) {
    enterIsolate(owner)
    try {
        block()
    } finally {
        leaveIsolate()
    }
}


internal
interface MutableWriteContext : WriteContext, MutableIsolateContext


internal
interface MutableReadContext : ReadContext, MutableIsolateContext


internal
abstract class AbstractIsolateContext<T> : MutableIsolateContext {

    private
    var currentIsolate: T? = null

    protected
    abstract fun newIsolate(owner: Task): T

    protected
    fun getIsolate(): T = currentIsolate.let { isolate ->
        require(isolate != null) {
            "`isolate` is only available during Task serialization."
        }
        isolate
    }

    override fun enterIsolate(owner: Task) {
        require(currentIsolate === null)
        currentIsolate = newIsolate(owner)
    }

    override fun leaveIsolate() {
        require(currentIsolate !== null)
        currentIsolate = null
    }
}


interface EncodingProvider {
    fun WriteContext.encodingFor(candidate: Any?): Encoding?
}


interface DecodingProvider {
    fun ReadContext.decode(): Any?
}


internal
class DefaultWriteContext(

    private
    val encodings: EncodingProvider,

    private
    val encoder: Encoder,

    override val logger: Logger

) : AbstractIsolateContext<WriteIsolate>(), MutableWriteContext, Encoder by encoder {

    override val isolate: WriteIsolate
        get() = getIsolate()

    override fun writeActionFor(value: Any?): Encoding? = encodings.run {
        encodingFor(value)
    }

    // TODO: consider interning strings
    override fun writeString(string: CharSequence) =
        encoder.writeString(string)

    override fun newIsolate(owner: Task): WriteIsolate =
        DefaultWriteIsolate(owner)
}


internal
class DefaultReadContext(

    private
    val decoding: DecodingProvider,

    private
    val decoder: Decoder,

    override val logger: Logger

) : AbstractIsolateContext<ReadIsolate>(), MutableReadContext, Decoder by decoder {

    private
    lateinit var projectProvider: ProjectProvider

    private
    lateinit var classLoader: ClassLoader

    internal
    fun initialize(projectProvider: ProjectProvider, classLoader: ClassLoader) {
        this.projectProvider = projectProvider
        this.classLoader = classLoader
    }

    override fun read(): Any? = decoding.run {
        decode()
    }

    override val isolate: ReadIsolate
        get() = getIsolate()

    override val taskClassLoader: ClassLoader
        get() = classLoader

    override fun getProject(path: String): ProjectInternal =
        projectProvider(path)

    override fun newIsolate(owner: Task): ReadIsolate =
        DefaultReadIsolate(owner)
}


internal
typealias ProjectProvider = (String) -> ProjectInternal


internal
class DefaultWriteIsolate(override val owner: Task) : WriteIsolate {

    override val identities: WriteIdentities = WriteIdentities()
}


internal
class DefaultReadIsolate(override val owner: Task) : ReadIsolate {

    override val identities: ReadIdentities = ReadIdentities()
}
