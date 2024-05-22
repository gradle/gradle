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

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.internal.Describables
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.graph.ClassDecoder
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.ReadIdentities
import org.gradle.internal.serialize.graph.ownerService


internal
class DefaultClassDecoder : ClassDecoder {

    private
    val classes = ReadIdentities()

    private
    val scopes = ReadIdentities()

    override fun ReadContext.decodeClass(): Class<*> {
        val id = readSmallInt()
        val type = classes.getInstance(id)
        if (type != null) {
            return type as Class<*>
        }
        val name = readString()
        val classLoader = if (readBoolean()) {
            val scope = readScope()
            if (readBoolean()) {
                scope.localClassLoader
            } else {
                scope.exportClassLoader
            }
        } else {
            this.classLoader
        }
        val newType = Class.forName(name, false, classLoader)
        classes.putInstance(id, newType)
        return newType
    }

    private
    fun ReadContext.readScope(): ClassLoaderScope {
        val id = readSmallInt()
        val scope = scopes.getInstance(id)
        if (scope != null) {
            return scope as ClassLoaderScope
        }

        val parent = if (readBoolean()) {
            readScope()
        } else {
            ownerService<ClassLoaderScopeRegistry>().coreAndPluginsScope
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
    fun ReadContext.readHashCode() = if (readBoolean()) {
        HashCode.fromBytes(readBinary())
    } else {
        null
    }
}
