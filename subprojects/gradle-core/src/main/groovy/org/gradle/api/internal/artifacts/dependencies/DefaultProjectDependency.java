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
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.artifacts.*;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultProjectDependency extends AbstractModuleDependency implements ProjectDependency {
    private Project dependencyProject;
    private final ProjectDependenciesBuildInstruction instruction;
    private Set<File> transitiveCache = null;
    private Set<File> nonTransitiveCache = null;

    public DefaultProjectDependency(Project dependencyProject, ProjectDependenciesBuildInstruction instruction) {
        this(dependencyProject, null, instruction);
    }

    public DefaultProjectDependency(Project dependencyProject, String configuration,
                                    ProjectDependenciesBuildInstruction instruction) {
        super(configuration);
        this.dependencyProject = dependencyProject;
        this.instruction = instruction;
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
                getConfiguration(), instruction);
        copyTo(copiedProjectDependency);
        return copiedProjectDependency;
    }

    public Set<File> resolve() {
        return resolve(true);
    }

    public Set<File> resolve(boolean transitive) {
        if ((transitiveCache == null && transitive) || (nonTransitiveCache == null && !transitive)) {
            Set<File> files = new LinkedHashSet<File>();
            if (!transitive || !isTransitive()) {
                files.addAll(getProjectConfiguration().files(new Spec<Dependency>() {
                    public boolean isSatisfiedBy(Dependency dependency) {
                        return (dependency instanceof SelfResolvingDependency) &&
                                !(dependency instanceof ProjectDependency);
                    }
                }));
            } else {
                for (SelfResolvingDependency selfResolvingDependency : getProjectConfiguration().getAllDependencies(
                        SelfResolvingDependency.class)) {
                    files.addAll(selfResolvingDependency.resolve());
                }
            }
            if (transitive) {
                transitiveCache = files;
            } else {
                nonTransitiveCache = files;
            }
        }
        return transitive ? transitiveCache : nonTransitiveCache;
    }

    public TaskDependency getBuildDependencies() {
        if (!instruction.isRebuild()) {
            return new DefaultTaskDependency();
        }
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                DefaultTaskDependency taskDependency = new DefaultTaskDependency();
                Configuration configuration = getProjectConfiguration();
                taskDependency.add(configuration);
                taskDependency.add(configuration.getBuildArtifacts());
                for (String taskName : instruction.getTaskNames()) {
                    taskDependency.add(dependencyProject.getTasks().getByName(taskName));
                }
                return taskDependency.getDependencies(task);
            }
        };
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
        if (!this.instruction.equals(that.instruction)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "DefaultProjectDependency{" + "dependencyProject='" + dependencyProject + '\'' + ", configuration"
                + getConfiguration() + '\'' + '}';
    }
}