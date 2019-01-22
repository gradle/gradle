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
package org.gradle.internal.component;

import com.google.common.collect.ImmutableList;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.Collection;

public class NoMatchingCapabilitiesException extends RuntimeException {
    public NoMatchingCapabilitiesException(ComponentResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, ImmutableList<? extends ConfigurationMetadata> candidates) {
        super(buildMessage(targetComponent, requestedCapabilities, candidates));
    }

    private static String buildMessage(ComponentResolveMetadata targetComponent, Collection<? extends Capability> requestedCapabilities, ImmutableList<? extends ConfigurationMetadata> candidates) {
        StringBuilder sb = new StringBuilder("Unable to find a variant of ");
        sb.append(targetComponent.getId()).append(" providing the requested ");
        sb.append(CapabilitiesSupport.prettifyCapabilities(targetComponent, requestedCapabilities));
        sb.append(":\n");
        for (ConfigurationMetadata candidate : candidates) {
            sb.append("   - Variant ").append(candidate.getName()).append(" provides ");
            sb.append(CapabilitiesSupport.sortedCapabilityList(targetComponent, candidate.getCapabilities().getCapabilities())).append("\n");
        }
        return sb.toString();
    }

}
