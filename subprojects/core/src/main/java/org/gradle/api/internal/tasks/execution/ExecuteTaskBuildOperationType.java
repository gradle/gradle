/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * The overall execution of a task, including:
 *
 * - User lifecycle callbacks (TaskExecutionListener.beforeExecute and TaskExecutionListener.afterExecute)
 * - Gradle execution mechanics (e.g. up-to-date and build cache “checks”, property validation, build cache output store, etc.)
 *
 * That is, this operation does not represent just the execution of task actions.
 *
 * This operation can fail _and_ yield a result.
 * If the operation gets as far as invoking the task executer
 * (i.e. beforeTask callbacks did not fail), then a result is expected.
 * If the task execution fails, or if afterTask callbacks fail, an operation failure is expected _in addition_.
 *
 * Important note: the scan listener currently expects to receive the operation started notification on the
 * _same thread_ that is about to execute the task. If this changes (e.g. notifications are dispatched async),
 * additional changes need to be made to convey the thread/worker that is going to be used to execute the task.
 */
@UsedByScanPlugin
public final class ExecuteTaskBuildOperationType implements BuildOperationType<ExecuteTaskBuildOperationType.Details, ExecuteTaskBuildOperationType.Result> {

    @SuppressWarnings("deprecation")
    public interface Details extends org.gradle.internal.execution.ExecuteTaskBuildOperationType.Details {

    }

    @SuppressWarnings("deprecation")
    public interface Result extends org.gradle.internal.execution.ExecuteTaskBuildOperationType.Result {

    }

    private ExecuteTaskBuildOperationType() {

    }

}
