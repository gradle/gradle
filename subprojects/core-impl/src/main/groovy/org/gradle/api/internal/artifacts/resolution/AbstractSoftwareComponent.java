/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.resolution;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.resolution.SoftwareComponent;

public abstract class AbstractSoftwareComponent implements SoftwareComponent {
    private final ComponentIdentifier componentId;

    protected AbstractSoftwareComponent(ComponentIdentifier componentId) {
        this.componentId = componentId;
    }

    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        return componentId.equals(((AbstractSoftwareComponent) other).componentId);
    }

    @Override
    public final int hashCode() {
        return componentId.hashCode();
    }
}
