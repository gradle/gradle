/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.registration;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.features.registration.ConfigurationRegistrar;

/**
 * Default implementation of {@link ConfigurationRegistrar} which delegates to {@link org.gradle.api.artifacts.ConfigurationContainer},
 *
 * @since 9.5.0
 */
public class DefaultConfigurationRegistrar implements ConfigurationRegistrar {
    private final org.gradle.api.artifacts.ConfigurationContainer configurationContainer;

    public DefaultConfigurationRegistrar(org.gradle.api.artifacts.ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
    }

    @Override
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name) {
        return configurationContainer.resolvable(name);
    }

    @Override
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name, Action<? super ResolvableConfiguration> action) {
        return configurationContainer.resolvable(name, action);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name) {
        return configurationContainer.consumable(name);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name, Action<? super ConsumableConfiguration> action) {
        return configurationContainer.consumable(name, action);
    }

    @Override
    public NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name) {
        return configurationContainer.dependencyScope(name);
    }

    @Override
    public NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name, Action<? super DependencyScopeConfiguration> action) {
        return configurationContainer.dependencyScope(name, action);
    }
}
