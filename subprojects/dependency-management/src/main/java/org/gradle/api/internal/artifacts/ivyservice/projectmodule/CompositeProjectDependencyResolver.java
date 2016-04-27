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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.api.internal.component.ArtifactType;
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

public class CompositeProjectDependencyResolver implements DependencyToComponentIdResolver, ArtifactResolver, ComponentMetaDataResolver {
    private final CompositeProjectComponentRegistry projectComponentRegistry;
    private final CompositeProjectArtifactBuilder artifactBuilder;

    public CompositeProjectDependencyResolver(CompositeProjectComponentRegistry projectComponentRegistry, CompositeProjectArtifactBuilder artifactBuilder) {
        this.projectComponentRegistry = projectComponentRegistry;
        this.artifactBuilder = artifactBuilder;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof ModuleComponentSelector) {
            try {
                maybeResolveInComposite(dependency, result);
            } catch (ModuleVersionResolveException e) {
                result.failed(e);
            }
        }
    }

    public void maybeResolveInComposite(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        ModuleComponentSelector selector = (ModuleComponentSelector) dependency.getSelector();
        ProjectComponentIdentifier replacement = projectComponentRegistry.getReplacementProject(selector);
        if (replacement == null) {
            return;
        }

        LocalComponentMetaData metaData = projectComponentRegistry.getProject(replacement);
        result.resolved(metaData);
        result.setSelectionReason(VersionSelectionReasons.COMPOSITE_BUILD);
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {

    }

    @Override
    public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
    }

    @Override
    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        if (artifact instanceof CompositeProjectComponentArtifactMetaData) {
            CompositeProjectComponentArtifactMetaData artifactMetaData = (CompositeProjectComponentArtifactMetaData) artifact;
            String projectPath = ((ProjectComponentIdentifier) artifact.getComponentId()).getProjectPath();

            // Run the tasks to build this artifact in the composite participant
            artifactBuilder.build(projectPath, artifactMetaData.getTaskNames());

            File localArtifactFile = artifactMetaData.getFile();
            if (localArtifactFile != null) {
                result.resolved(localArtifactFile);
            } else {
                result.notFound(artifact.getId());
            }
        }
    }
}
