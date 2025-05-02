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
 * Default implementation of {@link FeatureCapabilitySelector}.
 */
public final class DefaultFeatureCapabilitySelector implements CapabilitySelectorInternal, FeatureCapabilitySelector {

    private final String featureName;

    public DefaultFeatureCapabilitySelector(String featureName) {
        this.featureName = featureName;
    }

    @Override
    public String getFeatureName() {
        return featureName;
    }

    @Override
    public boolean matches(String capabilityGroup, String capabilityName, ImmutableCapability implicitCapability) {
        return capabilityGroup.equals(implicitCapability.getGroup()) &&
            capabilityName.equals(implicitCapability.getName() + "-" + featureName);
    }

    @Override
    public String getDisplayName() {
        return "feature '" + featureName + "'";
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

        DefaultFeatureCapabilitySelector that = (DefaultFeatureCapabilitySelector) o;
        return featureName.equals(that.featureName);
    }

    @Override
    public int hashCode() {
        return featureName.hashCode();
    }
}
