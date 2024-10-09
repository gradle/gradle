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

package org.gradle.internal.configuration.problems

import org.gradle.internal.DisplayName
import org.gradle.internal.cc.impl.problems.JsonWriter
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.configuration.problems.StructuredMessage.Fragment.Reference
import org.gradle.internal.configuration.problems.StructuredMessage.Fragment.Text
import org.gradle.internal.problems.failure.Failure
import org.gradle.problems.Location
import kotlin.reflect.KClass


/**
 * A problem that does not necessarily compromise the execution of the build.
 */
data class PropertyProblem(
    val trace: PropertyTrace,
    val message: StructuredMessage,
    val exception: Throwable? = null,
    /**
     * A failure containing stack tracing information.
     * The failure may be synthetic when the cause of the problem was not an exception.
     */
    val stackTracingFailure: Failure? = null,
    val documentationSection: DocumentationSection? = null
)


// TODO:configuration-cache extract interface and move enum back to :configuration-cache
enum class DocumentationSection(val anchor: String) {
    NotYetImplemented("config_cache:not_yet_implemented"),
    NotYetImplementedSourceDependencies("config_cache:not_yet_implemented:source_dependencies"),
    NotYetImplementedJavaSerialization("config_cache:not_yet_implemented:java_serialization"),
    NotYetImplementedTestKitJavaAgent("config_cache:not_yet_implemented:testkit_build_with_java_agent"),
    NotYetImplementedBuildServiceInFingerprint("config_cache:not_yet_implemented:build_services_in_fingerprint"),
    TaskOptOut("config_cache:task_opt_out"),
    RequirementsBuildListeners("config_cache:requirements:build_listeners"),
    RequirementsDisallowedTypes("config_cache:requirements:disallowed_types"),
    RequirementsExternalProcess("config_cache:requirements:external_processes"),
    RequirementsTaskAccess("config_cache:requirements:task_access"),
    RequirementsSysPropEnvVarRead("config_cache:requirements:reading_sys_props_and_env_vars"),
    RequirementsUseProjectDuringExecution("config_cache:requirements:use_project_during_execution")
}


typealias StructuredMessageBuilder = StructuredMessage.Builder.() -> Unit

const val BACKTICK = '`'


/**
 * @see prefer [BACKTICK] if wrapping strings that may already use single quotes
 */
private
const val SINGLE_QUOTE = '\''


data class StructuredMessage(val fragments: List<Fragment>) {

    /**
     * Renders a message as a string using the given delimiter for symbol references.
     *
     * We conventionally use either [BACKTICK] or [SINGLE_QUOTE] for wrapping symbol references.
     *
     * For the configuration cache report, we should favor [BACKTICK] over [SINGLE_QUOTE] as
     * quoted fragments may already contain single quotes which are used elsewhere.
     */
    fun render(quote: Char = SINGLE_QUOTE) = fragments.joinToString(separator = "") { fragment ->
        when (fragment) {
            is Text -> fragment.text
            is Reference -> "$quote${fragment.name}$quote"
        }
    }

    override fun toString(): String = render()

    sealed class Fragment {

        data class Text(val text: String) : Fragment()

        data class Reference(val name: String) : Fragment()
    }

    companion object {

        fun forText(text: String) = StructuredMessage(listOf(Text(text)))

        fun build(builder: StructuredMessageBuilder) = StructuredMessage(
            Builder().apply(builder).fragments
        )
    }

    class Builder {

        internal
        val fragments = mutableListOf<Fragment>()

        fun text(string: String): Builder = apply {
            fragments.add(Text(string))
        }

        fun reference(name: String): Builder = apply {
            fragments.add(Reference(name))
        }

        fun reference(type: Class<*>): Builder = apply {
            reference(type.name)
        }

        fun reference(type: KClass<*>): Builder = apply {
            reference(type.qualifiedName!!)
        }

        fun message(message: StructuredMessage): Builder = apply {
            fragments.addAll(message.fragments)
        }

        fun build(): StructuredMessage = StructuredMessage(fragments.toList())
    }
}

fun JsonWriter.writeStructuredMessage(message: StructuredMessage) {
    jsonObjectList(message.fragments) { fragment ->
        writeFragment(fragment)
    }
}

fun JsonWriter.writeFragment(fragment: StructuredMessage.Fragment) {
    when (fragment) {
        is Reference -> property("name", fragment.name)
        is Text -> property("text", fragment.text)
    }
}


/**
 * Subtypes are expected to support [PropertyTrace.equals] and [PropertyTrace.hashCode].
 *
 * Subclasses also must provide custom `toString()` implementations,
 * which should invoke [PropertyTrace.asString].
 */
