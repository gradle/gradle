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
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.MetadataSourcedComponentArtifacts;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifacts;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import java.io.File;

public class ProjectDependencyResolver implements ComponentMetaDataResolver, DependencyToComponentIdResolver, ArtifactResolver {
    private final LocalComponentRegistry localComponentRegistry;
    private final ProjectArtifactBuilder artifactBuilder;
    private final ComponentIdentifierFactory componentIdentifierFactory;

    public ProjectDependencyResolver(LocalComponentRegistry localComponentRegistry, ProjectArtifactBuilder artifactBuilder, ComponentIdentifierFactory componentIdentifierFactory) {
        this.localComponentRegistry = localComponentRegistry;
        this.artifactBuilder = artifactBuilder;
        this.componentIdentifierFactory = componentIdentifierFactory;
    }

    @Override
    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof ProjectComponentSelector) {
            ProjectComponentSelector selector = (ProjectComponentSelector) dependency.getSelector();
            ProjectComponentIdentifier project = componentIdentifierFactory.createProjectComponentIdentifier(selector);
            LocalComponentMetadata componentMetaData = localComponentRegistry.getComponent(project);
            if (componentMetaData == null) {
                result.failed(new ModuleVersionResolveException(selector, project + " not found."));
            } else {
                result.resolved(componentMetaData);
            }
        }
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (identifier instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) identifier;
            LocalComponentMetadata componentMetaData = localComponentRegistry.getComponent(projectId);
            if (componentMetaData == null) {
                result.failed(new ModuleVersionResolveException(DefaultProjectComponentSelector.newSelector(projectId), projectId + " not found."));
            } else {
                result.resolved(componentMetaData);
            }
        }
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        if (isProjectModule(component.getComponentId())) {
            throw new UnsupportedOperationException("Resolving artifacts by type is not yet supported for project modules");
        }
    }

    @Override
    public void resolveArtifacts(ComponentResolveMetadata component, BuildableComponentArtifactsResolveResult result) {
        if (isProjectModule(component.getComponentId())) {
            ComponentArtifacts artifacts = new MetadataSourcedComponentArtifacts();
            artifacts = new ProjectDependencyComponentArtifacts(artifactBuilder, artifacts);
            result.resolved(artifacts);
        }
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        if (isProjectModule(artifact.getComponentId())) {
            LocalComponentArtifactMetadata projectArtifact = (LocalComponentArtifactMetadata) artifact;

            // Run any registered actions to build this artifact
            artifactBuilder.build(projectArtifact);

            File localArtifactFile = projectArtifact.getFile();
            if (localArtifactFile != null) {
                result.resolved(localArtifactFile);
            } else {
                result.notFound(projectArtifact.getId());
            }
        }
    }

    private boolean isProjectModule(ComponentIdentifier componentId) {
        return componentId instanceof ProjectComponentIdentifier;
    }
}
