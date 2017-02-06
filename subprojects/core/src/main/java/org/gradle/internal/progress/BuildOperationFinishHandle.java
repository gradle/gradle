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

package org.gradle.internal.progress;

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.time.TimeProvider;

public class BuildOperationFinishHandle<T> {

    private final ThreadLocal<DefaultBuildOperationExecutor.OperationDetails> currentOperationThreadLocal;
    private final DefaultBuildOperationExecutor.OperationDetails beforeOperation;
    private final DefaultBuildOperationExecutor.OperationDetails currentOperation;
    private final BuildOperationInternal operation;
    private final TimeProvider timeProvider;
    private final BuildOperationListener listener;
    private final ProgressLogger progressLogger;
    private final long startTime;
    private final T item;
    private DefaultBuildOperationExecutor.BuildOperationContextImpl context;

    public BuildOperationFinishHandle(ThreadLocal<DefaultBuildOperationExecutor.OperationDetails> currentOperationThreadLocal, DefaultBuildOperationExecutor.OperationDetails beforeOperationDetails, DefaultBuildOperationExecutor.OperationDetails currentOperationDetails,  BuildOperationInternal operation, DefaultBuildOperationExecutor.BuildOperationContextImpl context, TimeProvider timeProvider, BuildOperationListener listener, ProgressLogger progressLogger, long startTime, T item) {
        this.currentOperationThreadLocal = currentOperationThreadLocal;
        this.beforeOperation = beforeOperationDetails;
        this.currentOperation = currentOperationDetails;
        this.operation = operation;
        this.context = context;
        this.timeProvider = timeProvider;
        this.listener = listener;
        this.progressLogger = progressLogger;
        this.startTime = startTime;
        this.item = item;
    }

    public void finish(Action<BuildOperationContext> action) {
        Throwable failure = null;
        try {
            action.execute(context);
        } catch (Throwable t) {
            context.failed(t);
            failure = t;
        }
        if (progressLogger != null) {
            progressLogger.completed();
        }
        long endTime = timeProvider.getCurrentTime();
        listener.finished(operation, new OperationResult(startTime, endTime, context.failure));
        this.currentOperationThreadLocal.set(beforeOperation);
        currentOperation.running.set(false);

        if (failure != null) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }

    public T get() {
        return item;
    }
}
