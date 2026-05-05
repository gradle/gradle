/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.PolymorphicDomainObjectContainer

import org.gradle.kotlin.dsl.support.delegates.NamedDomainObjectContainerDelegate

import kotlin.reflect.KClass


/**
 * Allows the container to be configured via an augmented DSL.
 *
 * @param configuration The expression to configure this container with
 * @return The container.
 */
@Suppress("nothing_to_inline")
inline operator fun <T : Any, C : NamedDomainObjectContainer<T>> C.invoke(
    configuration: Action<NamedDomainObjectContainerScope<T>>
): C = apply {
    configuration.execute(NamedDomainObjectContainerScope.of(this))
}


/**
 * Receiver for [NamedDomainObjectContainer] configuration blocks.
 */
open
class NamedDomainObjectContainerScope<T : Any>
internal constructor(
    override val delegate: NamedDomainObjectContainer<T>
) : NamedDomainObjectContainerDelegate<T>(), PolymorphicDomainObjectContainer<T> {

    companion object {
        fun <T : Any> of(container: NamedDomainObjectContainer<T>) =
            NamedDomainObjectContainerScope(container)
    }

    override fun <U : T> register(name: String, type: Class<U>, configurationAction: Action<in U>): NamedDomainObjectProvider<U> =
        polymorphicDomainObjectContainer().register(name, type, configurationAction)

    override fun <U : T> register(name: String, type: Class<U>): NamedDomainObjectProvider<U> =
        polymorphicDomainObjectContainer().register(name, type)

    override fun <U : T> create(name: String, type: Class<U>): U =
        polymorphicDomainObjectContainer().create(name, type)

    override fun <U : T> create(name: String, type: Class<U>, configuration: Action<in U>): U =
        polymorphicDomainObjectContainer().create(name, type, configuration)

    override fun <U : T> maybeCreate(name: String, type: Class<U>): U =
        polymorphicDomainObjectContainer().maybeCreate(name, type)

    override fun <U : T> containerWithType(type: Class<U>): NamedDomainObjectContainer<U> =
        polymorphicDomainObjectContainer().containerWithType(type)

    /**
     * Configures an object by name, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [NamedDomainObjectContainer.named]
     * @see [NamedDomainObjectProvider.configure]
     */
    operator fun String.invoke(configuration: T.() -> Unit): NamedDomainObjectProvider<T> =
        named(this).apply { configure(configuration) }

    /**
     * Configures an object by name, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [PolymorphicDomainObjectContainer.named]
     * @see [NamedDomainObjectProvider.configure]
     */
    operator fun <U : T> String.invoke(type: KClass<U>, configuration: U.() -> Unit): NamedDomainObjectProvider<U> =
        delegate.named(this, type, configuration)

    /**
     * Locates an object by name and type, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [PolymorphicDomainObjectContainer.named]
     */
    operator fun <U : T> String.invoke(type: KClass<U>): NamedDomainObjectProvider<U> =
        delegate.named(this, type)

    /**
     * Cast this to [PolymorphicDomainObjectContainer] or throw [IllegalArgumentException].
     *
     * We must rely on the dynamic cast and possible runtime failure here due to a Kotlin extension member limitation.
     * Kotlin currently can't disambiguate between invoke operators with more specific receivers in a type hierarchy.
     *
     * See https://youtrack.jetbrains.com/issue/KT-15711
     */
    private
    fun polymorphicDomainObjectContainer() =
        delegate as? PolymorphicDomainObjectContainer<T>
            ?: throw IllegalArgumentException("Container '$delegate' is not polymorphic.")
}
