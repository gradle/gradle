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
import org.gradle.instantexecution.DefaultInstantExecution
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.beans.BeanStateReader
import org.gradle.instantexecution.serialization.beans.BeanStateWriter
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import kotlin.reflect.KClass


/**
 * Binary encoding for type [T].
 */
interface Codec<T> : EncodingProvider<T>, DecodingProvider<T>


interface WriteContext : IsolateContext, MutableIsolateContext, Encoder {

    val sharedIdentities: WriteIdentities

    override val isolate: WriteIsolate

    fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter

    suspend fun write(value: Any?)

    fun writeClass(type: Class<*>)
}


interface ReadContext : IsolateContext, MutableIsolateContext, Decoder {

    val sharedIdentities: ReadIdentities

    override val isolate: ReadIsolate

    val classLoader: ClassLoader

    fun beanStateReaderFor(beanType: Class<*>): BeanStateReader

    fun getProject(path: String): ProjectInternal

    /**
     * When in immediate mode, [read] calls are NOT suspending.
     * Useful for bridging with non-suspending serialization protocols such as [java.io.Serializable].
     */
    var immediateMode: Boolean // TODO:instant-execution prevent StackOverflowErrors when crossing protocols

    suspend fun read(): Any?

    fun readClass(): Class<*>
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
                append("Gradle runtime")
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

    private
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

    abstract fun <T> service(type: Class<T>): T

    abstract val delegate: Any

    class OwnerTask(override val delegate: Task) : IsolateOwner() {
        override fun <T> service(type: Class<T>): T = (delegate.project as ProjectInternal).services.get(type)
    }

    class OwnerGradle(override val delegate: Gradle) : IsolateOwner() {
        override fun <T> service(type: Class<T>): T = (delegate as GradleInternal).services.get(type)
    }

    class OwnerHost(override val delegate: DefaultInstantExecution.Host) : IsolateOwner() {
        override fun <T> service(type: Class<T>): T = delegate.getService(type)
    }
}


internal
inline fun <reified T> IsolateOwner.service() =
    service(T::class.java)


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


interface MutableIsolateContext {
    fun push(codec: Codec<Any?>)
    fun push(owner: IsolateOwner, codec: Codec<Any?>)
    fun pop()
}


internal
inline fun <T : ReadContext, R> T.withImmediateMode(block: T.() -> R): R {
    val immediateMode = this.immediateMode
    try {
        this.immediateMode = true
        return block()
    } finally {
        this.immediateMode = immediateMode
    }
}


internal
inline fun <T : MutableIsolateContext, R> T.withIsolate(owner: IsolateOwner, codec: Codec<Any?>, block: T.() -> R): R {
    push(owner, codec)
    try {
        return block()
    } finally {
        pop()
    }
}


internal
inline fun <T : MutableIsolateContext, R> T.withCodec(codec: Codec<Any?>, block: T.() -> R): R {
    push(codec)
    try {
        return block()
    } finally {
        pop()
    }
}


internal
inline fun <T : IsolateContext, R> T.withBeanTrace(beanType: Class<*>, action: () -> R): R =
    withPropertyTrace(PropertyTrace.Bean(beanType, trace)) {
        action()
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
inline fun WriteContext.encodePreservingIdentityOf(reference: Any, encode: WriteContext.(Any) -> Unit) {
    encodePreservingIdentityOf(isolate.identities, reference, encode)
}


internal
inline fun WriteContext.encodePreservingSharedIdentityOf(reference: Any, encode: WriteContext.(Any) -> Unit) =
    encodePreservingIdentityOf(sharedIdentities, reference, encode)


internal
inline fun WriteContext.encodePreservingIdentityOf(identities: WriteIdentities, reference: Any, encode: WriteContext.(Any) -> Unit) {
    val id = identities.getId(reference)
    if (id != null) {
        writeSmallInt(id)
    } else {
        writeSmallInt(identities.putInstance(reference))
        encode(reference)
    }
}


internal
inline fun <T> ReadContext.decodePreservingIdentity(decode: ReadContext.(Int) -> T): T =
    decodePreservingIdentity(isolate.identities, decode)


internal
inline fun <T : Any> ReadContext.decodePreservingSharedIdentity(decode: ReadContext.(Int) -> T): T =
    decodePreservingIdentity(sharedIdentities) { id ->
        decode(id).also {
            sharedIdentities.putInstance(id, it)
        }
    }


internal
inline fun <T> ReadContext.decodePreservingIdentity(identities: ReadIdentities, decode: ReadContext.(Int) -> T): T {
    val id = readSmallInt()
    val previousValue = identities.getInstance(id)
    return when {
        previousValue != null -> previousValue.uncheckedCast()
        else -> decode(id).also {
            require(identities.getInstance(id) === it) {
                "`decode(id)` should register the decoded instance"
            }
        }
    }
}
