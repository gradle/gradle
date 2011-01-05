/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.apache.tools.ant.taskdefs.optional.junit.XMLConstants;
import org.apache.tools.ant.util.DOMElementWriter;
import org.apache.tools.ant.util.DateUtils;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestOutputEvent;
import org.gradle.api.internal.tasks.testing.results.StateTrackingTestResultProcessor;
import org.gradle.util.UncheckedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.Map;

public class JUnitXmlReportGenerator extends StateTrackingTestResultProcessor {
    private final File testResultsDir;
    private final DocumentBuilder documentBuilder;
    private final String hostName;
    private Document testSuiteReport;
    private TestState testSuite;
    private Element rootElement;
    private Map<TestOutputEvent.Destination, StringBuilder> outputs
            = new EnumMap<TestOutputEvent.Destination, StringBuilder>(TestOutputEvent.Destination.class);

    public JUnitXmlReportGenerator(File testResultsDir) {
        this.testResultsDir = testResultsDir;
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
        hostName = getHostname();
    }

    @Override
    public void output(Object testId, TestOutputEvent event) {
        outputs.get(event.getDestination()).append(event.getMessage());
    }

    @Override
    protected void started(TestState state) {
        TestDescriptorInternal test = state.test;
        if (test.getName().equals(test.getClassName())) {
            testSuiteReport = documentBuilder.newDocument();
            rootElement = testSuiteReport.createElement(XMLConstants.TESTSUITE);
            testSuiteReport.appendChild(rootElement);
            // Add an empty properties element for compatibility
            rootElement.appendChild(testSuiteReport.createElement(XMLConstants.PROPERTIES));
            outputs.put(TestOutputEvent.Destination.StdOut, new StringBuilder());
            outputs.put(TestOutputEvent.Destination.StdErr, new StringBuilder());
            testSuite = state;
        }
    }

    @Override
    protected void completed(TestState state) {
        String testClassName = state.test.getClassName();
        Element element;
        if (!state.equals(testSuite)) {
            element = testSuiteReport.createElement(XMLConstants.TESTCASE);
            element.setAttribute(XMLConstants.ATTR_NAME, state.test.getName());
            element.setAttribute(XMLConstants.ATTR_CLASSNAME, testClassName);
            rootElement.appendChild(element);
        } else {
            element = rootElement;
            rootElement.setAttribute(XMLConstants.ATTR_NAME, testClassName);
            rootElement.setAttribute(XMLConstants.ATTR_TESTS, String.valueOf(state.testCount));
            rootElement.setAttribute(XMLConstants.ATTR_FAILURES, String.valueOf(state.failedCount));
            rootElement.setAttribute(XMLConstants.ATTR_ERRORS, "0");
            rootElement.setAttribute(XMLConstants.TIMESTAMP, DateUtils.format(state.getStartTime(),
                    DateUtils.ISO8601_DATETIME_PATTERN));
            rootElement.setAttribute(XMLConstants.HOSTNAME, hostName);
            Element stdoutElement = testSuiteReport.createElement(XMLConstants.SYSTEM_OUT);
            stdoutElement.appendChild(testSuiteReport.createCDATASection(outputs.get(TestOutputEvent.Destination.StdOut)
                    .toString()));
            rootElement.appendChild(stdoutElement);
            Element stderrElement = testSuiteReport.createElement(XMLConstants.SYSTEM_ERR);
            stderrElement.appendChild(testSuiteReport.createCDATASection(outputs.get(TestOutputEvent.Destination.StdErr)
                    .toString()));
            rootElement.appendChild(stderrElement);
        }

        element.setAttribute(XMLConstants.ATTR_TIME, String.valueOf(state.getExecutionTime() / 1000.0));
        for (Throwable failure : state.failures) {
            Element failureElement = testSuiteReport.createElement(XMLConstants.FAILURE);
            element.appendChild(failureElement);
            failureElement.setAttribute(XMLConstants.ATTR_MESSAGE, failureMessage(failure));
            failureElement.setAttribute(XMLConstants.ATTR_TYPE, failure.getClass().getName());
            failureElement.appendChild(testSuiteReport.createTextNode(stackTrace(failure)));
        }

        if (state.equals(testSuite)) {
            File reportFile = new File(testResultsDir, "TEST-" + testClassName + ".xml");
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

            testSuite = null;
            outputs.clear();
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
}
