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

import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;

public interface ConfigurationContainerInternal extends ConfigurationContainer {
    @Override
    ConfigurationInternal getByName(String name) throws UnknownConfigurationException;
    @Override
    ConfigurationInternal detachedConfiguration(Dependency... dependencies);

    ConfigurationInternal consumable(String name, boolean lockRole);
    ConfigurationInternal resolvable(String name, boolean lockRole);
    ConfigurationInternal bucket(String name, boolean lockRole);

    default ConfigurationInternal consumable(String name) {
        return consumable(name, false);
    }

    default ConfigurationInternal resolvable(String name) {
        return resolvable(name, false);
    }

    default ConfigurationInternal bucket(String name) {
        return bucket(name, false);
    }

    ConfigurationInternal deprecatedConsumable(String name, boolean lockRole);
    ConfigurationInternal deprecatedResolvable(String name, boolean lockRole);

    default ConfigurationInternal deprecatedConsumable(String name) {
        return deprecatedConsumable(name, false);
    }

    default ConfigurationInternal deprecatedResolvable(String name) {
        return deprecatedResolvable(name, false);
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String)}, and then
     * immediately assigns it a role by setting internal status flags to mark possible usage options
     * for the configuration.
     */
    ConfigurationInternal createWithRole(String name, ConfigurationRole role, boolean lockRole);

    default ConfigurationInternal createWithRole(String name, ConfigurationRole role) {
        return createWithRole(name, role, false);
    }

    /**
     * If it does not already exist, creates a new configuration in the same manner as {@link #maybeCreate(String)}, and then
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

    abstract class RoleAssigner {
        private RoleAssigner() { /* not instantiable */ }

        public static boolean isUsageConsistentWithRole(ConfigurationInternal configuration, ConfigurationRole role) {
            return (role.isConsumable() == configuration.isCanBeConsumed())
                    && (role.isResolvable() == configuration.isCanBeResolved())
                    && (role.isDeclarableAgainst() == configuration.isCanBeDeclaredAgainst())
                    && (role.isConsumptionDeprecated() == configuration.isDeprecatedForConsumption())
                    && (role.isResolutionDeprecated() == configuration.isDeprecatedForResolution())
                    && (role.isDeclarationAgainstDeprecated() == configuration.isDeprecatedForDeclarationAgainst());
        }

        public static String describeDifferenceFromRole(ConfigurationInternal configuration, ConfigurationRole role) {
            if (!isUsageConsistentWithRole(configuration, role)) {
                return "Usage for configuration: " + configuration.getName() + " is not consistent with the role: " + role.getName() + ".\n" +
                        "Expected that it is:\n" +
                        role.describe() + "\n" +
                        "But is actually is:\n" +
                        "\tconsumable=" + configuration.isCanBeConsumed() +
                        ", resolvable=" + configuration.isCanBeResolved() +
                        ", declarableAgainst=" + configuration.isCanBeDeclaredAgainst() +
                        ", deprecatedForConsumption=" + configuration.isDeprecatedForConsumption() +
                        ", deprecatedForResolution=" + configuration.isDeprecatedForResolution() +
                        ", deprecatedForDeclarationAgainst=" + configuration.isDeprecatedForDeclarationAgainst();
            } else {
                return "Usage for configuration: " + configuration.getName() + " is consistent with the role: " + role.getName() + ".";
            }
        }

        public static void assertIsInRole(ConfigurationInternal configuration, ConfigurationRole role) {
            if (!isUsageConsistentWithRole(configuration, role)) {
                throw new IllegalStateException(describeDifferenceFromRole(configuration, role));
            }
        }
    }
}
