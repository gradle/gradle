/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.BuildTreeLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentCache;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.gradle.util.Path;

import javax.inject.Inject;

/**
 * Default implementation of {@link BuildTreeLocalComponentProvider}.
 *
 * <p>Currently, the metadata for a component is different based on whether it is consumed from the
 * producing build or from another build. This distinction can go away in Gradle 9.0.</p>
 */
public class DefaultBuildTreeLocalComponentProvider implements BuildTreeLocalComponentProvider, HoldsProjectState {

    private final ProjectStateRegistry projectStateRegistry;
    private final LocalComponentCache localComponentCache;
    private final LocalComponentProvider localComponentProvider;

    /**
     * Caches the "true" metadata instances for local components.
     */
    private final InMemoryLoadingCache<ProjectComponentIdentifier, LocalComponentGraphResolveState> originalComponents;

    @Inject
    public DefaultBuildTreeLocalComponentProvider(
        ProjectStateRegistry projectStateRegistry,
        InMemoryCacheFactory cacheFactory,
        LocalComponentCache localComponentCache,
        LocalComponentProvider localComponentProvider
    ) {
        this.projectStateRegistry = projectStateRegistry;
        this.localComponentCache = localComponentCache;
        this.localComponentProvider = localComponentProvider;
        this.originalComponents = cacheFactory.createCalculatedValueCache(Describables.of("local metadata"), this::createLocalComponent);
    }

    @Override
    public LocalComponentGraphResolveState getComponent(ProjectComponentIdentifier projectIdentifier, Path currentBuildPath) {
        return originalComponents.get(projectIdentifier);
    }

    private LocalComponentGraphResolveState createLocalComponent(ProjectComponentIdentifier projectIdentifier) {
        return localComponentCache.computeIfAbsent(
            ((DefaultProjectComponentIdentifier) projectIdentifier).getIdentityPath(),
            path -> localComponentProvider.getComponent(projectStateRegistry.stateFor(path))
        );
    }

    @Override
    public void discardAll() {
        originalComponents.invalidate();
    }
}
