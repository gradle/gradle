/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.attributes;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;

import java.util.Map;
import java.util.Set;

public class AttributeDesugaring {
    private final Map<ImmutableAttributes, ImmutableAttributes> desugared = Maps.newIdentityHashMap();
    private final ImmutableAttributesFactory attributesFactory;

    public AttributeDesugaring(ImmutableAttributesFactory attributesFactory) {
        this.attributesFactory = attributesFactory;
    }

    /**
     * Desugars attributes so that what we're going to serialize consists only of String or Boolean attributes,
     * and not their original types.
     * @return desugared attributes
     */
    public ImmutableAttributes desugar(ImmutableAttributes attributes) {
        if (attributes.isEmpty()) {
            return attributes;
        }
        return desugared.computeIfAbsent(attributes,  key -> {
            AttributeContainerInternal mutable = attributesFactory.mutable();
            Set<Attribute<?>> keySet = key.keySet();
            for (Attribute<?> attribute : keySet) {
                Object value = key.getAttribute(attribute);
                Attribute<Object> desugared = Cast.uncheckedCast(attribute);
                if (attribute.getType() == Boolean.class || attribute.getType() == String.class) {
                    mutable.attribute(desugared, value);
                } else {
                    desugared = Cast.uncheckedCast(Attribute.of(attribute.getName(), String.class));
                    mutable.attribute(desugared, value.toString());
                }
            }
            return mutable.asImmutable();
        });
    }

    public ComponentSelector desugarSelector(ComponentSelector selector) {
        if (selector instanceof ModuleComponentSelector) {
            ModuleComponentSelector module = (ModuleComponentSelector) selector;
            AttributeContainer moduleAttributes = module.getAttributes();
            if (!moduleAttributes.isEmpty()) {
                ImmutableAttributes attributes = ((AttributeContainerInternal) moduleAttributes).asImmutable();
                return DefaultModuleComponentSelector.newSelector(module.getModuleIdentifier(), module.getVersionConstraint(), desugar(attributes), module.getRequestedCapabilities());
            }
        }
        if (selector instanceof DefaultProjectComponentSelector) {
            DefaultProjectComponentSelector project = (DefaultProjectComponentSelector) selector;
            AttributeContainer projectAttributes = project.getAttributes();
            if (!projectAttributes.isEmpty()) {
                ImmutableAttributes attributes = ((AttributeContainerInternal) projectAttributes).asImmutable();
                return new DefaultProjectComponentSelector(project.getBuildIdentifier(), project.getIdentityPath(), project.projectPath(), project.getProjectName(), desugar(attributes), project.getRequestedCapabilities());
            }
        }
        return selector;
    }
}
