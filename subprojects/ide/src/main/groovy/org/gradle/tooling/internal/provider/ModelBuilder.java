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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.*;
import org.gradle.tooling.internal.*;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion2;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseTaskVersion1;
import org.gradle.util.GUtil;
import org.gradle.util.ReflectionUtil;

import java.io.File;
import java.util.*;

/**
* @author Adam Murdoch, Szczepan Faber, @date: 17.03.11
*/
public class ModelBuilder {
    private boolean projectDependenciesOnly;
    private Object currentProject;
    private final Map<String, EclipseProjectVersion3> projectMapping = new HashMap<String, EclipseProjectVersion3>();
    private GradleInternal gradle;
    private final TasksFactory tasksFactory;

    public ModelBuilder(boolean includeTasks, boolean projectDependenciesOnly) {
        this.tasksFactory = new TasksFactory(includeTasks);
        this.projectDependenciesOnly = projectDependenciesOnly;
    }

    public Object buildAll(GradleInternal gradle) {
        this.gradle = gradle;
        Project root = gradle.getRootProject();
        tasksFactory.collectTasks(root);
        new EclipsePluginApplier().apply(root);
        buildHierarchy(root);
        populate(root);
        return currentProject;
    }

    private void addProject(Project project, EclipseProjectVersion3 eclipseProject) {
        if (project == gradle.getDefaultProject()) {
            currentProject = eclipseProject;
        }
        projectMapping.put(project.getPath(), eclipseProject);
    }

    private void populate(Project project) {
        EclipseModel eclipseModel = project.getPlugins().getPlugin(EclipsePlugin.class).getModel();
        EclipseClasspath classpath = eclipseModel.getClasspath();

        classpath.setProjectDependenciesOnly(projectDependenciesOnly);
        List<ClasspathEntry> entries = classpath.resolveDependencies();

        final List<ExternalDependencyVersion1> externalDependencies = new LinkedList<ExternalDependencyVersion1>();
        final List<EclipseProjectDependencyVersion2> projectDependencies = new LinkedList<EclipseProjectDependencyVersion2>();
        final List<EclipseSourceDirectoryVersion1> sourceDirectories = new LinkedList<EclipseSourceDirectoryVersion1>();

        for (ClasspathEntry entry : entries) {
            if (entry instanceof Library) {
                Library library = (Library) entry;
                final File file = project.file(library.getPath());
                final File source = library.getSourcePath() == null ? null : project.file(library.getSourcePath());
                final File javadoc = library.getJavadocPath() == null ? null : project.file(library.getJavadocPath());
                externalDependencies.add(new DefaultExternalDependency(file, javadoc, source));
            } else if (entry instanceof ProjectDependency) {
                final ProjectDependency projectDependency = (ProjectDependency) entry;
                final String path = StringUtils.removeStart(projectDependency.getPath(), "/");
                projectDependencies.add(new DefaultEclipseProjectDependency(path, projectMapping.get(projectDependency.getGradlePath())));
            } else if (entry instanceof SourceFolder) {
                String path = ((SourceFolder) entry).getPath();
                sourceDirectories.add(new DefaultEclipseSourceDirectory(path, project.file(path)));
            }
        }

        final EclipseProjectVersion3 eclipseProject = projectMapping.get(project.getPath());
        ReflectionUtil.setProperty(eclipseProject, "classpath", externalDependencies);
        ReflectionUtil.setProperty(eclipseProject, "projectDependencies", projectDependencies);
        ReflectionUtil.setProperty(eclipseProject, "sourceDirectories", sourceDirectories);

        if (ReflectionUtil.hasProperty(eclipseProject, "linkedResources")) {
            List<DefaultEclipseLinkedResource> linkedResources = new LinkedList<DefaultEclipseLinkedResource>();
            for(Link r: eclipseModel.getProject().getLinkedResources()) {
                linkedResources.add(new DefaultEclipseLinkedResource(r.getName(), r.getType(), r.getLocation(), r.getLocationUri()));
            }
            ReflectionUtil.setProperty(eclipseProject, "linkedResources", linkedResources);
        }

        List<EclipseTaskVersion1> out = new ArrayList<EclipseTaskVersion1>();
        for (final Task t : tasksFactory.getTasks(project)) {
            out.add(new DefaultEclipseTask(eclipseProject, t.getPath(), t.getName(), t.getDescription()));
        }
        ReflectionUtil.setProperty(eclipseProject, "tasks", out);

        for (Project childProject : project.getChildProjects().values()) {
            populate(childProject);
        }
    }

    private EclipseProjectVersion3 buildHierarchy(Project project) {
        List<EclipseProjectVersion3> children = new ArrayList<EclipseProjectVersion3>();
        for (Project child : project.getChildProjects().values()) {
            children.add(buildHierarchy(child));
        }

        EclipseModel eclipseModel = project.getPlugins().getPlugin(EclipsePlugin.class).getModel();
        org.gradle.plugins.ide.eclipse.model.EclipseProject internalProject = eclipseModel.getProject();
        String name = internalProject.getName();
        String description = GUtil.elvis(internalProject.getComment(), null);
        EclipseProjectVersion3 eclipseProject = new DefaultEclipseProject(name, project.getPath(), description, project.getProjectDir(), children);
        for (Object child : children) {
            ReflectionUtil.setProperty(child, "parent", eclipseProject);
        }
        addProject(project, eclipseProject);
        return eclipseProject;
    }
}
