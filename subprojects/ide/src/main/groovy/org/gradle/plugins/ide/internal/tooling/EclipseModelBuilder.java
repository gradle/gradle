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

package org.gradle.plugins.ide.internal.tooling;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.*;
import org.gradle.plugins.ide.internal.tooling.eclipse.*;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion2;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseTaskVersion1;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

public class EclipseModelBuilder implements ToolingModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;

    private boolean projectDependenciesOnly;
    private DefaultEclipseProject result;
    private final Map<String, DefaultEclipseProject> projectMapping = new HashMap<String, DefaultEclipseProject>();
    private TasksFactory tasksFactory;
    private DefaultGradleProject<?> rootGradleProject;
    private Project currentProject;

    public EclipseModelBuilder(GradleProjectBuilder gradleProjectBuilder) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.eclipse.EclipseProject")
                || modelName.equals("org.gradle.tooling.model.eclipse.HierarchicalEclipseProject");
    }

    public DefaultEclipseProject buildAll(String modelName, Project project) {
        boolean includeTasks = modelName.equals("org.gradle.tooling.model.eclipse.EclipseProject");
        tasksFactory = new TasksFactory(includeTasks);
        projectDependenciesOnly = modelName.equals("org.gradle.tooling.model.eclipse.HierarchicalEclipseProject");
        currentProject = project;
        Project root = project.getRootProject();
        rootGradleProject = gradleProjectBuilder.buildAll(project);
        tasksFactory.collectTasks(root);
        applyEclipsePlugin(root);
        buildHierarchy(root);
        populate(root);
        return result;
    }

    private void applyEclipsePlugin(Project root) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            p.getPlugins().apply(EclipsePlugin.class);
        }
        root.getPlugins().getPlugin(EclipsePlugin.class).makeSureProjectNamesAreUnique();
    }

    private void addProject(Project project, DefaultEclipseProject eclipseProject) {
        if (project == currentProject) {
            result = eclipseProject;
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
            //we don't handle Variables at the moment because users didn't request it yet
            //and it would probably push us to add support in the tooling api to retrieve the variable mappings.
            if (entry instanceof Library) {
                AbstractLibrary library = (AbstractLibrary) entry;
                final File file = library.getLibrary().getFile();
                final File source = library.getSourcePath() == null ? null : library.getSourcePath().getFile();
                final File javadoc = library.getJavadocPath() == null ? null : library.getJavadocPath().getFile();
                externalDependencies.add(new DefaultEclipseExternalDependency(file, javadoc, source, library.getModuleVersion()));
            } else if (entry instanceof ProjectDependency) {
                final ProjectDependency projectDependency = (ProjectDependency) entry;
                final String path = StringUtils.removeStart(projectDependency.getPath(), "/");
                projectDependencies.add(new DefaultEclipseProjectDependency(path, projectMapping.get(projectDependency.getGradlePath())));
            } else if (entry instanceof SourceFolder) {
                String path = ((SourceFolder) entry).getPath();
                sourceDirectories.add(new DefaultEclipseSourceDirectory(path, project.file(path)));
            }
        }

        DefaultEclipseProject eclipseProject = projectMapping.get(project.getPath());
        eclipseProject.setClasspath(externalDependencies);
        eclipseProject.setProjectDependencies(projectDependencies);
        eclipseProject.setSourceDirectories(sourceDirectories);

        List<DefaultEclipseLinkedResource> linkedResources = new LinkedList<DefaultEclipseLinkedResource>();
        for(Link r: eclipseModel.getProject().getLinkedResources()) {
            linkedResources.add(new DefaultEclipseLinkedResource(r.getName(), r.getType(), r.getLocation(), r.getLocationUri()));
        }
        eclipseProject.setLinkedResources(linkedResources);

        List<EclipseTaskVersion1> tasks = new ArrayList<EclipseTaskVersion1>();
        for (Task t : tasksFactory.getTasks(project)) {
            tasks.add(new DefaultEclipseTask(eclipseProject, t.getPath(), t.getName(), t.getDescription()));
        }
        eclipseProject.setTasks(tasks);

        for (Project childProject : project.getChildProjects().values()) {
            populate(childProject);
        }
    }

    private DefaultEclipseProject buildHierarchy(Project project) {
        List<DefaultEclipseProject> children = new ArrayList<DefaultEclipseProject>();
        for (Project child : project.getChildProjects().values()) {
            children.add(buildHierarchy(child));
        }

        EclipseModel eclipseModel = project.getPlugins().getPlugin(EclipsePlugin.class).getModel();
        org.gradle.plugins.ide.eclipse.model.EclipseProject internalProject = eclipseModel.getProject();
        String name = internalProject.getName();
        String description = GUtil.elvis(internalProject.getComment(), null);
        DefaultEclipseProject eclipseProject =
                new DefaultEclipseProject(name, project.getPath(), description, project.getProjectDir(), children)
                .setGradleProject(rootGradleProject.findByPath(project.getPath()));

        for (DefaultEclipseProject child : children) {
            child.setParent(eclipseProject);
        }
        addProject(project, eclipseProject);
        return eclipseProject;
    }
}
