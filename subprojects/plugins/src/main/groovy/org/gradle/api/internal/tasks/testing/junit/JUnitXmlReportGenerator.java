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

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.junit.result.XmlTestsuiteFactory;
import org.gradle.api.internal.tasks.testing.junit.result.XmlTestsuite;
import org.gradle.api.internal.tasks.testing.results.StateTrackingTestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.TestState;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.File;

public class JUnitXmlReportGenerator extends StateTrackingTestResultProcessor {
    private final File testResultsDir;
    private final XmlTestsuiteFactory testsuiteFactory = new XmlTestsuiteFactory();
    private TestState testSuite;
    private XmlTestsuite xmlTestsuite;

    public JUnitXmlReportGenerator(File testResultsDir) {
        this.testResultsDir = testResultsDir;
    }

    public void output(TestDescriptor test, TestOutputEvent event) {
        xmlTestsuite.addOutput(event.getDestination(), event.getMessage());
    }

    @Override
    protected void started(TestState state) {
        TestDescriptorInternal test = state.test;
        if (test.getName().equals(test.getClassName())) {
            xmlTestsuite = testsuiteFactory.create(testResultsDir, test.getClassName(), state.getStartTime());
            testSuite = state;
        }
    }

    @Override
    protected void completed(TestState state) {
        if (!state.equals(testSuite)) {
            xmlTestsuite.addTestCase(state.test.getName(), state.resultType, state.getExecutionTime(), state.failures);
        } else {
            xmlTestsuite.writeSuiteData(state.getExecutionTime());
            testSuite = null;
        }
    }
}
