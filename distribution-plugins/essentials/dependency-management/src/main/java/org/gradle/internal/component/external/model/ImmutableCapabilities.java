/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.Capability;

import java.util.List;

public class ImmutableCapabilities implements CapabilitiesMetadata {
    public static final ImmutableCapabilities EMPTY = new ImmutableCapabilities(ImmutableList.<ImmutableCapability>of());

    private final ImmutableList<? extends Capability> capabilities;

    public static ImmutableCapabilities of(CapabilitiesMetadata capabilities) {
        if (capabilities instanceof ImmutableCapabilities) {
            return (ImmutableCapabilities) capabilities;
        }
        return of(capabilities.getCapabilities());
    }

    public static ImmutableCapabilities of(List<? extends Capability> capabilities) {
        if (capabilities.isEmpty()) {
            return EMPTY;
        }
        if (capabilities.size() == 1) {
            Capability single = capabilities.get(0);
            if (single instanceof ShadowedCapability) {
                return new ShadowedSingleImmutableCapabilities(single);
            }
        }
        return new ImmutableCapabilities(ImmutableList.copyOf(capabilities));
    }

    public ImmutableCapabilities(ImmutableList<? extends Capability> capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public List<? extends Capability> getCapabilities() {
        return capabilities;
    }

    private static class ShadowedSingleImmutableCapabilities extends ImmutableCapabilities implements ShadowedCapabilityOnly {

        public ShadowedSingleImmutableCapabilities(Capability single) {
            super(ImmutableList.of(single));
        }
    }
}
