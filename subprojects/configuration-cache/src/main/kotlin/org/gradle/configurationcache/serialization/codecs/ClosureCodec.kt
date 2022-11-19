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

package org.gradle.configurationcache.serialization.codecs

import groovy.lang.Closure
import groovy.lang.GroovyObjectSupport
import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.Project
import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.StructuredMessage
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.IsolateContext
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.internal.metaobject.ConfigureDelegate


object ClosureCodec : Codec<Closure<*>> {
    override suspend fun WriteContext.encode(value: Closure<*>) {
        writeReference(findRootOwner(value))
        writeReference(value.thisObject)
        BeanCodec.run { encode(value.dehydrate()) }
    }

    private
    fun findRootOwner(value: Any): Any? {
        return when (value) {
            is org.gradle.api.Script -> {
                value
            }

            is ConfigureDelegate -> {
                findRootOwner(value._original_owner())
            }

            is Closure<*> -> {
                findRootOwner(value.owner)
            }

            else -> {
                null
            }
        }
    }

    private
    suspend fun WriteContext.writeReference(value: Any?) {
        when (value) {
            is org.gradle.api.Script -> {
                // Cannot warn about an unsupported type here, because we don't know whether the closure will attempt to use the script object
                // and almost every closure in a Groovy build script legitimately has the script as an owner
                // So instead, warn when the script object is used by the closure when executing
                write(ScriptReference())
            }

            else -> {
                // Discard the value for now
                write(null)
            }
        }
    }

    override suspend fun ReadContext.decode(): Closure<*> {
        val owner = readReference()
        val thisObject = readReference()
        return BeanCodec.run {
            decode() as Closure<*>
        }.rehydrate(null, owner, thisObject)
    }

    private
    suspend fun ReadContext.readReference(): Any? {
        val reference = read()
        val trace = trace
        return if (reference is ScriptReference) {
            BrokenScript(trace, this)
        } else {
            reference
        }
    }

    private
    class ScriptReference

    private
    class BrokenScript(private val trace: PropertyTrace, private val context: IsolateContext) : GroovyObjectSupport() {
        private
        val projectMetadata = InvokerHelper.getMetaClass(Project::class.java)

        override fun getProperty(propertyName: String): Any? {
            if (projectMetadata.hasProperty(null, propertyName) == null) {
                throw MissingPropertyException(propertyName)
            }
            reportReference()
            throw IllegalStateException("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }

        override fun setProperty(propertyName: String, newValue: Any?) {
            if (projectMetadata.hasProperty(null, propertyName) == null) {
                throw MissingPropertyException(propertyName)
            }
            reportReference()
            throw IllegalStateException("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }

        override fun invokeMethod(name: String, args: Any): Any {
            if (projectMetadata.respondsTo(null, name).isEmpty()) {
                throw MissingMethodException(name, Project::class.java, arrayOf())
            }
            reportReference()
            throw IllegalStateException("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }

        private
        fun reportReference() {
            context.onProblem(PropertyProblem(
                trace,
                StructuredMessage.build {
                    text("cannot reference a Gradle script object from a Groovy closure as these are not support with the configuration cache.")
                },
                null,
                DocumentationSection.RequirementsDisallowedTypes
            ))
        }
    }
}
