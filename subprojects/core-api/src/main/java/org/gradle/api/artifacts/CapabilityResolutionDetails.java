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
package org.gradle.api.artifacts;

import org.gradle.api.capabilities.Capability;
import org.gradle.internal.HasInternalProtocol;

import java.util.List;

/**
 * Gives access to the resolution details of a single capability conflict.
 * This class may be used to resolve a capability conflict by either selecting
 * explicitly one of the candidates, or selecting the one with the highest
 * version of the capability.
 *
 * @since 5.6
 */
@HasInternalProtocol
public interface CapabilityResolutionDetails {
    /**
     * Returns the capability in conflict
     */
    Capability getCapability();

    /**
     * Returns the list of components which are in conflict on this capability
     */
    List<ComponentVariantIdentifier> getCandidates();

    /**
     * Selects a particular candidate to solve the conflict. It is recommended to
     * provide a human-readable explanation to the choice by calling the {@link #because(String)} method
     *
     * @param candidate the selected candidate
     * @return this details instance
     *
     * @since 6.0
     */
    CapabilityResolutionDetails select(ComponentVariantIdentifier candidate);

    /**
     * Selects a particular candidate to solve the conflict. It is recommended to
     * provide a human-readable explanation to the choice by calling the {@link #because(String)} method
     *
     * @param notation the selected candidate
     *
     * @return this details instance
     */
    CapabilityResolutionDetails select(Object notation);

    /**
     * Automatically selects the candidate module which has the highest version of the
     * capability. A reason is automatically added so calling {@link #because(String)} would override
     * the automatic selection description.
     *
     * @return this details instance
     */
    CapabilityResolutionDetails selectHighestVersion();

    /**
     * Describes why a particular candidate is selected.
     *
     * @param reason the reason why a candidate is selected.
     *
     * @return this details instance
     */
    CapabilityResolutionDetails because(String reason);
}
