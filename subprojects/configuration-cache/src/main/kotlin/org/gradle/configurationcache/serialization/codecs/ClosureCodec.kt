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
import groovy.lang.Script
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodeBean
import org.gradle.configurationcache.serialization.encodeBean
import org.gradle.configurationcache.serialization.readEnum
import org.gradle.configurationcache.serialization.writeEnum
import org.gradle.groovy.scripts.BasicScript
import org.gradle.internal.metaobject.ConfigureDelegate


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
            is ConfigureDelegate -> findOwningScript(value._original_owner())
            is Closure<*> -> findOwningScript(value.owner)
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
            ClosureReference.Project -> BrokenScript(Project::class.java)
            ClosureReference.Settings -> BrokenScript(Settings::class.java)
            ClosureReference.Init -> BrokenScript(Gradle::class.java)
            ClosureReference.NotScript -> BrokenObject
        }

    private
    enum class ClosureReference {
        Project, Settings, Init, NotScript
    }

    private
    object BrokenObject : GroovyObjectSupport()

    private
    class BrokenScript(targetType: Class<*>) : Script() {
        private
        val targetMetadata = InvokerHelper.getMetaClass(targetType)

        override fun run(): Any {
            scriptReferenced()
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
            scriptReferenced()
        }

        override fun setProperty(propertyName: String, newValue: Any?) {
            // See above for why this check happens
            if (targetMetadata.hasProperty(null, propertyName) == null) {
                throw MissingPropertyException(propertyName)
            }
            scriptReferenced()
        }

        override fun invokeMethod(name: String, args: Any): Any {
            // See above for why this check happens
            if (targetMetadata.respondsTo(null, name).isEmpty()) {
                throw MissingMethodException(name, Project::class.java, arrayOf())
            }
            scriptReferenced()
        }

        private
        fun scriptReferenced(): Nothing {
            throw IllegalStateException("Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.")
        }
    }
}
