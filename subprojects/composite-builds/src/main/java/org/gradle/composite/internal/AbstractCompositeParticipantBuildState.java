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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ForeignBuildIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Pair;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractCompositeParticipantBuildState extends AbstractBuildState implements CompositeBuildParticipantBuildState {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompositeParticipantBuildState.class);

    private Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules;

    public AbstractCompositeParticipantBuildState(BuildTreeState buildTree, BuildDefinition buildDefinition, @Nullable BuildState parent) {
        super(buildTree, buildDefinition, parent);
    }

    @Override
    public synchronized Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules() {
        if (availableModules == null) {
            ensureChildBuildConfigured();
            availableModules = new LinkedHashSet<>();
            for (ProjectState project : getProjects().getAllProjects()) {
                registerProject(availableModules, project.getMutableModel());
            }
        }
        return availableModules;
    }

    protected void ensureChildBuildConfigured() {
        ensureProjectsConfigured();
    }

    private void registerProject(Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules, ProjectInternal project) {
        ProjectComponentIdentifier projectIdentifier = new DefaultProjectComponentIdentifier(getBuildIdentifier(), project.getIdentityPath(), project.getProjectPath(), project.getName());
        ModuleVersionIdentifier moduleId = DefaultModuleVersionIdentifier.newId(project.getDependencyMetaDataProvider().getModule());
        LOGGER.info("Registering {} in composite build. Will substitute for module '{}'.", project, moduleId.getModule());
        availableModules.add(Pair.of(moduleId, projectIdentifier));
    }

    @Override
    public ProjectComponentIdentifier idToReferenceProjectFromAnotherBuild(ProjectComponentIdentifier identifier) {
        DefaultProjectComponentIdentifier original = (DefaultProjectComponentIdentifier) identifier;
        Path foreignBuildPath = Path.path(getBuildIdentifier().getBuildPath());
        return new DefaultProjectComponentIdentifier(new ForeignBuildIdentifier(foreignBuildPath), original.getIdentityPath(), original.projectPath(), original.getProjectName());
    }
}
