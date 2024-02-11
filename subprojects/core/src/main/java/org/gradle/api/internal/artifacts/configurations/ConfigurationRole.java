/*
 * Copyright 2022 the original author or authors.
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

/**
 * Defines how a {@link org.gradle.api.artifacts.Configuration} is intended to be used.
 *
 * Standard roles are defined in {@link ConfigurationRoles}.
 *
 * @since 8.1
 */
public interface ConfigurationRole {
    /**
     * Returns a human-readable name for this role.
     */
    String getName();

    boolean isConsumable();
    boolean isResolvable();
    boolean isDeclarable();
    boolean isConsumptionDeprecated();
    boolean isResolutionDeprecated();
    boolean isDeclarationAgainstDeprecated();

    /**
     * Obtains a human-readable summary of the usage allowed by the given role.
     */
    default String describeUsage() {
        return UsageDescriber.describeRole(this);
    }

    /**
     * Checks that the current allowed usage of a configuration is the same as that specified by this role.
     *
     * Does not check deprecation status.
     *
     * @param configuration the configuration to check
     * @return {@code true} if so; {@code false} otherwise
     */
    default boolean isUsageConsistentWithRole(Configuration configuration) {
        return (isConsumable() == configuration.isCanBeConsumed())
            && (isResolvable() == configuration.isCanBeResolved())
            && (isDeclarable() == configuration.isCanBeDeclared());
    }
}
