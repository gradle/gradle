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
package org.gradle.internal.component.resolution.failure.formatting;

import org.gradle.api.capabilities.Capability;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Static utility methods for describing capabilities.
 */
public final class CapabilitiesDescriber {
    private CapabilitiesDescriber() {}

    public static String describeCapabilitiesWithTitle(Collection<? extends Capability> capabilities) {
        StringBuilder sb = new StringBuilder("capabilit");
        if (capabilities.size() > 1) {
            sb.append("ies ");
        } else {
            sb.append("y ");
        }
        sb.append(describeCapabilities(capabilities));
        return sb.toString();
    }

    public static String describeCapabilities(Collection<? extends Capability> capabilities) {
        return capabilities.stream()
            .map(CapabilitiesDescriber::describeCapability)
            .sorted()
            .collect(Collectors.joining(" and "));
    }

    private static String describeCapability(Capability c) {
        String version = c.getVersion();
        if (version != null) {
            return '\'' + c.getGroup() + ":" + c.getName() + ":" + c.getVersion() + '\'';
        }
        return '\'' + c.getGroup() + ":" + c.getName() + '\'';
    }

}
