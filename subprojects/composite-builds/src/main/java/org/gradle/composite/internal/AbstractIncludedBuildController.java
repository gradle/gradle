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

package org.gradle.composite.internal;

import org.gradle.api.internal.TaskInternal;

abstract class AbstractIncludedBuildController implements IncludedBuildController {
    @Override
    public IncludedBuildTaskResource locateTask(TaskInternal task) {
        return new TaskBackedResource(this, task);
    }

    @Override
    public IncludedBuildTaskResource locateTask(String taskPath) {
        return new PathBackedResource(this, taskPath);
    }

    protected abstract IncludedBuildTaskResource.State getTaskState(String taskPath);

    protected abstract TaskInternal getTask(String taskPath);

    /**
     * Queues a task for execution, but does not schedule it. Should call {@link #populateTaskGraph()} to actually schedule
     * the queued tasks for execution.
     */
    protected abstract void queueForExecution(String taskPath);

    private static class PathBackedResource implements IncludedBuildTaskResource {
        private final AbstractIncludedBuildController buildController;
        private final String taskPath;

        public PathBackedResource(AbstractIncludedBuildController buildController, String taskPath) {
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
        private final AbstractIncludedBuildController buildController;
        private final TaskInternal task;

        public TaskBackedResource(AbstractIncludedBuildController buildController, TaskInternal task) {
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
