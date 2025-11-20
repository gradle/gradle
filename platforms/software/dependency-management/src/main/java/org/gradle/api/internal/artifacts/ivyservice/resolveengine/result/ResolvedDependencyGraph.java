/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult;

/**
 * Representation of a fully resolved dependency graph.
 */
public class ResolvedDependencyGraph {

    private final long rootComponentId;
    private final long rootVariantId;
    private final ImmutableMap<Long, DefaultResolvedComponentResult> components;

    public ResolvedDependencyGraph(
        long rootComponentId,
        long rootVariantId,
        ImmutableMap<Long, DefaultResolvedComponentResult> components
    ) {
        this.rootComponentId = rootComponentId;
        this.rootVariantId = rootVariantId;
        this.components = components;
    }

    /**
     * The root component of the dependency graph.
     */
    public ResolvedComponentResult getRootComponent() {
        return components.get(rootComponentId);
    }

    /**
     * The root variant of the dependency graph.
     */
    public ResolvedVariantResult getRootVariant() {
        return components.get(rootComponentId).getVariant(rootVariantId);
    }

}
