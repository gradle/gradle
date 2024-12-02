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

import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.Set;

/**
 * Defines {@link ConfigurationRole}s representing common allowed usage patterns.
 *
 * These should be preferred over defining custom roles; whenever possible.  Use {@link #byUsage(boolean, boolean, boolean)}
 * to attempt to locate a matching role by its usage characteristics.
 *
 * @since 8.1
 */
public final class ConfigurationRoles {

    private ConfigurationRoles() {
        // Private to prevent instantiation.
    }

    /**
     * An unrestricted configuration, which can be used for any purpose.
     *
     * This is available for backwards compatibility, but should not be used for new configurations.  It is
     * the default role for configurations created when another more specific role is <strong>not</strong> specified.
     */
    @Deprecated
    public static final ConfigurationRole LEGACY = createNonDeprecatedRole("Legacy", true, true, true);

    /**
     * Meant to be used only for consumption by other projects.
     */
    public static final ConfigurationRole CONSUMABLE = createNonDeprecatedRole("Consumable", true, false, false);

    /**
     * Meant to be used only for resolving dependencies.
     */
    public static final ConfigurationRole RESOLVABLE = createNonDeprecatedRole("Resolvable", false, true, false);

    /**
     * Meant as a temporary solution for situations where we need to declare dependencies against a resolvable configuration.
     *
     * These situations should be updated to use a separate dependency scope configuration for declaring dependencies and extend it with a separate resolvable configuration.
     */
    @Deprecated
    public static final ConfigurationRole RESOLVABLE_DEPENDENCY_SCOPE = createNonDeprecatedRole("Resolvable Dependency Scope", false, true, true);

    /**
     * Meant as a temporary solution for situations where we need to declare dependencies against a consumable configuration.
     *
     * This <strong>SHOULD NOT</strong> be necessary, and is a symptom of an over-permissive configuration.
     */
    @Deprecated
    public static final ConfigurationRole CONSUMABLE_DEPENDENCY_SCOPE = createNonDeprecatedRole("Consumable Dependency Scope", true, false, true);

    /**
     * Meant to be used only for declaring dependencies.
     *
     * AKA {@code DECLARABLE}.
     */
    public static final ConfigurationRole DEPENDENCY_SCOPE = createNonDeprecatedRole("Dependency Scope", false, false, true);

    /**
     * Exclusively intended as a target migration role for configurations intended to be removed in the next major version.
     */
    public static final ConfigurationRole REMOVED = createNonDeprecatedRole("None", false, false, false);

    /**
     * Creates a new role which is not deprecated for any usage.
     */
    private static ConfigurationRole createNonDeprecatedRole(String name, boolean consumable, boolean resolvable, boolean declarable) {
        return new DefaultConfigurationRole(name, consumable, resolvable, declarable, false, false, false);
    }

    private static final Set<ConfigurationRole> ALL = ImmutableSet.of(
        LEGACY, CONSUMABLE, RESOLVABLE, RESOLVABLE_DEPENDENCY_SCOPE, CONSUMABLE_DEPENDENCY_SCOPE, DEPENDENCY_SCOPE
    );

    /**
     * Locates a pre-defined role allowing the given usage.
     *
     * @param consumable whether this role is consumable
     * @param resolvable whether this role is resolvable
     * @param declarable whether this role is declarable
     *
     * @return the role enum token with matching usage characteristics, if one exists; otherwise {@link Optional#empty()}
     */
    public static Optional<ConfigurationRole> byUsage(boolean consumable, boolean resolvable, boolean declarable) {
        for (ConfigurationRole role : ALL) {
            if (role.isConsumable() == consumable && role.isResolvable() == resolvable && role.isDeclarable() == declarable) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
