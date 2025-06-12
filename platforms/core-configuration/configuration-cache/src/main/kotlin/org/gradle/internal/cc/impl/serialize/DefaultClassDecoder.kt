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
import org.gradle.internal.instantiation.DeserializationInstantiator
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.graph.ClassDecoder
import org.gradle.internal.serialize.graph.ReadIdentities
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import java.util.IdentityHashMap


internal
class DefaultClassDecoder(
    private val defaultClassLoaderScope: ClassLoaderScope,
    private val instantiator: DeserializationInstantiator,
    private val scopeSpecDecoder: ClassLoaderScopeSpecDecoder = InlineClassLoaderScopeSpecDecoder()
) : ClassDecoder {

    private
    val classes = ReadIdentities()

    /**
     * Associate each spec with its corresponding scope.
     *
     * It is safe to maintain the association
     * by identity since [ScopeLookup] maintains a 1-to-1 mapping between scopes and their corresponding
     * specs and [ClassLoaderScopeSpecDecoder] preserves identities upon decoding.
     */
    private
    val scopeBySpec = IdentityHashMap<ClassLoaderScopeSpec, ClassLoaderScope>()

    override fun Decoder.decodeClass(): Class<*> = decodePreservingIdentity(classes) { id ->
        val isGenerated = readBoolean()
        val name = readString()
        val classLoader = decodeClassLoader()
        val newType = classForName(name, classLoader ?: gradleRuntimeClassLoader)
        val actualType = if (isGenerated) instantiator.getGeneratedType(newType) else newType
        classes.putInstance(id, actualType)
        actualType
    }

    override fun Decoder.decodeClassLoader(): ClassLoader? =
        if (readBoolean()) {
            val scope = readScope()
            val classLoader = if (readBoolean()) {
                scope.localClassLoader
            } else {
                scope.exportClassLoader
            }
            classLoader
        } else {
            null
        }

    private
    fun Decoder.readScope(): ClassLoaderScope {
        val spec = scopeSpecDecoder.run { decodeScope() }
        return scopeFor(spec)
    }

    private
    fun scopeFor(spec: ClassLoaderScopeSpec): ClassLoaderScope {

        val cached = scopeBySpec[spec]
        if (cached != null) {
            return cached
        }

        val parent = if (spec.parent != null) {
            scopeFor(spec.parent)
        } else {
            defaultClassLoaderScope
        }

        val newScope = if (spec.localImplementationHash != null && spec.exportClassPath.isEmpty) {
            parent.createLockedChild(
                spec.name,
                spec.origin,
                spec.localClassPath,
                spec.localImplementationHash,
                null
            )
        } else {
            parent
                .createChild(spec.name, spec.origin)
                .local(spec.localClassPath)
                .export(spec.exportClassPath)
                .lock()
        }

        scopeBySpec.put(spec, newScope)
        return newScope
    }
}
