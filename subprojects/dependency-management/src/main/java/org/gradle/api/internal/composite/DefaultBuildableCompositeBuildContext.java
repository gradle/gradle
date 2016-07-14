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
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

class DefaultBuildableCompositeBuildContext implements CompositeBuildContext {
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

    public Collection<ComponentArtifactMetadata> getAdditionalArtifacts(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = projectMetadata.get(project);
        return registeredProject == null ? null : registeredProject.artifacts;
     }

    public void register(ProjectComponentIdentifier project, LocalComponentMetadata localComponentMetadata, File projectDirectory) {
        if (projectMetadata.containsKey(project)) {
            String failureMessage = String.format("Project path '%s' is not unique in composite.", project.getProjectPath());
            throw new ReportedException(new GradleException(failureMessage));
        }
        projectMetadata.put(project, new RegisteredProject(localComponentMetadata, projectDirectory));
    }

    public void registerAdditionalArtifact(ProjectComponentIdentifier project, ComponentArtifactMetadata artifact) {
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
        Collection<ComponentArtifactMetadata> artifacts = Lists.newArrayList();

        public RegisteredProject(LocalComponentMetadata metaData, File projectDirectory) {
            this.metaData = metaData;
            this.projectDirectory = projectDirectory;
        }
    }
}
