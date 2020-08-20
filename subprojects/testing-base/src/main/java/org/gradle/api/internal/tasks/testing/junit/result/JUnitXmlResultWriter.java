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
import org.gradle.internal.xml.SimpleXmlWriter;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.io.OutputStream;

public class JUnitXmlResultWriter {

    private final String hostName;
    private final TestResultsProvider testResultsProvider;
    private final TestOutputAssociation outputAssociation;

    public JUnitXmlResultWriter(String hostName, TestResultsProvider testResultsProvider, TestOutputAssociation outputAssociation) {
        this.hostName = hostName;
        this.testResultsProvider = testResultsProvider;
        this.outputAssociation = outputAssociation;
    }

    /**
     * @param output The destination, unbuffered
     */
    public void write(TestClassResult result, OutputStream output) {
        long classId = result.getId();

        try {
            SimpleXmlWriter writer = new SimpleXmlWriter(output, "  ");
            writer.startElement("testsuite")
                    .attribute("name", result.getXmlTestSuiteName())
                    .attribute("tests", String.valueOf(result.getTestsCount()))
                    .attribute("skipped", String.valueOf(result.getSkippedCount()))
                    .attribute("failures", String.valueOf(result.getFailuresCount()))
                    .attribute("errors", "0")
                    .attribute("timestamp", DateUtils.format(result.getStartTime(), DateUtils.ISO8601_DATETIME_PATTERN))
                    .attribute("hostname", hostName)
                    .attribute("time", String.valueOf(result.getDuration() / 1000.0));

            writer.startElement("properties");
            writer.endElement();

            writeTests(writer, result.getResults(), result.getClassName(), classId);

            writer.startElement("system-out");
            writeOutputs(writer, classId, outputAssociation.equals(TestOutputAssociation.WITH_SUITE), TestOutputEvent.Destination.StdOut);
            writer.endElement();
            writer.startElement("system-err");
            writeOutputs(writer, classId, outputAssociation.equals(TestOutputAssociation.WITH_SUITE), TestOutputEvent.Destination.StdErr);
            writer.endElement();

            writer.endElement();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void writeOutputs(SimpleXmlWriter writer, long classId, boolean allClassOutput, TestOutputEvent.Destination destination) throws IOException {
        writer.startCDATA();
        if (allClassOutput) {
            testResultsProvider.writeAllOutput(classId, destination, writer);
        } else {
            testResultsProvider.writeNonTestOutput(classId, destination, writer);
        }
        writer.endCDATA();
    }

    private void writeOutputs(SimpleXmlWriter writer, long classId, long testId, TestOutputEvent.Destination destination) throws IOException {
        writer.startCDATA();
        testResultsProvider.writeTestOutput(classId, testId, destination, writer);
        writer.endCDATA();
    }

    private void writeTests(SimpleXmlWriter writer, Iterable<TestMethodResult> methodResults, String className, long classId) throws IOException {
        for (TestMethodResult methodResult : methodResults) {
            writer.startElement("testcase")
                    .attribute("name", methodResult.getDisplayName())
                    .attribute("classname", className)
                    .attribute("time", String.valueOf(methodResult.getDuration() / 1000.0));

            if (methodResult.getResultType() == TestResult.ResultType.SKIPPED) {
                writer.startElement("skipped");
                writer.endElement();
            } else {
                for (TestFailure failure : methodResult.getFailures()) {
                    writer.startElement("failure")
                            .attribute("message", failure.getMessage())
                            .attribute("type", failure.getExceptionType());

                    writer.characters(failure.getStackTrace());

                    writer.endElement();
                }
            }

            if (outputAssociation.equals(TestOutputAssociation.WITH_TESTCASE)) {
                writer.startElement("system-out");
                writeOutputs(writer, classId, methodResult.getId(), TestOutputEvent.Destination.StdOut);
                writer.endElement();
                writer.startElement("system-err");
                writeOutputs(writer, classId, methodResult.getId(), TestOutputEvent.Destination.StdErr);
                writer.endElement();
            }

            writer.endElement();
        }
    }
}
