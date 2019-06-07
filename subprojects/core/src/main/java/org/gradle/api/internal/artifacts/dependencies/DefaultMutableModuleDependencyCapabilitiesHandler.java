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
package org.gradle.api.internal.artifacts.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.List;
import java.util.Set;

public class DefaultMutableModuleDependencyCapabilitiesHandler implements ModuleDependencyCapabilitiesInternal {
    private final NotationParser<Object, Capability> notationParser;
    private final Set<Capability> requestedCapabilities = Sets.newLinkedHashSet();

    public DefaultMutableModuleDependencyCapabilitiesHandler(NotationParser<Object, Capability> notationParser) {
        this.notationParser = notationParser;
    }

    @Override
    public void requireCapability(Object capabilityNotation) {
        requestedCapabilities.add(notationParser.parseNotation(capabilityNotation));
    }

    @Override
    public void requireCapabilities(Object... capabilityNotations) {
        for (Object notation : capabilityNotations) {
            requireCapability(notation);
        }
    }

    @Override
    public List<Capability> getRequestedCapabilities() {
        return ImmutableList.copyOf(requestedCapabilities);
    }

    @Override
    public ModuleDependencyCapabilitiesInternal copy() {
        DefaultMutableModuleDependencyCapabilitiesHandler out = new DefaultMutableModuleDependencyCapabilitiesHandler(notationParser);
        out.requestedCapabilities.addAll(requestedCapabilities);
        return out;
    }
}
