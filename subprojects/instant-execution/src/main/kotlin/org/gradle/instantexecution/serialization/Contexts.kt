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
interface EncodingProvider {
    fun WriteContext.encodingFor(candidate: Any?): Encoding?
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

    override lateinit var classLoader: ClassLoader

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

    override fun getProject(path: String): ProjectInternal =
        projectProvider(path)

    override fun newIsolate(owner: Task): ReadIsolate =
        DefaultReadIsolate(owner)
}


internal
interface DecodingProvider {
    fun ReadContext.decode(): Any?
}


internal
typealias ProjectProvider = (String) -> ProjectInternal


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


internal
class DefaultWriteIsolate(override val owner: Task) : WriteIsolate {

    override val identities: WriteIdentities = WriteIdentities()
}


internal
class DefaultReadIsolate(override val owner: Task) : ReadIsolate {

    override val identities: ReadIdentities = ReadIdentities()
}
