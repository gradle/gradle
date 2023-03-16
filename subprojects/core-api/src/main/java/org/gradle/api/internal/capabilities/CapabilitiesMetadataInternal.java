/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.capabilities;

import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.Capability;

import java.util.List;

/**
 * Internal extension of {@link CapabilitiesMetadata} that adds methods not intended for public consumption.
 */
public interface CapabilitiesMetadataInternal extends CapabilitiesMetadata {
    /**
     * A method that helps performance of selection by quickly checking if a
     * metadata container only contains a single, shadowed (the implicit) capability.
     *
     * @return {@code true} if the variant only contains the implicit capability
     */
    default boolean isShadowedCapabilityOnly() {
        List<? extends Capability> capabilities = getCapabilities();
        return capabilities.size() == 1 && capabilities.get(0) instanceof ShadowedCapability;
    }
}
