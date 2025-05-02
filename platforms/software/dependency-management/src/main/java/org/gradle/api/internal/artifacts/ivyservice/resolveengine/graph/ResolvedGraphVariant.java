/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.jspecify.annotations.Nullable;

public interface ResolvedGraphVariant {
    /**
     * Returns a simple id for this node, unique across all nodes in the same graph.
     * This id cannot be used across graphs.
     */
    long getNodeId();

    /**
     * Get the resolve state of the owning component.
     */
    ComponentGraphResolveState getComponentResolveState();

    /**
     * Get the resolve state of this variant.
     */
    VariantGraphResolveState getResolveState();

    @Nullable
    ResolvedGraphVariant getExternalVariant();
}
