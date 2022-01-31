/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.problems

import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.internal.DisplayName
import kotlin.reflect.KClass


/**
 * A problem that does not necessarily compromise the execution of the build.
 */
data class PropertyProblem internal constructor(
    val trace: PropertyTrace,
    val message: StructuredMessage,
    val exception: Throwable? = null,
    val documentationSection: DocumentationSection? = null
)


enum class DocumentationSection(val anchor: String) {
    NotYetImplemented("config_cache:not_yet_implemented"),
    NotYetImplementedSourceDependencies("config_cache:not_yet_implemented:source_dependencies"),
    NotYetImplementedJavaSerialization("config_cache:not_yet_implemented:java_serialization"),
    NotYetImplementedTestKitJavaAgent("config_cache:not_yet_implemented:testkit_build_with_java_agent"),
    RequirementsBuildListeners("config_cache:requirements:build_listeners"),
    RequirementsDisallowedTypes("config_cache:requirements:disallowed_types"),
    RequirementsExternalProcess("config_cache:requirements:external_processes"),
    RequirementsTaskAccess("config_cache:requirements:task_access"),
    RequirementsSysPropEnvVarRead("config_cache:requirements:reading_sys_props_and_env_vars"),
    RequirementsUseProjectDuringExecution("config_cache:requirements:use_project_during_execution")
}


typealias StructuredMessageBuilder = StructuredMessage.Builder.() -> Unit


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

        fun build(builder: StructuredMessageBuilder) = StructuredMessage(
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

    class BuildLogic(
        val displayName: DisplayName
    ) : PropertyTrace()

    class BuildLogicClass(
        val name: String
    ) : PropertyTrace()

    class Task(
        val type: Class<*>,
        val path: String
    ) : PropertyTrace()

    class Bean(
        val type: Class<*>,
        val trace: PropertyTrace
    ) : PropertyTrace() {
        override val containingUserCode: String
            get() = trace.containingUserCode
    }

    class Property(
        val kind: PropertyKind,
        val name: String,
        val trace: PropertyTrace
    ) : PropertyTrace() {
        override val containingUserCode: String
            get() = trace.containingUserCode
    }

    override fun toString(): String =
        StringBuilder().apply {
            sequence.forEach {
                appendStringOf(it)
            }
        }.toString()

    /**
     * The user code where the problem occurred. User code should generally be some coarse-grained entity such as a plugin or script.
     */
    open val containingUserCode: String
        get() = StringBuilder().apply {
            appendStringOf(this@PropertyTrace)
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
            is BuildLogic -> {
                append(trace.displayName.displayName)
            }
            is BuildLogicClass -> {
                append("class ")
                quoted(trace.name)
            }
            is Unknown -> {
                append("unknown location")
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


fun UserCodeApplicationContext.location(consumer: String?): PropertyTrace {
    val currentApplication = current()
    return if (currentApplication != null) {
        PropertyTrace.BuildLogic(currentApplication.displayName)
    } else if (consumer != null) {
        PropertyTrace.BuildLogicClass(consumer)
    } else {
        PropertyTrace.Unknown
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
