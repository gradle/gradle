/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestOutputListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.serialize.PlaceholderExceptionSupport;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects the test results into memory and spools the test output to file during execution (to avoid holding it all in memory).
 */
public class TestReportDataCollector implements TestListener, TestOutputListener {

    /**
     * Object used in {@link #assignedIds} to represent the root node, to avoid using {@code null} as a key.
     */
    private static final Object ROOT_ID = new Object();
    private final Map<Long, PersistentTestResult.Builder> inProgressResults = new HashMap<>();
    private final Map<Object, Long> assignedIds = new HashMap<>();
    private final TestOutputStore.Writer outputWriter;
    private long internalIdCounter = 0L;

    public TestReportDataCollector(PersistentTestResult.Builder rootResult, TestOutputStore.Writer outputWriter) {
        long id = internalIdCounter++;
        this.inProgressResults.put(id, rootResult.id(id));
        this.assignedIds.put(ROOT_ID, id);
        this.outputWriter = outputWriter;
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
        startResultCapturing(suite);
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        finishResult((TestDescriptorInternal) suite, result);
    }

    @Override
    public void beforeTest(TestDescriptor suite) {
        startResultCapturing(suite);
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        finishResult((TestDescriptorInternal) testDescriptor, result);
    }

    private void startResultCapturing(TestDescriptor suite) {
        long id = internalIdCounter++;
        assignedIds.put(((TestDescriptorInternal) suite).getId(), id);
        PersistentTestResult.Builder testNodeBuilder = PersistentTestResult.builder()
            .id(id)
            .name(suite.getName())
            .displayName(suite.getDisplayName());
        inProgressResults.put(id, testNodeBuilder);
    }

    private void finishResult(TestDescriptorInternal testDescriptor, TestResult result) {
        long id = assignedIds.get(testDescriptor.getId());
        PersistentTestResult.Builder testNodeBuilder = inProgressResults.remove(id)
            .startTime(result.getStartTime())
            .endTime(result.getEndTime())
            .resultType(result.getResultType());

        for (Throwable throwable : result.getExceptions()) {
            testNodeBuilder.addFailure(new PersistentTestFailure(failureMessage(throwable), stackTrace(throwable), exceptionClassName(throwable)));
        }

        long parentId;
        if (testDescriptor.getParent() == null) {
            parentId = assignedIds.get(ROOT_ID);
        } else {
            parentId = assignedIds.get(testDescriptor.getParent().getId());
        }
        inProgressResults.get(parentId).addChild(testNodeBuilder.build());
    }

    private String failureMessage(Throwable throwable) {
        try {
            return throwable.toString();
        } catch (Throwable t) {
            String exceptionClassName = exceptionClassName(throwable);
            return String.format("Could not determine failure message for exception of type %s: %s",
                    exceptionClassName, t);
        }
    }

    private String exceptionClassName(Throwable throwable) {
        return throwable instanceof PlaceholderExceptionSupport ? ((PlaceholderExceptionSupport) throwable).getExceptionClassName() : throwable.getClass().getName();
    }

    private String stackTrace(Throwable throwable) {
        try {
            return getStacktrace(throwable);
        } catch (Throwable t) {
            return getStacktrace(t);
        }
    }

    private String getStacktrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        throwable.printStackTrace(writer);
        writer.close();
        return stringWriter.toString();
    }

    @Override
    public void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {
        long testId = assignedIds.get(((TestDescriptorInternal) testDescriptor).getId());
        outputWriter.onOutput(testId, outputEvent);
    }
}
