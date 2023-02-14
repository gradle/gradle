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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;

import java.util.List;

/**
 * Identifier of a variant of a component.
 */
public class ComponentVariantIdentifier {

    private final ComponentIdentifier componentId;
    private final AttributeContainer attributes;
    private final List<? extends Capability> capabilities;

    public ComponentVariantIdentifier(ComponentIdentifier componentId, AttributeContainer attributes, List<? extends Capability> capabilities) {
        this.componentId = componentId;
        this.attributes = attributes;
        this.capabilities = capabilities;
    }

    public ComponentIdentifier getComponentId() {
        return componentId;
    }

    public AttributeContainer getAttributes() {
        return attributes;
    }

    public List<? extends Capability> getCapabilities() {
        return capabilities;
    }
}
