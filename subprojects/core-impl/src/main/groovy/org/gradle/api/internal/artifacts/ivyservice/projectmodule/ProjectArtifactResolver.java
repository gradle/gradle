/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableMultipleArtifactResolveResult;
import org.gradle.api.internal.artifacts.metadata.LocalArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.LocalComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;

public class ProjectArtifactResolver implements ArtifactResolver {
    private final ProjectComponentRegistry projectComponentRegistry;
    private final ArtifactResolver delegate;

    public ProjectArtifactResolver(ProjectComponentRegistry projectComponentRegistry, ArtifactResolver delegate) {
        this.projectComponentRegistry = projectComponentRegistry;
        this.delegate = delegate;
    }

    public void resolve(ModuleVersionMetaData moduleMetaData, ModuleVersionArtifactMetaData artifact, BuildableArtifactResolveResult result) {
        if (isProjectModule(moduleMetaData)) {
            // TODO:DAZ We're now looking up the project separately per artifact: need to ensure this isn't a problem
            ProjectComponentIdentifier componentIdentifier = (ProjectComponentIdentifier) moduleMetaData.getComponentId();
            LocalComponentMetaData componentMetaData = projectComponentRegistry.getProject(componentIdentifier.getProjectPath());
            LocalArtifactMetaData artifactMetaData = componentMetaData.getArtifact(artifact.getId());
            if (artifactMetaData != null) {
                result.resolved(artifactMetaData.getFile());
            } else {
                result.notFound(artifact.getId());
            }
        } else {
            delegate.resolve(moduleMetaData, artifact, result);
        }
    }

    public void resolve(ModuleVersionMetaData moduleMetaData, Class<? extends SoftwareArtifact> artifactType, BuildableMultipleArtifactResolveResult result) {
        if (isProjectModule(moduleMetaData)) {
            throw new UnsupportedOperationException("TODO");
        } else {
            delegate.resolve(moduleMetaData, artifactType, result);
        }
    }

    private boolean isProjectModule(ModuleVersionMetaData moduleMetaData) {
        return moduleMetaData.getComponentId() instanceof ProjectComponentIdentifier;
    }
}
