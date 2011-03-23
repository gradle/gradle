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

import org.gradle.BuildAdapter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.GeneratorTaskConfigurer;
import org.gradle.plugins.ide.eclipse.EclipseConfigurer;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseDomainModel;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion2;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;

import java.io.File;
import java.util.*;

/**
* @author Adam Murdoch, Szczepan Faber, @date: 17.03.11
*/
public class ModelBuilder extends BuildAdapter {
    private DefaultEclipseProject currentProject;
    private final Map<String, EclipseProjectVersion2> projectMapping = new HashMap<String, EclipseProjectVersion2>();
    private GradleInternal gradle;

    @Override
    public void projectsEvaluated(Gradle gradle) {
        this.gradle = (GradleInternal) gradle;
        try {
            Project root = gradle.getRootProject();
            configureEclipsePlugin(root);
            build(root);
        } finally {
            this.gradle = null;
        }
    }

    private DefaultEclipseProject build(Project project) {
        Configuration configuration = project.getConfigurations().findByName(
                "testRuntime");
        List<ExternalDependencyVersion1> dependencies = new ArrayList<ExternalDependencyVersion1>();
        final List<EclipseProjectDependencyVersion1> projectDependencies = new ArrayList<EclipseProjectDependencyVersion1>();

        if (configuration != null) {
            Set<File> classpath = configuration.files(new Spec<Dependency>() {
                public boolean isSatisfiedBy(Dependency element) {
                    return element instanceof ExternalModuleDependency;
                }
            });
            for (final File file : classpath) {
                dependencies.add(new ExternalDependencyVersion1() {
                    public File getFile() {
                        return file;
                    }
                });
            }
            for (final ProjectDependency projectDependency : configuration.getAllDependencies(ProjectDependency.class)) {
                projectDependencies.add(new EclipseProjectDependencyVersion1() {
                    public EclipseProjectVersion2 getTargetProject() {
                        return projectMapping.get(projectDependency.getDependencyProject().getPath());
                    }

                    public String getPath() {
                        return projectDependency.getDependencyProject().getName();
                    }
                });
            }
        }

        EclipseDomainModel eclipseDomainModel = project.getPlugins().getPlugin(EclipsePlugin.class).getEclipseDomainModel();

        List<EclipseSourceDirectoryVersion1> sourceDirectories = new SourceDirectoriesFactory().create(project, eclipseDomainModel.getClasspath());

        List<DefaultEclipseProject> children = new ArrayList<DefaultEclipseProject>();
        for (Project child : project.getChildProjects().values()) {
            children.add(build(child));
        }

        String name = eclipseDomainModel.getProject().getName();
        DefaultEclipseProject eclipseProject = new DefaultEclipseProject(name, project.getPath(), project.getProjectDir(), children, sourceDirectories, dependencies, projectDependencies);
        for (DefaultEclipseProject child : children) {
            child.setParent(eclipseProject);
        }
        addProject(project, eclipseProject);
        return eclipseProject;
    }

    private void configureEclipsePlugin(Project root) {
        Set<Project> allprojects = root.getAllprojects();
        for (Project p : allprojects) {
            if (!p.getPlugins().hasPlugin("eclipse")) {
                p.getPlugins().apply("eclipse");
            }
        }

        //TODO SF: this is quite hacky for now. We should really execute 'eclipseConfigurer' task in a proper gradle fashion
        EclipseConfigurer eclipseConfigurer = (EclipseConfigurer) root.getTasks().getByName("eclipseConfigurer");
        eclipseConfigurer.configure();

        for (Project p : allprojects) {
            p.getTasks().withType(GeneratorTaskConfigurer.class, new Action<GeneratorTaskConfigurer>() {
                public void execute(GeneratorTaskConfigurer generatorTaskConfigurer) {
                    generatorTaskConfigurer.configure();
                }
            });
        }
    }

    private void addProject(Project project, DefaultEclipseProject eclipseProject) {
        if (project == gradle.getDefaultProject()) {
            currentProject = eclipseProject;
        }
        projectMapping.put(project.getPath(), eclipseProject);
    }

    public DefaultEclipseProject getCurrentProject() {
        return currentProject;
    }
}
