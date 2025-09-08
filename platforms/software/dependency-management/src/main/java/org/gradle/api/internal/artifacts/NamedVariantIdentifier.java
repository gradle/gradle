/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.VariantIdentifier;

/**
 * Identifier for a variant of a component, identified by its name.
 */
public class NamedVariantIdentifier implements VariantIdentifier {

    private final ComponentIdentifier componentIdentifier;
    private final String name;
    private final int hashCode;

    public NamedVariantIdentifier(
        ComponentIdentifier componentIdentifier,
        String name
    ) {
        this.componentIdentifier = componentIdentifier;
        this.name = name;
        this.hashCode = computeHashCode(componentIdentifier, name);
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return name + "(" + componentIdentifier.getDisplayName() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NamedVariantIdentifier that = (NamedVariantIdentifier) o;
        return componentIdentifier.equals(that.componentIdentifier) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int computeHashCode(ComponentIdentifier componentIdentifier, String name) {
        int result = componentIdentifier.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }
}
