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
