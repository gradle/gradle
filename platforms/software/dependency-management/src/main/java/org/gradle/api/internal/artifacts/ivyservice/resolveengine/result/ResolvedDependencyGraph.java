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

import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * State captured from a resolved dependency graph necessary to reconstruct it
 * later when exporting the results to public APIs.
 *
 * @param requestAttributes The original request attributes used to build the graph.
 * @param graphSource A supplier of the graph structure, which deserializes the graph on-demand.
 * @param availableVariantsByComponent Additional data captured when {@link ResolutionStrategyInternal#getIncludeAllSelectableVariantResults()} is enabled.
 */
public record ResolvedDependencyGraph(
    ImmutableAttributes requestAttributes,
    Supplier<GraphStructure> graphSource,
    @Nullable List<List<ResolvedVariantResult>> availableVariantsByComponent
) { }
