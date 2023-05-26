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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependenciesConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;

/**
 * Extends {@link ConfigurationContainer} to define internal-only methods for creating configurations.
 * All methods in this interface produce <strong>unlocked</strong> configurations, meaning they
 * are allowed to change roles. Starting in Gradle 9.0, all Gradle-created configurations will be locked.
 *
 * <p>The methods on this interface are meant to be transitional, and as such all usages of this interface
 * should be migrated to the public API starting in Gradle 9.0.<p>
 *
 * <strong>New configurations should leverage the role-based factory methods on {@link ConfigurationContainer}.</strong>
 */
public interface RoleBasedConfigurationContainerInternal extends ConfigurationContainer {

    /**
     * Creates a consumable configuration which can change roles.
     */
    NamedDomainObjectProvider<ConsumableConfiguration> consumableUnlocked(String name);

    /**
     * Creates a consumable configuration which can change roles and executes the provided
     * {@code action} against the configuration.
     */
    NamedDomainObjectProvider<ConsumableConfiguration> consumableUnlocked(String name, Action<? super ConsumableConfiguration> action);

    /**
     * Creates a resolvable configuration which can change roles and executes the provided
     * {@code action} against the configuration.
     */
    NamedDomainObjectProvider<ResolvableConfiguration> resolvableUnlocked(String name);

    /**
     * Creates a resolvable configuration which can change roles and executes the provided
     * {@code action} against the configuration.
     */
    NamedDomainObjectProvider<ResolvableConfiguration> resolvableUnlocked(String name, Action<? super ResolvableConfiguration> action);

    /**
     * Creates a dependencies configuration which can change roles and executes the provided
     * {@code action} against the configuration.
     */
    NamedDomainObjectProvider<DependenciesConfiguration> dependenciesUnlocked(String name);

    /**
     * Creates a dependencies configuration which can change role and executes the provided
     * {@code action} against the configuration.
     */
    NamedDomainObjectProvider<DependenciesConfiguration> dependenciesUnlocked(String name, Action<? super DependenciesConfiguration> action);

    /**
     * Creates a new configuration, which can change roles, with initial role {@code role}.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    NamedDomainObjectProvider<Configuration> migratingUnlocked(String name, ConfigurationRole role);

    /**
     * Creates a new configuration, which can change roles, with initial role {@code role},
     * and executes the provided {@code action} against the configuration.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    NamedDomainObjectProvider<Configuration> migratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action);

    /**
     * Creates a resolvable + dependencies configuration which can change roles.
     *
     * @deprecated Whether concept of a resolvable + dependencies configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependencies configurations.
     */
    @Deprecated
    NamedDomainObjectProvider<Configuration> resolvableDependenciesUnlocked(String name);

    /**
     * Creates a resolvable + dependencies configuration which can change rolesand executes the provided
     * {@code action} against the configuration.
     *
     * @deprecated Whether concept of a resolvable + dependencies configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependencies configurations.
     */
    @Deprecated
    NamedDomainObjectProvider<Configuration> resolvableDependenciesUnlocked(String name, Action<? super Configuration> action);

    /**
     * If a configuration with the given name already exists, configure it with the given action and return it.
     * Otherwise, register a new resolvable configuration with the given name and configure it with the given action.
     */
    NamedDomainObjectProvider<? extends Configuration> maybeRegisterResolvableUnlocked(String name, Action<? super Configuration> action);

    /**
     * If a configuration with the given name already exists, configure it with the given action and return it.
     * Otherwise, register a new consumable configuration with the given name and configure it with the given action.
     */
    NamedDomainObjectProvider<? extends Configuration> maybeRegisterConsumableUnlocked(String name, Action<? super Configuration> action);

    /**
     * If a configuration with the given name already exists, configure it with the given action and return it.
     * Otherwise, register a new dependencies configuration with the given name and configure it with the given action.
     */
    NamedDomainObjectProvider<? extends Configuration> maybeRegisterDependenciesUnlocked(String name, Action<? super Configuration> action);

    /**
     * If a configuration with the given name already exists, configure it with the given action and return it.
     * Otherwise, register a new dependencies configuration with the given name and configure it with the given action.
     *
     * <p>If {@code warnOnDuplicate} is false, the normal deprecation warning will not be emitted. Setting this to false
     * should be avoided except in edge cases where it may emit deprecation warnings affecting large third-party plugins.</p>
     */
    NamedDomainObjectProvider<? extends Configuration> maybeRegisterDependenciesUnlocked(String name, boolean warnOnDuplicate, Action<? super Configuration> action);

    /**
     * If a configuration with the given name already exists, configure it with the given action and return it.
     * Otherwise, register a new configuration with the given name and configure it with the given action.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    NamedDomainObjectProvider<? extends Configuration> maybeRegisterMigratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action);

    /**
     * If a configuration with the given name already exists, configure it with the given action and return it.
     * Otherwise, register a new resolvable + dependencies configuration with the given name and configure it with the given action.
     *
     * @deprecated Whether concept of a resolvable + dependencies configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependencies configurations.
     */
    @Deprecated
    NamedDomainObjectProvider<? extends Configuration> maybeRegisterResolvableDependenciesUnlocked(String name, Action<? super Configuration> action);
}
