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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines how a {@link org.gradle.api.artifacts.Configuration} is intended to be used.
 *
 * Standard roles are defined in {@link ConfigurationRoles}.
 *
 * @since 8.0
 */
public interface ConfigurationRole {
    String getName();

    boolean isConsumable();
    boolean isResolvable();
    boolean isDeclarableAgainst();
    boolean isConsumptionDeprecated();
    boolean isResolutionDeprecated();
    boolean isDeclarationAgainstDeprecated();

    /**
     * Obtains a human-readable description of the usage allowed by the given role.
     */
    default String describe() {
        return RoleDescriber.describeRole(this);
    }

    /**
     * Attempts to locate a pre-defined role allowing the given usage in {@link ConfigurationRoles} and return it;
     * if such a roles does not exist, creates a custom anonymous implementation and return it instead.
     *
     * @param consumable whether this role is consumable
     * @param resolvable whether this role is resolvable
     * @param declarableAgainst whether this role is declarable against
     * @param consumptionDeprecated whether this role is deprecated for consumption
     * @param resolutionDeprecated whether this role is deprecated for resolution
     * @param declarationAgainstDeprecated whether this role is deprecated for declaration against
     *
     * @return a role with matching usage characteristics
     */
    static ConfigurationRole forUsage(boolean consumable, boolean resolvable, boolean declarableAgainst, boolean consumptionDeprecated, boolean resolutionDeprecated, boolean declarationAgainstDeprecated) {
        return forUsage(RoleDescriber.CUSTOM_ROLE_NAME, consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated);
    }

    static ConfigurationRole forUsage(String name, boolean consumable, boolean resolvable, boolean declarableAgainst, boolean consumptionDeprecated, boolean resolutionDeprecated, boolean declarationAgainstDeprecated) {
        return forUsage(name, consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated, null);
    }

    static ConfigurationRole forUsage(String name, boolean consumable, boolean resolvable, boolean declarableAgainst, boolean consumptionDeprecated, boolean resolutionDeprecated, boolean declarationAgainstDeprecated, @Nullable String description) {
        return ConfigurationRoles.byUsage(consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated)
                .map(ConfigurationRole.class::cast)
                .orElse(new ConfigurationRole() {
                    @Override
                    public String getName() {
                        return name;
                    }

                    @Override
                    public boolean isConsumable() {
                        return consumable;
                    }

                    @Override
                    public boolean isResolvable() {
                        return resolvable;
                    }

                    @Override
                    public boolean isDeclarableAgainst() {
                        return declarableAgainst;
                    }

                    @Override
                    public boolean isConsumptionDeprecated() {
                        return consumptionDeprecated;
                    }

                    @Override
                    public boolean isResolutionDeprecated() {
                        return resolutionDeprecated;
                    }

                    @Override
                    public boolean isDeclarationAgainstDeprecated() {
                        return declarationAgainstDeprecated;
                    }

                    @Override
                    public String describe() {
                        if (description != null) {
                            return description;
                        } else {
                            return RoleDescriber.describeRole(this);
                        }
                    }
                });
    }

    /**
     * Can build a human-readable description of the usage a {@link ConfigurationRole} allows on a {@link org.gradle.api.artifacts.Configuration}.
     */
    abstract class RoleDescriber {
        private static final String CUSTOM_ROLE_NAME = "Custom Role";

        private static final String CONSUMABLE = "Consumable - this configuration can be selected by another project as a dependency";
        private static final String RESOLVABLE = "Resolvable - this configuration can be resolved by this project to a set of files";
        private static final String DECLARABLE_AGAINST = "Declarable Against - this configuration can have dependencies added to it";
        private static final String UNUSABLE = "Consumption Deprecated - this configuration should not be selected by another project as a dependency";

        private static final String IS_DEPRECATED = "(but this behavior is marked deprecated)";

        private RoleDescriber() { /* not instantiable */ }

        /**
         * Builds a human-readable description of the usage allowed by the given role.
         *
         * @param role the role to describe
         * @return a human-readable description of the role's allowed usage
         */
        public static String describeRole(ConfigurationRole role) {
            List<String> descriptions = new ArrayList<>();
            if (role.isConsumable()) {
                descriptions.add("\t" + CONSUMABLE + describeDeprecation(role.isConsumptionDeprecated()));
            }
            if (role.isResolvable()) {
                descriptions.add("\t" + RESOLVABLE + describeDeprecation(role.isResolutionDeprecated()));
            }
            if (role.isDeclarableAgainst()) {
                descriptions.add("\t" + DECLARABLE_AGAINST + describeDeprecation(role.isDeclarationAgainstDeprecated()));
            }
            if (descriptions.isEmpty()) {
                descriptions.add("\t" + UNUSABLE);
            }
            return String.join("\n", descriptions);
        }

        private static String describeDeprecation(boolean deprecated) {
            return deprecated ? " " + IS_DEPRECATED : "";
        }
    }
}
