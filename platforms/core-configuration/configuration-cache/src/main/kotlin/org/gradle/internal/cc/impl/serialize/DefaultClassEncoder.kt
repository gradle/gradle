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

package org.gradle.internal.cc.impl.serialize

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.serialize.graph.ClassEncoder
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.WriteIdentities
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf


internal
class DefaultClassEncoder(
    private val scopeLookup: ScopeLookup,
    private val scopeSpecEncoder: ClassLoaderScopeSpecEncoder = InlineClassLoaderScopeSpecEncoder()
) : ClassEncoder {

    private
    val classes = WriteIdentities()

    override fun WriteContext.encodeClass(type: Class<*>) {
        encodePreservingIdentityOf(classes, type) { newType ->
            // TODO:configuration-cache - should collect the details of the decoration (eg enabled annotations, etc), and also carry this information with the serialized class reference
            val originalType = GeneratedSubclasses.unpack(newType)
            writeBoolean(originalType !== newType)
            val className = originalType.name
            writeString(className)
            encodeClassLoaderFor(className, originalType.classLoader)
        }
    }

    override fun WriteContext.encodeClassLoader(classLoader: ClassLoader?) {
        // TODO:configuration-cache validate classloader encoding here
        encodeClassLoaderFor(null, classLoader)
    }

    private
    fun WriteContext.encodeClassLoaderFor(className: String?, classLoader: ClassLoader?) {
        if (classLoader == null) {
            writeBoolean(false)
            return
        }

        val scopeAndRole = scopeLookup.scopeFor(classLoader)
        if (scopeAndRole == null) {
            writeBoolean(false)
            if (className != null) {
                // Ensure class can be found in the Gradle runtime classloader since its original classloader could not be encoded.
                ensureClassCanBeFoundInGradleRuntimeClassLoader(className, classLoader)
            }
            return
        }

        writeBoolean(true)
        val (scope, role) = scopeAndRole
        scopeSpecEncoder.run {
            encodeScope(scope)
        }
        writeBoolean(role.local)
    }

    private
    fun WriteContext.ensureClassCanBeFoundInGradleRuntimeClassLoader(className: String, originalClassLoader: ClassLoader) {
        try {
            classForName(className, gradleRuntimeClassLoader)
        } catch (e: ClassNotFoundException) {
            onProblem(
                PropertyProblem(
                    trace,
                    StructuredMessage.build {
                        text("Class ")
                        reference(className)
                        text(" cannot be encoded because ")
                        text(describeClassLoader(originalClassLoader))
                        text(" could not be encoded and the class is not available through the default class loader.\n")
                        text(scopeLookup.describeKnownClassLoaders())
                        text("\nPlease report this error, run './gradlew --stop' and try again.")
                    },
                    exception = e
                )
            )
        }
    }
}


internal
fun ScopeLookup.describeKnownClassLoaders(): String = knownClassLoaders.let { classLoaders ->
    if (classLoaders.isEmpty()) "No class loaders are currently known."
    else "These are the known class loaders:\n${classLoaders.joinToString("\n") { "\t- $it" }}"
}
