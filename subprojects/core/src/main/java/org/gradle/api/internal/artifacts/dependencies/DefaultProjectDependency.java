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

import com.google.common.base.Objects;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.CachingDependencyResolveContext;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.exceptions.ConfigurationNotConsumableException;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependencyInternal {
    private final ProjectInternal dependencyProject;
    private final boolean buildProjectDependencies;
    private final TaskDependencyFactory taskDependencyFactory;

    public DefaultProjectDependency(ProjectInternal dependencyProject, boolean buildProjectDependencies, TaskDependencyFactory taskDependencyFactory) {
        this(dependencyProject, null, buildProjectDependencies, taskDependencyFactory);
    }

    public DefaultProjectDependency(ProjectInternal dependencyProject, boolean buildProjectDependencies) {
        this(dependencyProject, null, buildProjectDependencies, DefaultTaskDependencyFactory.withNoAssociatedProject());
    }

    public DefaultProjectDependency(ProjectInternal dependencyProject, String configuration, boolean buildProjectDependencies, TaskDependencyFactory taskDependencyFactory) {
        super(configuration);
        this.dependencyProject = dependencyProject;
        this.buildProjectDependencies = buildProjectDependencies;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public Project getDependencyProject() {
        return dependencyProject;
    }

    @Override
    public String getGroup() {
        return dependencyProject.getGroup().toString();
    }

    @Override
    public String getName() {
        return dependencyProject.getName();
    }

    @Override
    public String getVersion() {
        return dependencyProject.getVersion().toString();
    }

    @Override
    public Configuration findProjectConfiguration() {
        ConfigurationContainer dependencyConfigurations = getDependencyProject().getConfigurations();
        String declaredConfiguration = getTargetConfiguration();
        Configuration selectedConfiguration = dependencyConfigurations.getByName(GUtil.elvis(declaredConfiguration, Dependency.DEFAULT_CONFIGURATION));
        if (!selectedConfiguration.isCanBeConsumed()) {
            throw new ConfigurationNotConsumableException(dependencyProject.getDisplayName(), selectedConfiguration.getName());
        }
        warnIfConfigurationIsDeprecated((DeprecatableConfiguration) selectedConfiguration);
        return selectedConfiguration;
    }

    private void warnIfConfigurationIsDeprecated(DeprecatableConfiguration selectedConfiguration) {
        selectedConfiguration.maybeEmitConsumptionDeprecation();
    }

    @Override
    public ProjectDependency copy() {
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(dependencyProject, getTargetConfiguration(), buildProjectDependencies, taskDependencyFactory);
        copyTo(copiedProjectDependency);
        return copiedProjectDependency;
    }

    @Override
    public Set<File> resolve() {
        return resolve(true);
    }

    @Override
    public Set<File> resolve(boolean transitive) {
        CachingDependencyResolveContext context = new CachingDependencyResolveContext(taskDependencyFactory, transitive, Collections.emptyMap());
        context.add(this);
        return context.resolve().getFiles();
    }

    @Override
    public void resolve(DependencyResolveContext context) {
        boolean transitive = isTransitive() && context.isTransitive();
        if (transitive) {
            Configuration projectConfiguration = findProjectConfiguration();
            for (Dependency dependency : projectConfiguration.getAllDependencies()) {
                context.add(dependency);
            }
            for (DependencyConstraint dependencyConstraint : projectConfiguration.getAllDependencyConstraints()) {
                context.add(dependencyConstraint);
            }
        }
    }

    @Override
    public TaskDependencyInternal getBuildDependencies() {
        return taskDependencyFactory.visitingDependencies(context -> {
            if (!buildProjectDependencies) {
                return;
            }

            Configuration configuration = findProjectConfiguration();
            context.add(configuration);
            context.add(configuration.getAllArtifacts());
        });
    }

    @Override
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
        if (getTargetConfiguration() != null ? !this.getTargetConfiguration().equals(that.getTargetConfiguration())
            : that.getTargetConfiguration() != null) {
            return false;
        }
        if (this.buildProjectDependencies != that.buildProjectDependencies) {
            return false;
        }
        if (!Objects.equal(getAttributes(), that.getAttributes())) {
            return false;
        }
        if (!Objects.equal(getRequestedCapabilities(), that.getRequestedCapabilities())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getDependencyProject().hashCode() ^ (getTargetConfiguration() != null ? getTargetConfiguration().hashCode() : 31) ^ (buildProjectDependencies ? 1 : 0);
    }

    @Override
    public String toString() {
        return "DefaultProjectDependency{" + "dependencyProject='" + dependencyProject + '\'' + ", configuration='"
            + (getTargetConfiguration() == null ? Dependency.DEFAULT_CONFIGURATION : getTargetConfiguration()) + '\'' + '}';
    }
}
