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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.artifacts.CapabilitiesResolution;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.jspecify.annotations.Nullable;

/**
 * Internal counterpart to {@link CapabilitiesResolution}.
 */
public interface CapabilitiesResolutionInternal extends CapabilitiesResolution {

    ImmutableList<CapabilityResolutionRule> getRules();

    /**
     * An action that may resolve a capability conflict.
     */
    final class CapabilityResolutionRule {

        private final @Nullable ImmutableCapability capability;
        private final Action<? super CapabilityResolutionDetails> action;

        public CapabilityResolutionRule(
            @Nullable ImmutableCapability capability,
            Action<? super CapabilityResolutionDetails> action
        ) {
            this.capability = capability;
            this.action = action;
        }

        /**
         * Returns true if this rule may be executed to resolve conflicts on
         * a capability with the given group and name.
         */
        public boolean appliesTo(String group, String name) {
            return capability == null || (capability.getGroup().equals(group) && capability.getName().equals(name));
        }

        /**
         * The action to apply.
         */
        public Action<? super CapabilityResolutionDetails> getAction() {
            return action;
        }

    }

}
