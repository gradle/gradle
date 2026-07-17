/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.work;

import java.util.concurrent.Executor;

/**
 * A {@link SubmissionQueue} that hands tasks straight to an {@link Executor}, without requiring a
 * worker lease. Intended for IO-bound work, whose parallelism should not be capped by the
 * configured maximum number of workers.
 *
 * <p>Instances are stateless and may be shared across any number of queues.
 */
public final class UnconstrainedSubmissionQueue implements SubmissionQueue {
    private final Executor executor;

    public UnconstrainedSubmissionQueue(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void add(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void processWorkUsingCurrentThreadUntilEmpty() {
        // We shouldn't need to use the current thread as the executor work should eventually drain off,
        // and operations here are unconstrained and should not block.
    }
}
