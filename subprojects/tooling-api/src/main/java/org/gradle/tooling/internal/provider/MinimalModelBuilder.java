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
package org.gradle.tooling.internal.provider;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.TaskVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion2;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the minimal Eclipse model. This is a copy of {@link ModelBuilder}, for now.
 *
 * TODO: merge this into {@link ModelBuilder}
 *
 * @author Adam Murdoch, Szczepan Faber, @date: 17.03.11
 */
public class MinimalModelBuilder extends AbstractModelBuilder {
    protected DefaultEclipseProject build(Project project) {
        Configuration configuration = project.getConfigurations().findByName("testRuntime");
        final List<EclipseProjectDependencyVersion2> projectDependencies = new ArrayList<EclipseProjectDependencyVersion2>();
        if (configuration != null) {
            for (final ProjectDependency projectDependency : configuration.getAllDependencies(ProjectDependency.class)) {
                projectDependencies.add(new EclipseProjectDependencyVersion2() {
                    public HierarchicalEclipseProjectVersion1 getTargetProject() {
                        return get(projectDependency.getDependencyProject());
                    }

                    public String getPath() {
                        return projectDependency.getDependencyProject().getName();
                    }
                });
            }
        }

        List<DefaultEclipseProject> children = buildChildren(project);

        String name = project.getName();
        String description = project.getDescription();
        List<TaskVersion1> tasks = Collections.emptyList();
        List<EclipseSourceDirectoryVersion1> sourceDirectories = Collections.emptyList();
        List<ExternalDependencyVersion1> dependencies = Collections.emptyList();
        DefaultEclipseProject eclipseProject = new DefaultEclipseProject(name, project.getPath(), description, project.getProjectDir(), children, tasks, sourceDirectories, dependencies, projectDependencies);
        for (DefaultEclipseProject child : children) {
            child.setParent(eclipseProject);
        }
        addProject(project, eclipseProject);
        return eclipseProject;
    }

    @Override
    protected void configureEclipsePlugin(Project root) {
    }
}
