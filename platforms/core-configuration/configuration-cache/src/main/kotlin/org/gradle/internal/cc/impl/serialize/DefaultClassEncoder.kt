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
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.ClassEncoder
import org.gradle.internal.serialize.graph.ClassLoaderRole
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.WriteIdentities


internal
interface ScopeLookup {
    fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>?
    val knownClassLoaders: Set<ClassLoader>
}


internal
data class ClassLoaderScopeSpec(
    val parent: ClassLoaderScopeSpec?,
    val name: String,
    val origin: ClassLoaderScopeOrigin?
) {
    var localClassPath: ClassPath = ClassPath.EMPTY
    var localImplementationHash: HashCode? = null
    var exportClassPath: ClassPath = ClassPath.EMPTY

    override fun toString(): String {
        return if (parent != null) {
            "$parent:$name"
        } else {
            name
        }
    }
}


internal
class DefaultClassEncoder(
    private val scopeLookup: ScopeLookup
) : ClassEncoder {

    private
    val classes = WriteIdentities()

    private
    val scopes = WriteIdentities()

    override fun WriteContext.encodeClass(type: Class<*>) {
        val id = classes.getId(type)
        if (id != null) {
            writeSmallInt(id)
        } else {
            val newId = classes.putInstance(type)
            writeSmallInt(newId)
            // TODO:configuration-cache - should collect the details of the decoration (eg enabled annotations, etc), and also carry this information with the serialized class reference
            val originalType = GeneratedSubclasses.unpack(type)
            writeBoolean(originalType !== type)
            val className = originalType.name
            writeString(className)
            val classLoader = originalType.classLoader
            if (!writeClassLoaderScopeOf(classLoader) && classLoader != null) {
                // Ensure class can be found in the Gradle runtime classloader since its original classloader could not be encoded.
                ensureClassCanBeFoundInGradleRuntimeClassLoader(className, classLoader)
            }
        }
    }

    override fun WriteContext.encodeClassLoader(classLoader: ClassLoader?) {
        writeClassLoaderScopeOf(classLoader)
    }

    private
    fun Encoder.writeClassLoaderScopeOf(classLoader: ClassLoader?): Boolean {
        val scope = classLoader?.let { scopeLookup.scopeFor(it) }
        if (scope == null) {
            writeBoolean(false)
            return false
        } else {
            writeBoolean(true)
            writeScope(scope.first)
            writeBoolean(scope.second.local)
            return true
        }
    }

    private
    fun Encoder.writeScope(scope: ClassLoaderScopeSpec) {
        val id = scopes.getId(scope)
        if (id != null) {
            writeSmallInt(id)
        } else {
            val newId = scopes.putInstance(scope)
            writeSmallInt(newId)
            if (scope.parent == null) {
                writeBoolean(false)
            } else {
                writeBoolean(true)
                writeScope(scope.parent)
            }
            writeString(scope.name)
            if (scope.origin is ClassLoaderScopeOrigin.Script) {
                writeBoolean(true)
                writeString(scope.origin.fileName)
                writeString(scope.origin.longDisplayName.displayName)
                writeString(scope.origin.shortDisplayName.displayName)
            } else {
                writeBoolean(false)
            }
            writeClassPath(scope.localClassPath)
            writeHashCode(scope.localImplementationHash)
            writeClassPath(scope.exportClassPath)
        }
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

    private
    fun Encoder.writeHashCode(hashCode: HashCode?) {
        if (hashCode == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeBinary(hashCode.toByteArray())
        }
    }
}


internal
fun ScopeLookup.describeKnownClassLoaders(): String = knownClassLoaders.let { classLoaders ->
    if (classLoaders.isEmpty()) "No class loaders are currently known."
    else "These are the known class loaders:\n${classLoaders.joinToString("\n") { "\t- $it" }}"
}
