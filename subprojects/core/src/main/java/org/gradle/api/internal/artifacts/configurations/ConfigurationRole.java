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

import java.util.ArrayList;
import java.util.List;

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
    boolean isDeclarableAgainst();
    boolean isConsumptionDeprecated();
    boolean isResolutionDeprecated();
    boolean isDeclarationAgainstDeprecated();

    /**
     * Obtains a human-readable summary of the usage allowed by the given role.
     */
    default String describeUsage() {
        return RoleDescriber.describeRole(this);
    }

    /**
     * This static util class hides methods internal to the {@code default} methods of {@link ConfigurationRole} which
     * can be used to build a human-readable description of the usage a role allows.
     */
    abstract class RoleDescriber {
        private static final String CONSUMABLE = "Consumable - this configuration can be selected by another project as a dependency";
        private static final String RESOLVABLE = "Resolvable - this configuration can be resolved by this project to a set of files";
        private static final String DECLARABLE_AGAINST = "Declarable Against - this configuration can have dependencies added to it";
        private static final String UNUSABLE = "This configuration does not allow any usage";

        private static final String IS_DEPRECATED = "(but this behavior is marked deprecated)";

        private RoleDescriber() { /* not instantiable */ }

        /**
         * Builds a human-readable description of the usage allowed by the given role.
         *
         * @param role the role to describe
         * @return a human-readable description of the role's allowed usage
         */
        public static String describeRole(ConfigurationRole role) {
            return describeUsage(role.isConsumable(), role.isResolvable(), role.isDeclarableAgainst(), role.isConsumptionDeprecated(), role.isResolutionDeprecated(), role.isDeclarationAgainstDeprecated());
        }

        /**
         * Builds a human-readable description of the given usage.
         *
         * @param consumable whether consumption is allowed
         * @param resolvable whether resolution is allowed
         * @param declarableAgainst whether declaring dependencies is allowed
         * @param consumptionDeprecated whether consumption is deprecated
         * @param resolutionDeprecated whether resolution is deprecated
         * @param declarationAgainstDeprecated whether declaring dependencies is deprecated
         * @return a human-readable description of the given usage
         */
        public static String describeUsage(boolean consumable, boolean resolvable, boolean declarableAgainst, boolean consumptionDeprecated, boolean resolutionDeprecated, boolean declarationAgainstDeprecated) {
            List<String> descriptions = new ArrayList<>();
            if (consumable) {
                descriptions.add("\t" + CONSUMABLE + describeDeprecation(consumptionDeprecated));
            }
            if (resolvable) {
                descriptions.add("\t" + RESOLVABLE + describeDeprecation(resolutionDeprecated));
            }
            if (declarableAgainst) {
                descriptions.add("\t" + DECLARABLE_AGAINST + describeDeprecation(declarationAgainstDeprecated));
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
