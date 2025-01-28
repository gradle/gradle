/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.capability;

import org.gradle.api.internal.capabilities.ImmutableCapability;

/**
 * Default implementation of {@link SuffixCapabilitySelector}.
 */
public final class DefaultSuffixCapabilitySelector implements CapabilitySelectorInternal, SuffixCapabilitySelector {

    private final String suffix;

    public DefaultSuffixCapabilitySelector(String suffix) {
        this.suffix = suffix;
    }

    @Override
    public String getSuffix() {
        return suffix;
    }

    @Override
    public boolean matches(String capabilityGroup, String capabilityName, ImmutableCapability implicitCapability) {
        return capabilityGroup.equals(implicitCapability.getGroup()) &&
            capabilityName.equals(implicitCapability.getName() + suffix);
    }

    @Override
    public String getDisplayName() {
        return "suffix '" + suffix + "'";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultSuffixCapabilitySelector that = (DefaultSuffixCapabilitySelector) o;
        return suffix.equals(that.suffix);
    }

    @Override
    public int hashCode() {
        return suffix.hashCode();
    }
}
