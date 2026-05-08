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
import org.gradle.internal.metaobject.HierarchicalDynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.invocation.GradleLifecycleActionExecutor;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @implNote This implementation purposefully leaks mutable state across project boundaries.
 * That is safe because it is only used outside of Isolated Projects mode, where unrestricted
 * cross-project access is allowed.
 */
public class DefaultCrossProjectModelAccess implements CrossProjectModelAccess {

    private final ProjectStateLookup projectStateLookup;
    private final ProjectRegistry projectRegistry;
    private final Instantiator instantiator;
    private final GradleLifecycleActionExecutor gradleLifecycleActionExecutor;

    public DefaultCrossProjectModelAccess(
        ProjectStateLookup projectStateLookup,
        ProjectRegistry projectRegistry,
        Instantiator instantiator,
        GradleLifecycleActionExecutor gradleLifecycleActionExecutor
    ) {
        this.projectStateLookup = projectStateLookup;
        this.projectRegistry = projectRegistry;
        this.instantiator = instantiator;
        this.gradleLifecycleActionExecutor = gradleLifecycleActionExecutor;
    }

    @Override
    public ProjectInternal access(ProjectIdentity referrer, ProjectIdentity target) {
        ProjectState projectState = projectStateLookup.stateFor(target.getBuildTreePath());
        return LifecycleAwareProject.wrap(projectState.getMutableModel(), referrer, instantiator, gradleLifecycleActionExecutor);
    }

    @Override
    @Nullable
    public ProjectInternal findProject(ProjectIdentity referrer, Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Project path must be absolute");
        }

        ProjectInternal project = projectRegistry.getProjectInternal(path.asString());
        return project != null ? LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor) : null;
    }

    @Override
    public Map<String, Project> getChildProjects(ProjectIdentity referrer, ProjectIdentity target) {
        ProjectState targetState = projectStateLookup.stateFor(target.getBuildTreePath());
        return targetState.getChildProjects().stream().collect(
            Collectors.toMap(
                ProjectState::getName,
                projectState -> LifecycleAwareProject.wrap(projectState.getMutableModel(), referrer, instantiator, gradleLifecycleActionExecutor)
            )
        );
    }

    @Override
    public Set<? extends ProjectInternal> getSubprojects(ProjectIdentity referrer, ProjectIdentity target) {
        return projectRegistry.getSubProjects(target.getProjectPath().asString()).stream()
            .map(project -> LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public Set<? extends ProjectInternal> getAllprojects(ProjectIdentity referrer, ProjectIdentity target) {
        return projectRegistry.getAllProjects(target.getProjectPath().asString()).stream()
            .map(project -> LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public GradleInternal gradleInstanceForProject(ProjectIdentity referrer, GradleInternal gradle) {
        return gradle;
    }

    @Override
    @Nullable
    public TaskDependencyUsageTracker taskDependencyUsageTracker(ProjectIdentity referrer) {
        return null;
    }

    @Override
    public TaskExecutionGraphInternal taskGraphForProject(ProjectIdentity referrer, TaskExecutionGraphInternal taskGraph) {
        return taskGraph;
    }

    @Override
    @Nullable
    public HierarchicalDynamicObject parentProjectDynamicInheritedScope(ProjectIdentity referrer) {
        ProjectState parent = projectStateLookup.stateFor(referrer.getBuildTreePath()).getParent();
        return parent != null ? parent.fromMutableState(ProjectInternal::getInheritedScope) : null;
    }
}
