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

import org.apache.commons.lang.WordUtils;

import java.util.Optional;

/**
 * Defines {@link ConfigurationRole}s representing common allowed usage patterns.
 *
 * These should be preferred over defining custom roles; whenever possible.  Use {@link #byUsage(boolean, boolean, boolean, boolean, boolean, boolean)}
 * to attempt to locate a matching role by its usage characteristics.
 *
 * @since 8.1
 */
public enum ConfigurationRoles implements ConfigurationRole {
    /**
     * An unrestricted configuration, which can be used for any purpose.
     *
     * This is available for backwards compatibility, but should not be used for new configurations.  It is
     * the default role for configurations created when another more specific role is <strong>not</strong> specified.
     */
    @Deprecated
    LEGACY(true, true, true, false, false, false),

    /**
     * Meant to be used only for consumption by other projects.
     */
    INTENDED_CONSUMABLE(true, false, false, false, false, false),

    /**
     * Meant to be used only for resolving dependencies.
     */
    INTENDED_RESOLVABLE(false, true, false, false, false, false),

    /**
     * Meant as a temporary solution for situations where we need to declare dependencies against a resolvable configuration.
     *
     * These situations should be updated to use a separate bucket configuration for declaring dependencies and extend it with a separate resolvable configuration.
     */
    @Deprecated
    INTENDED_RESOLVABLE_BUCKET(false, true, true, false, false, false),

    /**
     * Meant to be used only for declaring dependencies.
     *
     * AKA {@code INTENDED_DECLARABLE}.
     */
    INTENDED_BUCKET(false, false, true, false, false, false),

    /**
     * Meant to be used only for consumption, permits other usage but emits warnings if used otherwise.
     */
    @Deprecated
    DEPRECATED_CONSUMABLE(true, true, true, false, true, true),

    /**
     * Meant to be used only for resolution, permits other usage but emits warnings if used otherwise.
     */
    @Deprecated
    DEPRECATED_RESOLVABLE(true, true, true, true, false, true);

    private final boolean consumable;
    private final boolean resolvable;
    private final boolean declarableAgainst;
    private final boolean consumptionDeprecated;
    private final boolean resolutionDeprecated;
    private final boolean declarationAgainstDeprecated;

    /**
     * Locates a pre-defined role allowing the given usage.
     *
     * @param consumable whether this role is consumable
     * @param resolvable whether this role is resolvable
     * @param declarableAgainst whether this role is declarable against
     * @param consumptionDeprecated whether this role is deprecated for consumption
     * @param resolutionDeprecated whether this role is deprecated for resolution
     * @param declarationAgainstDeprecated whether this role is deprecated for declaration against
     *
     * @return the role enum token with matching usage characteristics, if one exists; otherwise {@link Optional#empty()}
     */
    public static Optional<ConfigurationRoles> byUsage(boolean consumable, boolean resolvable, boolean declarableAgainst, boolean consumptionDeprecated, boolean resolutionDeprecated, boolean declarationAgainstDeprecated) {
        for (ConfigurationRoles role : values()) {
            if (role.consumable == consumable && role.resolvable == resolvable && role.declarableAgainst == declarableAgainst && role.consumptionDeprecated == consumptionDeprecated && role.resolutionDeprecated == resolutionDeprecated && role.declarationAgainstDeprecated == declarationAgainstDeprecated) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    ConfigurationRoles(boolean consumable, boolean resolvable, boolean declarableAgainst, boolean consumptionDeprecated, boolean resolutionDeprecated, boolean declarationAgainstDeprecated) {
        this.consumable = consumable;
        this.resolvable = resolvable;
        this.declarableAgainst = declarableAgainst;
        this.consumptionDeprecated = consumptionDeprecated;
        this.resolutionDeprecated = resolutionDeprecated;
        this.declarationAgainstDeprecated = declarationAgainstDeprecated;
    }

    @Override
    public String getName() {
        return upperSnakeToProperCase(name());
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

    private String upperSnakeToProperCase(String name) {
        return WordUtils.capitalizeFully(name.replaceAll("_", " "));
    }
}
