/*
 * Copyright 2010 the original author or authors.
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

/**
 * A node identifier that uniquely identifies a variant within a component by the variant's name.
 *
 * Note: Generally, variants should be identified by their attributes and capabilities, as the name
 * is more of a human-readable identifier.
 */
public class ComponentVariantNodeIdentifier implements NodeIdentifier {
    private final ComponentIdentifier componentId;
    private final String variantName;
    private final int hashCode;

    public ComponentVariantNodeIdentifier(ComponentIdentifier componentId, String variantName) {
        this.componentId = componentId;
        this.variantName = variantName;
        this.hashCode = 31 * componentId.hashCode() + variantName.hashCode();
    }

    @Override
    public String toString() {
        return componentId.getDisplayName() + ":" + variantName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ComponentVariantNodeIdentifier that = (ComponentVariantNodeIdentifier) o;

        if (!componentId.equals(that.componentId)) {
            return false;
        }
        return variantName.equals(that.variantName);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
