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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

@ServiceScope(Scopes.Build.class)
public class ProjectArtifactResolver implements ArtifactResolver {
    private final ProjectStateRegistry projectStateRegistry;

    public ProjectArtifactResolver(ProjectStateRegistry projectStateRegistry) {
        this.projectStateRegistry = projectStateRegistry;
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactResolveResult result) {
        LocalComponentArtifactMetadata projectArtifact = (LocalComponentArtifactMetadata) artifact;
        ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getComponentId();
        File localArtifactFile = projectStateRegistry.stateFor(projectId).fromMutableState(p -> projectArtifact.getFile());
        if (localArtifactFile != null) {
            result.resolved(localArtifactFile);
        } else {
            result.notFound(projectArtifact.getId());
        }
    }

    public ValueCalculator<File> resolveArtifactLater(ComponentArtifactMetadata artifact) {
        LocalComponentArtifactMetadata projectArtifact = (LocalComponentArtifactMetadata) artifact;
        ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getComponentId();
        ProjectState projectState = projectStateRegistry.stateFor(projectId);
        return new ResolvingCalculator(projectState, projectArtifact);
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
