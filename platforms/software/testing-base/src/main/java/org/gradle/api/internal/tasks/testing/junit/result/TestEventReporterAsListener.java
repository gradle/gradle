/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.internal.tasks.testing.GroupTestEventReporterInternal;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestEventReporterInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@NullMarked
public class TestEventReporterAsListener implements TestListenerInternal, AutoCloseable {
    private final Function<TestDescriptorInternal, GroupTestEventReporterInternal> rootInitializer;
    private final Map<Object, TestEventReporter> reportersById = new HashMap<>();

    public TestEventReporterAsListener(Function<TestDescriptorInternal, GroupTestEventReporterInternal> rootInitializer) {
        this.rootInitializer = rootInitializer;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        TestEventReporter reporter;
        if (testDescriptor.getParent() != null) {
            GroupTestEventReporterInternal parentReporter =
                (GroupTestEventReporterInternal) reportersById.get(testDescriptor.getParent().getId());

            if (testDescriptor.isComposite()) {
                reporter = parentReporter.reportTestGroupDirectly(testDescriptor);
            } else {
                reporter = parentReporter.reportTestDirectly(testDescriptor);
            }
        } else {
            reporter = rootInitializer.apply(testDescriptor);
        }
        reporter.started(Instant.ofEpochMilli(startEvent.getStartTime()));
        reportersById.put(testDescriptor.getId(), reporter);
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        boolean isRoot = testDescriptor.getParent() == null;
        // Don't remove or close the root reporter, as we can't trigger full failure reporting until after the executor is finished.
        TestEventReporter reporter = isRoot
            ? reportersById.get(testDescriptor.getId())
            : reportersById.remove(testDescriptor.getId());
        if (reporter == null) {
            throw new IllegalStateException("No reporter found for test descriptor: " + testDescriptor);
        }
        try {
            if (testResult.getResultType() == null) {
                throw new IllegalArgumentException("Result type is required");
            }
            switch (testResult.getResultType()) {
                case SUCCESS:
                    reporter.succeeded(Instant.ofEpochMilli(completeEvent.getEndTime()));
                    break;
                case FAILURE:
                    ((TestEventReporterInternal) reporter).failed(Instant.ofEpochMilli(completeEvent.getEndTime()), testResult.getFailures());
                    break;
                case SKIPPED:
                    reporter.skipped(Instant.ofEpochMilli(completeEvent.getEndTime()));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown result type: " + testResult.getResultType());
            }
        } catch (Throwable t) {
            try {
                if (!isRoot) {
                    reporter.close();
                }
            } catch (Exception e) {
                // Suppress exception from close to avoid masking the original exception
                // In most cases, close will throw an exception if the reporter is not in a valid state.
                t.addSuppressed(e);
            }
            throw t;
        }
        // If successful, we close the reporter without suppressing any exceptions.
        if (!isRoot) {
            reporter.close();
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        TestEventReporter reporter = reportersById.get(testDescriptor.getId());
        if (reporter == null) {
            throw new IllegalStateException("No reporter found for test descriptor: " + testDescriptor);
        }
        reporter.output(Instant.ofEpochMilli(event.getLogTime()), event.getDestination(), event.getMessage());
    }

    @Override
    public void metadata(TestDescriptorInternal testDescriptor, TestMetadataEvent event) {
        TestEventReporter reporter = reportersById.get(testDescriptor.getId());
        if (reporter == null) {
            throw new IllegalStateException("No reporter found for test descriptor: " + testDescriptor);
        }
        reporter.metadata(Instant.ofEpochMilli(event.getLogTime()), event.getValues());
    }

    @Override
    public void close() {
        List<Throwable> allExceptions = new ArrayList<>();
        for (TestEventReporter reporter : reportersById.values()) {
            try {
                reporter.close();
            } catch (Throwable t) {
                allExceptions.add(t);
            }
        }
        reportersById.clear();
        if (!allExceptions.isEmpty()) {
            throw new DefaultMultiCauseException("Failed to close some test reporters", allExceptions);
        }
    }
}
