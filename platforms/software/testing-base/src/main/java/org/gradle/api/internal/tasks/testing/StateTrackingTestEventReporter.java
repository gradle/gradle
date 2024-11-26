/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@NonNullApi
class StateTrackingTestEventReporter<T extends TestEventReporter> implements TestEventReporter {
    private final AtomicLong totalCount;
    private final AtomicLong successfulCount;
    private final AtomicLong failureCount;
    protected final T delegate;

    StateTrackingTestEventReporter(AtomicLong totalCount, AtomicLong successfulCount, AtomicLong failureCount, T delegate) {
        this.totalCount = totalCount;
        this.successfulCount = successfulCount;
        this.failureCount = failureCount;
        this.delegate = delegate;
    }

    @Override
    public void started(Instant startTime) {
        totalCount.incrementAndGet();
        delegate.started(startTime);
    }

    @Override
    public void output(Instant logTime, TestOutputEvent.Destination destination, String output) {
        delegate.output(logTime, destination, output);
    }

    @Override
    public void succeeded(Instant endTime) {
        successfulCount.incrementAndGet();
        delegate.succeeded(endTime);
    }

    @Override
    public void skipped(Instant endTime) {
        delegate.skipped(endTime);
    }

    @Override
    public void failed(Instant endTime, String message, String additionalContent) {
        failureCount.incrementAndGet();
        delegate.failed(endTime, message);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
