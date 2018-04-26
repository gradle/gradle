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

import org.gradle.api.execution.Cancellable;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.initialization.BuildCancellationToken;

public class CancellableTaskExecuter implements TaskExecuter {
    private final TaskExecuter delegate;
    private final BuildCancellationToken buildCancellationToken;

    public CancellableTaskExecuter(TaskExecuter delegate, BuildCancellationToken buildCancellationToken) {
        this.delegate = delegate;
        this.buildCancellationToken = buildCancellationToken;
    }

    @Override
    public void execute(final TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (task instanceof Cancellable) {
            Runnable onCancel = new Runnable() {
                @Override
                public void run() {
                    Cancellable.class.cast(task).onCancel();
                }
            };
            buildCancellationToken.addCallback(onCancel);
            delegate.execute(task, state, context);
            buildCancellationToken.removeCallback(onCancel);
        } else {
            delegate.execute(task, state, context);
        }
    }
}
