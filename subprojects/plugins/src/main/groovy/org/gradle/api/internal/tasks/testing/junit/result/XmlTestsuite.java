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

import org.apache.tools.ant.util.DOMElementWriter;
import org.apache.tools.ant.util.DateUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 10/3/12
 */
public class XmlTestsuite {

    private Document testSuiteReport;
    private Element rootElement;
    private final String hostname;
    private final String className;
    private final long startTime;
    private Map<TestOutputEvent.Destination, StringBuilder> outputs
            = new EnumMap<TestOutputEvent.Destination, StringBuilder>(TestOutputEvent.Destination.class);
    private File reportFile;
    private long failedCount;
    private long testCount;

    XmlTestsuite(Document testSuiteReport, File testResultsDir, String className, long startTime) {
        this.className = className;
        this.startTime = startTime;
        this.hostname = getHostname();
        this.testSuiteReport = testSuiteReport;
        rootElement = testSuiteReport.createElement("testsuite");
        testSuiteReport.appendChild(rootElement);
        // Add an empty properties element for compatibility
        rootElement.appendChild(testSuiteReport.createElement("properties"));

        outputs.put(TestOutputEvent.Destination.StdOut, new StringBuilder());
        outputs.put(TestOutputEvent.Destination.StdErr, new StringBuilder());

        reportFile = new File(testResultsDir, "TEST-" + className + ".xml");
    }

    public String getClassName() {
        return className;
    }

    public void addTestCase(String testName, TestResult.ResultType resultType, long executionTime, Collection<Throwable> failures) {
        String testCase = resultType == TestResult.ResultType.SKIPPED ? "ignored-testcase" : "testcase";
        Element element = testSuiteReport.createElement(testCase);
        element.setAttribute("name", testName);
        element.setAttribute("classname", this.className);
        element.setAttribute("time", String.valueOf(executionTime / 1000.0));
        if (!failures.isEmpty()) {
            failedCount++;
            for (Throwable failure : failures) {
                Element failureElement = testSuiteReport.createElement("failure");
                element.appendChild(failureElement);
                failureElement.setAttribute("message", failureMessage(failure));
                failureElement.setAttribute("type", failure.getClass().getName());
                failureElement.appendChild(testSuiteReport.createTextNode(stackTrace(failure)));
            }
        }
        testCount++;
        rootElement.appendChild(element);
    }

    public void writeSuiteData(long executionTime) {
        rootElement.setAttribute("name", this.className);
        rootElement.setAttribute("tests", String.valueOf(this.testCount));
        rootElement.setAttribute("failures", String.valueOf(this.failedCount));
        rootElement.setAttribute("errors", "0");
        rootElement.setAttribute("timestamp", DateUtils.format(this.startTime, DateUtils.ISO8601_DATETIME_PATTERN));
        rootElement.setAttribute("hostname", this.hostname);
        Element stdoutElement = testSuiteReport.createElement("system-out");
        stdoutElement.appendChild(testSuiteReport.createCDATASection(outputs.get(TestOutputEvent.Destination.StdOut)
                .toString()));
        rootElement.appendChild(stdoutElement);
        Element stderrElement = testSuiteReport.createElement("system-err");
        stderrElement.appendChild(testSuiteReport.createCDATASection(outputs.get(TestOutputEvent.Destination.StdErr)
                .toString()));
        rootElement.appendChild(stderrElement);
        rootElement.setAttribute("time", String.valueOf(executionTime / 1000.0));
        outputs.clear();

        writeTo(reportFile);
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

    private String failureMessage(Throwable throwable) {
        try {
            return throwable.toString();
        } catch (Throwable t) {
            return String.format("Could not determine failure message for exception of type %s: %s",
                    throwable.getClass().getName(), t);
        }
    }

    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    public void writeTo(File reportFile) {
        try {
            OutputStream outstr = new BufferedOutputStream(new FileOutputStream(reportFile));
            try {
                new DOMElementWriter(true).write(rootElement, outstr);
            } finally {
                outstr.close();
            }
        } catch (IOException e) {
            throw new GradleException(String.format("Could not write test report file '%s'.", reportFile), e);
        }
    }

    public void addOutput(TestOutputEvent.Destination destination, String message) {
        outputs.get(destination).append(message);
    }
}
