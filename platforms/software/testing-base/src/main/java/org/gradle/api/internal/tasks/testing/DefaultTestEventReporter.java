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
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import javax.annotation.Nullable;
import java.time.Instant;

@NonNullApi
public class DefaultTestEventReporter implements TestEventReporter {

    protected final TestListenerInternal listener;
    protected final TestDescriptorInternal parentId;
    protected final TestDescriptorInternal testDescriptor;

    public DefaultTestEventReporter(TestListenerInternal listener, @Nullable TestDescriptorInternal parentId, TestDescriptorInternal testDescriptor) {
        this.listener = listener;
        this.parentId = parentId;
        this.testDescriptor = testDescriptor;
    }

    @Override
    public void started(Instant startTime) {
        listener.started(testDescriptor, new TestStartEvent(startTime.toEpochMilli(), parentId == null ? null : parentId.getId()));
    }

    @Override
    public void output(Instant logTime, TestOutputEvent.Destination destination, String output) {
        listener.output(testDescriptor, new DefaultTestOutputEvent(logTime.toEpochMilli(), destination, output));
    }

    @Override
    public void succeeded(Instant endTime) {
        listener.completed(testDescriptor, null, new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.SUCCESS));
    }

    @Override
    public void skipped(Instant endTime) {
        listener.completed(testDescriptor, null, new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.SKIPPED));
    }

    @Override
    public void failed(Instant endTime) {
        listener.completed(testDescriptor, null, new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.FAILURE));
    }

    @Override
    public void failed(Instant endTime, String message) {
        // TODO: listener.failure(testDescriptor.getId(), TestFailure.fromTestFrameworkFailure(new VerificationException(message)));
        listener.completed(testDescriptor, null, new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.FAILURE));
    }

    @Override
    public void close() {
        // do nothing
    }
}
