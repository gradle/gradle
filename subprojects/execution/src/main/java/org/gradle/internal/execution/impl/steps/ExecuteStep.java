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

package org.gradle.internal.execution.impl.steps;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.GradleException;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.execution.ExecutionException;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;

public class ExecuteStep implements Step<Context> {

    private final BuildCancellationToken cancellationToken;
    private final OutputChangeListener outputChangeListener;

    public ExecuteStep(
        BuildCancellationToken cancellationToken,
        OutputChangeListener outputChangeListener
    ) {
        this.cancellationToken = cancellationToken;
        this.outputChangeListener = outputChangeListener;
    }

    @Override
    public ExecutionResult execute(Context context) {
        UnitOfWork work = context.getWork();
        boolean didWork = true;
        GradleException failure;
        try {
            outputChangeListener.beforeOutputChange();

            didWork = work.execute();
            if (cancellationToken.isCancellationRequested()) {
                failure = new BuildCancelledException("Build cancelled during executing " + work.getDisplayName());
            } else {
                failure = null;
            }
        } catch (Throwable t) {
            // TODO Should we catch Exception here?
            failure = new ExecutionException(work, t);
        }
        if (failure != null) {
            return ExecutionResult.failure(failure);
        } else {
            return didWork
                ? ExecutionResult.success(ExecutionOutcome.EXECUTED)
                : ExecutionResult.success(ExecutionOutcome.UP_TO_DATE);
        }
    }
}
