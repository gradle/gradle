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

package org.gradle.internal.component.external.model.ivy;

import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Resolution state for an ivy component. Exposes the configurations of the component.
 */
public interface IvyComponentGraphResolveState extends ExternalModuleComponentGraphResolveState {

    /**
     * Get all names such that {@link #getConfiguration(String)} return a non-null value.
     */
    Set<String> getConfigurationNames();

    /**
     * Returns the configuration with the given name.
     */
    @Nullable
    ConfigurationGraphResolveState getConfiguration(String configurationName);

    @Override
    IvyGraphSelectionCandidates getCandidatesForGraphVariantSelection();

    interface IvyGraphSelectionCandidates extends GraphSelectionCandidates {

        /**
         * Returns the variant that is identified by the given configuration name.
         */
        @Nullable
        VariantGraphResolveState getVariantByConfigurationName(String name);

    }

}
