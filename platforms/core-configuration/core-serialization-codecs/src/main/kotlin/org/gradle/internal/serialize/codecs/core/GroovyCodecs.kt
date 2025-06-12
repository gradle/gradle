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

package org.gradle.internal.serialize.codecs.core

import groovy.lang.Closure
import groovy.lang.GroovyObjectSupport
import groovy.lang.MetaClass
import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import groovy.lang.Script
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.groovy.scripts.BasicScript
import org.gradle.internal.configuration.problems.DocumentationSection.RequirementsGradleModelTypes
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.metaobject.ConfigureDelegate
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.BindingsBuilder
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.serviceOf
import org.gradle.internal.serialize.graph.writeEnum


fun BindingsBuilder.groovyCodecs() {
    bind(ClosureCodec)
    bind(GroovyMetaClassCodec)
}


internal
object ClosureCodec : Codec<Closure<*>> {
    override suspend fun WriteContext.encode(value: Closure<*>) {
        // Write the owning script for the closure
        // Discard the delegate, this will be replaced by the caller
        writeReference(findOwningScript(value))
        writeReference(value.thisObject)
        encodeBean(value.dehydrate())
    }

    override suspend fun ReadContext.decode(): Closure<*> {
        val owner = readReference()
        val thisObject = readReference()

        return (decodeBean() as Closure<*>).rehydrate(null, owner, thisObject)
    }

    /**
     * Travels up the 'owner' chain of a closure to locate the script that the closure belongs to, if any
     */
    private
    fun findOwningScript(value: Any): Any? {
        return when (value) {
            is org.gradle.api.Script -> value
            is ConfigureDelegate -> value._original_owner()?.let { findOwningScript(it) }
            is Closure<*> -> value.owner?.let { findOwningScript(it) }
            else -> null
        }
    }

    private
    fun WriteContext.writeReference(value: Any?) {
        // Cannot warn about a script reference here, because we don't know whether the closure will attempt to use the script object when it executes,
        // and since almost every closure in a Groovy build script legitimately has the script as an owner, this will generate false problems.
        // So instead, warn when the script object is used by the closure when executing
        writeEnum(
            when {
                value is BasicScript && value.scriptTarget is Project -> ClosureReference.Project
                value is BasicScript && value.scriptTarget is Settings -> ClosureReference.Settings
                value is BasicScript && value.scriptTarget is Gradle -> ClosureReference.Init
                else -> ClosureReference.NotScript
            }
        )
    }

    private
    fun ReadContext.readReference(): Any =
        when (readEnum<ClosureReference>()) {
            ClosureReference.Project -> BrokenScript(Project::class.java, trace, problemsFactory(), problemsListener)
            ClosureReference.Settings -> BrokenScript(Settings::class.java, trace, problemsFactory(), problemsListener)
            ClosureReference.Init -> BrokenScript(Gradle::class.java, trace, problemsFactory(), problemsListener)
            ClosureReference.NotScript -> BrokenObject
        }

    private
    fun ReadContext.problemsFactory(): ProblemFactory =
        isolate.owner.serviceOf<ProblemFactory>()

    private
    enum class ClosureReference {
        Project, Settings, Init, NotScript
    }

    private
    object BrokenObject : GroovyObjectSupport()

    private
    class BrokenScript(
        targetType: Class<*>,
        private val trace: PropertyTrace,
        private val problemFactory: ProblemFactory,
        private val problemsListener: ProblemsListener
    ) : Script() {
        private
        val targetMetadata = ThreadSafeMetaClassWrapper(targetType)

        override fun run(): Any {
            scriptReferenced(invocationDescription = "Script.run")
        }

        override fun getProperty(propertyName: String): Any {
            // When the closure or a nested closure uses 'owner first' resolution strategy, the closure will attempt to locate the property on this object before trying to locate it on
            // the delegate. So, only treat references to `Project` properties as a problem and throw 'missing property' exception for anything unknown so that the closure can continue
            // with the delegate
            if (targetMetadata.hasProperty(null, propertyName) == null) {
                if (propertyName == "class") {
                    return javaClass
                }
                throw MissingPropertyException(propertyName)
            }
            scriptReferenced(invocationDescription = propertyName)
        }

        override fun setProperty(propertyName: String, newValue: Any?) {
            // See above for why this check happens
            if (targetMetadata.hasProperty(null, propertyName) == null) {
                throw MissingPropertyException(propertyName)
            }
            scriptReferenced(invocationDescription = propertyName)
        }

        override fun invokeMethod(name: String, args: Any): Any {
            // See above for why this check happens
            if (targetMetadata.respondsTo(null, name).isEmpty()) {
                throw MissingMethodException(name, targetMetadata.targetType, arrayOf())
            }
            scriptReferenced(invocationDescription = name)
        }

        private
        fun scriptReferenced(invocationDescription: String): Nothing {
            val exceptionMessage =
                "Invocation of '$invocationDescription' references a Gradle script object from a Groovy closure at execution time, which is unsupported with the configuration cache."

            val problem = problemFactory.problem {
                text("invocation of ")
                reference(invocationDescription)
                text(" references a Gradle script object from a Groovy closure at execution time, which is unsupported with the configuration cache.")
            }
                .exception(exceptionMessage)
                .documentationSection(RequirementsGradleModelTypes)
                .mapLocation { trace }
                .build()

            problemsListener.onExecutionTimeProblem(problem)

            // We normally fail immediately on execution-time problems, except when in the warning mode.
            // However, even in the warning mode, we don't have a reasonable way of proceeding in this situation
            // so we make sure to throw
            throw problem.exception ?: InvalidUserCodeException(exceptionMessage)
        }
    }
}


internal
object GroovyMetaClassCodec : Codec<MetaClass> {
    override suspend fun WriteContext.encode(value: MetaClass) {
        writeClass(value.theClass)
    }

    override suspend fun ReadContext.decode(): MetaClass? {
        return InvokerHelper.getMetaClass(readClass())
    }
}


/**
 * MetaClass implementations in Groovy (at least, in Groovy 4) are not fully thread-safe.
 * This wrapper adds the necessary level of thread-safety for concurrent property lookups.
 *
 * This can be removed after updating to a thread-safe version of Groovy runtime.
 */
@JvmInline
private value class ThreadSafeMetaClassWrapper private constructor(
    private val metaClass: MetaClass
) {
    constructor(cls: Class<*>) : this(synchronized(cls) { InvokerHelper.getMetaClass(cls) })

    val targetType: Class<*>
        get() = metaClass.theClass

    fun hasProperty(obj: Any?, propertyName: String) = synchronized(metaClass) { metaClass.hasProperty(obj, propertyName) }

    fun respondsTo(obj: Any?, name: String) = synchronized(metaClass) { metaClass.respondsTo(obj, name) }
}
