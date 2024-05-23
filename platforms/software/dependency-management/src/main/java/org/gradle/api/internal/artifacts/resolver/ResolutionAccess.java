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

package org.gradle.api.internal.artifacts.resolver;

import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * An internal lazy reference to a graph resolution. Provides access to the inputs and
 * outputs of a graph resolution.
 */
public interface ResolutionAccess {

    /**
     * Get the owner of the resolution.
     */
    ResolutionHost getHost();

    /**
     * Get the request attributes for this resolution. Calling this method will lock-in the
     * request attributes from further mutation but will not perform resolution.
     */
    ImmutableAttributes getAttributes();

    /**
     * Get the default artifact sort order for this resolution.
     */
    ResolutionStrategy.SortOrder getDefaultSortOrder();

    /**
     * Get the raw results of the resolution. The returned results are lazy. Calling
     * this method will not perform resolution.
     */
    ResolutionResultProvider<ResolverResults> getResults();

    /**
     * Get the public representation of {@link #getResults()}, which exposes the raw
     * results as well-known user-facing types like {@link org.gradle.api.file.FileCollection}.
     */
    ResolutionOutputsInternal getPublicView();
}
