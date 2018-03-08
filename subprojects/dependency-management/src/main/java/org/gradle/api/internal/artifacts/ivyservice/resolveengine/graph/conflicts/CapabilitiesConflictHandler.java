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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.capabilities.CapabilityDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;

public interface CapabilitiesConflictHandler extends ConflictHandler<CapabilitiesConflictHandler.Candidate, Void, Void> {
    class Candidate {
        private final ComponentState component;
        private final CapabilityDescriptor capabilityDescriptor;

        public Candidate(ComponentState component, CapabilityDescriptor capabilityDescriptor) {
            this.component = component;
            this.capabilityDescriptor = capabilityDescriptor;
        }

        public ComponentState getComponent() {
            return component;
        }

        public CapabilityDescriptor getCapabilityDescriptor() {
            return capabilityDescriptor;
        }
    }
}
