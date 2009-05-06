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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.Configuration;
import org.gradle.util.WrapUtil;

/**
* @author Hans Dockter
*/
public class DefaultProjectDependency extends AbstractDependency implements ProjectDependency {
    private Project dependencyProject;

    private boolean transitive = true;

    public DefaultProjectDependency(Project dependencyProject) {
        this.dependencyProject = dependencyProject;
    }

    public Project getDependencyProject() {
        return dependencyProject;
    }
    
    public boolean isTransitive() {
        return transitive;
    }

    public DefaultProjectDependency setTransitive(boolean transitive) {
        this.transitive = transitive;
        return this;
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

    public Configuration getConfiguration() {
        return dependencyProject.getConfigurations().getByName(getDependencyConfiguration());
    }

    public Dependency copy() {
        DefaultProjectDependency copiedProjectDependency = new DefaultProjectDependency(dependencyProject);
        Dependencies.copy(this, copiedProjectDependency);
        return copiedProjectDependency;
    }

    public boolean contentEquals(Dependency dependency) {
        if (this == dependency) return true;
        if (dependency == null || getClass() != dependency.getClass()) return false;

        ProjectDependency that = (ProjectDependency) dependency;
        if (!Dependencies.isCommonContentEquals(this, that)) return false;

        return dependencyProject.equals(that.getDependencyProject());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProjectDependency that = (ProjectDependency) o;

        return Dependencies.isKeyEquals(this, that);
    }

    @Override
    public String toString() {
        return "DefaultProjectDependency{" +
                "dependencyProject='" + dependencyProject + '\'' +
                ", dependencyConfiguration" + getDependencyConfiguration() + '\'' +
                '}';
    }
}