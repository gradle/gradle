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

public interface ConfigurationRole {
    boolean isConsumable();
    boolean isResolvable();
    boolean isDeclarableAgainst();
    boolean isConsumptionDeprecated();
    boolean isResolutionDeprecated();
    boolean isDeclarationAgainstDeprecated();

    default String describe() {
        return RoleDescriber.describeRole(this);
    }

    static ConfigurationRole forUsage(boolean consumable, boolean resolvable, boolean declarableAgainst, boolean consumptionDeprecated, boolean resolutionDeprecated, boolean declarationAgainstDeprecated) {
        return new ConfigurationRole() {
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
        };
    }

    final class RoleDescriber {
        public static String describeRole(ConfigurationRole role) {
            List<String> descriptions = new ArrayList<>();
            if (role.isConsumable()) {
                descriptions.add("\tConsumable - this configuration can be selected by another project as a dependency" + describeDeprecation(role.isConsumptionDeprecated()));
            }
            if (role.isResolvable()) {
                descriptions.add("\tResolvable - this configuration can be resolved by this project to a set of files" + describeDeprecation(role.isResolutionDeprecated()));
            }
            if (role.isDeclarableAgainst()) {
                descriptions.add("\tDeclarable Against - this configuration can have dependencies added to it" + describeDeprecation(role.isDeclarationAgainstDeprecated()));
            }
            if (descriptions.isEmpty()) {
                descriptions.add("\tUnusable - this configuration cannot be used for any purpose");
            }
            return String.join("\n", descriptions);
        }

        private static String describeDeprecation(boolean deprecated) {
            return deprecated ? " (but this behavior is marked deprecated)" : "";
        }
    }
}
