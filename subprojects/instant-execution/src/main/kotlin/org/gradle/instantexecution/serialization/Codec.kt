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
import kotlin.reflect.KClass


/**
 * Binary encoding for type [T].
 */
interface Codec<T> {

    suspend fun WriteContext.encode(value: T)

    suspend fun ReadContext.decode(): T?
}


interface WriteContext : IsolateContext, Encoder {

    override val isolate: WriteIsolate

    fun beanPropertyWriterFor(beanType: Class<*>): BeanPropertyWriter

    fun writeActionFor(value: Any?): Encoding?

    suspend fun write(value: Any?) {
        writeActionFor(value)!!(value)
    }
}


typealias Encoding = suspend WriteContext.(value: Any?) -> Unit


interface ReadContext : IsolateContext, Decoder {

    override val isolate: ReadIsolate

    val classLoader: ClassLoader

    fun beanPropertyReaderFor(beanType: Class<*>): BeanPropertyReader

    fun getProject(path: String): ProjectInternal

    suspend fun read(): Any?
}


interface IsolateContext {

    val logger: Logger

    val isolate: Isolate

    var trace: PropertyTrace

    fun onProblem(problem: PropertyProblem)
}


sealed class PropertyProblem {

    abstract val trace: PropertyTrace

    abstract val message: StructuredMessage

    /**
     * A problem that does not necessarily compromise the execution of the build.
     */
    data class Warning(
        override val trace: PropertyTrace,
        override val message: StructuredMessage
    ) : PropertyProblem()

    /**
     * A problem that compromises the execution of the build.
     * Instant execution state should be discarded.
     */
    data class Error(
        override val trace: PropertyTrace,
        override val message: StructuredMessage,
        val exception: Throwable
    ) : PropertyProblem()
}


data class StructuredMessage(val fragments: List<Fragment>) {

    override fun toString(): String = fragments.joinToString(separator = "") { fragment ->
        when (fragment) {
            is Fragment.Text -> fragment.text
            is Fragment.Reference -> "'${fragment.name}'"
        }
    }

    sealed class Fragment {

        data class Text(val text: String) : Fragment()

        data class Reference(val name: String) : Fragment()
    }

    companion object {

        fun build(builder: Builder.() -> Unit) = StructuredMessage(
            Builder().apply(builder).fragments
        )
    }

    class Builder {

        internal
        val fragments = mutableListOf<Fragment>()

        fun text(string: String) {
            fragments.add(Fragment.Text(string))
        }

        fun reference(name: String) {
            fragments.add(Fragment.Reference(name))
        }

        fun reference(type: Class<*>) {
            reference(type.name)
        }

        fun reference(type: KClass<*>) {
            reference(type.qualifiedName!!)
        }
    }
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

    override fun toString(): String =
        StringBuilder().apply {
            sequence.forEach {
                appendStringOf(it)
            }
        }.toString()

    private
    fun StringBuilder.appendStringOf(trace: PropertyTrace) {
        when (trace) {
            is Gradle -> {
                append("Gradle state")
            }
            is Property -> {
                append(trace.kind)
                append(" ")
                quoted(trace.name)
                append(" of ")
            }
            is Bean -> {
                quoted(trace.type.name)
                append(" bean found in ")
            }
            is Task -> {
                append("task ")
                quoted(trace.path)
                append(" of type ")
                quoted(trace.type.name)
            }
            is Unknown -> {
                append("unknown property")
            }
        }
    }

    private
    fun StringBuilder.quoted(s: String) {
        append('`')
        append(s)
        append('`')
    }

    val sequence: Sequence<PropertyTrace>
        get() = sequence {
            var trace = this@PropertyTrace
            while (true) {
                yield(trace)
                trace = trace.tail ?: break
            }
        }

    val tail: PropertyTrace?
        get() = when (this) {
            is Bean -> trace
            is Property -> trace
            else -> null
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

    fun <T> service(type: Class<T>): T =
        when (this) {
            is OwnerTask -> (delegate.project as ProjectInternal).services.get(type)
            is OwnerGradle -> (delegate as GradleInternal).services.get(type)
        }

    abstract val delegate: Any

    class OwnerTask(override val delegate: Task) : IsolateOwner()

    class OwnerGradle(override val delegate: Gradle) : IsolateOwner()
}


internal
inline fun <reified T> IsolateOwner.service() =
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
inline fun <T : MutableIsolateContext, R> T.withIsolate(owner: IsolateOwner, block: T.() -> R): R {
    enterIsolate(owner)
    try {
        return block()
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
