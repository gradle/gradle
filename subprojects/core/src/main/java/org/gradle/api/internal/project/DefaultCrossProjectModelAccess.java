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
import org.gradle.api.plugins.ExtensionContainer;
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
    public ProjectInternal access(ProjectIdentity referrer, ProjectInternal project) {
        return LifecycleAwareProject.wrap(project, referrer, instantiator, gradleLifecycleActionExecutor);
    }

    @Override
    public ProjectInternal accessFromState(ProjectIdentity referrer, ProjectState projectState) {
        return projectState.fromMutableState(project ->
            // We purposefully leak mutable state here, as we're not in IP so it's safe.
            access(referrer, project)
        );
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
    public HierarchicalDynamicObject parentProjectDynamicInheritedScope(ProjectState referrer) {
        ProjectState parent = referrer.getParent();
        // We purposefully leak mutable state here, as we're not in IP so it's safe.
        return parent != null ? parent.fromMutableState(ProjectInternal::getInheritedScope) : null;
    }

    @Override
    public ExtensionContainer getExtensionContainerForProject(ProjectIdentity referrer, ExtensionContainer extensionContainer) {
        return extensionContainer;
    }
}
