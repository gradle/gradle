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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.ProjectAccessListener;

public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependencyInternal {
    private ProjectInternal dependencyProject;
    private final ProjectAccessListener projectAccessListener;

    public DefaultProjectDependency(ProjectInternal dependencyProject, ProjectAccessListener projectAccessListener) {
        this(dependencyProject, null, projectAccessListener);
    }

    public DefaultProjectDependency(ProjectInternal dependencyProject, String configuration,
                                    ProjectAccessListener projectAccessListener) {
        super(configuration);
        this.dependencyProject = dependencyProject;
        this.projectAccessListener = projectAccessListener;
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

    public Configuration getProjectConfiguration() {
        return dependencyProject.getConfigurations().getByName(getConfiguration());
    }

    public ProjectDependency copy() {
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(dependencyProject,
                getConfiguration(), projectAccessListener);
        copyTo(copiedProjectDependency);
        return copiedProjectDependency;
    }

    public void beforeResolved() {
        projectAccessListener.beforeResolvingProjectDependency(dependencyProject);
    }

    @Override
    public void resolve(DependencyResolveContext context) {
        boolean transitive = isTransitive() && context.isTransitive();
        if (transitive) {
            for (Dependency dependency : getProjectConfiguration().getAllDependencies()) {
                context.add(dependency);
            }
        }
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
        if (!this.getConfiguration().equals(that.getConfiguration())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getDependencyProject().hashCode() ^ getConfiguration().hashCode();
    }


    @Override
    public String toString() {
        return "DefaultProjectDependency{" + "dependencyProject='" + dependencyProject + '\'' + ", configuration='"
                + getConfiguration() + '\'' + '}';
    }
}
