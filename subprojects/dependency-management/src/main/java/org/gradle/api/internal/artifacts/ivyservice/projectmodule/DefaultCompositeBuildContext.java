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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;

public class DefaultCompositeBuildContext implements CompositeBuildContext {
    // TODO:DAZ Reduce the verbosity of composite build
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompositeBuildContext.class);

    private final Multimap<ModuleIdentifier, ProjectComponentIdentifier> replacementProjects = ArrayListMultimap.create();
    private final Map<ProjectComponentIdentifier, RegisteredProject> projectMetadata = Maps.newHashMap();

    @Override
    public ProjectComponentIdentifier getReplacementProject(ModuleComponentSelector selector) {
        ModuleIdentifier candidateId = DefaultModuleIdentifier.newId(selector.getGroup(), selector.getModule());
        Collection<ProjectComponentIdentifier> providingProjects = replacementProjects.get(candidateId);
        if (providingProjects.isEmpty()) {
            LOGGER.info("Found no composite build substitute for module '" + candidateId + "'.");
            return null;
        }
        if (providingProjects.size() == 1) {
            ProjectComponentIdentifier match = providingProjects.iterator().next();
            LOGGER.info("Found project '" + match + "' as substitute for module '" + candidateId + "'.");
            return match;
        }
        SortedSet<String> sortedProjects = Sets.newTreeSet(CollectionUtils.collect(providingProjects, new Transformer<String, ProjectComponentIdentifier>() {
            @Override
            public String transform(ProjectComponentIdentifier projectComponentIdentifier) {
                return projectComponentIdentifier.getProjectPath();
            }
        }));
        String failureMessage = String.format("Module version '%s' is not unique in composite: can be provided by projects %s.", selector.getDisplayName(), sortedProjects);
        throw new ModuleVersionResolveException(selector, failureMessage);
    }

    @Override
    public LocalComponentMetaData getProject(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = projectMetadata.get(project);
        return registeredProject == null ? null : registeredProject.metaData;
    }

    @Override
    public File getProjectDirectory(ProjectComponentIdentifier project) {
        RegisteredProject registeredProject = projectMetadata.get(project);
        if (registeredProject == null) {
            throw new IllegalStateException(String.format("Requested %s which was never registered", project));
        }
        return registeredProject.projectDirectory;
    }

    @Override
    public void register(ModuleIdentifier moduleId, ProjectComponentIdentifier project, LocalComponentMetaData localComponentMetaData, File projectDirectory) {
        LOGGER.info("Registering project '" + project + "' in composite build. Will substitute for module '" + moduleId + "'.");
        replacementProjects.put(moduleId, project);
        if (projectMetadata.containsKey(project)) {
            String failureMessage = String.format("Project path '%s' is not unique in composite.", project.getProjectPath());
            throw new ReportedException(new GradleException(failureMessage));
        }
        projectMetadata.put(project, new RegisteredProject(localComponentMetaData, projectDirectory));
    }

    private static class RegisteredProject {
        LocalComponentMetaData metaData;
        File projectDirectory;

        public RegisteredProject(LocalComponentMetaData metaData, File projectDirectory) {
            this.metaData = metaData;
            this.projectDirectory = projectDirectory;
        }
    }
}
