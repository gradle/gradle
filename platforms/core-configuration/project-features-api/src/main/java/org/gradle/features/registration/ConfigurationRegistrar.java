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

package org.gradle.features.registration;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;

/**
 * A registrar for {@link Configuration} instances.
 *
 * <p>An instance of this type can be injected into an object by annotating a public constructor or method with {@code javax.inject.Inject}.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 *
 * @since 9.5.0
 */
@Incubating
public interface ConfigurationRegistrar {
    /**
     * Registers a {@link ResolvableConfiguration} with an immutable role. Resolvable configurations
     * are meant to resolve dependency graphs and their artifacts.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the configuration to register.
     * @return A provider which creates a new resolvable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 9.5.0
     */
    @Incubating
    NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name);

    /**
     * Registers a {@link ResolvableConfiguration} via {@link #resolvable(String)} and then
     * configures it with the provided action.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the configuration to register.
     * @param action The action to apply to the configuration.
     *
     * @return A provider which creates a new resolvable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 9.5.0
     */
    @Incubating
    NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name, Action<? super ResolvableConfiguration> action);

    /**
     * Registers a new {@link ConsumableConfiguration} with an immutable role. Consumable configurations
     * are meant to act as a variant in the context of Dependency Management and Publishing.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the configuration to register.
     *
     * @return A provider which creates a new consumable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 9.5.0
     */
    @Incubating
    NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name);

    /**
     * Registers a {@link ConsumableConfiguration} via {@link #consumable(String)} and then
     * configures it with the provided action.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the configuration to register.
     * @param action The action to apply to the configuration.
     *
     * @return A provider which creates a new consumable configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 9.5.0
     */
    @Incubating
    NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name, Action<? super ConsumableConfiguration> action);

    /**
     * Registers a new {@link DependencyScopeConfiguration} with an immutable role. Dependency scope configurations
     * collect dependencies, dependency constraints, and exclude rules to be used by both resolvable
     * and consumable configurations.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the configuration to register.
     *
     * @return A provider which creates a new dependency scope configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 9.5.0
     */
    @Incubating
    NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name);

    /**
     * Registers a {@link DependencyScopeConfiguration} via {@link #dependencyScope(String)} and then
     * configures it with the provided action.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the configuration to register.
     * @param action The action to apply to the configuration.
     *
     * @return A provider which creates a new dependency scope configuration.
     *
     * @throws InvalidUserDataException If a configuration with the given {@code name} already exists in this container.
     *
     * @since 9.5.0
     */
    @Incubating
    NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name, Action<? super DependencyScopeConfiguration> action);

}
