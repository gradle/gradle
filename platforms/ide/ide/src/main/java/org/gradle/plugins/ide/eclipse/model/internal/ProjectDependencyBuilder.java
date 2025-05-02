/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants;
import org.gradle.plugins.ide.eclipse.internal.EclipseProjectMetadata;
import org.gradle.plugins.ide.eclipse.model.FileReference;
import org.gradle.plugins.ide.eclipse.model.ProjectDependency;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;

public class ProjectDependencyBuilder {
    private final IdeArtifactRegistry ideArtifactRegistry;

    public ProjectDependencyBuilder(IdeArtifactRegistry ideArtifactRegistry) {
        this.ideArtifactRegistry = ideArtifactRegistry;
    }

    public ProjectDependency build(ProjectComponentIdentifier componentIdentifier, FileReference publication, TaskDependency buildDependencies, boolean testDependency, boolean asJavaModule) {
        ProjectDependency dependency = buildProjectDependency(determineTargetProjectPath(componentIdentifier));
        dependency.setPublication(publication);
        if (buildDependencies != null) {
            dependency.buildDependencies(buildDependencies);
        }

        if (testDependency) {
            dependency.getEntryAttributes().put(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE);
        }

        if (asJavaModule) {
            dependency.getEntryAttributes().put(EclipsePluginConstants.MODULE_ATTRIBUTE_KEY, EclipsePluginConstants.MODULE_ATTRIBUTE_VALUE);
        }

        if (containsTestFixtures(componentIdentifier)) {
            dependency.getEntryAttributes().put(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, "false");
        } else {
            dependency.getEntryAttributes().put(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, "true");
        }
        return dependency;
    }

    private String determineTargetProjectPath(ProjectComponentIdentifier id) {
        return "/" + determineTargetProjectName(id);
    }

    public String determineTargetProjectName(ProjectComponentIdentifier id) {
        EclipseProjectMetadata eclipseProject = ideArtifactRegistry.getIdeProject(EclipseProjectMetadata.class, id);
        return eclipseProject == null ? id.getProjectName() : eclipseProject.getName();
    }

    private boolean containsTestFixtures(ProjectComponentIdentifier id) {
        EclipseProjectMetadata eclipseProject = ideArtifactRegistry.getIdeProject(EclipseProjectMetadata.class, id);
        return eclipseProject != null ? eclipseProject.hasJavaTestFixtures() : false;
    }

    private ProjectDependency buildProjectDependency(String path) {
        final ProjectDependency out = new ProjectDependency(path);
        out.setExported(false);
        return out;
    }
}
