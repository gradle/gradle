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
 * A node identifier uniquely identified by a component identifier and a variant name.
 */
public class ComponentVariantIdentifier implements NodeIdentifier {
    private final ComponentIdentifier componentId;
    private final String variantName;

    public ComponentVariantIdentifier(ComponentIdentifier componentId, String variantName) {
        this.componentId = componentId;
        this.variantName = variantName;
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

        ComponentVariantIdentifier that = (ComponentVariantIdentifier) o;

        if (!componentId.equals(that.componentId)) {
            return false;
        }
        return variantName.equals(that.variantName);
    }

    @Override
    public int hashCode() {
        return componentId.hashCode() ^ variantName.hashCode();
    }
}
