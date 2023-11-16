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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.capabilities.ImmutableCapability;

import javax.annotation.Nullable;
import java.util.Collection;

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
public class ImmutableCapabilities {
    public static final ImmutableCapabilities EMPTY = new ImmutableCapabilities(ImmutableSet.of());

    private final ImmutableSet<ImmutableCapability> capabilities;

    public ImmutableCapabilities(ImmutableSet<ImmutableCapability> capabilities) {
        this.capabilities = capabilities;
    }

    public static ImmutableCapabilities of(@Nullable Capability capability) {
        if (capability == null) {
            return EMPTY;
        }
        return new ImmutableCapabilities(ImmutableSet.of(asImmutable(capability)));
    }

    public static ImmutableCapabilities of(@Nullable Collection<? extends Capability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return EMPTY;
        }
        if (capabilities.size() == 1) {
            Capability single = capabilities.iterator().next();
            return of(single);
        }

        ImmutableSet.Builder<ImmutableCapability> builder = ImmutableSet.builderWithExpectedSize(capabilities.size());
        for (Capability capability : capabilities) {
            builder.add(asImmutable(capability));
        }
        return new ImmutableCapabilities(builder.build());
    }

    private static ImmutableCapability asImmutable(Capability capability) {
        if (capability instanceof ImmutableCapability) {
            return (ImmutableCapability) capability;
        } else {
            return new DefaultImmutableCapability(capability.getGroup(), capability.getName(), capability.getVersion());
        }
    }

    public ImmutableSet<ImmutableCapability> asSet() {
        return capabilities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImmutableCapabilities that = (ImmutableCapabilities) o;
        return capabilities.equals(that.capabilities);
    }

    @Override
    public int hashCode() {
        return capabilities.hashCode();
    }
}
