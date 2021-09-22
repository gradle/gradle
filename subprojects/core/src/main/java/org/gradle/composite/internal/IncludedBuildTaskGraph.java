/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.buildtree.BuildTreeWorkGraph;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.function.Function;

/**
 * This should evolve to represent a build tree task graph (that is, should also manage the tasks of the root build).
 */
@ServiceScope(Scopes.BuildTree.class)
public interface IncludedBuildTaskGraph {
    /**
     * Locates a task node in another build's work graph. Does not schedule the task for execution, use {@link IncludedBuildTaskResource#queueForExecution()} to queue the task for execution.
     */
    IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, TaskInternal task);

    /**
     * Locates a task node in another build's work graph. Does not schedule the task for execution, use {@link IncludedBuildTaskResource#queueForExecution()} to queue the task for execution.
     */
    IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, String taskPath);

    /**
     * Runs the given action against a new, empty task graph. This allows tasks to be run while calculating the task graph of the build tree, for example to run buildSrc tasks or
     * to build local plugins.
     *
     * It would be better if this method were to act on a "build tree task graph" object that can be populated, executed and then discarded. However, quite a few consumers
     * of this type and {@link org.gradle.execution.taskgraph.TaskExecutionGraphInternal} assume that there is a single reusable instance of these types available as services.
     */
    <T> T withNewTaskGraph(Function<BuildTreeWorkGraph, T> action);
}
