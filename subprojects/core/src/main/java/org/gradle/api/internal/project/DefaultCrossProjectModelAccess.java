/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.tasks.TaskDependencyUsageTracker;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.invocation.GradleLifecycleActionExecutor;
import org.jspecify.annotations.Nullable;
import org.gradle.util.Path;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DefaultCrossProjectModelAccess implements CrossProjectModelAccess {

    private final ProjectRegistry projectRegistry;
    private final Instantiator instantiator;
    private final GradleLifecycleActionExecutor gradleLifecycleActionExecutor;

    public DefaultCrossProjectModelAccess(
        ProjectRegistry projectRegistry,
        Instantiator instantiator,
        GradleLifecycleActionExecutor gradleLifecycleActionExecutor
    ) {
        this.projectRegistry = projectRegistry;
        this.instantiator = instantiator;
        this.gradleLifecycleActionExecutor = gradleLifecycleActionExecutor;
    }

    @Override
    public ProjectInternal access(ProjectInternal referrer, ProjectInternal project) {
        return LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor);
    }

    @Override
    @Nullable
    public ProjectInternal findProject(ProjectInternal referrer, Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Project path must be absolute");
        }

        ProjectInternal project = projectRegistry.getProjectInternal(path.asString());
        return project != null ? LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor) : null;
    }

    @Override
    public Map<String, Project> getChildProjects(ProjectInternal referrer, ProjectInternal target) {
        return target.getOwner().getChildProjects().stream().collect(
            Collectors.toMap(
                ProjectState::getName,
                projectState -> LifecycleAwareProject.wrap(projectState.getMutableModel(), referrer, instantiator, gradleLifecycleActionExecutor)
            )
        );
    }

    @Override
    public Set<? extends ProjectInternal> getSubprojects(ProjectInternal referrer, ProjectInternal target) {
        return projectRegistry.getSubProjects(target.getPath()).stream()
            .map(project -> LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public Set<? extends ProjectInternal> getAllprojects(ProjectInternal referrer, ProjectInternal target) {
        return projectRegistry.getAllProjects(target.getPath()).stream()
            .map(project -> LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public GradleInternal gradleInstanceForProject(ProjectInternal referrerProject, GradleInternal gradle) {
        return gradle;
    }

    @Override
    @Nullable
    public TaskDependencyUsageTracker taskDependencyUsageTracker(ProjectInternal referrerProject) {
        return null;
    }

    @Override
    public TaskExecutionGraphInternal taskGraphForProject(ProjectInternal referrerProject, TaskExecutionGraphInternal taskGraph) {
        return taskGraph;
    }

    @Override
    @Nullable
    public DynamicObject parentProjectDynamicInheritedScope(ProjectInternal referrerProject) {
        ProjectInternal parent = referrerProject.getParent();
        return parent != null ? parent.getInheritedScope() : null;
    }

}
