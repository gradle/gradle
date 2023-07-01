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


/**
 * Allows the container to be configured via an augmented DSL.
 *
 * @param configuration The expression to configure this container with
 * @return The container.
 *
 * @since 8.3
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
 * @since 8.3
 */
@Incubating
class ConfigurationContainerScope
private constructor(
    override val delegate: ConfigurationContainer
) : NamedDomainObjectContainerScope<Configuration>(delegate), ConfigurationContainer {

    companion object {
        fun of(container: ConfigurationContainer) =
            ConfigurationContainerScope(container)
    }

    override fun detachedConfiguration(vararg dependencies: Dependency?): Configuration =
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
