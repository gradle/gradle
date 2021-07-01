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
        IncludedBuildController buildController = includedBuilds.getBuildController(targetBuild);
        return new TaskBackedResource(buildController, task);
    }

    @Override
    public IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, String taskPath) {
        IncludedBuildController buildController = includedBuilds.getBuildController(targetBuild);
        return new PathBackedResource(buildController, taskPath);
    }

    @Override
    public void runScheduledTasks(Consumer<? super Throwable> taskFailures) {
        includedBuilds.populateTaskGraphs();
        includedBuilds.startTaskExecution();
        includedBuilds.awaitTaskCompletion(taskFailures);
    }

    private static class PathBackedResource implements IncludedBuildTaskResource {
        private final IncludedBuildController buildController;
        private final String taskPath;

        public PathBackedResource(IncludedBuildController buildController, String taskPath) {
            this.buildController = buildController;
            this.taskPath = taskPath;
        }

        @Override
        public void queueForExecution() {
            buildController.queueForExecution(taskPath);
        }

        @Override
        public TaskInternal getTask() {
            return buildController.getTask(taskPath);
        }

        @Override
        public State getTaskState() {
            return buildController.getTaskState(taskPath);
        }
    }

    private static class TaskBackedResource implements IncludedBuildTaskResource {
        private final IncludedBuildController buildController;
        private final TaskInternal task;

        public TaskBackedResource(IncludedBuildController buildController, TaskInternal task) {
            this.buildController = buildController;
            this.task = task;
        }

        @Override
        public void queueForExecution() {
            buildController.queueForExecution(task.getPath());
        }

        @Override
        public TaskInternal getTask() {
            return task;
        }

        @Override
        public State getTaskState() {
            return buildController.getTaskState(task.getPath());
        }
    }
}
