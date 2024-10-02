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

import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.cc.base.exceptions.ConfigurationCacheError
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.ClassEncoder
import org.gradle.internal.serialize.graph.ClassLoaderRole
import org.gradle.internal.serialize.graph.WriteIdentities


internal
interface ScopeLookup {
    fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>?
    val knownClassLoaders: Set<ClassLoader>
}


internal
fun ScopeLookup.describeKnownClassLoaders() =
    "These are the known class loaders:\n${knownClassLoaders.joinToString("\n") { "\t- $it" }}\n"


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

    override fun Encoder.encodeClass(type: Class<*>) {
        val id = classes.getId(type)
        if (id != null) {
            writeSmallInt(id)
        } else {
            val newId = classes.putInstance(type)
            writeSmallInt(newId)
            val className = type.name
            writeString(className)
            val classLoader = type.classLoader
            if (!writeClassLoaderScopeOf(classLoader) && classLoader != null) {
                // Ensure class can be found in the Gradle runtime classloader since its original classloader could not be encoded.
                ensureClassCanBeFoundInGradleRuntimeClassLoader(className, classLoader)
            }
        }
    }

    override fun Encoder.encodeClassLoader(classLoader: ClassLoader?) {
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
    fun ensureClassCanBeFoundInGradleRuntimeClassLoader(className: String, originalClassLoader: ClassLoader) {
        try {
            classForName(className, gradleRuntimeClassLoader)
        } catch (e: ClassNotFoundException) {
            throw ConfigurationCacheError(
                "Class '${className}' cannot be encoded because ${describeClassLoader(originalClassLoader)} could not be encoded " +
                    "and the class is not available through the default class loader.\n" +
                    scopeLookup.describeKnownClassLoaders() +
                    "Please report this error, run './gradlew --stop' and try again.",
                e
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
