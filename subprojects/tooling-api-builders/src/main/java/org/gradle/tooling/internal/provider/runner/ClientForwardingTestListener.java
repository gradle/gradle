/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.provider.events.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Test listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 */
class ClientForwardingTestListener implements TestListenerInternal {

    private final BuildEventConsumer eventConsumer;

    ClientForwardingTestListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        eventConsumer.dispatch(new DefaultTestStartedProgressEvent(startEvent.getStartTime(), adapt(testDescriptor)));
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        eventConsumer.dispatch(new DefaultTestFinishedProgressEvent(completeEvent.getEndTime(), adapt(testDescriptor), adapt(testResult)));
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        // Don't forward
    }

    private static DefaultTestDescriptor adapt(TestDescriptorInternal testDescriptor) {
        return testDescriptor.isComposite() ? toTestDescriptorForSuite(testDescriptor) : toTestDescriptorForTest(testDescriptor);
    }

    private static DefaultTestDescriptor toTestDescriptorForSuite(TestDescriptorInternal suite) {
        Object id = suite.getId();
        String name = suite.getName();
        String displayName = suite.toString();
        String testKind = InternalJvmTestDescriptor.KIND_SUITE;
        String suiteName = suite.getName();
        String className = suite.getClassName();
        String methodName = null;
        Object parentId = suite.getParent() != null ? suite.getParent().getId() : null;
        return new DefaultTestDescriptor(id, name, displayName, testKind, suiteName, className, methodName, parentId);
    }

    private static DefaultTestDescriptor toTestDescriptorForTest(TestDescriptorInternal test) {
        Object id = test.getId();
        String name = test.getName();
        String displayName = test.toString();
        String testKind = InternalJvmTestDescriptor.KIND_ATOMIC;
        String suiteName = null;
        String className = test.getClassName();
        String methodName = test.getName();
        Object parentId = test.getParent() != null ? test.getParent().getId() : null;
        return new DefaultTestDescriptor(id, name, displayName, testKind, suiteName, className, methodName, parentId);
    }

    private static AbstractTestResult adapt(TestResult result) {
        TestResult.ResultType resultType = result.getResultType();
        switch (resultType) {
            case SUCCESS:
                return new DefaultTestSuccessResult(result.getStartTime(), result.getEndTime());
            case SKIPPED:
                return new DefaultTestSkippedResult(result.getStartTime(), result.getEndTime());
            case FAILURE:
                return new DefaultTestFailureResult(result.getStartTime(), result.getEndTime(), convertExceptions(result.getExceptions()));
            default:
                throw new IllegalStateException("Unknown test result type: " + resultType);
        }
    }

    private static List<DefaultFailure> convertExceptions(List<Throwable> exceptions) {
        List<DefaultFailure> failures = new ArrayList<DefaultFailure>(exceptions.size());
        for (Throwable exception : exceptions) {
            failures.add(DefaultFailure.fromThrowable(exception));
        }
        return failures;
    }

}
