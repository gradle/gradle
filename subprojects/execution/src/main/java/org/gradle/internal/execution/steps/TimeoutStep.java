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

package org.gradle.internal.execution.steps;

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.timeout.Timeout;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.operations.CurrentBuildOperationRef;

import java.time.Duration;
import java.util.Optional;

public class TimeoutStep<C extends Context, R extends Result> implements Step<C, R> {

    private final TimeoutHandler timeoutHandler;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final Step<? super C, ? extends R> delegate;

    public TimeoutStep(
        TimeoutHandler timeoutHandler,
        CurrentBuildOperationRef currentBuildOperationRef,
        Step<? super C, ? extends R> delegate
    ) {
        this.timeoutHandler = timeoutHandler;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        Optional<Duration> timeoutProperty = work.getTimeout();
        if (timeoutProperty.isPresent()) {
            Duration timeout = timeoutProperty.get();
            if (timeout.isNegative()) {
                throw new InvalidUserDataException("Timeout of " + work.getDisplayName() + " must be positive, but was " + timeout.toString().substring(2));
            }
            return executeWithTimeout(work, context, timeout);
        } else {
            return executeWithoutTimeout(work, context);
        }
    }

    private R executeWithTimeout(UnitOfWork work, C context, Duration timeout) {
        Timeout taskTimeout = timeoutHandler.start(Thread.currentThread(), timeout, work, currentBuildOperationRef.get());
        try {
            return executeWithoutTimeout(work, context);
        } finally {
            if (taskTimeout.stop()) {
                //noinspection ResultOfMethodCallIgnored
                Thread.interrupted();
                //noinspection ThrowFromFinallyBlock
                throw new GradleException("Timeout has been exceeded");
            }
        }
    }

    private R executeWithoutTimeout(UnitOfWork work, C context) {
        return delegate.execute(work, context);
    }
}
