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
package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;
import org.gradle.api.internal.capabilities.ImmutableCapability;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultMutableCapabilitiesMetadata implements MutableCapabilitiesMetadata {
    private final Set<ImmutableCapability> descriptors;

    public DefaultMutableCapabilitiesMetadata(ImmutableCapabilities capabilities) {
        this.descriptors = new LinkedHashSet<>(capabilities.asSet());
    }

    @Override
    public void addCapability(String group, String name, String version) {
        for (Capability descriptor : descriptors) {
            if (descriptor.getGroup().equals(group) && descriptor.getName().equals(name) && !descriptor.getVersion().equals(version)) {
                throw new InvalidUserDataException("Cannot add capability " + group + ":" + name + " with version " + version + " because it's already defined with version " + descriptor.getVersion());
            }
        }
        descriptors.add(new DefaultImmutableCapability(group, name, version));
    }

    @Override
    public void removeCapability(String group, String name) {
        descriptors.removeIf(next -> next.getGroup().equals(group) && next.getName().equals(name));
    }

    @Override
    public CapabilitiesMetadata asImmutable() {
        return new DefaultCapabilitiesMetadata(getCapabilities());
    }

    @Override
    public ImmutableList<? extends Capability> getCapabilities() {
        return ImmutableList.copyOf(descriptors);
    }
}
