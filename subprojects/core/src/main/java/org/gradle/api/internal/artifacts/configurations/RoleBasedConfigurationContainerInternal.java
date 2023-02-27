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
import org.gradle.internal.Actions;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

/**
 * Extends {@link ConfigurationContainer} with methods that can use {@link ConfigurationRole}s to
 * define the allowed usage of a configuration at creation time.
 * <p>
 * This is an internal API, and is not yet intended for use outside of the Gradle build.
 */
public interface RoleBasedConfigurationContainerInternal extends ConfigurationContainer {
    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_CONSUMABLE}.
     */
    default Configuration consumable(String name, boolean lockRole) {
        return createWithRole(name, ConfigurationRoles.INTENDED_CONSUMABLE, lockRole);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_RESOLVABLE}.
     */
    default Configuration resolvable(String name, boolean lockRole) {
        return createWithRole(name, ConfigurationRoles.INTENDED_RESOLVABLE, lockRole);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_RESOLVABLE_BUCKET}.
     */
    @SuppressWarnings("deprecation")
    default Configuration resolvableBucket(String name, boolean lockRole) {
        return createWithRole(name, ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET, lockRole);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_BUCKET}.
     */
    default Configuration bucket(String name, boolean lockRole) {
        return createWithRole(name, ConfigurationRoles.INTENDED_BUCKET, lockRole);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_CONSUMABLE} that is <strong>NOT</strong> locked
     * against further usage mutations.
     */
    default Configuration consumable(String name) {
        return consumable(name, false);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_RESOLVABLE} that is <strong>NOT</strong> locked
     * against further usage mutations.
     */
    default Configuration resolvable(String name) {
        return resolvable(name, false);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_RESOLVABLE_BUCKET} that is <strong>NOT</strong> locked
     * against further usage mutations.
     */
    default Configuration resolvableBucket(String name) {
        return resolvableBucket(name, false);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#INTENDED_BUCKET} that is <strong>NOT</strong> locked
     * against further usage mutations.
     */
    default Configuration bucket(String name) {
        return bucket(name, false);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#DEPRECATED_CONSUMABLE}.
     */
    @SuppressWarnings("deprecation")
    default Configuration deprecatedConsumable(String name, boolean lockRole) {
        return createWithRole(name, ConfigurationRoles.DEPRECATED_CONSUMABLE, lockRole);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#DEPRECATED_RESOLVABLE}.
     */
    @SuppressWarnings("deprecation")
    default Configuration deprecatedResolvable(String name, boolean lockRole) {
        return createWithRole(name, ConfigurationRoles.DEPRECATED_RESOLVABLE, lockRole);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#DEPRECATED_CONSUMABLE} that is <strong>NOT</strong> locked
     * against further usage mutations.
     */
    default Configuration deprecatedConsumable(String name) {
        return deprecatedConsumable(name, false);
    }

    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * using the role of {@link ConfigurationRoles#DEPRECATED_RESOLVABLE} that is <strong>NOT</strong> locked
     * against further usage mutations.
     */
    default Configuration deprecatedResolvable(String name) {
        return deprecatedResolvable(name, false);
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String)} using the given role
     * at creation.
     *
     * @param name the name of the configuration
     * @param role the role defining the configuration's allowed usage
     * @param lockUsage {@code true} if the configuration's allowed usage should be locked to prevent any changes; {@code false} otherwise
     * @param configureAction an action to run upon the configuration's creation to configure it
     * @return the new configuration
     */
    Configuration createWithRole(String name, ConfigurationRole role, boolean lockUsage, Action<? super Configuration> configureAction);

    /**
     * Creates a new configuration in the same manner as {@link #create(String)} using the given role
     * at creation.
     *
     * @param name the name of the configuration
     * @param role the role defining the configuration's allowed usage
     * @param lockUsage {@code true} if the configuration's allowed usage should be locked to prevent any changes; {@code false} otherwise
     * @return the new configuration
     */
    default Configuration createWithRole(String name, ConfigurationRole role, boolean lockUsage) {
        return createWithRole(name, role, lockUsage, Actions.doNothing());
    }

    /**
     * Creates a new configuration in the same manner as {@link #create(String)} using the given role
     * at creation and configuring it with the given action, without automatically locking the configuration's allowed usage.
     *
     * @param name the name of the configuration
     * @param role the role defining the configuration's allowed usage
     * @param configureAction an action to run upon the configuration's creation to configure it
     * @return the new configuration
     */
    default Configuration createWithRole(String name, ConfigurationRole role, Action<? super Configuration> configureAction) {
        return createWithRole(name, role, false, configureAction);
    }


    /**
     * Creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)}
     * without locking the configuration's allowed usage.
     */
    default Configuration createWithRole(String name, ConfigurationRole role) {
        return createWithRole(name, role, false);
    }

    /**
     * If it does not already exist, creates a new configuration in the same manner as {@link #createWithRole(String, ConfigurationRole, boolean)};
     * if the configuration does already exist, this method will <strong>NOT</strong>> change anything about its allowed,
     * including its role, but <strong>CAN</strong> optionally confirm that the current usage of the configuration
     * matches the given role and/or prevent any further changes to the configuration's allowed usage.
     *
     * @param name the name of the configuration
     * @param role the role defining the configuration's allowed usage
     * @param lockUsage {@code true} if the configuration's allowed usage should be locked to prevent any changes; {@code false} otherwise
     * @param assertInRole {@code true} if the configuration's current usage should be confirmed to match that specified by the given role
     * @return the new configuration
     */
    default Configuration maybeCreateWithRole(String name, ConfigurationRole role, boolean lockUsage, boolean assertInRole) {
        DeprecatableConfiguration configuration = (DeprecatableConfiguration) findByName(name);
        if (configuration == null) {
            return createWithRole(name, role, lockUsage);
        } else {
            if (assertInRole) {
                RoleChecker.assertIsInRole(configuration, role);
            }
            if (lockUsage) {
               configuration.preventUsageMutation();
            }
            return configuration;
        }
    }

    /**
     * This static util class hides methods internal to the {@code default} methods in the {@link RoleBasedConfigurationContainerInternal} interface.
     */
    abstract class RoleChecker {
        private RoleChecker() { /* not instantiable */ }

        /**
         * Checks that the current allowed usage of a configuration is the same as that specified by a given role.
         *
         * @param configuration the configuration to check
         * @param role the role to check against
         * @return {@code true} if so; {@code false} otherwise
         */
        public static boolean isUsageConsistentWithRole(DeprecatableConfiguration configuration, ConfigurationRole role) {
            return (role.isConsumable() == configuration.isCanBeConsumed())
                    && (role.isResolvable() == configuration.isCanBeResolved())
                    && (role.isDeclarableAgainst() == configuration.isCanBeDeclaredAgainst())
                    && (role.isConsumptionDeprecated() == configuration.isDeprecatedForConsumption())
                    && (role.isResolutionDeprecated() == configuration.isDeprecatedForResolution())
                    && (role.isDeclarationAgainstDeprecated() == configuration.isDeprecatedForDeclarationAgainst());
        }

        /**
         * Checks that the current allowed usage of a configuration is the same as that specified by a given role,
         * and throws an exception with a message describing the differences if not.
         *
         * @param configuration the configuration to check
         * @param role the role to check against
         */
        public static void assertIsInRole(DeprecatableConfiguration configuration, ConfigurationRole role) {
            if (!isUsageConsistentWithRole(configuration, role)) {
                throw new GradleException(describeDifferenceFromRole(configuration, role));
            }
        }

        private static String describeDifferenceFromRole(DeprecatableConfiguration configuration, ConfigurationRole role) {
            if (!isUsageConsistentWithRole(configuration, role)) {
                ConfigurationRole currentUsage = ConfigurationRole.forUsage(
                        configuration.isCanBeConsumed(), configuration.isCanBeResolved(), configuration.isCanBeDeclaredAgainst(),
                        configuration.isDeprecatedForConsumption(), configuration.isDeprecatedForResolution(), configuration.isDeprecatedForDeclarationAgainst());
                return "Usage for configuration: " + configuration.getName() + " is not consistent with the role: " + role.getName() + ".\n" +
                        "Expected that it is:\n" +
                        role.describeUsage() + "\n" +
                        "But is actually is:\n" +
                        currentUsage.describeUsage();
            } else {
                return "Usage for configuration: " + configuration.getName() + " is consistent with the role: " + role.getName() + ".";
            }
        }
    }
}
