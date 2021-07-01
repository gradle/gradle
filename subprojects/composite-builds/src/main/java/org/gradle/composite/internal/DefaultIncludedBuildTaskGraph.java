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

import java.util.function.Consumer;
import java.util.function.Supplier;


public class DefaultIncludedBuildTaskGraph implements IncludedBuildTaskGraph {
    private final IncludedBuildControllers includedBuilds;

    public DefaultIncludedBuildTaskGraph(IncludedBuildControllers includedBuilds) {
        this.includedBuilds = includedBuilds;
    }

    @Override
    public <T> T withNestedTaskGraph(Supplier<T> action) {
        return includedBuilds.withNestedTaskGraph(action);
    }

    @Override
    public IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, TaskInternal task) {
        return includedBuilds.getBuildController(targetBuild).locateTask(task);
    }

    @Override
    public IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, String taskPath) {
        return includedBuilds.getBuildController(targetBuild).locateTask(taskPath);
    }

    @Override
    public void populateTaskGraphs() {
        includedBuilds.populateTaskGraphs();
    }

    @Override
    public void startTaskExecution() {
        includedBuilds.startTaskExecution();
    }

    @Override
    public void awaitTaskCompletion(Consumer<? super Throwable> taskFailures) {
        includedBuilds.awaitTaskCompletion(taskFailures);
    }

    @Override
    public void runScheduledTasks(Consumer<? super Throwable> taskFailures) {
        includedBuilds.populateTaskGraphs();
        includedBuilds.startTaskExecution();
        includedBuilds.awaitTaskCompletion(taskFailures);
    }
}
