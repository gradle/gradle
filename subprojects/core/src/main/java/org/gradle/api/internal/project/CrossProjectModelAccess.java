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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Mediates access to other projects, across project boundaries, within a single build.
 */
@ServiceScope(Scope.Build.class)
public interface CrossProjectModelAccess {

    /**
     * Locates the project with the given path.
     *
     * @param referrer The project from which the return value will be used.
     * @param path An absolute path to the requested project, relative to the current build.
     *
     * @throws IllegalArgumentException If {@code path} is not absolute.
     */
    @Nullable ProjectInternal findProject(ProjectIdentity referrer, Path path);

    /**
     * Wrap the project identified by {@code target} to ensure mutable state access is correctly handled
     * across project boundaries, according to the current cross-project model access policy.
     *
     * @param referrer The project from which the return value will be used.
     * @param target The identity of the project to access.
     */
    ProjectInternal access(ProjectIdentity referrer, ProjectIdentity target);

    /**
     * @param referrer The project from which the return value will be used.
     * @param target The project to get the children of.
     */
    Map<String, Project> getChildProjects(ProjectIdentity referrer, ProjectIdentity target);

    /**
     * @param referrer The project from which the return value will be used.
     * @param target The project to get the subprojects of.
     */
    Set<? extends ProjectInternal> getSubprojects(ProjectIdentity referrer, ProjectIdentity target);

    /**
     * @param referrer The project from which the return value will be used.
     * @param target The project to get all projects of.
     */
    Set<? extends ProjectInternal> getAllprojects(ProjectIdentity referrer, ProjectIdentity target);

    /**
     * Given the request from the referrer to access the specified Gradle instance, returns
     * an instance that behaves correctly regarding cross project model access.
     *
     * @param referrer The project that is going to use the Gradle instance.
     * @param gradle The Gradle instance that the project has direct access to.
     * @return A Gradle instance that implements correct cross-project model access.
     */
    GradleInternal gradleInstanceForProject(ProjectIdentity referrer, GradleInternal gradle);

    /**
     * Provides an implementation of a tracker that handles the usages of TaskDependency API in the context
     * of the current project. The tracker checks that the usages for possible violation of cross-project model access restriction.
     *
     * @param referrer The project providing the context.
     */
    @Nullable
    TaskDependencyUsageTracker taskDependencyUsageTracker(ProjectIdentity referrer);

    /**
     * Provides an implementation of {@code TaskExecutionGraph} such that it handles access to the
     * tasks in the other projects according to the current cross-project model access policy.
     *
     * @param referrer The project that views the task graph.
     * @return A task graph instance that implements correct cross-project model access.
     */
    TaskExecutionGraphInternal taskGraphForProject(ProjectIdentity referrer, TaskExecutionGraphInternal taskGraph);

    /**
     * Produces a {@code DynamicObject} for the inherited scope from the parent project of the specified project, behaving correctly
     * regarding cross-project model access.
     *
     * @param referrer The project that needs to get an inherited scope dynamic object from its parent.
     * @return A {@code DynamicObject} for the {@code referrer}'s parent project, or null if there is no parent project.
     * The returned object handles cross-project model access according to the current policy.
     */
    @Nullable
    HierarchicalDynamicObject parentProjectDynamicInheritedScope(ProjectIdentity referrer);
}
