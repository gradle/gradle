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

import java.util.Objects;

/**
 * Default implementation of {@link ConfigurationRole}.
 */
public final class DefaultConfigurationRole implements ConfigurationRole {

    private final String name;
    private final boolean consumable;
    private final boolean resolvable;
    private final boolean declarable;
    private final boolean consumptionDeprecated;
    private final boolean resolutionDeprecated;
    private final boolean declarationDeprecated;

    public DefaultConfigurationRole(
        String name,
        boolean consumable,
        boolean resolvable,
        boolean declarable,
        boolean consumptionDeprecated,
        boolean resolutionDeprecated,
        boolean declarationDeprecated
    ) {
        this.name = name;
        this.consumable = consumable;
        this.resolvable = resolvable;
        this.declarable = declarable;
        this.consumptionDeprecated = consumptionDeprecated;
        this.resolutionDeprecated = resolutionDeprecated;
        this.declarationDeprecated = declarationDeprecated;
    }

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
    public boolean isDeclarable() {
        return declarable;
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
        return declarationDeprecated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultConfigurationRole that = (DefaultConfigurationRole) o;
        return consumable == that.consumable &&
            resolvable == that.resolvable &&
            declarable == that.declarable &&
            consumptionDeprecated == that.consumptionDeprecated &&
            resolutionDeprecated == that.resolutionDeprecated &&
            declarationDeprecated == that.declarationDeprecated &&
            name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, consumable, resolvable, declarable, consumptionDeprecated, resolutionDeprecated, declarationDeprecated);
    }

    @Override
    public String toString() {
        return getName();
    }
}
