/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf
import org.gradle.api.reflect.TypeOf.typeOf
import org.gradle.internal.extensibility.DefaultExtensionsSchema
import org.gradle.internal.extensibility.DefaultExtraPropertiesExtension
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.isolate.graph.IsolatedActionDeserializer
import org.gradle.internal.isolate.graph.IsolatedActionSerializer
import org.gradle.internal.isolate.graph.SerializedIsolatedActionGraph

internal class IsolatedExtensionsContainer(
    private val serializer: IsolatedActionSerializer,
    private val deserializer: IsolatedActionDeserializer,
    private val delegate: ExtensionContainerInternal,
    private val gradleProperties : GradleProperties
) : ExtensionContainerInternal {

    private val serializedExtensionsByType = mutableMapOf<TypeOf<*>, SerializedIsolatedActionGraph<Any>>()
    private val serializedExtensionsByName = mutableMapOf<String, SerializedIsolatedActionGraph<Any>>()
    private val serializedExtensionsAsMap: SerializedIsolatedActionGraph<Map<String, Any>> by lazy {
        serializer.serialize(delegate.getAsMap())
    }
    private val serializedExtraProperties : SerializedIsolatedActionGraph<Map<String, Any>> by lazy {
        serializer.serialize(delegate.extraProperties.properties)
    }

    override fun getAsMap(): Map<String, Any> =
        deserializer.deserialize(serializedExtensionsAsMap)

    override fun getExtensionsSchema(): ExtensionsSchema =
        DefaultExtensionsSchema.create(delegate.extensionsSchema)

    override fun <T : Any> getByType(type: Class<T>): T = getByType(typeOf(type))

    override fun <T : Any> getByType(type: TypeOf<T>): T {
        val serialized = serializedExtensionsByType.computeIfAbsent(type) {
            serializer.serialize(delegate.getByType(type))
        }
        return deserializer.deserialize(serialized).uncheckedCast()
    }

    override fun <T : Any> findByType(type: Class<T>): T? = findByType(typeOf(type))

    override fun <T : Any> findByType(type: TypeOf<T>): T? {
        val serialized = serializedExtensionsByType.compute(type) { _, value ->
            value ?: delegate.findByType(type)?.let { serializer.serialize(it) }
        }
        return serialized?.let { deserializer.deserialize(it).uncheckedCast() }
    }

    override fun getByName(name: String): Any {
        val serialized = serializedExtensionsByName.computeIfAbsent(name) {
            serializer.serialize((delegate.getByName(name)))
        }
        return deserializer.deserialize(serialized)
    }

    override fun findByName(name: String): Any? {
        val serialized = serializedExtensionsByName.compute(name) { _, value ->
            value ?: delegate.findByName(name)?.let { serializer.serialize(it) }
        }
        return serialized?.let { deserializer.deserialize(it) }
    }

    override fun getExtraProperties(): ExtraPropertiesExtension {
        val properties = deserializer.deserialize(serializedExtraProperties)
        return DefaultExtraPropertiesExtension(properties).apply { setGradleProperties(gradleProperties) }
    }

    override fun <T : Any> add(publicType: Class<T>, name: String, extension: T) {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun <T : Any> add(publicType: TypeOf<T>, name: String, extension: T) {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun add(name: String, extension: Any) {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun <T : Any> create(publicType: Class<T>, name: String, instanceType: Class<out T>, vararg constructionArguments: Any): T {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun <T : Any> create(publicType: TypeOf<T>, name: String, instanceType: Class<out T>, vararg constructionArguments: Any): T {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun <T : Any> create(name: String, type: Class<T>, vararg constructionArguments: Any): T {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun <T : Any> configure(type: Class<T>, action: Action<in T>) {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun <T : Any> configure(type: TypeOf<T>, action: Action<in T>) {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }

    override fun <T : Any> configure(name: String, action: Action<in T>) {
        throw InvalidUserCodeException("Mutation of the isolated extensions is prohibited")
    }
}
