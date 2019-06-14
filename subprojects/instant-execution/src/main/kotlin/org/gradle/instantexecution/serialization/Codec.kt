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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.instantexecution.serialization.beans.BeanPropertyReader
import org.gradle.instantexecution.serialization.beans.BeanPropertyWriter
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

    fun beanPropertyWriterFor(beanType: Class<*>): BeanPropertyWriter

    fun writeActionFor(value: Any?): Encoding?

    fun write(value: Any?) {
        writeActionFor(value)!!(value)
    }
}


typealias Encoding = WriteContext.(value: Any?) -> Unit


interface ReadContext : IsolateContext, Decoder {

    override val isolate: ReadIsolate

    val classLoader: ClassLoader

    fun beanPropertyReaderFor(beanType: Class<*>): BeanPropertyReader

    fun getProject(path: String): ProjectInternal

    fun read(): Any?
}


interface IsolateContext {

    val logger: Logger

    val isolate: Isolate

    var trace: PropertyTrace
}


sealed class PropertyTrace {

    object Unknown : PropertyTrace()

    object Gradle : PropertyTrace()

    class Task(
        val type: Class<*>,
        val path: String
    ) : PropertyTrace()

    class Bean(
        val type: Class<*>,
        val trace: PropertyTrace
    ) : PropertyTrace()

    class Property(
        val kind: PropertyKind,
        val name: String,
        val trace: PropertyTrace
    ) : PropertyTrace()

    override fun toString(): String = when (this) {
        is Gradle -> "Gradle state"
        is Property -> "$kind `$name` of $trace"
        is Bean -> "`${type.name}` bean found in $trace"
        is Task -> "task `$path` of type `${type.name}`"
        is Unknown -> "unknown property"
    }
}


enum class PropertyKind {
    Field {
        override fun toString() = "field"
    },
    InputProperty {
        override fun toString() = "input property"
    },
    OutputProperty {
        override fun toString() = "output property"
    }
}


sealed class IsolateOwner {

    abstract val delegate: Any

    fun <T> service(type: Class<T>): T =
        when (this) {
            is OwnerTask -> (delegate.project as ProjectInternal).services.get(type)
            is OwnerGradle -> (delegate as GradleInternal).services.get(type)
        }

    class OwnerTask(override val delegate: Task) : IsolateOwner()
    class OwnerGradle(override val delegate: Gradle) : IsolateOwner()
}


internal
inline
fun <reified T> IsolateOwner.service() =
    service(T::class.java)


interface Isolate {

    val owner: IsolateOwner
}


interface WriteIsolate : Isolate {

    val identities: WriteIdentities
}


interface ReadIsolate : Isolate {

    val identities: ReadIdentities
}


internal
interface MutableIsolateContext {

    fun enterIsolate(owner: IsolateOwner)

    fun leaveIsolate()
}


internal
inline fun <T : MutableIsolateContext> T.withIsolate(owner: IsolateOwner, block: T.() -> Unit) {
    enterIsolate(owner)
    try {
        block()
    } finally {
        leaveIsolate()
    }
}


internal
inline fun <T : IsolateContext, R> T.withPropertyTrace(trace: PropertyTrace, block: T.() -> R): R {
    val previousTrace = this.trace
    this.trace = trace
    try {
        return block()
    } finally {
        this.trace = previousTrace
    }
}


internal
interface MutableWriteContext : WriteContext, MutableIsolateContext


internal
interface MutableReadContext : ReadContext, MutableIsolateContext
