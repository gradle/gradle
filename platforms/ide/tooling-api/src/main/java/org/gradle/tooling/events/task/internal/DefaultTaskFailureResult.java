/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.events.task.internal;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.internal.DefaultOperationFailureResult;
import org.gradle.tooling.events.task.TaskFailureResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Implementation of the {@code TaskFailureResult} interface.
 */
public final class DefaultTaskFailureResult extends DefaultOperationFailureResult implements TaskFailureResult {

    private final TaskExecutionDetails taskExecutionDetails;

    public DefaultTaskFailureResult(long startTime, long endTime, List<? extends Failure> failures, TaskExecutionDetails taskExecutionDetails) {
        super(startTime, endTime, failures);
        this.taskExecutionDetails = taskExecutionDetails;
    }

    @Override
    public boolean isIncremental() {
        return taskExecutionDetails.isIncremental();
    }

    @Override
    @Nullable
    public List<String> getExecutionReasons() {
        return taskExecutionDetails.getExecutionReasons();
    }

}
