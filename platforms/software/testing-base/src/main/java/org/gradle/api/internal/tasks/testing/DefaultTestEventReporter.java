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

import com.google.common.base.Preconditions;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestFailureDetails;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.model.internal.manage.schema.extract.ScalarTypes;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@NonNullApi
class DefaultTestEventReporter implements TestEventReporter {

    protected final TestListenerInternal listener;
    protected final TestDescriptorInternal testDescriptor;
    protected final TestResultState testResultState;

    private long startTime;

    DefaultTestEventReporter(TestListenerInternal listener, TestDescriptorInternal testDescriptor, TestResultState testResultState) {
        this.listener = listener;
        this.testDescriptor = testDescriptor;
        this.testResultState = testResultState;
    }

    /**
     * Only non-composite test events should be counted in the test result state.
     * @return true if this is a composite test event reporter
     */
    protected boolean isComposite() {
        return false;
    }

    @Override
    public void started(Instant startTime) {
        if (!isComposite()) {
            testResultState.incrementTotalCount();
        }
        this.startTime = startTime.toEpochMilli();
        listener.started(testDescriptor, new TestStartEvent(startTime.toEpochMilli(), testDescriptor.getParent() == null ? null : testDescriptor.getParent().getId()));
    }

    @Override
    public void output(Instant logTime, TestOutputEvent.Destination destination, String output) {
        listener.output(testDescriptor, new DefaultTestOutputEvent(logTime.toEpochMilli(), destination, output));
    }

    @Override
    public void metadata(Instant logTime, Map<String, Object> metadata) {
        Preconditions.checkNotNull(logTime, "logTime can not be null!");
        Preconditions.checkNotNull(metadata, "Metadata can not be null!");
        checkAllowableTypes(metadata);

        listener.metadata(testDescriptor, new DefaultTestMetadataEvent(logTime.toEpochMilli(), metadata));
    }

    private static void checkAllowableTypes(Map<String, Object> metadata) {
        if (metadata.entrySet().stream().anyMatch(entry -> entry.getKey() == null)) {
            throw new IllegalArgumentException("Metadata key cannot be null");
        }
        metadata.entrySet().stream().filter(entry -> !isAllowedType(entry.getValue())).findFirst().ifPresent(entry -> {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(String.format("Metadata '%s' has null value", entry.getKey()));
            } else {
                throw new IllegalArgumentException(String.format("Metadata '%s' has unsupported value type '%s'", entry.getKey(), entry.getValue().getClass().getName()));
            }
        });
    }

    private static boolean isAllowedType(@Nullable Object value) {
        if (value == null) {
            return false;
        }

        if (ScalarTypes.isScalarType(ModelType.of(value.getClass()))) {
            return true;
        }

        if (value instanceof Collection && value instanceof Serializable) {
            return ((Collection<?>) value).stream().allMatch(DefaultTestEventReporter::isAllowedType);
        }

        return false;
    }

    @Override
    public void succeeded(Instant endTime) {
        if (!isComposite()) {
            testResultState.incrementSuccessfulCount();
        }
        listener.completed(testDescriptor, new DefaultTestResult(TestResult.ResultType.SUCCESS, startTime, endTime.toEpochMilli(), testResultState.getTotalCount(), testResultState.getSuccessfulCount(), testResultState.getFailureCount(), Collections.emptyList()), new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.SUCCESS));
    }

    @Override
    public void skipped(Instant endTime) {
        listener.completed(testDescriptor, new DefaultTestResult(TestResult.ResultType.SKIPPED, startTime, endTime.toEpochMilli(), testResultState.getTotalCount(), testResultState.getSuccessfulCount(), testResultState.getFailureCount(), Collections.emptyList()), new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.SKIPPED));
    }

    @Override
    public void failed(Instant endTime, String message, String additionalContent) {
        if (!isComposite()) {
            testResultState.incrementFailureCount();
        }
        TestFailureDetails failureDetails = new DefaultTestFailureDetails(message, Throwable.class.getName(), additionalContent, true, false, null, null, null, null);
        TestFailure testFailure = new DefaultTestFailure(new Throwable(message), failureDetails, Collections.emptyList());
        listener.completed(testDescriptor, new DefaultTestResult(TestResult.ResultType.FAILURE, startTime, endTime.toEpochMilli(), testResultState.getTotalCount(), testResultState.getSuccessfulCount(), testResultState.getFailureCount(), Collections.singletonList(testFailure)), new TestCompleteEvent(endTime.toEpochMilli(), TestResult.ResultType.FAILURE));
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public String toString() {
        return "reporter for " + testDescriptor.getDisplayName();
    }
}
