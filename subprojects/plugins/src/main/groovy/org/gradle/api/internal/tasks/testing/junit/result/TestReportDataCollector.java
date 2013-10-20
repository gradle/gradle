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

import org.gradle.api.tasks.testing.*;
import org.gradle.messaging.remote.internal.PlaceholderException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects the test results into memory and spools the test output to file during execution (to avoid holding it all in memory).
 */
public class TestReportDataCollector implements TestListener, TestOutputListener {

    private final Map<String, TestClassResult> results;
    private final TestOutputStore.Writer outputWriter;
    private final Map<TestDescriptor, TestMethodResult> currentTestMethods = new HashMap<TestDescriptor, TestMethodResult>();
    private long internalIdCounter = 1;

    public TestReportDataCollector(Map<String, TestClassResult> results, TestOutputStore.Writer outputWriter) {
        this.results = results;
        this.outputWriter = outputWriter;
    }

    public void beforeSuite(TestDescriptor suite) {
    }

    public void afterSuite(TestDescriptor suite, TestResult result) {
    }

    public void beforeTest(TestDescriptor testDescriptor) {
        TestMethodResult methodResult = new TestMethodResult(internalIdCounter++, testDescriptor.getName());
        currentTestMethods.put(testDescriptor, methodResult);
    }

    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        String className = testDescriptor.getClassName();
        TestMethodResult methodResult = currentTestMethods.remove(testDescriptor).completed(result);
        for (Throwable throwable : result.getExceptions()) {
            methodResult.addFailure(failureMessage(throwable), stackTrace(throwable), exceptionClassName(throwable));
        }
        TestClassResult classResult = results.get(className);
        if (classResult == null) {
            classResult = new TestClassResult(internalIdCounter++, className, result.getStartTime());
            results.put(className, classResult);
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
        return throwable instanceof PlaceholderException ? ((PlaceholderException) throwable).getExceptionClassName() : throwable.getClass().getName();
    }

    private String stackTrace(Throwable throwable) {
        try {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            throwable.printStackTrace(writer);
            writer.close();
            return stringWriter.toString();
        } catch (Throwable t) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            t.printStackTrace(writer);
            writer.close();
            return stringWriter.toString();
        }
    }

    public void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {
        String className = testDescriptor.getClassName();
        if (className == null) {
            //this means that we receive an output before even starting any class (or too late).
            //we don't have a place for such output in any of the reports so skipping.
            return;
        }
        TestClassResult classResult = results.get(className);
        if (classResult == null) {
            classResult = new TestClassResult(internalIdCounter++, className, 0);
            results.put(className, classResult);
        }

        TestMethodResult methodResult = currentTestMethods.get(testDescriptor);
        if (methodResult == null) {
            outputWriter.onOutput(classResult.getId(), outputEvent);
        } else {
            outputWriter.onOutput(classResult.getId(), methodResult.getId(), outputEvent);
        }
    }
}