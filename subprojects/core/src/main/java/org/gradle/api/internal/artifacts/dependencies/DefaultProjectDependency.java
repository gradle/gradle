/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.CachingDependencyResolveContext;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependencyInternal {
    private final ProjectInternal dependencyProject;
    private final boolean buildProjectDependencies;
    private final ProjectAccessListener projectAccessListener;

    public DefaultProjectDependency(ProjectInternal dependencyProject, ProjectAccessListener projectAccessListener, boolean buildProjectDependencies) {
        this(dependencyProject, null, projectAccessListener, buildProjectDependencies);
    }

    public DefaultProjectDependency(ProjectInternal dependencyProject, String configuration,
                                    ProjectAccessListener projectAccessListener, boolean buildProjectDependencies) {
        super(configuration);
        this.dependencyProject = dependencyProject;
        this.projectAccessListener = projectAccessListener;
        this.buildProjectDependencies = buildProjectDependencies;
    }

    public Project getDependencyProject() {
        return dependencyProject;
    }

    public String getGroup() {
        return dependencyProject.getGroup().toString();
    }

    public String getName() {
        return dependencyProject.getName();
    }

    public String getVersion() {
        return dependencyProject.getVersion().toString();
    }

    @Deprecated
    public Configuration getProjectConfiguration() {
        return dependencyProject.getConfigurations().getByName(getConfiguration());
    }

    @Override
    public Configuration findProjectConfiguration(Map<String, String> clientAttributes) {
        Configuration selectedConfiguration = null;
        ConfigurationContainer dependencyConfigurations = getDependencyProject().getConfigurations();
        String declaredConfiguration = getTargetConfiguration().orNull();
        if (declaredConfiguration == null && clientAttributes!=null && !clientAttributes.isEmpty()) {
            for (Configuration dependencyConfiguration : dependencyConfigurations) {
                if (dependencyConfiguration.hasAttributes()) {
                    Map<String, String> attributes = dependencyConfiguration.getAttributes();
                    if (attributes.entrySet().containsAll(clientAttributes.entrySet())) {
                        selectedConfiguration = dependencyConfiguration;
                        break;
                    }
                }
            }
        }
        if (selectedConfiguration == null) {
            selectedConfiguration = dependencyConfigurations.getByName(GUtil.elvis(declaredConfiguration, Dependency.DEFAULT_CONFIGURATION));
        }
        return selectedConfiguration;
    }

    public ProjectDependency copy() {
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(dependencyProject,
            getTargetConfiguration().orNull(), projectAccessListener, buildProjectDependencies);
        copyTo(copiedProjectDependency);
        return copiedProjectDependency;
    }

    public Set<File> resolve() {
        return resolve(true);
    }

    public Set<File> resolve(boolean transitive) {
        CachingDependencyResolveContext context = new CachingDependencyResolveContext(transitive, null);
        context.add(this);
        return context.resolve().getFiles();
    }

    public void beforeResolved() {
        projectAccessListener.beforeResolvingProjectDependency(dependencyProject);
    }

    @Override
    public void resolve(DependencyResolveContext context) {
        boolean transitive = isTransitive() && context.isTransitive();
        if (transitive) {
            for (Dependency dependency : findProjectConfiguration(context.getAttributes()).getAllDependencies()) {
                context.add(dependency);
            }
        }
    }

    public TaskDependencyInternal getBuildDependencies() {
        return new TaskDependencyImpl(null);
    }

    @Override
    public TaskDependencyInternal getTaskDependency(Map<String, String> attributes) {
        return new TaskDependencyImpl(attributes);
    }

    public boolean contentEquals(Dependency dependency) {
        if (this == dependency) {
            return true;
        }
        if (dependency == null || getClass() != dependency.getClass()) {
            return false;
        }

        ProjectDependency that = (ProjectDependency) dependency;
        if (!isCommonContentEquals(that)) {
            return false;
        }

        return dependencyProject.equals(that.getDependencyProject());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultProjectDependency that = (DefaultProjectDependency) o;
        if (!this.getDependencyProject().equals(that.getDependencyProject())) {
            return false;
        }
        if (!this.getTargetConfiguration().equals(that.getTargetConfiguration())) {
            return false;
        }
        if (this.buildProjectDependencies != that.buildProjectDependencies) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getDependencyProject().hashCode() ^ getTargetConfiguration().hashCode() ^ (buildProjectDependencies ? 1 : 0);
    }


    @Override
    public String toString() {
        return "DefaultProjectDependency{" + "dependencyProject='" + dependencyProject + '\'' + ", configuration='"
                + getTargetConfiguration().or(Dependency.DEFAULT_CONFIGURATION) + '\'' + '}';
    }

    private class TaskDependencyImpl extends AbstractTaskDependency {
        private final Map<String, String> attributes;

        private TaskDependencyImpl(Map<String, String> attributes) {
            this.attributes = attributes;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            if (!buildProjectDependencies) {
                return;
            }
            projectAccessListener.beforeResolvingProjectDependency(dependencyProject);

            Configuration configuration = findProjectConfiguration(attributes);
            context.add(configuration);
            context.add(configuration.getAllArtifacts());
        }
    }
}
