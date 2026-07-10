/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.component.ComponentIdentifier;

/**
 * Identifies the "implicit" artifact variant for a graph variant.
 */
public class ComponentConfigurationIdentifier implements VariantResolveMetadata.Identifier {
    private final ComponentIdentifier component;
    private final String configurationName;
    private final int hashCode;

    public ComponentConfigurationIdentifier(ComponentIdentifier component, String configurationName) {
        this.component = component;
        this.configurationName = configurationName;

        this.hashCode = computeHashCode(component, configurationName);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ComponentConfigurationIdentifier other = (ComponentConfigurationIdentifier) obj;
        return component.equals(other.component) && configurationName.equals(other.configurationName);
    }

    private static int computeHashCode(ComponentIdentifier component, String configurationName) {
        return 31 * component.hashCode() + configurationName.hashCode();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
