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

package org.gradle.configurationcache.serialization

import org.gradle.configurationcache.ClassLoaderScopeSpec
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.graph.ClassEncoder
import org.gradle.internal.serialize.graph.ClassLoaderRole
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.WriteIdentities


internal
interface ScopeLookup {
    fun scopeFor(classLoader: ClassLoader?): Pair<ClassLoaderScopeSpec, ClassLoaderRole>?
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
            val scope = scopeLookup.scopeFor(type.classLoader)
            val newId = classes.putInstance(type)
            writeSmallInt(newId)
            writeString(type.name)
            if (scope == null) {
                writeBoolean(false)
            } else {
                writeBoolean(true)
                writeScope(scope.first)
                writeBoolean(scope.second.local)
            }
        }
    }

    private
    fun WriteContext.writeScope(scope: ClassLoaderScopeSpec) {
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
    fun WriteContext.writeHashCode(hashCode: HashCode?) {
        if (hashCode == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeBinary(hashCode.toByteArray())
        }
    }
}
