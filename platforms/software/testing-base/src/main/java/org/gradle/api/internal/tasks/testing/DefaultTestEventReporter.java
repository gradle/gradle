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
import org.gradle.api.tasks.VerificationException;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import javax.annotation.Nullable;
import java.time.Instant;

@NonNullApi
public class DefaultTestEventReporter implements TestEventReporter {
    @NonNullApi
    protected enum State {
        CREATED, STARTED, COMPLETED, CLOSED
    }

    protected final TestResultProcessor processor;
    protected final @Nullable DefaultGroupTestEventReporter parent;
    protected final TestDescriptorInternal testDescriptor;
    private State state = State.CREATED;

    public DefaultTestEventReporter(
        TestResultProcessor processor, @Nullable DefaultGroupTestEventReporter parent, TestDescriptorInternal testDescriptor
    ) {
        this.processor = processor;
        this.parent = parent;
        this.testDescriptor = testDescriptor;
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

    protected void cleanup() {
    }

    @Override
    public void started(Instant startTime) {
        if (state != State.CREATED) {
            throw new IllegalStateException("started(...) cannot be called twice");
        }
        state = State.STARTED;
        processor.started(testDescriptor, new TestStartEvent(startTime.toEpochMilli(), parent == null ? null : parent.testDescriptor.getId()));
    }

    @Override
    public void output(Instant logTime, TestOutputEvent.Destination destination, String output) {
        requireRunning();
        processor.output(testDescriptor.getId(), new DefaultTestOutputEvent(logTime.toEpochMilli(), destination, output));
    }

    private void markCompleted() {
        requireRunning();
        state = State.COMPLETED;
    }

    @Override
    public void succeeded(Instant endTime) {
        markCompleted();
        processor.completed(testDescriptor.getId(), new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.SUCCESS));
    }

    @Override
    public void skipped(Instant endTime) {
        markCompleted();
        processor.completed(testDescriptor.getId(), new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.SKIPPED));
    }

    @Override
    public void failed(Instant endTime) {
        markCompleted();
        processor.completed(testDescriptor.getId(), new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.FAILURE));
    }

    @Override
    public void failed(Instant endTime, String message) {
        markCompleted();
        processor.failure(testDescriptor.getId(), TestFailure.fromTestFrameworkFailure(new VerificationException(message)));
        processor.completed(testDescriptor.getId(), new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.FAILURE));
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
        if (parent != null) {
            parent.removeChild(this);
        }
        cleanup();
    }
}
