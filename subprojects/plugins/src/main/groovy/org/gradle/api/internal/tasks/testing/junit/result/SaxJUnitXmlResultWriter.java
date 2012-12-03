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
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import javax.xml.stream.XMLOutputFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 11/13/12
 */
public class SaxJUnitXmlResultWriter {

    private final String hostName;
    private final TestResultsProvider testResultsProvider;

    public SaxJUnitXmlResultWriter(String hostName, TestResultsProvider testResultsProvider, XMLOutputFactory xmlOutputFactory) {
        this.hostName = hostName;
        this.testResultsProvider = testResultsProvider;
    }

    public void write(String className, TestClassResult result, Writer output) {
        try {
            SimpleXmlWriter writer = new SimpleXmlWriter(output);
            writer.writeXmlDeclaration("UTF-8", "1.0");
            writer.writeCharacters("\n  ");
            writer.writeStartElement(new SimpleXmlWriter.Element("testsuite")
                .attribute("name", className)
                .attribute("tests", String.valueOf(result.getTestsCount()))
                .attribute("failures", String.valueOf(result.getFailuresCount()))
                .attribute("errors", "0")
                .attribute("timestamp", DateUtils.format(result.getStartTime(), DateUtils.ISO8601_DATETIME_PATTERN))
                .attribute("hostname", hostName)
                .attribute("time", String.valueOf(result.getDuration() / 1000.0)));

            //TODO SF indentation belongs elsewhere
            writer.writeCharacters("\n  ");
            writer.writeEmptyElement("properties");

            writeTests(writer, result.getResults(), className);

            writer.writeCharacters("\n  ");
            writer.writeStartElement("system-out");
            writer.writeCDATA(testResultsProvider.getOutputs(className, TestOutputEvent.Destination.StdOut));
            writer.writeEndElement();

            writer.writeCharacters("\n  ");
            writer.writeStartElement("system-err");
            writer.writeCDATA(testResultsProvider.getOutputs(className, TestOutputEvent.Destination.StdErr));
            writer.writeEndElement();
            writer.writeCharacters("\n");

            writer.writeEndElement();
        } catch (IOException e) {
            throw new RuntimeException("Problems writing the XML results for class: " + className, e);
        }
    }

    private void writeTests(SimpleXmlWriter writer, Set<TestMethodResult> methodResults, String className) throws IOException {
        for (TestMethodResult methodResult : methodResults) {
            writer.writeCharacters("\n    ");
            String testCase = methodResult.result.getResultType() == TestResult.ResultType.SKIPPED ? "ignored-testcase" : "testcase";
            writer.writeStartElement(new SimpleXmlWriter.Element(testCase)
                    .attribute("name", methodResult.name)
                    .attribute("classname", className)
                    .attribute("time", String.valueOf(methodResult.getDuration() / 1000.0)));

            for (Throwable failure : methodResult.result.getExceptions()) {
                writer.writeCharacters("\n      ");
                writer.writeStartElement(new SimpleXmlWriter.Element("failure")
                        .attribute("message", failureMessage(failure))
                        .attribute("type", failure.getClass().getName()));

                writer.writeCharacters(stackTrace(failure));

                writer.writeEndElement();
            }

            writer.writeEndElement();
        }
    }

    //below methods are "inherited" from the original xml writer

    private String failureMessage(Throwable throwable) {
        try {
            return throwable.toString();
        } catch (Throwable t) {
            return String.format("Could not determine failure message for exception of type %s: %s",
                    throwable.getClass().getName(), t);
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