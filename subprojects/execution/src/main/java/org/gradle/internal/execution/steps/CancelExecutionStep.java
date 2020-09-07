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

import org.gradle.api.BuildCancelledException;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.execution.Context;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.Step;

public class CancelExecutionStep<C extends Context> implements Step<C, Result> {
    private final BuildCancellationToken cancellationToken;
    private final Step<? super C, ? extends Result> delegate;

    public CancelExecutionStep(
        BuildCancellationToken cancellationToken,
        Step<? super C, ? extends Result> delegate
    ) {
        this.cancellationToken = cancellationToken;
        this.delegate = delegate;
    }

    @Override
    public Result execute(C context) {
        Thread thread = Thread.currentThread();
        Runnable interrupt = thread::interrupt;
        try {
            cancellationToken.addCallback(interrupt);
            return delegate.execute(context);
        } finally {
            cancellationToken.removeCallback(interrupt);
            if (cancellationToken.isCancellationRequested()) {
                Thread.interrupted();
                throw new BuildCancelledException("Build cancelled while executing " + context.getWork().getDisplayName());
            }
        }
    }
}
