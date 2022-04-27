/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableResolvableArtifactResult;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServiceScope(Scopes.Build.class)
public class ProjectArtifactResolver implements ArtifactResolver, Stoppable {
    private final ProjectStateRegistry projectStateRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final Map<ComponentArtifactIdentifier, ResolvableArtifact> resolvedArtifactCache;

    public ProjectArtifactResolver(ProjectStateRegistry projectStateRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.projectStateRegistry = projectStateRegistry;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.resolvedArtifactCache = new ConcurrentHashMap<>();
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resolveArtifact(ModuleVersionIdentifier ownerId, ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableResolvableArtifactResult result) {
        result.resolved(resolvedArtifactCache.computeIfAbsent(artifact.getId(), artifactId -> {
            LocalComponentArtifactMetadata projectArtifact = (LocalComponentArtifactMetadata) artifact;
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getComponentId();
            ProjectState projectState = projectStateRegistry.stateFor(projectId);
            CalculatedValue<File> artifactSource = calculatedValueContainerFactory.create(Describables.of(artifact.getId()), new ResolvingCalculator(projectState, projectArtifact));
            return new DefaultResolvableArtifact(ownerId, artifact.getName(), artifact.getId(), context -> context.add(artifact.getBuildDependencies()), artifactSource, calculatedValueContainerFactory);
        }));
    }

    @Override
    public void stop() {
        resolvedArtifactCache.clear();
    }

    private static class ResolvingCalculator implements ValueCalculator<File> {
        private final ProjectState projectState;
        private final LocalComponentArtifactMetadata projectArtifact;

        public ResolvingCalculator(ProjectState projectState, LocalComponentArtifactMetadata projectArtifact) {
            this.projectState = projectState;
            this.projectArtifact = projectArtifact;
        }

        @Override
        public boolean usesMutableProjectState() {
            return true;
        }

        @Override
        public ProjectInternal getOwningProject() {
            return projectState.getMutableModel();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }

        @Override
        public File calculateValue(NodeExecutionContext context) {
            return projectState.fromMutableState(p -> projectArtifact.getFile());
        }
    }
}
