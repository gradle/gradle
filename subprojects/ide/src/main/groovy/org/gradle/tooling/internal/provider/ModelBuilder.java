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
import org.gradle.api.internal.GradleInternal;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion2;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;
import org.gradle.tooling.internal.provider.dependencies.EclipseProjectDependenciesFactory;
import org.gradle.tooling.internal.provider.dependencies.ExternalDependenciesFactory;
import org.gradle.tooling.internal.provider.dependencies.SourceDirectoriesFactory;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author Adam Murdoch, Szczepan Faber, @date: 17.03.11
*/
public class ModelBuilder {
    private final boolean includeTasks;
    private boolean projectDependenciesOnly;
    private DefaultEclipseProject currentProject;
    private final Map<String, DefaultEclipseProject> projectMapping = new HashMap<String, DefaultEclipseProject>();
    private GradleInternal gradle;

    public ModelBuilder(boolean includeTasks, boolean projectDependenciesOnly) {
        this.includeTasks = includeTasks;
        this.projectDependenciesOnly = projectDependenciesOnly;
    }

    public void buildAll(GradleInternal gradle) {
        this.gradle = gradle;
        build(gradle.getRootProject());
    }

    public DefaultEclipseProject getProject() {
        return currentProject;
    }

    private List<DefaultEclipseProject> buildChildren(Project project) {
        List<DefaultEclipseProject> children = new ArrayList<DefaultEclipseProject>();
        for (Project child : project.getChildProjects().values()) {
            children.add(build(child));
        }
        return children;
    }

    private void addProject(Project project, DefaultEclipseProject eclipseProject) {
        if (project == gradle.getDefaultProject()) {
            currentProject = eclipseProject;
        }
        projectMapping.put(project.getPath(), eclipseProject);
    }

    private DefaultEclipseProject build(Project project) {
        EclipseModel eclipseModel = project.getPlugins().getPlugin(EclipsePlugin.class).getModel();
        EclipseClasspath classpath = eclipseModel.getClasspath();

        classpath.setProjectDependenciesOnly(projectDependenciesOnly);
        List<ClasspathEntry> entries = classpath.resolveDependencies();

        List<ExternalDependencyVersion1> dependencies = new ExternalDependenciesFactory().create(project, entries);
        List<EclipseProjectDependencyVersion2> projectDependencies = new EclipseProjectDependenciesFactory().create(projectMapping, entries);
        List<EclipseSourceDirectoryVersion1> sourceDirectories = new SourceDirectoriesFactory().create(project, entries);

        List<DefaultEclipseProject> children = buildChildren(project);

        org.gradle.plugins.ide.eclipse.model.EclipseProject internalProject = eclipseModel.getProject();
        String name = internalProject.getName();
        String description = GUtil.elvis(internalProject.getComment(), null);
        DefaultEclipseProject eclipseProject = new DefaultEclipseProject(name, project.getPath(), description, project.getProjectDir(), children, sourceDirectories, dependencies, projectDependencies);
        if (includeTasks) {
            eclipseProject.setTasks(new TasksFactory().create(project, eclipseProject));
        }
        for (DefaultEclipseProject child : children) {
            child.setParent(eclipseProject);
        }
        addProject(project, eclipseProject);
        return eclipseProject;
    }
}