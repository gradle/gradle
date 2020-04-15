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

import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import java.util.List;

public class DefaultPlannedTask implements CalculateTaskGraphBuildOperationType.PlannedTask {
    private final CalculateTaskGraphBuildOperationType.TaskIdentity taskIdentity;
    private final List<CalculateTaskGraphBuildOperationType.TaskIdentity> dependencies;
    private final List<CalculateTaskGraphBuildOperationType.TaskIdentity> mustRunAfter;
    private final List<CalculateTaskGraphBuildOperationType.TaskIdentity> shouldRunAfter;
    private final List<CalculateTaskGraphBuildOperationType.TaskIdentity> finalizers;

    public DefaultPlannedTask(CalculateTaskGraphBuildOperationType.TaskIdentity taskIdentity,
                              List<CalculateTaskGraphBuildOperationType.TaskIdentity> dependencies,
                              List<CalculateTaskGraphBuildOperationType.TaskIdentity> mustRunAfter,
                              List<CalculateTaskGraphBuildOperationType.TaskIdentity> shouldRunAfter,
                              List<CalculateTaskGraphBuildOperationType.TaskIdentity> finalizers) {
        this.taskIdentity = taskIdentity;
        this.dependencies = dependencies;
        this.mustRunAfter = mustRunAfter;
        this.shouldRunAfter = shouldRunAfter;
        this.finalizers = finalizers;
    }

    @Override
    public CalculateTaskGraphBuildOperationType.TaskIdentity getTask() {
        return taskIdentity;
    }

    @Override
    public List<CalculateTaskGraphBuildOperationType.TaskIdentity> getDependencies() {
        return dependencies;
    }

    @Override
    public List<CalculateTaskGraphBuildOperationType.TaskIdentity> getMustRunAfter() {
        return mustRunAfter;
    }

    @Override
    public List<CalculateTaskGraphBuildOperationType.TaskIdentity> getShouldRunAfter() {
        return shouldRunAfter;
    }

    @Override
    public List<CalculateTaskGraphBuildOperationType.TaskIdentity> getFinalizedBy() {
        return finalizers;
    }
}
