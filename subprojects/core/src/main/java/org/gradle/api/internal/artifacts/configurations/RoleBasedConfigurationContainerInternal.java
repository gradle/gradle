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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependenciesConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;

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
    ConsumableConfiguration consumableUnlocked(String name);

    /**
     * Creates a resolvable configuration which can change roles.
     */
    ResolvableConfiguration resolvableUnlocked(String name);

    /**
     * Creates a dependencies configuration which can change roles.
     */
    DependenciesConfiguration dependenciesUnlocked(String name);

    /**
     * Creates a new configuration, which can change roles, with initial role {@code role}.
     * Intended only for use with roles defined in {@link ConfigurationRolesForMigration}.
     */
    Configuration migratingUnlocked(String name, ConfigurationRole role);

    /**
     * Creates a resolvable + dependencies configuration which can change roles.
     *
     * @deprecated Whether concept of a resolvable + dependencies configuration should exist
     * is still under debate. However, in general, we should try to split up configurations which
     * have this role into separate resolvable and dependencies configurations.
     */
    @Deprecated
    Configuration resolvableDependenciesUnlocked(String name);

    default Configuration maybeCreateResolvable(String name) {
        Configuration configuration = findByName(name);
        if (configuration == null) {
            return resolvableUnlocked(name);
        }

        DeprecationEmitter.configurationExists(name);
        return configuration;
    }

    default Configuration maybeCreateConsumableUnlocked(String name) {
        Configuration configuration = findByName(name);
        if (configuration == null) {
            return consumableUnlocked(name);
        }

        DeprecationEmitter.configurationExists(name);
        return configuration;
    }

    default Configuration maybeCreateDependenciesUnlocked(String name) {
        return maybeCreateDependenciesUnlocked(name, true);
    }

    default Configuration maybeCreateDependenciesUnlocked(String name, boolean warnOnDuplicate) {
        Configuration configuration = findByName(name);
        if (configuration == null) {
            return dependenciesUnlocked(name);
        }

        if (warnOnDuplicate) {
            DeprecationEmitter.configurationExists(name);
        }
        return configuration;
    }

    default Configuration maybeCreateMigratingUnlocked(String name, ConfigurationRole role) {
        Configuration configuration = findByName(name);
        if (configuration == null) {
            return migratingUnlocked(name, role);
        }

        DeprecationEmitter.configurationExists(name);
        return configuration;
    }

    @Deprecated
    default Configuration maybeCreateResolvableDependenciesUnlocked(String name) {
        Configuration configuration = findByName(name);
        if (configuration == null) {
            return resolvableDependenciesUnlocked(name);
        }

        DeprecationEmitter.configurationExists(name);
        return configuration;
    }

    /**
     * This static util class hides methods internal to the {@code default} methods in this interface.
     */
    final class DeprecationEmitter {
        private DeprecationEmitter() { /* not instantiable */ }

        public static void configurationExists(String configurationName) {
            DeprecationLogger.deprecateBehaviour("The configuration " + configurationName + " was created explicitly. This configuration name is reserved for creation by Gradle.")
                .withAdvice("Do not create a configuration with this name.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "configurations_allowed_usage")
                .nagUser();
        }
    }
}
