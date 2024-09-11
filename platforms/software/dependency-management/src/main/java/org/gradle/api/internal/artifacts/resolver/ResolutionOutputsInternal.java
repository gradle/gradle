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
     */
    Provider<ResolvedComponentResult> getRootComponent();

    @Override
    FileCollectionInternal getFiles();

    @Override
    ArtifactCollectionInternal getArtifacts();
}