sealed class PropertyTrace {

    object Unknown : PropertyTrace() {
        override fun toString(): String = asString()
        override fun equals(other: Any?): Boolean = other === this
        override fun hashCode(): Int = 0
        override fun describe(builder: StructuredMessage.Builder) {
            builder.text("unknown location")
        }
    }

    object Gradle : PropertyTrace() {
        override fun toString(): String = asString()
        override fun equals(other: Any?): Boolean = other === this
        override fun hashCode(): Int = 1
        override fun describe(builder: StructuredMessage.Builder) {
            builder.text("Gradle runtime")
        }
    }

    @Suppress("DataClassPrivateConstructor")
    data class BuildLogic private constructor(
        val source: DisplayName,
        val lineNumber: Int? = null
    ) : PropertyTrace() {
        constructor(location: Location) : this(location.sourceShortDisplayName, location.lineNumber)
        constructor(userCodeSource: UserCodeSource) : this(userCodeSource.displayName)
        override fun toString(): String = asString()
        override fun describe(builder: StructuredMessage.Builder) {
            with(builder) {
                text(source.displayName)
                lineNumber?.let {
                    text(": line $it")
                }
            }
        }
    }

    data class BuildLogicClass(
        val name: String
    ) : PropertyTrace() {
        override fun toString(): String = asString()
        override fun describe(builder: StructuredMessage.Builder) {
            with(builder) {
                text("class ")
                reference(name)
            }
        }
    }

    data class Task(
        val type: Class<*>,
        val path: String
    ) : PropertyTrace() {
        override fun toString(): String = asString()
        override fun describe(builder: StructuredMessage.Builder) {
            with(builder) {
                text("task ")
                reference(path)
                text(" of type ")
                reference(type.name)
            }
        }
    }

    data class Bean(
        val type: Class<*>,
        val trace: PropertyTrace
    ) : PropertyTrace() {
        override val containingUserCodeMessage: StructuredMessage
            get() = trace.containingUserCodeMessage
        override fun toString(): String = asString()
        override fun describe(builder: StructuredMessage.Builder) {
            with(builder) {
                reference(type.name)
                text(" bean found in ")
            }
        }
    }

    data class Property(
        val kind: PropertyKind,
        val name: String,
        val trace: PropertyTrace
    ) : PropertyTrace() {
        override val containingUserCodeMessage: StructuredMessage
            get() = trace.containingUserCodeMessage
        override fun toString(): String = asString()
        override fun describe(builder: StructuredMessage.Builder) {
            with(builder) {
                text("$kind ")
                reference(name)
                text(" of ")
            }
        }
    }

    data class Project(
        val path: String,
        val trace: PropertyTrace
    ) : PropertyTrace() {
        override val containingUserCodeMessage: StructuredMessage
            get() = trace.containingUserCodeMessage
        override fun toString(): String = asString()
        override fun describe(builder: StructuredMessage.Builder) {
            with(builder) {
                text("project ")
                reference(path)
                text(" in ")
            }
        }
    }

    data class SystemProperty(
        val name: String,
        val trace: PropertyTrace
    ) : PropertyTrace() {
        override val containingUserCodeMessage: StructuredMessage
            get() = trace.containingUserCodeMessage
        override fun toString(): String = asString()
        override fun describe(builder: StructuredMessage.Builder) {
            with(builder) {
                text("system property ")
                reference(name)
                text(" set at ")
            }
        }
    }

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

    abstract override fun toString(): String

    /**
     * The shared logic for implementing `toString()` in subclasses.
     *
     * Renders this trace as a string (including a nested trace if it exists).
     */
    protected
    fun asString(): String = StructuredMessage.Builder().apply {
        sequence.forEach {
            it.describe(this)
        }
    }.build().render(BACKTICK)

    /**
     * Renders this trace using [BACKTICK] for wrapping symbols.
     */
    fun render(): String = asString()

    /**
     * The user code where the problem occurred. User code should generally be some coarse-grained entity such as a plugin or script.
     */
    open val containingUserCode: String
        get() = containingUserCodeMessage.render(BACKTICK)

    open val containingUserCodeMessage: StructuredMessage
        get() = StructuredMessage.Builder().also {
            describe(it)
        }.build()

    abstract fun describe(builder: StructuredMessage.Builder)

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
            is SystemProperty -> trace
            is Project -> trace
            else -> null
        }
}


enum class PropertyKind {
    Field {
        override fun toString() = "field"
    },
    PropertyUsage {
        override fun toString() = "property usage"
    },
    InputProperty {
        override fun toString() = "input property"
    },
    OutputProperty {
        override fun toString() = "output property"
    }
}
