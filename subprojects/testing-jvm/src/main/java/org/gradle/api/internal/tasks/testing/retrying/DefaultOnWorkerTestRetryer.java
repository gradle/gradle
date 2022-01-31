/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.retrying;

import org.gradle.api.internal.tasks.testing.TestResultProcessor;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultOnWorkerTestRetryer implements OnWorkerTestRetryer {

    private final AtomicLong failureCount = new AtomicLong();

    @Override
    public TestResultProcessor createAttachedResultProcessor(TestResultProcessor other) {
        return new UntilFailureTestResultProcessor(failureCount, other);
    }

    @Override
    public void runTests(Runnable runnable) {
        long executions = Math.max(untilFailureRetryCount, 1);
        while (shouldRetry(executions--)) {
            processor.runTests();
        }
    }

    private boolean shouldRetry(long executions) {
        return executions > 0 && !hasAnyTestFailed.get();
    }
}
