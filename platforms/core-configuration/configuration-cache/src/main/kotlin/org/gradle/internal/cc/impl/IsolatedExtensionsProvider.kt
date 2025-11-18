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

import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.Describables
import org.gradle.internal.Try
import org.gradle.internal.Try.ofFailable
import org.gradle.internal.extensibility.ExtensionsStorage
import org.gradle.internal.isolate.graph.IsolatedActionDeserializer
import org.gradle.internal.isolate.graph.IsolatedActionSerializer
import org.gradle.internal.isolate.graph.SerializedIsolatedActionGraph
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainerFactory

interface IsolatedExtensionsProvider {

    fun <T> findByType(type: TypeOf<T>): Try<T>?

    fun findByName(name: String): Try<Any>?

    fun getExtraProperties(): ImmutableMap<String, Any>
}

internal class DefaultIsolatedExtensionsProvider(
    private val projectState: ProjectState,
    private val serializer: IsolatedActionSerializer,
    private val deserializer: IsolatedActionDeserializer,
    calculatedValueContainerFactory: CalculatedValueContainerFactory
) : IsolatedExtensionsProvider {

    private val extensions: CalculatedValue<Map<String, SerializedExtension<*>>> =
        calculatedValueContainerFactory.create(Describables.of("serialized extensions for", projectState.displayName)) {
            projectState.fromMutableState {
                it.extensions.holdersAsMap.entries.associate { (key, extension) ->
                    key to serialize(extension)
                }
            }
        }

//    val properties = ....

    private fun <T : Any> serialize(holder: ExtensionsStorage.ExtensionHolder<T>): SerializedExtension<T> {
        val type = holder.publicType
        val value = ofFailable<SerializedIsolatedActionGraph<T>> { serializer.serialize(holder.get()) }
        return SerializedExtension<T>(type, value)
    }

    private fun getSerializedExtensions(): Map<String, SerializedExtension<*>> {
        // TODO val extensions by lazy {} ?
        extensions.finalizeIfNotAlready();
        return extensions.get()
    }

    override fun <T> findByType(type: TypeOf<T>): Try<T>? {
        val extensionOfExactType = getSerializedExtensions().values.firstOrNull { it.type == type }
        val serialized = extensionOfExactType?.value
            ?: run {
                val extensionOfAssignableType = getSerializedExtensions().values.firstOrNull { type.isAssignableFrom(it.type) }
                extensionOfAssignableType?.value
            }
        return serialized?.map { deserializer.deserialize(it) as T }
    }

    override fun findByName(name: String): Try<Any>? = getSerializedExtensions()[name]?.value?.map {
        deserializer.deserialize(it)
    }

    override fun getExtraProperties(): ImmutableMap<String, Any> {
        // TODO It's already serialized, just deserialize it? We potentially may want to keep it serialized separately, if so, we should filter it out of extensions
        return ImmutableMap.of()
    }

    data class SerializedExtension<T : Any>(
        val type: TypeOf<T>,
        val value: Try<SerializedIsolatedActionGraph<T>>
    )
}
