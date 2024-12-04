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

@NonNullApi
class LifecycleTrackingTestEventReporter<T extends TestEventReporter> implements TestEventReporter {
    protected final T delegate;

    @NonNullApi
    private enum State {
        CREATED, STARTED, COMPLETED, CLOSED;
    }
    private State state = State.CREATED;

    LifecycleTrackingTestEventReporter(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public void started(Instant startTime) {
        if (state != State.CREATED) {
            throw new IllegalStateException("started(...) cannot be called twice");
        }
        state = State.STARTED;
        delegate.started(startTime);
    }

    @Override
    public void output(Instant logTime, TestOutputEvent.Destination destination, String output) {
        requireRunning();
        delegate.output(logTime, destination, output);
    }

    @Override
    public void metadata(Instant logTime, String key, Object value) {
        requireRunning();
        delegate.metadata(logTime, key, value);
    }

    @Override
    public void succeeded(Instant endTime) {
        markCompleted();
        delegate.succeeded(endTime);
    }

    @Override
    public void skipped(Instant endTime) {
        markCompleted();
        delegate.skipped(endTime);
    }

    @Override
    public void failed(Instant endTime, String message, String additionalContent) {
        markCompleted();
        delegate.failed(endTime, message, additionalContent);
    }

    @Override
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        if (state == State.STARTED) {
            throw new IllegalStateException("completed(...) must be called before close() if started(...) was called");
        }
        state = State.CLOSED;

        delegate.close();
    }

    protected boolean isCompleted() {
        return state == State.CLOSED;
    }

    protected void requireRunning() {
        switch (state) {
            case CREATED:
                throw new IllegalStateException("started(...) must be called before any other method");
            case COMPLETED:
                throw new IllegalStateException("completed(...) has already been called");
            case CLOSED:
                throw new IllegalStateException("close() has already been called");
        }
    }

    protected void markCompleted() {
        requireRunning();
        state = State.COMPLETED;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
