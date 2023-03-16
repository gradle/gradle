/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedTask;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.TaskIdentity;
import org.gradle.internal.taskgraph.NodeIdentity;

import java.util.List;

/**
 * Default implementation of {@link PlannedTask}.
 */
public class DefaultPlannedTask implements PlannedTask {

    private final TaskIdentity taskIdentity;
    private final List<? extends NodeIdentity> nodeDependencies;
    private final List<TaskIdentity> taskDependencies;
    private final List<TaskIdentity> mustRunAfter;
    private final List<TaskIdentity> shouldRunAfter;
    private final List<TaskIdentity> finalizers;

    public DefaultPlannedTask(
        TaskIdentity taskIdentity,
        List<? extends NodeIdentity> nodeDependencies,
        List<TaskIdentity> taskDependencies,
        List<TaskIdentity> mustRunAfter,
        List<TaskIdentity> shouldRunAfter,
        List<TaskIdentity> finalizers
    ) {
        this.taskIdentity = taskIdentity;
        this.nodeDependencies = nodeDependencies;
        this.taskDependencies = taskDependencies;
        this.mustRunAfter = mustRunAfter;
        this.shouldRunAfter = shouldRunAfter;
        this.finalizers = finalizers;
    }

    @Override
    public TaskIdentity getTask() {
        return taskIdentity;
    }

    @Override
    public List<TaskIdentity> getDependencies() {
        return taskDependencies;
    }

    @Override
    public List<TaskIdentity> getMustRunAfter() {
        return mustRunAfter;
    }

    @Override
    public List<TaskIdentity> getShouldRunAfter() {
        return shouldRunAfter;
    }

    @Override
    public List<TaskIdentity> getFinalizedBy() {
        return finalizers;
    }

    @Override
    public NodeIdentity getNodeIdentity() {
        return getTask();
    }

    @Override
    public List<? extends NodeIdentity> getNodeDependencies() {
        return nodeDependencies;
    }

    @Override
    public String toString() {
        return taskIdentity.toString();
    }
}
