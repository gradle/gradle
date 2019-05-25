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
import org.gradle.internal.execution.Context;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.timeout.Timeout;
import org.gradle.internal.execution.timeout.TimeoutHandler;

import java.time.Duration;
import java.util.Optional;

public class TimeoutStep<C extends Context> implements Step<C, Result> {
    private final TimeoutHandler timeoutHandler;
    private final Step<? super C, ? extends Result> delegate;

    public TimeoutStep(
        TimeoutHandler timeoutHandler,
        Step<? super C, ? extends Result> delegate
    ) {
        this.timeoutHandler = timeoutHandler;
        this.delegate = delegate;
    }

    @Override
    public Result execute(C context) {
        UnitOfWork work = context.getWork();
        Optional<Duration> timeoutProperty = work.getTimeout();
        if (timeoutProperty.isPresent()) {
            Duration timeout = timeoutProperty.get();
            if (timeout.isNegative()) {
                throw new InvalidUserDataException("Timeout of " + work.getDisplayName() + " must be positive, but was " + timeout.toString().substring(2));
            }
            return executeWithTimeout(context, timeout);
        } else {
            return executeWithoutTimeout(context);
        }
    }

    private Result executeWithTimeout(C context, Duration timeout) {
        Timeout taskTimeout = timeoutHandler.start(Thread.currentThread(), timeout);
        try {
            return executeWithoutTimeout(context);
        } finally {
            if (taskTimeout.stop()) {
                //noinspection ResultOfMethodCallIgnored
                Thread.interrupted();
                //noinspection ThrowFromFinallyBlock
                throw new GradleException("Timeout has been exceeded");
            }
        }
    }

    private Result executeWithoutTimeout(C context) {
        return delegate.execute(context);
    }
}
