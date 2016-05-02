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
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.CompositeProjectDirectoryMapper;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ear.EarPlugin;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.EclipseWtpPlugin;
import org.gradle.plugins.ide.eclipse.model.*;
import org.gradle.plugins.ide.internal.tooling.eclipse.EclipseWtpClasspathAttributeSupport;
import org.gradle.plugins.ide.internal.tooling.eclipse.*;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.provider.model.internal.ProjectToolingModelBuilder;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

public class EclipseModelBuilder implements ProjectToolingModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;
    private final Transformer<File, String> compositeProjectMapper;

    private boolean projectDependenciesOnly;
    private DefaultEclipseProject result;
    private final Map<String, DefaultEclipseProject> projectMapping = new HashMap<String, DefaultEclipseProject>();
    private TasksFactory tasksFactory;
    private DefaultGradleProject<?> rootGradleProject;
    private Project currentProject;

    public EclipseModelBuilder(GradleProjectBuilder gradleProjectBuilder, ServiceRegistry services) {
        this.gradleProjectBuilder = gradleProjectBuilder;
        compositeProjectMapper = new CompositeProjectDirectoryMapper(services);
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.eclipse.EclipseProject")
            || modelName.equals("org.gradle.tooling.model.eclipse.HierarchicalEclipseProject");
    }

    @Override
    public void addModels(String modelName, Project project, Map<String, Object> models) {
        DefaultEclipseProject eclipseProject = buildAll(modelName, project);
        addModels(eclipseProject, models);
    }

    private void addModels(DefaultEclipseProject eclipseProject, Map<String, Object> models) {
        models.put(eclipseProject.getPath(), eclipseProject);
        for (DefaultEclipseProject childProject : eclipseProject.getChildren()) {
            addModels(childProject, models);
        }
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
        applyEclipseWtpPluginOnWebProjects(root);
        buildHierarchy(root);
        populate(root);
        return result;
    }

    private void applyEclipsePlugin(Project root) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            p.getPluginManager().apply(EclipsePlugin.class);
        }
        root.getPlugins().getPlugin(EclipsePlugin.class).makeSureProjectNamesAreUnique();
    }

    private void applyEclipseWtpPluginOnWebProjects(Project root) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            if (isWebProject(p)) {
                p.getPluginManager().apply(EclipseWtpPlugin.class);
            }
        }
    }

    private boolean isWebProject(Project project) {
        PluginContainer container = project.getPlugins();
        return container.hasPlugin(WarPlugin.class)
            || container.hasPlugin(EarPlugin.class)
            || container.hasPlugin(EclipseWtpPlugin.class);
    }

    private DefaultEclipseProject buildHierarchy(Project project) {
        List<DefaultEclipseProject> children = new ArrayList<DefaultEclipseProject>();
        for (Project child : project.getChildProjects().values()) {
            children.add(buildHierarchy(child));
        }

        EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
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

    private void addProject(Project project, DefaultEclipseProject eclipseProject) {
        if (project == currentProject) {
            result = eclipseProject;
        }
        projectMapping.put(project.getPath(), eclipseProject);
    }

    private void populate(Project project) {
        EclipseModel eclipseModel = project.getExtensions().getByType(EclipseModel.class);
        EclipseClasspath classpath = eclipseModel.getClasspath();

        classpath.setProjectDependenciesOnly(projectDependenciesOnly);
        List<ClasspathEntry> entries = classpath.resolveDependencies();

        final List<DefaultEclipseExternalDependency> externalDependencies = new LinkedList<DefaultEclipseExternalDependency>();
        final List<DefaultEclipseProjectDependency> projectDependencies = new LinkedList<DefaultEclipseProjectDependency>();
        final List<DefaultEclipseSourceDirectory> sourceDirectories = new LinkedList<DefaultEclipseSourceDirectory>();

        Map<AbstractLibrary, DefaultEclipseExternalDependency> entryToExternalDependency = new HashMap<AbstractLibrary, DefaultEclipseExternalDependency>();
        Map<ProjectDependency, DefaultEclipseProjectDependency> entryToProjectDependency = new HashMap<ProjectDependency, DefaultEclipseProjectDependency>();

        for (ClasspathEntry entry : entries) {
            //we don't handle Variables at the moment because users didn't request it yet
            //and it would probably push us to add support in the tooling api to retrieve the variable mappings.
            if (entry instanceof Library) {
                AbstractLibrary library = (AbstractLibrary) entry;
                final File file = library.getLibrary().getFile();
                final File source = library.getSourcePath() == null ? null : library.getSourcePath().getFile();
                final File javadoc = library.getJavadocPath() == null ? null : library.getJavadocPath().getFile();
                DefaultEclipseExternalDependency dependency = new DefaultEclipseExternalDependency(file, javadoc, source, library.getModuleVersion(), library.isExported());
                externalDependencies.add(dependency);
                entryToExternalDependency.put(library, dependency);
            } else if (entry instanceof ProjectDependency) {
                final ProjectDependency projectDependency = (ProjectDependency) entry;
                final String path = StringUtils.removeStart(projectDependency.getPath(), "/");
                DefaultEclipseProject targetProject = projectMapping.get(projectDependency.getGradlePath());
                DefaultEclipseProjectDependency dependency;
                if (targetProject == null) {
                    File projectDirectory = compositeProjectMapper.transform(projectDependency.getGradlePath());
                    dependency = new DefaultEclipseProjectDependency(path, projectDirectory, projectDependency.isExported());
                } else {
                    dependency = new DefaultEclipseProjectDependency(path, targetProject, projectDependency.isExported());
                }
                projectDependencies.add(dependency);
                entryToProjectDependency.put(projectDependency, dependency);
            } else if (entry instanceof SourceFolder) {
                final SourceFolder sourceFolder = (SourceFolder) entry;
                String path = sourceFolder.getPath();
                sourceDirectories.add(new DefaultEclipseSourceDirectory(path, sourceFolder.getDir()));
            }
        }

        DefaultEclipseProject eclipseProject = projectMapping.get(project.getPath());
        eclipseProject.setClasspath(externalDependencies);
        eclipseProject.setProjectDependencies(projectDependencies);
        eclipseProject.setSourceDirectories(sourceDirectories);

        List<DefaultEclipseLinkedResource> linkedResources = new LinkedList<DefaultEclipseLinkedResource>();
        for (Link r : eclipseModel.getProject().getLinkedResources()) {
            linkedResources.add(new DefaultEclipseLinkedResource(r.getName(), r.getType(), r.getLocation(), r.getLocationUri()));
        }
        eclipseProject.setLinkedResources(linkedResources);

        List<DefaultEclipseTask> tasks = new ArrayList<DefaultEclipseTask>();
        for (Task t : tasksFactory.getTasks(project)) {
            tasks.add(new DefaultEclipseTask(eclipseProject, t.getPath(), t.getName(), t.getDescription()));
        }
        eclipseProject.setTasks(tasks);

        List<DefaultEclipseProjectNature> natures = new ArrayList<DefaultEclipseProjectNature>();
        for(String n: eclipseModel.getProject().getNatures()) {
            natures.add(new DefaultEclipseProjectNature(n));
        }
        eclipseProject.setProjectNatures(natures);

        List<DefaultEclipseBuildCommand> buildCommands = new ArrayList<DefaultEclipseBuildCommand>();
        for (BuildCommand b : eclipseModel.getProject().getBuildCommands()) {
            buildCommands.add(new DefaultEclipseBuildCommand(b.getName(), b.getArguments()));
        }
        eclipseProject.setBuildCommands(buildCommands);
        EclipseJdt jdt = eclipseModel.getJdt();
        if (jdt != null) {
            eclipseProject.setJavaSourceSettings(new DefaultEclipseJavaSourceSettings()
                .setSourceLanguageLevel(jdt.getSourceCompatibility())
                .setTargetBytecodeVersion(jdt.getTargetCompatibility())
                .setJdk(DefaultInstalledJdk.current())
            );
        }

        if (isWebProject(project)) {
            EclipseWtp wtp = project.getExtensions().findByType(EclipseModel.class).getWtp();
            EclipseWtpClasspathAttributeSupport wtpSupport = new EclipseWtpClasspathAttributeSupport(project, wtp);
            wtpSupport.defineAttributesForExternalDependencies(entryToExternalDependency);
            wtpSupport.defineAttributesForProjectDependencies(entryToProjectDependency);
        }

        for (Project childProject : project.getChildProjects().values()) {
            populate(childProject);
        }
    }
}
