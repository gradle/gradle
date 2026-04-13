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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.Describables;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServiceScope(Scope.BuildTree.class)
public class ProjectArtifactResolver implements ArtifactResolver, HoldsProjectState {
    private final Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts = new ConcurrentHashMap<>();
    private final ProjectStateRegistry projectStateRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public ProjectArtifactResolver(ProjectStateRegistry projectStateRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.projectStateRegistry = projectStateRegistry;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resolveArtifact(ComponentArtifactResolveMetadata component, ComponentArtifactMetadata artifact, BuildableArtifactResolveResult result) {
        // We cannot use computeIfAbsent() here because the creation involves projectState.fromMutableState()
        // which acquires a project lock. Holding ConcurrentHashMap's compute lock while acquiring a project lock
        // can deadlock when multiple projects are locked concurrently (e.g., IDEA model dependency calculation).
        // Instead, we use putIfAbsent() after pre-computing the candidate. Two threads may both create a candidate,
        // but only one is stored and returned — the loser's candidate is discarded.
        ResolvableArtifact resolvableArtifact = allResolvedArtifacts.get(artifact.getId());
        if (resolvableArtifact == null) {
            LocalComponentArtifactMetadata projectArtifact = (LocalComponentArtifactMetadata) artifact;
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getComponentId();
            File localArtifactFile = projectStateRegistry.stateFor(projectId).fromMutableState(p -> projectArtifact.getFile());
            if (localArtifactFile != null) {
                CalculatedValue<File> artifactSource = calculatedValueContainerFactory.create(Describables.of(artifact.getId()), resolveArtifactLater(artifact));
                ResolvableArtifact candidate = new DefaultResolvableArtifact(component.getModuleVersionId(), artifact.getName(), artifact.getId(), context -> context.add(artifact.getBuildDependencies()), artifactSource, calculatedValueContainerFactory);
                ResolvableArtifact existing = allResolvedArtifacts.putIfAbsent(artifact.getId(), candidate);
                resolvableArtifact = existing != null ? existing : candidate;
            }
        }
        if (resolvableArtifact != null) {
            result.resolved(resolvableArtifact);
        } else {
            result.notFound(artifact.getId());
        }
    }

    public ValueCalculator<File> resolveArtifactLater(ComponentArtifactMetadata artifact) {
        LocalComponentArtifactMetadata projectArtifact = (LocalComponentArtifactMetadata) artifact;
        ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getComponentId();
        ProjectState projectState = projectStateRegistry.stateFor(projectId);
        return new ResolvingCalculator(projectState, projectArtifact);
    }

    @Override
    public void discardAll() {
        allResolvedArtifacts.clear();
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
        public File calculateValue(NodeExecutionContext context) {
            return projectState.fromMutableState(p -> projectArtifact.getFile());
        }
    }
}
