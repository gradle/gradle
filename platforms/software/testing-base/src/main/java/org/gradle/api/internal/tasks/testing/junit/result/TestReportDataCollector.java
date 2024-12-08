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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
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
import java.util.List;
import java.util.Map;

/**
 * Collects the test results into memory and spools the test output to file during execution (to avoid holding it all in memory).
 *
 * <p>
 * It assumes a single top-level suite will be started, which will be used as the "root" of the test results.
 * </p>
 */
public class TestReportDataCollector implements TestListener, TestOutputListener {

    private final Map<Object, Long> assignedIds = new HashMap<>();
    private final ListMultimap<Object, PersistentTestResultTree> childTreesById = ArrayListMultimap.create();
    private final TestOutputStore.Writer outputWriter;
    private PersistentTestResultTree rootTree;
    private long internalIdCounter = 0L;

    public TestReportDataCollector(TestOutputStore.Writer outputWriter) {
        this.outputWriter = outputWriter;
    }

    public PersistentTestResultTree getRootTree() {
        if (rootTree == null) {
            throw new IllegalStateException("No root suite has been finished");
        }
        return rootTree;
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
        startResultCapturing((TestDescriptorInternal) suite);
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        finishResult((TestDescriptorInternal) suite, result);
    }

    @Override
    public void beforeTest(TestDescriptor suite) {
        startResultCapturing((TestDescriptorInternal) suite);
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        finishResult((TestDescriptorInternal) testDescriptor, result);
    }

    private void startResultCapturing(TestDescriptorInternal descriptor) {
        // Assign ID for use in output capturing
        long id = internalIdCounter++;
        assignedIds.put(descriptor.getId(), id);
    }

    private void finishResult(TestDescriptorInternal descriptor, TestResult result) {
        PersistentTestResult.Builder testNodeBuilder = PersistentTestResult.builder()
            .name(descriptor.getName())
            .displayName(descriptor.getDisplayName())
            .legacyProperties(new PersistentTestResult.LegacyProperties(
                descriptor.isClass(),
                descriptor.getClassName(),
                descriptor.getClassDisplayName()
            ))
            .startTime(result.getStartTime())
            .endTime(result.getEndTime())
            .resultType(result.getResultType());

        for (Throwable throwable : result.getExceptions()) {
            testNodeBuilder.addFailure(new PersistentTestFailure(failureMessage(throwable), stackTrace(throwable), exceptionClassName(throwable)));
        }

        long id = assignedIds.get(descriptor.getId());
        List<PersistentTestResultTree> childTrees = this.childTreesById.removeAll(descriptor.getId());

        PersistentTestResultTree tree = new PersistentTestResultTree(id, testNodeBuilder.build(), childTrees);

        if (descriptor.getParent() == null) {
            if (rootTree != null) {
                throw new IllegalStateException("Root suite has already been finished");
            }
            rootTree = tree;
        } else {
            this.childTreesById.put(descriptor.getParent().getId(), tree);
        }
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
