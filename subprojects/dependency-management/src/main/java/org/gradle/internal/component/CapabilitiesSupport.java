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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.model.ComponentResolveMetadata;

import java.util.Collection;
import java.util.stream.Collectors;

class CapabilitiesSupport {
    private static String displayName(Capability c) {
        String version = c.getVersion();
        if (version != null) {
            return c.getGroup() + ":" + c.getName() + ":" + c.getVersion();
        }
        return c.getGroup() + ":" + c.getName();
    }

    static String sortedCapabilityList(ComponentResolveMetadata target, Collection<? extends Capability> capabilities) {
        if (capabilities.isEmpty()) {
            ModuleVersionIdentifier mvi = target.getModuleVersionId();
            return displayName(new ImmutableCapability(mvi.getGroup(), mvi.getName(), mvi.getVersion()));
        }
        return capabilities.stream()
                .map(CapabilitiesSupport::displayName)
                .sorted()
                .collect(Collectors.joining(" and "));
    }

    static String prettifyCapabilities(ComponentResolveMetadata targetComponent, Collection<? extends Capability> capabilities) {
        StringBuilder sb = new StringBuilder("capabilit");
        if (capabilities.size()>1) {
            sb.append("ies ");
            sb.append(sortedCapabilityList(targetComponent, capabilities));
        } else {
            sb.append("y ").append(sortedCapabilityList(targetComponent, capabilities));
        }
        return sb.toString();
    }
}
