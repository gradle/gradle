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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class DefaultBuildableCompositeBuildContext implements CompositeBuildContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildableCompositeBuildContext.class);

    private final Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> provided = Sets.newHashSet();
    private final Map<ProjectComponentIdentifier, RegisteredProject> projectMetadata = Maps.newHashMap();

    @Override
    public LocalComponentMetadata getComponent(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = projectMetadata.get(project);
        return registeredProject == null ? null : registeredProject.metaData;
    }

    @Override
    public File getProjectDirectory(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = getRegisteredProject(project);
        return registeredProject.projectDirectory;
    }

    @Override
    public Set<ProjectComponentIdentifier> getAllProjects() {
        return projectMetadata.keySet();
    }

    @Override
    public Collection<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getProvidedComponents() {
        return provided;
    }

    public Collection<LocalComponentArtifactMetadata> getAdditionalArtifacts(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = projectMetadata.get(project);
        return registeredProject == null ? null : registeredProject.artifacts;
     }

    @Override
    public void registerSubstitution(ModuleVersionIdentifier moduleId, ProjectComponentIdentifier project) {
        LOGGER.info("Registering project '" + project + "' in composite build. Will substitute for module '" + moduleId.getModule() + "'.");
        provided.add(Pair.of(moduleId, project));
    }

    public void register(ProjectComponentIdentifier project, LocalComponentMetadata localComponentMetadata, File projectDirectory) {
        if (projectMetadata.containsKey(project)) {
            String failureMessage = String.format("Project path '%s' is not unique in composite.", project.getProjectPath());
            throw new GradleException(failureMessage);
        }
        projectMetadata.put(project, new RegisteredProject(localComponentMetadata, projectDirectory));
    }

    public void registerAdditionalArtifact(ProjectComponentIdentifier project, LocalComponentArtifactMetadata artifact) {
        getRegisteredProject(project).artifacts.add(artifact);
    }

    private RegisteredProject getRegisteredProject(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = projectMetadata.get(project);
        if (registeredProject == null) {
            throw new IllegalStateException(String.format("Requested %s which was never registered", project));
        }
        return registeredProject;
    }

    private static class RegisteredProject {
        LocalComponentMetadata metaData;
        File projectDirectory;
        Collection<LocalComponentArtifactMetadata> artifacts = Lists.newArrayList();

        public RegisteredProject(LocalComponentMetadata metaData, File projectDirectory) {
            this.metaData = metaData;
            this.projectDirectory = projectDirectory;
        }
    }
}
