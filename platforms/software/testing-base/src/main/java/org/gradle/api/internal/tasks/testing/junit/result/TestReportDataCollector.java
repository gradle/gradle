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
 */
public class TestReportDataCollector implements TestListener, TestOutputListener {

    public static final String EXECUTION_FAILURE = "failed to execute tests";
    private final Map<String, TestClassResult> results;
    private final TestOutputStore.Writer outputWriter;
    private final Map<TestDescriptor, TestMethodResult> currentTestMethods = new HashMap<TestDescriptor, TestMethodResult>();
    private final ListMultimap<Object, TestOutputEvent> pendingOutputEvents = ArrayListMultimap.create();
    private long internalIdCounter = 1;

    public TestReportDataCollector(Map<String, TestClassResult> results, TestOutputStore.Writer outputWriter) {
        this.results = results;
        this.outputWriter = outputWriter;
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        List<TestOutputEvent> outputEvents = pendingOutputEvents.removeAll(((TestDescriptorInternal) suite).getId());
        if (result.getResultType() == TestResult.ResultType.FAILURE && !result.getExceptions().isEmpty()) {
            //there are some exceptions attached to the suite. Let's make sure they are reported to the user.
            //this may happen for example when suite initialisation fails and no tests are executed
            TestMethodResult methodResult = new TestMethodResult(internalIdCounter++, EXECUTION_FAILURE);
            TestClassResult classResult = new TestClassResult(internalIdCounter++, suite.getName(), result.getStartTime());
            for (Throwable throwable : result.getExceptions()) {
                methodResult.addFailure(failureMessage(throwable), stackTrace(throwable), exceptionClassName(throwable));
            }
            for (TestOutputEvent outputEvent : outputEvents) {
                outputWriter.onOutput(classResult.getId(), methodResult.getId(), outputEvent);
            }
            methodResult.completed(result);
            classResult.add(methodResult);
            results.put(suite.getName(), classResult);
        } else if (result.getResultType() == TestResult.ResultType.SKIPPED) {
            String parentClassName = findEnclosingClassName(suite.getParent());
            String classDisplayName = ((TestDescriptorInternal) suite).getClassDisplayName();
            if (parentClassName != null) {
                TestClassResult classResult = results.get(parentClassName);

                if (classResult == null) {
                    classResult = new TestClassResult(internalIdCounter++, parentClassName, classDisplayName, result.getStartTime());
                    results.put(parentClassName, classResult);
                }

                TestMethodResult methodResult = new TestMethodResult(internalIdCounter++, suite.getName());
                methodResult.completed(result);
                classResult.add(methodResult);
            }
        }
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
        TestDescriptorInternal testDescriptorInternal = (TestDescriptorInternal) testDescriptor;
        TestMethodResult methodResult = new TestMethodResult(internalIdCounter++, testDescriptorInternal.getName(), testDescriptorInternal.getDisplayName());
        currentTestMethods.put(testDescriptor, methodResult);
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        String className = testDescriptor.getClassName();
        String classDisplayName = ((TestDescriptorInternal) testDescriptor).getClassDisplayName();
        TestMethodResult methodResult = currentTestMethods.remove(testDescriptor).completed(result);
        for (Throwable throwable : result.getExceptions()) {
            methodResult.addFailure(failureMessage(throwable), stackTrace(throwable), exceptionClassName(throwable));
        }
        TestClassResult classResult = results.get(className);
        if (classResult == null) {
            classResult = new TestClassResult(internalIdCounter++, className, classDisplayName, result.getStartTime());
            results.put(className, classResult);
        } else if (classResult.getStartTime() == 0) {
            //class results may be created earlier, where we don't yet have access to the start time
            classResult.setStartTime(result.getStartTime());
        }
        classResult.add(methodResult);
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
        String className = findEnclosingClassName(testDescriptor);
        if (className == null) {
            pendingOutputEvents.put(((TestDescriptorInternal) testDescriptor).getId(), outputEvent);
            return;
        }
        TestClassResult classResult = results.get(className);
        if (classResult == null) {
            //it's possible that we receive an output for a suite here
            //in this case we will create the test result for a suite that normally would not be created
            //feels like this scenario should modelled more explicitly
            classResult = new TestClassResult(internalIdCounter++, className, ((TestDescriptorInternal) testDescriptor).getClassDisplayName(), 0);
            results.put(className, classResult);
        }

        TestMethodResult methodResult = currentTestMethods.get(testDescriptor);
        if (methodResult == null) {
            outputWriter.onOutput(classResult.getId(), outputEvent);
        } else {
            outputWriter.onOutput(classResult.getId(), methodResult.getId(), outputEvent);
        }
    }

    private static String findEnclosingClassName(TestDescriptor testDescriptor) {
        if (testDescriptor == null) {
            return null;
        }
        String className = testDescriptor.getClassName();
        if (className != null) {
            return className;
        }
        return findEnclosingClassName(testDescriptor.getParent());
    }
}
