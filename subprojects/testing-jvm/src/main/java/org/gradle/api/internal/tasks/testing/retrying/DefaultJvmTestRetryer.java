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

import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultJvmTestRetryer implements JvmTestRetryer {

    private final AtomicBoolean hasAnyTestFail;
    private final long maxRetryCount;
    private final boolean stopAfterFailure;

    public DefaultJvmTestRetryer(JvmRetrySpec retrySpec) {
        this.maxRetryCount = Math.max(retrySpec.getMaxRetries(), 1);
        this.stopAfterFailure = retrySpec.isStopAfterFailure();
        this.hasAnyTestFail = new AtomicBoolean();
    }

    @Override
    public TestResultProcessor decorateTestResultProcessor(TestResultProcessor other) {
        return new JvmTestRetryerResultProcessor(hasAnyTestFail, other);
    }

    @Override
    public void retryUntilFailure(Runnable runnable) {
        long executions = maxRetryCount;
        while (shouldRetry(executions--)) {
            runnable.run();
        }
    }

    private boolean shouldRetry(long executions) {
        return executions > 0 && !hasAnyTestFailed();
    }

    private boolean hasAnyTestFailed() {
        return stopAfterFailure && hasAnyTestFail.get();
    }
}
