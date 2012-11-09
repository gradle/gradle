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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.junit.result.XmlTestSuite;
import org.gradle.api.internal.tasks.testing.junit.result.XmlTestSuiteFactory;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;

import java.io.File;
import java.util.*;

public class TestNGJUnitXmlReportGenerator implements TestResultProcessor {

    //TODO SF there's still duplication between JUnit / TestNG processors wrt generation of the xml results.
    //this one probably deserves some unit testing

    private final File testResultsDir;
    private final XmlTestSuiteFactory testSuiteFactory = new XmlTestSuiteFactory();

    private Map<Object, TestInfo> tests = new HashMap<Object, TestInfo>();
    private Map<String, XmlTestSuite> testSuites = new HashMap<String, XmlTestSuite>();
    private Map<Object, Collection<Throwable>> failures = new HashMap<Object, Collection<Throwable>>();

    private TimeProvider timeProvider = new TrueTimeProvider();

    public TestNGJUnitXmlReportGenerator(File testResultsDir) {
        this.testResultsDir = testResultsDir;
    }

    class TestInfo {
        TestDescriptorInternal test;
        long started;
        TestInfo(TestDescriptorInternal test, long started) {
            this.test = test;
            this.started = started;
        }
    }

    public void started(TestDescriptorInternal test, TestStartEvent event) {
        //it would be nice if we didn't have to maintain the testId->descriptor map
        tests.put(test.getId(), new TestInfo(test, event.getStartTime()));
        if (!test.isComposite()) { //test method
            if (!testSuites.containsKey(test.getClassName())) {
                testSuites.put(test.getClassName(), testSuiteFactory.create(testResultsDir, test.getClassName(), timeProvider.getCurrentTime()));
            }
        }
    }

    public void completed(final Object testId, final TestCompleteEvent event) {
        final TestInfo testInfo = tests.remove(testId);
        if (!testInfo.test.isComposite()) { //test method
            XmlTestSuite xmlTestSuite = testSuites.get(testInfo.test.getClassName());
            Collection<Throwable> failures = this.failures.containsKey(testId) ? this.failures.remove(testId) : Collections.<Throwable>emptySet();
            xmlTestSuite.addTestCase(testInfo.test.getName(), event.getResultType(), event.getEndTime() - testInfo.started, failures);
        } else if (testInfo.test.getParent() == null) {
            for (XmlTestSuite xmlTestSuite : testSuites.values()) {
                xmlTestSuite.writeSuiteData(0); //it's hard to reliably say when TestNG test class has finished.
            }
            testSuites.clear();
        }
    }

    public void output(Object testId, TestOutputEvent event) {
        TestInfo testInfo = tests.get(testId);
        if (testInfo != null && testSuites.containsKey(testInfo.test.getClassName())) {
            //if the test does not exist or suite it means it was already completed.
            // TODO SF we should add this output to the parent class but in case of TestNG, atm we dont know what the parent class is
            // because the start/end class events are not generated.
            testSuites.get(testInfo.test.getClassName()).addOutput(event.getDestination(), event.getMessage());
        }
    }

    public void failure(Object testId, Throwable failure) {
        if (!failures.containsKey(testId)) {
            failures.put(testId, new LinkedHashSet<Throwable>());
        }
        failures.get(testId).add(failure);
    }
}
