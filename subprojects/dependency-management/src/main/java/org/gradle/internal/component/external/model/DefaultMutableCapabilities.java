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
import org.gradle.internal.Cast;

import java.util.List;

public class DefaultMutableCapabilities implements MutableCapabilitiesMetadata {
    private final List<Capability> descriptors;

    public DefaultMutableCapabilities(List<Capability> descriptors) {
        this.descriptors = descriptors;
    }

    @Override
    public void addCapability(String group, String name, String version) {
        for (Capability descriptor : descriptors) {
            if (descriptor.getGroup().equals(group) && descriptor.getName().equals(name) && !descriptor.getVersion().equals(version)) {
                throw new InvalidUserDataException("Cannot add capability " + group + ":" + name + " with version " + version + " because it's already defined with version " + descriptor.getVersion());
            }
        }
        descriptors.add(new ImmutableCapability(group, name, version));
    }

    @Override
    public void removeCapability(String group, String name) {
        descriptors.removeIf(next -> next.getGroup().equals(group) && next.getName().equals(name));
    }

    @Override
    public CapabilitiesMetadata asImmutable() {
        ImmutableList<ImmutableCapability> capabilities = Cast.uncheckedCast(getCapabilities());
        return new ImmutableCapabilities(capabilities);
    }

    @Override
    public List<? extends Capability> getCapabilities() {
        return ImmutableList.copyOf(descriptors);
    }

}
