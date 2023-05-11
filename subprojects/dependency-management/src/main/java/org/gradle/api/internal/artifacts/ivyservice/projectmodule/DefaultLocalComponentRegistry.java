/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultLocalComponentRegistry implements LocalComponentRegistry, HoldsProjectState {
    private final BuildIdentifier thisBuild;
    private final ProjectStateRegistry projectStateRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final LocalComponentProvider provider;
    private final LocalComponentInAnotherBuildProvider otherBuildProvider;
    private final Map<ProjectComponentIdentifier, CalculatedValueContainer<LocalComponentGraphResolveState, ?>> projects = new ConcurrentHashMap<>();

    public DefaultLocalComponentRegistry(
        BuildIdentifier thisBuild,
        ProjectStateRegistry projectStateRegistry,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        LocalComponentProvider provider,
        LocalComponentInAnotherBuildProvider otherBuildProvider
    ) {
        this.thisBuild = thisBuild;
        this.projectStateRegistry = projectStateRegistry;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.provider = provider;
        this.otherBuildProvider = otherBuildProvider;
    }

    @Override
    public LocalComponentGraphResolveState getComponent(ProjectComponentIdentifier projectIdentifier) {
        ProjectState projectState = projectStateRegistry.stateFor(projectIdentifier);
        if (isLocalProject(projectIdentifier)) {
            CalculatedValueContainer<LocalComponentGraphResolveState, ?> valueContainer = projects.computeIfAbsent(projectIdentifier, projectComponentIdentifier ->
                calculatedValueContainerFactory.create(Describables.of("metadata of", projectIdentifier), new MetadataSupplier(projectState)));
            // Calculate the value after adding the entry to the map, so that the value container can take care of thread synchronization
            valueContainer.finalizeIfNotAlready();
            return valueContainer.get();
        } else {
            return otherBuildProvider.getComponent(projectState);
        }
    }

    private boolean isLocalProject(ProjectComponentIdentifier projectIdentifier) {
        return projectIdentifier.getBuild().equals(thisBuild);
    }

    @Override
    public void discardAll() {
        projects.clear();
    }

    private class MetadataSupplier implements ValueCalculator<LocalComponentGraphResolveState> {
        private final ProjectState projectState;

        public MetadataSupplier(ProjectState projectState) {
            this.projectState = projectState;
        }

        @Override
        public LocalComponentGraphResolveState calculateValue(NodeExecutionContext context) {
            return provider.getComponent(projectState);
        }
    }
}
