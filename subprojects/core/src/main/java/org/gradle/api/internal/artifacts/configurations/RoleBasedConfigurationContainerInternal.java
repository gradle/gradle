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
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.Collections;
import java.util.List;

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
    Configuration consumableUnlocked(String name);

    /**
     * Creates a consumable configuration which can change roles and executes the provided
     * {@code action} against the configuration.
     */
    Configuration consumableUnlocked(String name, Action<? super Configuration> action);

    /**
     * Creates a resolvable configuration which can change roles.
     */
    Configuration resolvableUnlocked(String name);

    /**
     * Creates a resolvable configuration which can change roles and executes the provided
     * {@code action} against the configuration.
     */
    Configuration resolvableUnlocked(String name, Action<? super Configuration> action);

    /**
     * Creates a dependency scope configuration which can change roles.
     */
    Configuration dependencyScopeUnlocked(String name);

    /**
     * Creates a dependency scope configuration which can change role and executes the provided
     * {@code action} against the configuration.
     */
    Configuration dependencyScopeUnlocked(String name, Action<? super Configuration> action);

    /**
     * Creates a new configuration, which can change roles, with initial role {@code role}.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    Configuration migratingUnlocked(String name, ConfigurationRole role);

    /**
     * Creates a new configuration, which can change roles, with initial role {@code role},
     * and executes the provided {@code action} against the configuration.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    Configuration migratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action);

    /**
     * Creates a resolvable + dependency scope configuration which can change roles.
     *
     * @deprecated Whether concept of a resolvable + dependency scope configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependency scope configurations.
     */
    @Deprecated
    Configuration resolvableDependencyScopeUnlocked(String name);

    /**
     * Creates a resolvable + dependency scope configuration which can change roles and executes the provided
     * {@code action} against the configuration.
     *
     * @deprecated Whether concept of a resolvable + dependency scope configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependency scope configurations.
     */
    @Deprecated
    Configuration resolvableDependencyScopeUnlocked(String name, Action<? super Configuration> action);

    /**
     * If a configuration with the given name already exists, return it.
     * Otherwise, creates a new resolvable configuration with the given name.
     *
     * If a configuration with this name already exists this method will <strong>overwrite</strong> its current usage to match what
     * would be set if the configuration needed to be created; it will emit an additional deprecation warning when doing this.
     */
    Configuration maybeCreateResolvableUnlocked(String name);

    /**
     * If a configuration with the given name already exists, return it.
     * Otherwise, creates a new consumable configuration with the given name.
     *
     * If a configuration with this name already exists this method will <strong>overwrite</strong> its current usage to match what
     * would be set if the configuration needed to be created; it will emit an additional deprecation warning when doing this.
     */
    Configuration maybeCreateConsumableUnlocked(String name);

    /**
     * If a configuration with the given name already exists, return it.
     * Otherwise, creates a new dependency scope configuration with the given name.
     *
     * If a configuration with this name already exists this method will <strong>overwrite</strong> its current usage to match what
     * would be set if the configuration needed to be created; it will emit an additional deprecation warning when doing this.
     */
    Configuration maybeCreateDependencyScopeUnlocked(String name);

    /**
     * If a configuration with the given name already exists,return it.
     * Otherwise, creates a new dependency scope configuration with the given name.
     *
     * <p>If {@code verifyPrexisting} is false, the normal deprecation warning will not be emitted. Setting this to false
     * should be avoided except in edge cases where it may emit deprecation warnings affecting large third-party plugins.</p>
     *
     * If a configuration with this name already exists this method will <strong>overwrite</strong> its current usage to match what
     * would be set if the configuration needed to be created and emit an additional deprecation warning when doing this
     * <strong>IFF</strong> {@code verifyPrexisting} is set to {@code true}.
     */
    Configuration maybeCreateDependencyScopeUnlocked(String name, boolean verifyPrexisting);

    /**
     * If a configuration with the given name already exists, return it.
     * Otherwise, creates a new configuration with the given name.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     *
     * If a configuration with this name already exists this method will <strong>overwrite</strong> its current usage to match what
     * would be set if the configuration needed to be created and emit an additional deprecation warning.
     *
     * @throws org.gradle.api.InvalidUserDataException If a non-migration role is used.
     */
    Configuration maybeCreateMigratingUnlocked(String name, ConfigurationRole role);

    /**
     * If a configuration with the given name already exists, return it.
     * Otherwise, creates a new resolvable + dependency scope configuration with the given name.
     *
     * If a configuration with this name already exists this method will <strong>overwrite</strong> its current usage to match what
     * would be set if the configuration needed to be created and emit an additional deprecation warning.
     *
     * @deprecated Whether concept of a resolvable + dependency scope configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependency scope configurations.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    Configuration maybeCreateResolvableDependencyScopeUnlocked(String name);

    /**
     * If a configuration with the given name already exists, verify it's usage matches the expected role and return it.
     * Otherwise, create a new configuration as defined by the given {@code request}.
     *
     * @param request information about the desired configuration
     * @return the configuration that was created or already existed
     * @throws GradleException if the request cannot be fulfilled
     */
    Configuration maybeCreate(AbstractRoleBasedConfigurationCreationRequest request);

    /**
     * An {@code abstract} implementation of {@link ConfigurationCreationRequest} to be extended by any
     * creation context that involves pre-defined {@link ConfigurationRole}s.
     *
     * This abstract type provides support for warning and error messages related to the need to mutate
     * the role of an existing configuration to match a request.
     */
    abstract class AbstractRoleBasedConfigurationCreationRequest implements ConfigurationCreationRequest {
        protected final String configurationName;
        protected final ConfigurationRole role;

        protected AbstractRoleBasedConfigurationCreationRequest(String configurationName, ConfigurationRole role) {
            this.configurationName = configurationName;
            this.role = role;
        }

        @Override
        public String getConfigurationName() {
            return configurationName;
        }

        public ConfigurationRole getRole() {
            return role;
        }

        protected String getUsageDiscoveryMessage(DeprecatableConfiguration conf) {
            String currentUsageDesc = UsageDescriber.describeCurrentUsage(conf);
            return String.format("Configuration %s already exists with permitted usage(s):\n" +
                "%s\n", getConfigurationName(), currentUsageDesc);
        }

        protected String getUsageExpectationMessage(@SuppressWarnings("unused") DeprecatableConfiguration conf) {
            String expectedUsageDesc = UsageDescriber.describeRole(getRole());
            return String.format("Yet Gradle expected to create it with the usage(s):\n" +
                "%s\n" +
                "Gradle will mutate the usage of configuration %s to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names", expectedUsageDesc, getConfigurationName());
        }

        /**
         * Issues a deprecation warning when a configuration already exists and Gradle needs to mutate its
         * usage to match the role in the request.
         *
         * @param conf the existing configuration
         */
        public void warnAboutNeedToMutateUsage(DeprecatableConfiguration conf) {
            String msgDiscovery = getUsageDiscoveryMessage(conf);
            String msgExpectation = getUsageExpectationMessage(conf);

            DeprecationLogger.deprecate(msgDiscovery + msgExpectation)
                .withAdvice(ConfigurationCreationRequest.getDefaultReservedNameAdvice(configurationName))
                .willBecomeAnErrorInGradle9()
                .withUserManual("authoring_maintainable_build_scripts", "sec:dont_anticipate_configuration_creation")
                .nagUser();
        }

        /**
         * Throws an exception when Gradle fails to mutate the usage of a pre-existing configuration.
         * @throws UnmodifiableUsageException always
         */
        public void failOnInabilityToMutateUsage() {
            throw new UnmodifiableUsageException(getConfigurationName(), Collections.singletonList(ConfigurationCreationRequest.getDefaultReservedNameAdvice(getConfigurationName())));
        }

        /**
         * An exception thrown when Gradle cannot mutate the usage of a configuration that already
         * exists, but does not match the expected usage for the given role.
         */
        public static class UnmodifiableUsageException extends GradleException implements ResolutionProvider {
            private final List<String> resolutions;

            public UnmodifiableUsageException(String configurationName, List<String> resolutions) {
                super(String.format("Gradle cannot mutate the usage of configuration '%s' because it is locked.", configurationName));
                this.resolutions = resolutions;
            }

            @Override
            public List<String> getResolutions() {
                return Collections.unmodifiableList(resolutions);
            }
        }
    }
}
