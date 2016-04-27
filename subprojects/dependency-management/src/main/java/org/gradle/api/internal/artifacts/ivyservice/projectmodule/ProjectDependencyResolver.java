/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentUsage;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import java.io.File;
import java.util.List;
import java.util.Set;

public class ProjectDependencyResolver implements ComponentMetaDataResolver, DependencyToComponentIdResolver, ArtifactResolver {
    private final ProjectComponentRegistry projectComponentRegistry;
    private final List<ProjectArtifactBuilder> artifactBuilders;

    public ProjectDependencyResolver(ProjectComponentRegistry projectComponentRegistry, List<ProjectArtifactBuilder> artifactBuilders) {
        this.projectComponentRegistry = projectComponentRegistry;
        this.artifactBuilders = artifactBuilders;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof ProjectComponentSelector) {
            ProjectComponentSelector selector = (ProjectComponentSelector) dependency.getSelector();
            ProjectComponentIdentifier project = DefaultProjectComponentIdentifier.newId(selector.getProjectPath());
            LocalComponentMetaData componentMetaData = projectComponentRegistry.getProject(project);
            if (componentMetaData == null) {
                result.failed(new ModuleVersionResolveException(selector, "project '" + project.getProjectPath() + "' not found."));
            } else {
                result.resolved(componentMetaData);
            }
        }
    }

    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (identifier instanceof ProjectComponentIdentifier) {
            LocalComponentMetaData componentMetaData = projectComponentRegistry.getProject((ProjectComponentIdentifier) identifier);
            if (componentMetaData == null) {
                String projectPath = ((ProjectComponentIdentifier) identifier).getProjectPath();
                result.failed(new ModuleVersionResolveException(new DefaultProjectComponentSelector(projectPath), "project '" + projectPath + "' not found."));
            } else {
                result.resolved(componentMetaData);
            }
        }
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        if (isProjectModule(component.getComponentId())) {
            throw new UnsupportedOperationException("Resolving artifacts by type is not yet supported for project modules");
        }
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
        if (isProjectModule(component.getComponentId())) {
            String configurationName = usage.getConfigurationName();
            Set<ComponentArtifactMetaData> artifacts = component.getConfiguration(configurationName).getArtifacts();
            result.resolved(artifacts);
        }
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        if (isProjectModule(artifact.getComponentId())) {

            // Run any registered actions to build this artifact
            for (ProjectArtifactBuilder artifactBuilder : artifactBuilders) {
                artifactBuilder.build(artifact);
            }

            LocalComponentArtifactIdentifier id = (LocalComponentArtifactIdentifier) artifact.getId();
            File localArtifactFile = id.getFile();
            if (localArtifactFile != null) {
                result.resolved(localArtifactFile);
            } else {
                result.notFound(artifact.getId());
            }
        }
    }

    private boolean isProjectModule(ComponentIdentifier componentId) {
        return componentId instanceof ProjectComponentIdentifier;
    }
}
