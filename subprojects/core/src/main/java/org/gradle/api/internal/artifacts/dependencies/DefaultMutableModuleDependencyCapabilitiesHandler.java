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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.DefaultFeatureCapabilitySelector;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;

public abstract class DefaultMutableModuleDependencyCapabilitiesHandler implements ModuleDependencyCapabilitiesInternal {

    private final NotationParser<Object, Capability> capabilityNotationParser;

    @Inject
    public DefaultMutableModuleDependencyCapabilitiesHandler(NotationParser<Object, Capability> capabilityNotationParser) {
        this.capabilityNotationParser = capabilityNotationParser;
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Override
    public abstract SetProperty<CapabilitySelector> getCapabilitySelectors();

    @Override
    public void requireCapability(Object capabilityNotation) {
        if (capabilityNotation instanceof Provider) {
            getCapabilitySelectors().add(((Provider<?>) capabilityNotation).map(this::convertExact));
        } else {
            getCapabilitySelectors().add(convertExact(capabilityNotation));
        }
    }

    private DefaultSpecificCapabilitySelector convertExact(Object notation) {
        Capability capability = capabilityNotationParser.parseNotation(notation);
        return new DefaultSpecificCapabilitySelector((ImmutableCapability) capability);
    }

    @Override
    public void requireFeature(String featureName) {
        getCapabilitySelectors().add(new DefaultFeatureCapabilitySelector(featureName));
    }

    @Override
    public void requireFeature(Provider<String> featureName) {
        getCapabilitySelectors().add(featureName.map(DefaultFeatureCapabilitySelector::new));
    }

    @Override
    public void requireCapabilities(Object... capabilityNotations) {
        for (Object notation : capabilityNotations) {
            requireCapability(notation);
        }
    }

    @Override
    public ModuleDependencyCapabilitiesInternal copy() {
        DefaultMutableModuleDependencyCapabilitiesHandler out = getObjectFactory().newInstance(
            DefaultMutableModuleDependencyCapabilitiesHandler.class, capabilityNotationParser
        );
        out.getCapabilitySelectors().addAll(getCapabilitySelectors());
        return out;
    }
}
