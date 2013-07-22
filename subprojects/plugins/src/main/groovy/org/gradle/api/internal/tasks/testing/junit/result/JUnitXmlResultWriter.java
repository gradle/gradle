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

import org.apache.tools.ant.util.DateUtils;
import org.gradle.api.internal.xml.SimpleXmlWriter;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.internal.PlaceholderException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class JUnitXmlResultWriter {

    private final String hostName;
    private final TestResultsProvider testResultsProvider;
    private final TestOutputAssociation outputAssociation;

    public JUnitXmlResultWriter(String hostName, TestResultsProvider testResultsProvider) {
        this(hostName, testResultsProvider, TestOutputAssociation.WITH_SUITE);
    }

    public JUnitXmlResultWriter(String hostName, TestResultsProvider testResultsProvider, TestOutputAssociation outputAssociation) {
        this.hostName = hostName;
        this.testResultsProvider = testResultsProvider;
        this.outputAssociation = outputAssociation;
    }

    public void write(TestClassResult result, OutputStream output) {
        String className = result.getClassName();
        try {
            SimpleXmlWriter writer = new SimpleXmlWriter(output, "  ");
            writer.startElement("testsuite")
                    .attribute("name", className)
                    .attribute("tests", String.valueOf(result.getTestsCount()))
                    .attribute("failures", String.valueOf(result.getFailuresCount()))
                    .attribute("errors", "0")
                    .attribute("timestamp", DateUtils.format(result.getStartTime(), DateUtils.ISO8601_DATETIME_PATTERN))
                    .attribute("hostname", hostName)
                    .attribute("time", String.valueOf(result.getDuration() / 1000.0));

            writer.startElement("properties");
            writer.endElement();

            writeTests(writer, result.getResults(), className);

            writer.startElement("system-out");
            writeOutputs(writer, className, outputAssociation.equals(TestOutputAssociation.WITH_SUITE), TestOutputEvent.Destination.StdOut);
            writer.endElement();
            writer.startElement("system-err");
            writeOutputs(writer, className, outputAssociation.equals(TestOutputAssociation.WITH_SUITE), TestOutputEvent.Destination.StdErr);
            writer.endElement();

            writer.endElement();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void writeOutputs(SimpleXmlWriter writer, String className, boolean allClassOutput, TestOutputEvent.Destination destination) throws IOException {
        writer.startCDATA();
        if (allClassOutput) {
            testResultsProvider.writeAllOutput(className, destination, writer);
        } else {
            testResultsProvider.writeNonTestOutput(className, destination, writer);
        }
        writer.endCDATA();
    }

    private void writeOutputs(SimpleXmlWriter writer, String className, Object testId, TestOutputEvent.Destination destination) throws IOException {
        writer.startCDATA();
        testResultsProvider.writeTestOutput(className, testId, destination, writer);
        writer.endCDATA();
    }

    private void writeTests(SimpleXmlWriter writer, Iterable<TestMethodResult> methodResults, String className) throws IOException {
        for (TestMethodResult methodResult : methodResults) {
            String testCase = methodResult.getResultType() == TestResult.ResultType.SKIPPED ? "ignored-testcase" : "testcase";
            writer.startElement(testCase)
                    .attribute("name", methodResult.getName())
                    .attribute("classname", className)
                    .attribute("time", String.valueOf(methodResult.getDuration() / 1000.0));

            for (Throwable failure : methodResult.getExceptions()) {
                writer.startElement("failure")
                        .attribute("message", failureMessage(failure))
                        .attribute("type", failure.getClass().getName());

                writer.characters(stackTrace(failure));

                writer.endElement();
            }

            if (outputAssociation.equals(TestOutputAssociation.WITH_TESTCASE)) {
                writer.startElement("system-out");
                writeOutputs(writer, className, methodResult.getId(), TestOutputEvent.Destination.StdOut);
                writer.endElement();
                writer.startElement("system-err");
                writeOutputs(writer, className, methodResult.getId(), TestOutputEvent.Destination.StdErr);
                writer.endElement();
            }

            writer.endElement();
        }
    }

    private String failureMessage(Throwable throwable) {
        try {
            return throwable.toString();
        } catch (Throwable t) {
            String exceptionClassName = throwable instanceof PlaceholderException ? ((PlaceholderException) throwable).getExceptionClassName() : throwable.getClass().getName();
            return String.format("Could not determine failure message for exception of type %s: %s",
                    exceptionClassName, t);
        }
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
}