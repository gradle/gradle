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
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.DomainObjectCollectionInternal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Extends {@link ConfigurationContainer} to define internal-only methods for creating configurations.
 * <p>
 * All methods in this interface produce <strong>locked</strong> configurations, meaning they
 * are <strong>not</strong> allowed to change roles. All Gradle-created configurations will be locked.
 * <p>
 * The methods on this interface are meant to be transitional, and as such all usages of this interface
 * should eventually be migrated to the public API.
 * <p>
 * <strong>New configurations should leverage the role-based factory methods on {@link ConfigurationContainer}.</strong>
 */
@ServiceScope(Scope.Project.class)
public interface RoleBasedConfigurationContainerInternal extends ConfigurationContainer, DomainObjectCollectionInternal<Configuration> {

    /**
     * Creates a consumable configuration which can <strong>NOT</strong> change roles.
     *
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration consumableLocked(String name);

    /**
     * Creates a consumable configuration which can <strong>NOT</strong> change roles and executes the provided
     * {@code action} against the configuration.
     *
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration consumableLocked(String name, Action<? super Configuration> action);

    /**
     * Creates a resolvable configuration which can <strong>NOT</strong> change roles.
     *
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration resolvableLocked(String name);

    /**
     * Creates a resolvable configuration which can <strong>NOT</strong> change roles and executes the provided
     * {@code action} against the configuration.
     *
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration resolvableLocked(String name, Action<? super Configuration> action);

    /**
     * Creates a dependency scope configuration which can <strong>NOT</strong> change roles.
     *
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration dependencyScopeLocked(String name);

    /**
     * Creates a dependency scope configuration which can <strong>NOT</strong> change roles and executes the provided
     * {@code action} against the configuration.
     *
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration dependencyScopeLocked(String name, Action<? super Configuration> action);

    /**
     * Creates a new configuration, which can <strong>NOT</strong> change roles, with initial role {@code role}.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration migratingLocked(String name, ConfigurationRole role);

    /**
     * Creates a new configuration, which can <strong>NOT</strong> change roles, with initial role {@code role},
     * and executes the provided {@code action} against the configuration.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    Configuration migratingLocked(String name, ConfigurationRole role, Action<? super Configuration> action);

    /**
     * Creates a resolvable + dependency scope configuration which can <strong>NOT</strong> change roles.
     *
     * @deprecated Whether concept of a resolvable + dependency scope configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependency scope configurations.
     *
     * @throws GradleException If the name is already in use or reserved for Gradle.
     */
    @Deprecated
    Configuration resolvableDependencyScopeLocked(String name);

    /**
     * Creates a resolvable + dependency scope configuration which can <strong>NOT</strong> change roles and executes the provided
     * {@code action} against the configuration.
     *
     * @deprecated Whether concept of a resolvable + dependency scope configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependency scope configurations.
     */
    @Deprecated
    Configuration resolvableDependencyScopeLocked(String name, Action<? super Configuration> action);

    // TODO: This is only used as a workaround for Kotlin in DefaultJvmFeature -- we should inline and remove this method.
    /**
     * If a configuration with the given name already exists,return it.
     * Otherwise, creates a new dependency scope configuration with the given name.
     *
     * <p>If {@code verifyPrexisting} is false, the normal deprecation warning will not be emitted. Setting this to false
     * should be avoided except in edge cases where it may emit deprecation warnings affecting large third-party plugins.</p>
     *
     * If a configuration with this name already exists this method will <strong>overwrite</strong> its current usage to match what
     * would be set if the configuration needed to be created and emit an additional deprecation warning when doing this
     * <strong>IFF</strong> {@code verifyPrexisting} is set to {@code true}.  Otherwise, it will throw a
     * {@link GradleException}.
     */
    Configuration maybeCreateDependencyScopeLocked(String name, boolean verifyPrexisting);
}
