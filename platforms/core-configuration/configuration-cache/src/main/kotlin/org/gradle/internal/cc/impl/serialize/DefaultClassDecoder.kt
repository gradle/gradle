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

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.Describables
import org.gradle.internal.hash.HashCode
import org.gradle.internal.instantiation.DeserializationInstantiator
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.graph.ClassDecoder
import org.gradle.internal.serialize.graph.ReadIdentities


internal
class DefaultClassDecoder(
    private val defaultClassLoaderScope: ClassLoaderScope,
    private val instantiator: DeserializationInstantiator
) : ClassDecoder {

    private
    val classes = ReadIdentities()

    private
    val scopes = ReadIdentities()

    override fun Decoder.decodeClass(): Class<*> {
        val id = readSmallInt()
        val type = classes.getInstance(id)
        if (type != null) {
            return type as Class<*>
        }
        val isGenerated = readBoolean()
        val name = readString()
        val classLoader = decodeClassLoader()
        val newType = classForName(name, classLoader ?: gradleRuntimeClassLoader)
        val actualType = if (isGenerated) instantiator.getGeneratedType(newType) else newType
        classes.putInstance(id, actualType)
        return actualType
    }

    override fun Decoder.decodeClassLoader(): ClassLoader? =
        if (readBoolean()) {
            val scope = readScope()
            if (readBoolean()) {
                scope.localClassLoader
            } else {
                scope.exportClassLoader
            }
        } else {
            null
        }

    private
    fun Decoder.readScope(): ClassLoaderScope {
        val id = readSmallInt()
        val scope = scopes.getInstance(id)
        if (scope != null) {
            return scope as ClassLoaderScope
        }

        val parent = if (readBoolean()) {
            readScope()
        } else {
            defaultClassLoaderScope
        }

        val name = readString()
        val origin = if (readBoolean()) {
            ClassLoaderScopeOrigin.Script(readString(), Describables.of(readString()), Describables.of(readString()))
        } else {
            null
        }
        val localClassPath = readClassPath()
        val localImplementationHash = readHashCode()
        val exportClassPath = readClassPath()

        val newScope = if (localImplementationHash != null && exportClassPath.isEmpty) {
            parent.createLockedChild(name, origin, localClassPath, localImplementationHash, null)
        } else {
            parent.createChild(name, origin).local(localClassPath).export(exportClassPath).lock()
        }

        scopes.putInstance(id, newScope)
        return newScope
    }

    private
    fun Decoder.readHashCode() = if (readBoolean()) {
        HashCode.fromBytes(readBinary())
    } else {
        null
    }
}
