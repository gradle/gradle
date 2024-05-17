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

import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;
import org.gradle.tooling.events.task.TaskSuccessResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Implementation of the {@code TaskSuccessResult} interface.
 */
public class DefaultTaskSuccessResult extends DefaultOperationSuccessResult implements TaskSuccessResult {

    private final boolean upToDate;
    private final boolean fromCache;
    private final TaskExecutionDetails taskExecutionDetails;

    public DefaultTaskSuccessResult(long startTime, long endTime, boolean upToDate, boolean fromCache, TaskExecutionDetails taskExecutionDetails) {
        super(startTime, endTime);
        this.upToDate = upToDate;
        this.fromCache = fromCache;
        this.taskExecutionDetails = taskExecutionDetails;
    }

    @Override
    public boolean isUpToDate() {
        return this.upToDate;
    }

    @Override
    public boolean isFromCache() {
        return fromCache;
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
