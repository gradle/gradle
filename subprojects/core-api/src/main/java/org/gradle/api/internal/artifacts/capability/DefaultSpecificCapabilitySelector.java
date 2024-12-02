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
 * Default implementation of {@link SpecificCapabilitySelector}.
 */
public final class DefaultSpecificCapabilitySelector implements CapabilitySelectorInternal, SpecificCapabilitySelector {

    // Ideally we would only hold a group and version, but for
    // backwards compatibility reasons we need to hold the requested
    // version even if it is ignored by the selector.
    private final ImmutableCapability backingCapability;

    public DefaultSpecificCapabilitySelector(ImmutableCapability backingCapability) {
        this.backingCapability = backingCapability;
    }

    @Override
    public String getGroup() {
        return backingCapability.getGroup();
    }

    @Override
    public String getName() {
        return backingCapability.getName();
    }

    @Override
    public boolean matches(String capabilityGroup, String capabilityName, ImmutableCapability implicitCapability) {
        return capabilityGroup.equals(getGroup()) && capabilityName.equals(getName());
    }

    @Override
    public String getDisplayName() {
        // We intentionally do not display the version here.
        // An exact version selector does not have a version.
        return "coordinates '" + getGroup() + ":" + getName() + "'";
    }

    /**
     * The originally requested capability, including the version, which is ignored
     * during capability selection. Avoid this method if possible.
     */
    @Deprecated
    public ImmutableCapability getBackingCapability() {
        return backingCapability;
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

        DefaultSpecificCapabilitySelector that = (DefaultSpecificCapabilitySelector) o;
        return getGroup().equals(that.getGroup()) && getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        int result = getGroup().hashCode();
        result = 31 * result + getName().hashCode();
        return result;
    }
}
