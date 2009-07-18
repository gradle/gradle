/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.api.artifacts.SelfResolvingDependency;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
* @author Hans Dockter
*/
public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependency {
    private Project dependencyProject;

    public DefaultProjectDependency(Project dependencyProject) {
        this(dependencyProject, null);
    }

    public DefaultProjectDependency(Project dependencyProject, String configuration) {
        super(configuration);
        this.dependencyProject = dependencyProject;
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
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(dependencyProject, getConfiguration());
        copyTo(copiedProjectDependency);
        return copiedProjectDependency;
    }

    public Set<File> resolve() {
        Set<File> files = new LinkedHashSet<File>();
        for (SelfResolvingDependency selfResolvingDependency : getProjectConfiguration().getAllDependencies(SelfResolvingDependency.class)) {
            files.addAll(selfResolvingDependency.resolve());
        }
        return files;
    }

    public boolean contentEquals(Dependency dependency) {
        if (this == dependency) return true;
        if (dependency == null || getClass() != dependency.getClass()) return false;

        ProjectDependency that = (ProjectDependency) dependency;
        if (!isCommonContentEquals(that)) return false;

        return dependencyProject.equals(that.getDependencyProject());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProjectDependency that = (ProjectDependency) o;
        if (!this.getDependencyProject().equals(that.getDependencyProject())) return false;
        if (!this.getConfiguration().equals(that.getConfiguration())) return false;
        return true;
    }

    @Override
    public String toString() {
        return "DefaultProjectDependency{" +
                "dependencyProject='" + dependencyProject + '\'' +
                ", configuration" + getConfiguration() + '\'' +
                '}';
    }
}