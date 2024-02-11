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

import org.gradle.internal.deprecation.DeprecatableConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This static util class can be used to build a human-readable description of the usage a role or configuration allows.
 */
public abstract class UsageDescriber {
    public static final String DEFAULT_CUSTOM_ROLE_NAME = "Custom Role";

    private static final String CONSUMABLE = "Consumable - this configuration can be selected by another project as a dependency";
    private static final String RESOLVABLE = "Resolvable - this configuration can be resolved by this project to a set of files";
    private static final String DECLARABLE_AGAINST = "Declarable - this configuration can have dependencies added to it";
    private static final String UNUSABLE = "This configuration does not allow any usage";

    private static final String IS_DEPRECATED = "(but this behavior is marked deprecated)";

    private UsageDescriber() { /* not instantiable */ }

    /**
     * Builds a human-readable description of the usage allowed by the given role.
     *
     * @param role the role to describe
     * @return a human-readable description of the role's allowed usage
     */
    public static String describeRole(ConfigurationRole role) {
        return describeUsage(role.isConsumable(), role.isResolvable(), role.isDeclarable(),
            role.isConsumptionDeprecated(), role.isResolutionDeprecated(), role.isDeclarationAgainstDeprecated());
    }

    /**
     * Builds a human-readable description of the current usage allowed by the given configuration.
     *
     * @param configuration the configuration to describe
     * @return a human-readable description of the role's allowed usage
     */
    public static String describeCurrentUsage(DeprecatableConfiguration configuration) {
        return describeUsage(configuration.isCanBeConsumed(), configuration.isCanBeResolved(), configuration.isCanBeDeclared(),
            configuration.isDeprecatedForConsumption(), configuration.isDeprecatedForResolution(), configuration.isDeprecatedForDeclarationAgainst());
    }

    /**
     * Builds a human-readable description of the usage allowed by the given set of flags.
     *
     * @param isConsumable whether the configuration is consumable
     * @param isResolvable whether the configuration is resolvable
     * @param isDeclarable whether the configuration is declarable
     * @param isConsumptionDeprecated whether the configuration's consumable behavior is deprecated
     * @param isResolutionDeprecated whether the configuration's resolvable behavior is deprecated
     * @param isDeclarationAgainstDeprecated whether the configuration's declarable behavior is deprecated
     * @return description of the given usage
     */
    public static String describeUsage(boolean isConsumable, boolean isResolvable, boolean isDeclarable,
                                       boolean isConsumptionDeprecated, boolean isResolutionDeprecated, boolean isDeclarationAgainstDeprecated) {
        List<String> descriptions = new ArrayList<>();
        if (isConsumable) {
            descriptions.add("\t" + CONSUMABLE + describeDeprecation(isConsumptionDeprecated));
        }
        if (isResolvable) {
            descriptions.add("\t" + RESOLVABLE + describeDeprecation(isResolutionDeprecated));
        }
        if (isDeclarable) {
            descriptions.add("\t" + DECLARABLE_AGAINST + describeDeprecation(isDeclarationAgainstDeprecated));
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
