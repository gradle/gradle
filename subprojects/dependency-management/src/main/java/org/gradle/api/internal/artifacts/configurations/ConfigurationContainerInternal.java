/*
 * Copyright 2011 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;

public interface ConfigurationContainerInternal extends ConfigurationContainer {
    @Override
    ConfigurationInternal getByName(String name) throws UnknownConfigurationException;
    @Override
    ConfigurationInternal detachedConfiguration(Dependency... dependencies);

    default ConfigurationInternal createWithRole(String name, ConfigurationRole role) {
        return createWithRole(name, role, true);
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     */
    default ConfigurationInternal createWithRole(String name, ConfigurationRole role, boolean lockRole) {
        ConfigurationInternal configuration = (ConfigurationInternal) create(name);
        RoleAssigner.assignRoleAtCreation(configuration, role, lockRole);
        return configuration;
    }

    default ConfigurationInternal maybeCreateWithRole(String name, ConfigurationRole role) {
        return maybeCreateWithRole(name, role, true, true);
    }

    /**
     * Creates a new configuration in the same manner as {@link #maybeCreate(String)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     *
     * If the configuration already exists, this method will <strong>NOT</strong>> change anything about it,
     * including its role.
     */
    default ConfigurationInternal maybeCreateWithRole(String name, ConfigurationRole role, boolean lockRole, boolean assertInRole) {
        ConfigurationInternal configuration = (ConfigurationInternal) findByName(name);
        if (configuration == null) {
            return createWithRole(name, role, lockRole);
        } else {
            if (assertInRole) {
                RoleAssigner.assertIsInRole(configuration, role);
            }
            return configuration;
        }
    }

    default ConfigurationInternal createWithRole(String name, ConfigurationRole role, Closure<? super Configuration> configureClosure) throws InvalidUserDataException {
        return createWithRole(name, role, true, configureClosure);
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String, Closure)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     */
    default ConfigurationInternal createWithRole(String name, ConfigurationRole role, boolean lockRole, Closure<? super Configuration> configureClosure) throws InvalidUserDataException {
        ConfigurationInternal configuration = (ConfigurationInternal) create(name, configureClosure);
        RoleAssigner.assignRoleAtCreation(configuration, role, lockRole);
        return configuration;
    }

    default ConfigurationInternal createWithRole(String name, ConfigurationRole role, Action<? super Configuration> configureAction) throws InvalidUserDataException {
        return createWithRole(name, role, true, configureAction);
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String, Action)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     */
    default ConfigurationInternal createWithRole(String name, ConfigurationRole role, boolean lockRole, Action<? super Configuration> configureAction) throws InvalidUserDataException {
        ConfigurationInternal configuration = (ConfigurationInternal) create(name, configureAction);
        RoleAssigner.assignRoleAtCreation(configuration, role, lockRole);
        return configuration;
    }

    abstract class RoleAssigner {
        private RoleAssigner() { /* not instantiable */ }

        /**
         * Assigns a usage role to a configuration at creation time, by setting internal usage flags (e.g. {@link ConfigurationInternal#isCanBeResolved()})
         * and/or marking such usages as deprecated.
         *
         * @return the given configuration; now configured for a role
         */
        public static void assignRoleAtCreation(ConfigurationInternal configuration, ConfigurationRole role, boolean lockRole) {
            configuration.setRoleAtCreation(role);
            configuration.setCanBeConsumed(role.isConsumable());
            configuration.setCanBeResolved(role.isResolvable());
            configuration.setCanBeDeclaredAgainst(role.isDeclarableAgainst());
            configuration.setDeprecatedForConsumption(role.isConsumptionDeprecated());
            configuration.setDeprecatedForResolution(role.isResolutionDeprecated());
            configuration.setDeprecatedForDeclarationAgainst(role.isDeclarationAgainstDeprecated());
            if (lockRole) {
                configuration.preventUsageMutation();
            }
        }

        public static boolean isUsageConsistentWithRole(ConfigurationInternal configuration, ConfigurationRole role) {
            return (role.isConsumable() == configuration.isCanBeConsumed())
                    && (role.isResolvable() == configuration.isCanBeResolved())
                    && (role.isDeclarableAgainst() == configuration.isCanBeDeclaredAgainst())
                    && (role.isConsumptionDeprecated() == configuration.isDeprecatedForConsumption())
                    && (role.isResolutionDeprecated() == configuration.isDeprecatedForResolution())
                    && (role.isDeclarationAgainstDeprecated() == configuration.isDeprecatedForDeclarationAgainst());
        }

        public static void assertIsInRole(ConfigurationInternal configuration, ConfigurationRole role) {
            if (!isUsageConsistentWithRole(configuration, role)) {
                throw new IllegalStateException("Configuration '" + configuration.getName() + "' usage is not consistent with the role: " + role.getName());
            }
        }
    }
}
