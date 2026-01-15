/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.component.ComponentSelectorInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;

import java.util.List;

class UnversionedModuleComponentSelector implements ComponentSelectorInternal {

    private final ModuleIdentifier moduleIdentifier;
    private final ImmutableAttributes attributes;
    private final ImmutableSet<CapabilitySelector> capabilitySelectors;

    UnversionedModuleComponentSelector(
        ModuleIdentifier id,
        ImmutableAttributes attributes,
        ImmutableSet<CapabilitySelector> capabilitySelectors
    ) {
        this.moduleIdentifier = id;
        this.attributes = attributes;
        this.capabilitySelectors = capabilitySelectors;
    }

    public ModuleIdentifier getModuleIdentifier() {
        return moduleIdentifier;
    }

    @Override
    public String getDisplayName() {
        return moduleIdentifier + ":*";
    }

    @Override
    public boolean matchesStrictly(ComponentIdentifier identifier) {
        return false;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<Capability> getRequestedCapabilities() {
        return capabilitySelectors.stream()
            .map(c -> {
                if (c instanceof SpecificCapabilitySelector) {
                    return ((DefaultSpecificCapabilitySelector) c).getBackingCapability();
                } else if (c instanceof FeatureCapabilitySelector) {
                    return new DefaultImmutableCapability(
                        getModuleIdentifier().getGroup(),
                        getModuleIdentifier().getName() + "-" + ((FeatureCapabilitySelector) c).getFeatureName(),
                        null
                    );
                } else {
                    throw new UnsupportedOperationException("Unsupported capability selector type: " + c.getClass().getName());
                }
            })
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableSet<CapabilitySelector> getCapabilitySelectors() {
        return capabilitySelectors;
    }

    public UnversionedModuleComponentSelector withAttributes(ImmutableAttributes newAttributes) {
        return new UnversionedModuleComponentSelector(
            moduleIdentifier,
            newAttributes,
            capabilitySelectors
        );
    }

    public UnversionedModuleComponentSelector withCapabilities(ImmutableSet<CapabilitySelector> newCapabilitySelectors) {
        return new UnversionedModuleComponentSelector(
            moduleIdentifier,
            attributes,
            newCapabilitySelectors
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UnversionedModuleComponentSelector that = (UnversionedModuleComponentSelector) o;
        return moduleIdentifier.equals(that.moduleIdentifier) &&
            attributes.equals(that.attributes) &&
            capabilitySelectors.equals(that.capabilitySelectors);
    }

    @Override
    public int hashCode() {
        int result = moduleIdentifier.hashCode();
        result = 31 * result + attributes.hashCode();
        result = 31 * result + capabilitySelectors.hashCode();
        return result;
    }

}
