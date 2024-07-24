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

import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ArtifactCollectionInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.provider.Provider;

/**
 * Internal counterpart of {@link ResolutionOutputs} that exposes the results as
 * their internal types, as well as the raw results before conversion to user-facing types.
 */
public interface ResolutionOutputsInternal extends ResolutionOutputs {

    /**
     * Get the raw results of the resolution. The returned results are lazy. Calling
     * this method will not perform resolution.
     */
    ResolutionResultProvider<ResolverResults> getRawResults();

    /**
     * Returns the resolved dependency graph as a reference to the root component.
     *
     * <p>This is here to support the existing public APIs. However, it is much more useful to expose
     * the root variant, which the public interface exposes at {@link #getRootVariant()}. Currently,
     * in order to traverse a graph the component is required, as it holds each variant's dependency set,
     * however we should have each variant own their outgoing dependencies as that better reflects the
     * reality of the structure of the graph.</p>
     */
    Provider<ResolvedComponentResult> getRootComponent();

    @Override
    FileCollectionInternal getFiles();

    @Override
    ArtifactCollectionInternal getArtifacts();
}
