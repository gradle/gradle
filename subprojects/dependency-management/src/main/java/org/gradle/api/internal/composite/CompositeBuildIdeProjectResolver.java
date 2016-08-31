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
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.List;
import java.util.Set;

// TODO:DAZ Split out the non-composite resolution stuff and name appropriately
public class CompositeBuildIdeProjectResolver {
    private final CompositeBuildContext compositeContext;
    private final LocalComponentRegistry registry;
    private final List<ProjectArtifactBuilder> artifactBuilders;

    public CompositeBuildIdeProjectResolver(ServiceRegistry services) {
        compositeContext = services.get(CompositeBuildContext.class);
        registry = services.get(LocalComponentRegistry.class);
        artifactBuilders = services.getAll(ProjectArtifactBuilder.class);
    }

    public File getProjectDirectory(ProjectComponentIdentifier projectId) {
        return compositeContext.getProjectDirectory(projectId);
    }

    public Set<ProjectComponentIdentifier> getProjectsInComposite() {
        return compositeContext.getAllProjects();
    }

    public LocalComponentArtifactMetadata resolveArtifact(ProjectComponentIdentifier project, String type) {
        return findArtifact(project, type);
    }

    // TODO:DAZ Push this into dependency resolution, getting artifact by type
    public File resolveArtifactFile(ProjectComponentIdentifier project, String type) {
        LocalComponentArtifactMetadata artifactMetaData = resolveArtifact(project, type);
        if (artifactMetaData == null) {
            return null;
        }
        for (ProjectArtifactBuilder artifactBuilder : artifactBuilders) {
            artifactBuilder.build(artifactMetaData);
        }
        return artifactMetaData.getFile();
    }

    private LocalComponentArtifactMetadata findArtifact(ProjectComponentIdentifier project, String type) {
        for (LocalComponentArtifactMetadata artifactMetaData : registry.getAdditionalArtifacts(project)) {
            if (artifactMetaData.getName().getType().equals(type)) {
                return artifactMetaData;
            }
        }
        return null;
    }

}
