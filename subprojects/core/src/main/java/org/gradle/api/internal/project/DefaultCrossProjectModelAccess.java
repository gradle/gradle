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
import org.gradle.internal.build.BuildProjectRegistry;
import org.gradle.internal.metaobject.HierarchicalDynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.invocation.GradleLifecycleActionExecutor;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DefaultCrossProjectModelAccess implements CrossProjectModelAccess {

    private final BuildProjectRegistry projectRegistry;
    private final Instantiator instantiator;
    private final GradleLifecycleActionExecutor gradleLifecycleActionExecutor;

    public DefaultCrossProjectModelAccess(
        BuildProjectRegistry projectRegistry,
        Instantiator instantiator,
        GradleLifecycleActionExecutor gradleLifecycleActionExecutor
    ) {
        this.projectRegistry = projectRegistry;
        this.instantiator = instantiator;
        this.gradleLifecycleActionExecutor = gradleLifecycleActionExecutor;
    }

    @Override
    public ProjectInternal access(ProjectIdentity referrer, ProjectInternal project) {
        return LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor);
    }

    @Override
    public ProjectInternal accessFromState(ProjectIdentity referrer, ProjectState projectState) {
        // This is safe without checking the lock as the mutable model is immediately wrapped in a thread-safe wrapper.
        return access(referrer, projectState.getMutableModel());
    }

    @Override
    @Nullable
    public ProjectInternal findProject(ProjectIdentity referrer, Path path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Project path must be absolute");
        }

        ProjectState projectState = projectRegistry.findProject(path);
        return projectState != null ? LifecycleAwareProject.wrap(projectState.getMutableModel(), referrer, instantiator, gradleLifecycleActionExecutor) : null;
    }

    @Override
    public Map<String, Project> getChildProjects(ProjectIdentity referrer, ProjectState target) {
        return target.getChildProjects().stream().collect(
            Collectors.toMap(
                ProjectState::getName,
                projectState -> LifecycleAwareProject.wrap(projectState.getMutableModel(), referrer, instantiator, gradleLifecycleActionExecutor)
            )
        );
    }

    @Override
    public Set<? extends ProjectInternal> getSubprojects(ProjectIdentity referrer, ProjectIdentity target) {
        ProjectState targetProjectState = projectRegistry.getProject(target.getProjectPath());
        return subprojectsFor(referrer, targetProjectState);
    }

    @Override
    public Set<? extends ProjectInternal> getAllprojects(ProjectIdentity referrer, ProjectIdentity target) {
        ProjectState targetProjectState = projectRegistry.getProject(target.getProjectPath());
        Set<ProjectInternal> allProjects = subprojectsFor(referrer, targetProjectState);
        allProjects.add(targetProjectState.getMutableModel());
        return allProjects;
    }

    private Set<ProjectInternal> subprojectsFor(ProjectIdentity referrer, ProjectState targetProjectState) {
        TreeSet<ProjectInternal> subprojects = new TreeSet<>();
        for (ProjectState subproject : ProjectOrderingUtil.orderedSubprojectsOf(targetProjectState)) {
            subprojects.add(LifecycleAwareProject.wrap(subproject.getMutableModel(), referrer, instantiator, gradleLifecycleActionExecutor));
        }
        return subprojects;
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
    public HierarchicalDynamicObject parentProjectDynamicInheritedScope(ProjectState referrer) {
        ProjectState parent = referrer.getParent();
        // We purposefully leak mutable state here, as we're not in IP so it's safe.
        return parent != null ? parent.fromMutableState(ProjectInternal::getInheritedScope) : null;
    }
}
