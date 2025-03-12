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
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.DomainObjectCollectionInternal;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Extends {@link ConfigurationContainer} to define internal-only methods for creating configurations.
 * <p>
 * TODO: Merge this class with ConfigurationContainerInternal
 * <p>
 * <strong>New configurations should leverage the role-based factory methods on {@link ConfigurationContainer}.</strong>
 */
@ServiceScope(Scope.Project.class)
public interface RoleBasedConfigurationContainerInternal extends ConfigurationContainer, DomainObjectCollectionInternal<Configuration> {

    /**
     * Registers a new configuration, with initial role {@code role}.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    NamedDomainObjectProvider<? extends Configuration> migrating(String name, ConfigurationRole role);

    /**
     * Registers a new configuration, with initial role {@code role},
     * and executes the provided {@code action} against the configuration.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    NamedDomainObjectProvider<? extends Configuration> migrating(String name, ConfigurationRole role, Action<? super Configuration> action);

    /**
     * Registers a resolvable + dependency scope configuration.
     *
     * @deprecated Whether concept of a resolvable + dependency scope configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependency scope configurations.
     */
    @Deprecated
    NamedDomainObjectProvider<? extends Configuration> resolvableDependencyScope(String name);

    /**
     * Registers a resolvable + dependency scope configuration and executes the provided
     * {@code action} against the configuration.
     *
     * @deprecated Whether concept of a resolvable + dependency scope configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependency scope configurations.
     */
    @Deprecated
    NamedDomainObjectProvider<? extends Configuration> resolvableDependencyScope(String name, Action<? super Configuration> action);

    /**
     * Registers a new configuration as defined by the given {@code context}.
     *
     * @param name the name of the configuration to create
     * @param role the role of the configuration to create
     * @param context information about the desired configuration
     * @param configureAction the action to apply to the configuration.
     *
     * @return the configuration that was created or already existed
     *
     * @throws GradleException if the request cannot be fulfilled
     */
    NamedDomainObjectProvider<? extends Configuration> registerWithContext(String name, ConfigurationRole role, RoleBasedConfigurationCreationRequest context, Action<? super Configuration> configureAction);

}
