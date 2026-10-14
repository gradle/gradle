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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestDiscoveryEvent;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.WorkItemProgressEvent;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

/**
 * Reports each test as a unit of build progress. Discovered tests are reported up front, tests that start without
 * having been discovered are reported when they start, and units still outstanding are settled by {@link #settle()}.
 */
public class ProgressReportingTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor delegate;
    private final OutputEventListener outputEventListener;
    private final Set<Object> runningTests = new HashSet<>();
    private final Set<Object> failedTests = new HashSet<>();
    private int discoveredNotStarted;
    private int outstanding;

    @Inject
    public ProgressReportingTestResultProcessor(TestResultProcessor delegate, OutputEventListener outputEventListener) {
        this.delegate = delegate;
        this.outputEventListener = outputEventListener;
    }

    @Override
    public void discovered(TestDiscoveryEvent event) {
        synchronized (this) {
            discoveredNotStarted += event.getTestCount();
            submit(event.getTestCount());
        }
        delegate.discovered(event);
    }

    @Override
    public void started(TestDescriptorInternal test, TestStartEvent event) {
        if (!test.isComposite()) {
            synchronized (this) {
                runningTests.add(test.getId());
                if (discoveredNotStarted > 0) {
                    discoveredNotStarted--;
                } else {
                    submit(1);
                }
            }
        }
        delegate.started(test, event);
    }

    @Override
    public void failure(Object testId, TestFailure result) {
        if (!result.getDetails().isAssumptionFailure()) {
            synchronized (this) {
                if (runningTests.contains(testId)) {
                    failedTests.add(testId);
                }
            }
        }
        delegate.failure(testId, result);
    }

    @Override
    public void completed(Object testId, TestCompleteEvent event) {
        synchronized (this) {
            if (runningTests.remove(testId)) {
                boolean failed = failedTests.remove(testId);
                complete(failed ? WorkItemProgressEvent.Kind.FAILED : WorkItemProgressEvent.Kind.SUCCEEDED, 1);
            }
        }
        delegate.completed(testId, event);
    }

    @Override
    public void output(Object testId, TestOutputEvent event) {
        delegate.output(testId, event);
    }

    @Override
    public void published(Object testId, TestMetadataEvent keyValueEvent) {
        delegate.published(testId, keyValueEvent);
    }

    public synchronized void settle() {
        complete(WorkItemProgressEvent.Kind.SUCCEEDED, outstanding);
        runningTests.clear();
        failedTests.clear();
        discoveredNotStarted = 0;
    }

    private void submit(int count) {
        if (count > 0) {
            outstanding += count;
            outputEventListener.onOutput(new WorkItemProgressEvent(WorkItemProgressEvent.Kind.SUBMITTED, count));
        }
    }

    private void complete(WorkItemProgressEvent.Kind kind, int count) {
        if (count > 0) {
            outstanding -= count;
            outputEventListener.onOutput(new WorkItemProgressEvent(kind, count));
        }
    }
}
