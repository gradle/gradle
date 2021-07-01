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

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.build.RootBuildState;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

class RootBuildController extends AbstractIncludedBuildController {
    private final RootBuildState rootBuild;

    public RootBuildController(RootBuildState rootBuild) {
        this.rootBuild = rootBuild;
    }

    @Override
    protected void queueForExecution(String taskPath) {
        if (findTaskInRootBuild(taskPath) == null) {
            rootBuild.getBuild().getTaskGraph().addAdditionalEntryTask(taskPath);
        }
    }

    @Override
    public boolean populateTaskGraph() {
        // This is done when queued above
        return false;
    }

    @Override
    public void validateTaskGraph() {
    }

    @Override
    public void startTaskExecution(ExecutorService executorService) {
        // This is started via another path
    }

    @Override
    public void awaitTaskCompletion(Consumer<? super Throwable> taskFailures) {
    }

    @Override
    protected IncludedBuildTaskResource.State getTaskState(String taskPath) {
        TaskInternal task = getTask(taskPath);
        if (task.getState().getFailure() != null) {
            return IncludedBuildTaskResource.State.FAILED;
        } else if (task.getState().getExecuted()) {
            return IncludedBuildTaskResource.State.SUCCESS;
        } else {
            return IncludedBuildTaskResource.State.WAITING;
        }
    }

    @Override
    protected TaskInternal getTask(String taskPath) {
        TaskInternal task = findTaskInRootBuild(taskPath);
        if (task == null) {
            throw new IllegalStateException("Root build task '" + taskPath + "' was never scheduled for execution.");
        }
        return task;
    }

    private TaskInternal findTaskInRootBuild(String taskPath) {
        for (Task task : rootBuild.getBuild().getTaskGraph().getAllTasks()) {
            if (task.getPath().equals(taskPath)) {
                return (TaskInternal) task;
            }
        }
        return null;
    }
}
