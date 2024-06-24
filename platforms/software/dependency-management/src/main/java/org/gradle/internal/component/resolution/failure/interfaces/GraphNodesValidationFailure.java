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

package org.gradle.internal.component.resolution.failure.interfaces;

import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;

import java.util.Set;

/**
 * Represents a failure validating the nodes for a specific component in the final resulting dependency graph.
 * <p>
 * When this failure occurs, we have always selected a component, as the entire graph has been resolved and
 * all components are selected.
 *
 * @implSpec This interface is meant only to be extended by other interfaces, it should not
 * be implemented directly.
 */
public interface GraphNodesValidationFailure extends GraphValidationFailure {
    /**
     * Gets the metadata for the component state in the graph for the component for which nodes validation failed.
     *
     * @return the component resolve state for the component containing the nodes failing validation
     */
    ComponentGraphResolveMetadata getFailingComponent();

    /**
     * Gets the metadata for the resolve states of the nodes in the graph that failed validation.
     *
     * @return the set of resolve states for the nodes that failed validation
     */
    Set<VariantGraphResolveMetadata> getFailingNodes();
}
