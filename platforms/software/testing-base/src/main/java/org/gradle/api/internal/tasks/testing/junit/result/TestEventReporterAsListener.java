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

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestEventReporterInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@NullMarked
public class TestEventReporterAsListener implements TestListenerInternal {
    private final GroupTestEventReporter root;
    private final Map<Object, TestEventReporter> reportersById = new HashMap<>();

    public TestEventReporterAsListener(GroupTestEventReporter root) {
        this.root = root;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        GroupTestEventReporter parentReporter;
        if (testDescriptor.getParent() != null) {
            parentReporter = (GroupTestEventReporter) reportersById.get(testDescriptor.getParent().getId());
        } else {
            parentReporter = root;
        }
        TestEventReporter reporter;
        if (testDescriptor.isComposite()) {
            reporter = parentReporter.reportTestGroup(testDescriptor.getName());
        } else {
            reporter = parentReporter.reportTest(testDescriptor.getName(), testDescriptor.getDisplayName());
        }
        reporter.started(Instant.ofEpochMilli(startEvent.getStartTime()));
        reportersById.put(testDescriptor.getId(), reporter);
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        TestEventReporter reporter = reportersById.get(testDescriptor.getId());
        if (reporter == null) {
            throw new IllegalStateException("No reporter found for test descriptor: " + testDescriptor);
        }
        try {
            if (completeEvent.getResultType() == null) {
                throw new IllegalArgumentException("Result type is required");
            }
            switch (completeEvent.getResultType()) {
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
                    throw new IllegalArgumentException("Unknown result type: " + completeEvent.getResultType());
            }
        } finally {
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
}
