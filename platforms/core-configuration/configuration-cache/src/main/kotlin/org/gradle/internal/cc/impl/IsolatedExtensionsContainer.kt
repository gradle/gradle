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
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.reflect.TypeOf
import org.gradle.api.reflect.TypeOf.typeOf
import org.gradle.internal.extensibility.DefaultExtraPropertiesExtension
import org.gradle.internal.extensibility.ExtensionsStorage

internal class IsolatedExtensionsContainer(
    private val projectExtensionsProvider: IsolatedExtensionsProvider,
    private val delegate: ExtensionContainerInternal,
    private val onExtensionIsolationException: (String, Throwable) -> Unit,
) : ExtensionContainerInternal {

    override fun getExtensionsSchema(): ExtensionsSchema = projectExtensionsProvider.getExtensionsSchema()

    override fun <T : Any> getByType(type: Class<T>): T = getByType(typeOf(type))

    override fun <T : Any> getByType(type: TypeOf<T>): T = findByType(type) ?: throw UnknownDomainObjectException("Extension of type ${type.simpleName} does not exist")

    override fun <T : Any> findByType(type: Class<T>): T? = findByType(typeOf(type))

    override fun <T : Any> findByType(type: TypeOf<T>): T? =
        projectExtensionsProvider.findByType(type)?.getOrMapFailure {
            onExtensionIsolationException("of type '${type.simpleName}'", it)
            delegate.getByType(type)
        }

    override fun getByName(name: String): Any = findByName(name) ?: throw UnknownDomainObjectException("Extension with name '$name' does not exist")

    override fun findByName(name: String): Any? =
        projectExtensionsProvider.findByName(name)?.getOrMapFailure {
            onExtensionIsolationException("with name '$name'", it)
            delegate.getByName(name)
        }

    override fun getExtraProperties(): ExtraPropertiesExtension =
        // TODO: Make sure the returned extensions container is immutable
        DefaultExtraPropertiesExtension(projectExtensionsProvider.getExtraProperties())

    override fun getHoldersAsMap(): ImmutableMap<String, ExtensionsStorage.ExtensionHolder<*>> {
        throw UnsupportedOperationException()
    }

    override fun getAsMap(): Map<String, Any> {
        throw UnsupportedOperationException()
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
