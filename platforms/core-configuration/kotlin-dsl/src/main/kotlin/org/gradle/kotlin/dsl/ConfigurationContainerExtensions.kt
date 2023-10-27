/*
 * Copyright 2023 the original author or authors.
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


@file:Incubating


package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import kotlin.reflect.KProperty
import org.gradle.api.InvalidUserDataException


/**
 * Allows the container to be configured via an augmented DSL.
 *
 * @param configuration The expression to configure this container with
 * @return The container.
 *
 * @since 8.4
 */
@Suppress("nothing_to_inline")
@Incubating
inline operator fun ConfigurationContainer.invoke(
    configuration: Action<ConfigurationContainerScope>
): ConfigurationContainer = apply {
    configuration.execute(ConfigurationContainerScope.of(this))
}


/**
 * Receiver for [ConfigurationContainer] configuration blocks.
 *
 * @since 8.4
 */
class ConfigurationContainerScope
private constructor(
    override val delegate: ConfigurationContainer
) : NamedDomainObjectContainerScope<Configuration>(delegate), ConfigurationContainer {

    companion object {
        fun of(container: ConfigurationContainer) =
            ConfigurationContainerScope(container)
    }

    override fun detachedConfiguration(vararg dependencies: Dependency): Configuration =
        delegate.detachedConfiguration(*dependencies)

    override fun resolvable(name: String): NamedDomainObjectProvider<ResolvableConfiguration> =
        delegate.resolvable(name)

    override fun resolvable(name: String, action: Action<in ResolvableConfiguration>): NamedDomainObjectProvider<ResolvableConfiguration> =
        delegate.resolvable(name, action)

    override fun consumable(name: String): NamedDomainObjectProvider<ConsumableConfiguration> =
        delegate.consumable(name)

    override fun consumable(name: String, action: Action<in ConsumableConfiguration>): NamedDomainObjectProvider<ConsumableConfiguration> =
        delegate.consumable(name, action)

    override fun dependencyScope(name: String): NamedDomainObjectProvider<DependencyScopeConfiguration> =
        delegate.dependencyScope(name)

    override fun dependencyScope(name: String, action: Action<in DependencyScopeConfiguration>): NamedDomainObjectProvider<DependencyScopeConfiguration> =
        delegate.dependencyScope(name, action)
}


/**
 * Registers a [ResolvableConfiguration] via [resolvable].
 *
 * @return A provider which creates a new resolvable configuration.
 *
 * @throws InvalidUserDataException If a configuration with the given name already exists in this container.
 *
 * @since 8.5
 */
@get:Incubating
val ConfigurationContainer.resolvable: ConfigurationProvider<ResolvableConfiguration>
    get() = ConfigurationProvider { name -> resolvable(name) }


/**
 * Registers a [ResolvableConfiguration] via [resolvable] and then
 * configures it with the provided action.
 *
 * @param action The action to apply to the configuration.
 *
 * @return A provider which creates a new resolvable configuration.
 *
 * @throws InvalidUserDataException If a configuration with the given name already exists in this container.
 *
 * @since 8.5
 */
@Incubating
fun ConfigurationContainer.resolvable(action: Action<in ResolvableConfiguration>): ConfigurationProvider<ResolvableConfiguration> =
    ConfigurationProvider { name -> resolvable(name, action) }


/**
 * Registers a [ConsumableConfiguration] via [consumable].
 *
 * @return A provider which creates a new consumable configuration.
 *
 * @throws InvalidUserDataException If a configuration with the given name already exists in this container.
 *
 * @since 8.5
 */
@get:Incubating
val ConfigurationContainer.consumable: ConfigurationProvider<ConsumableConfiguration>
    get() = ConfigurationProvider { name -> consumable(name) }


/**
 * Registers a [ConsumableConfiguration] via [consumable] and then
 * configures it with the provided action.
 *
 * @param action The action to apply to the configuration.
 *
 * @return A provider which creates a new consumable configuration.
 *
 * @throws InvalidUserDataException If a configuration with the given name already exists in this container.
 *
 * @since 8.5
 */
@Incubating
fun ConfigurationContainer.consumable(action: Action<in ConsumableConfiguration>): ConfigurationProvider<ConsumableConfiguration> =
    ConfigurationProvider { name -> consumable(name, action) }


/**
 * Registers a [DependencyScopeConfiguration] via [dependencyScope].
 *
 * @return A provider which creates a new dependency scope configuration.
 *
 * @throws InvalidUserDataException If a configuration with the given name already exists in this container.
 *
 * @since 8.5
 */
@get:Incubating
val ConfigurationContainer.dependencyScope: ConfigurationProvider<DependencyScopeConfiguration>
    get() = ConfigurationProvider { name -> dependencyScope(name) }


/**
 * Registers a [DependencyScopeConfiguration] via [dependencyScope] and then
 * configures it with the provided action.
 *
 * @param action The action to apply to the configuration.
 *
 * @return A provider which creates a new dependency scope configuration.
 *
 * @throws InvalidUserDataException If a configuration with the given name already exists in this container.
 *
 * @since 8.5
 */
@Incubating
fun ConfigurationContainer.dependencyScope(action: Action<in DependencyScopeConfiguration>): ConfigurationProvider<DependencyScopeConfiguration> =
    ConfigurationProvider { name -> dependencyScope(name, action) }


/**
 * Provides access to the [NamedDomainObjectProvider] for the element of the given
 * property name from the configuration container via a delegated property.
 *
 * @since 8.5
 */
@Incubating
class ConfigurationProvider<C : Configuration> internal constructor(private val createProvider: (String) -> NamedDomainObjectProvider<C>) {
    /**
     * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>
    ): ExistingDomainObjectDelegate<NamedDomainObjectProvider<C>> = ExistingDomainObjectDelegate.of(createProvider(property.name))
}
