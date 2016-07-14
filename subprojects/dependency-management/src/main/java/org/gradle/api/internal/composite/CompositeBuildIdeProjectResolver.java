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

package org.gradle.api.internal.composite;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactBuilder;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentArtifactIdentifier;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// TODO:DAZ Split out the non-composite resolution stuff and name appropriately
public class CompositeBuildIdeProjectResolver {
    private final CompositeBuildContext discovered;
    private final LocalComponentRegistry registry;
    private final List<ProjectArtifactBuilder> artifactBuilders;

    public CompositeBuildIdeProjectResolver(ServiceRegistry services) {
        List<CompositeBuildContext> registries = services.getAll(CompositeBuildContext.class);
        if (!registries.isEmpty()) {
            discovered = registries.iterator().next();
        } else {
            discovered = null;
        }
        registry = services.get(LocalComponentRegistry.class);
        artifactBuilders = services.getAll(ProjectArtifactBuilder.class);
    }

    public File getProjectDirectory(String projectPath) {
        ProjectComponentIdentifier projectComponentIdentifier = DefaultProjectComponentIdentifier.newId(projectPath);
        return getCompositeContext().getProjectDirectory(projectComponentIdentifier);
    }

    public Set<ProjectComponentIdentifier> getProjectsInComposite() {
        if (discovered == null) {
            return Collections.emptySet();
        }
        return getCompositeContext().getAllProjects();
    }

    private CompositeBuildContext getCompositeContext() {
        if (discovered == null) {
            throw new IllegalStateException("Not a composite");
        }
        return discovered;
    }

    public ComponentArtifactMetadata resolveArtifact(ProjectComponentIdentifier project, String type) {
        return findArtifact(project, type);
    }

    // TODO:DAZ Push this into dependency resolution, getting artifact by type
    public File resolveArtifactFile(ProjectComponentIdentifier project, String type) {
        ComponentArtifactMetadata artifactMetaData = resolveArtifact(project, type);
        if (artifactMetaData == null) {
            return null;
        }
        for (ProjectArtifactBuilder artifactBuilder : artifactBuilders) {
            artifactBuilder.build(artifactMetaData);
        }
        // TODO:DAZ Introduce a `LocalComponentArtifactMetaData` interface.
        return ((LocalComponentArtifactIdentifier) artifactMetaData).getFile();
    }

    private ComponentArtifactMetadata findArtifact(ProjectComponentIdentifier project, String type) {
        for (ComponentArtifactMetadata artifactMetaData : registry.getAdditionalArtifacts(project)) {
            if (artifactMetaData.getName().getType().equals(type)) {
                return artifactMetaData;
            }
        }
        return null;
    }

}
