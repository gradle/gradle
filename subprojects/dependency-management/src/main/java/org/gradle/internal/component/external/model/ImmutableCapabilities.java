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
import org.gradle.api.internal.capabilities.CapabilitiesMetadataInternal;
import org.gradle.api.internal.capabilities.ImmutableCapability;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A deeply immutable implementation of {@link CapabilitiesMetadata}.
 *
 * This type will ensure that all contents are immutable upon construction,
 * in order to allow instances of this type to be safely reused whenever possible
 * to avoid unnecessary memory allocations.
 *
 * Note that while this class is not itself {@code final}, all fields are private, so
 * subclassing should not break the immutability contract.
 */
public class ImmutableCapabilities implements CapabilitiesMetadataInternal {
    public static final ImmutableCapabilities EMPTY = new ImmutableCapabilities(ImmutableList.of());

    private final ImmutableList<ImmutableCapability> capabilities;

    public static ImmutableCapabilities of(CapabilitiesMetadata capabilities) {
        if (capabilities instanceof ImmutableCapabilities) {
            return (ImmutableCapabilities) capabilities;
        }
        return of(capabilities.getCapabilities());
    }

    public static ImmutableCapabilities of(@Nullable Capability capability) {
        if (capability == null) {
            return EMPTY;
        }
        return new ImmutableCapabilities(Collections.singleton(capability));
    }

    public static ImmutableCapabilities of(@Nullable Collection<? extends Capability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return EMPTY;
        }
        if (capabilities.size() == 1) {
            Capability single = capabilities.iterator().next();
            return of(single);
        }
        return new ImmutableCapabilities(capabilities);
    }

    private ImmutableCapabilities(Collection<? extends Capability> capabilities) {
        ImmutableList.Builder<ImmutableCapability> builder = new ImmutableList.Builder<>();
        for (Capability capability : capabilities) {
            if (capability instanceof ImmutableCapability) {
                builder.add((ImmutableCapability) capability);
            } else {
                builder.add(new DefaultImmutableCapability(capability.getGroup(), capability.getName(), capability.getVersion()));
            }
        }
        this.capabilities = builder.build();
    }

    @Override
    public List<ImmutableCapability> getCapabilities() {
        return capabilities;
    }
}
