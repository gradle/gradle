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

import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.internal.capabilities.ImmutableCapability;

/**
 * Internal counterpart to {@link CapabilitySelector}.
 */
public interface CapabilitySelectorInternal extends CapabilitySelector {

    /**
     * Returns whether the given capability satisfies this selector.
     *
     * @param capabilityGroup The group of the capability to match against
     * @param capabilityName The name of the capability to match against
     * @param implicitCapability The implicit capability of the target component
     *
     * @return {@code true} iff the capability satisfies this selector.
     */
    boolean matches(String capabilityGroup, String capabilityName, ImmutableCapability implicitCapability);

}
