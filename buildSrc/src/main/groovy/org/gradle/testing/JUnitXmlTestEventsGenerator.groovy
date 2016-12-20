/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing

import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChildren
import org.gradle.api.internal.tasks.testing.DecoratingTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.event.ListenerBroadcast

import javax.xml.datatype.DatatypeFactory

class JUnitXmlTestEventsGenerator {
    private final ListenerBroadcast<TestListener> testListenerBroadcast
    private final ListenerBroadcast<TestOutputListener> testOutputListenerBroadcast
    CoordinatorBuild coordinatorBuild

    JUnitXmlTestEventsGenerator(ListenerBroadcast<TestListener> testListenerBroadcast, ListenerBroadcast<TestOutputListener> testOutputListenerListenerBroadcast) {
        this.testOutputListenerBroadcast = testOutputListenerListenerBroadcast
        this.testListenerBroadcast = testListenerBroadcast
    }

    void processXmlFile(File resultsFile, Object build) {
        processXml(new XmlSlurper().parse(resultsFile), build)
    }

    void processXml(GPathResult testResult, Object build) {
        String suiteName = testResult.@name.text()
        def testSuiteDescriptor = new DecoratingTestDescriptor(new DefaultTestClassDescriptor(0, suiteName), createWorkerSuite())
        testListener.beforeSuite(testSuiteDescriptor.parent.parent)
        testListener.beforeSuite(testSuiteDescriptor.parent)
        testListener.beforeSuite(testSuiteDescriptor)
        String timestamp = testResult.@timestamp.text()
        long startTime = DatatypeFactory.newInstance().newXMLGregorianCalendar(timestamp).toGregorianCalendar().getTimeInMillis()
        testResult.testcase.each { testCase ->
            String testCaseClassName = testCase.@classname.text()
            String testMethodName = testCase.@name.text()
            def testCaseDescriptor = new DecoratingTestDescriptor(new DefaultTestMethodDescriptor(0, testCaseClassName, testMethodName), testSuiteDescriptor)
            def skipped = testCase.skipped
            def failure = testCase.failure
            long endTime = startTime + (testCase.@time.toDouble() * 1000).round()

            if (failure.size() > 0) {
                testListener.beforeTest(testCaseDescriptor)
                publishAdditionalMetadata(testCaseDescriptor, build)
                try {
                    String systemErr = testResult."system-err".text()
                    if (systemErr) {
                        testOutputListener.onOutput(testCaseDescriptor, new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, systemErr))
                    }
                } catch (Exception e) {
                    e.printStackTrace()
                }
                String failureText = failure.text()
                failureText = failureText.replace("java.lang.AssertionError: ", "")
                testListener.afterTest(testCaseDescriptor, new DefaultTestResult(TestResult.ResultType.FAILURE, startTime, endTime, 1, 0, 1, [new AssertionError(failureText)]))
            } else if (!(skipped.size() > 0)) {
                testListener.beforeTest(testCaseDescriptor)
                publishAdditionalMetadata(testCaseDescriptor, build)
                testListener.afterTest(testCaseDescriptor, new DefaultTestResult(TestResult.ResultType.SUCCESS, startTime, endTime, 1, 1, 0, []))
            }
        }
        testListener.afterSuite(testSuiteDescriptor, new DefaultTestResult(TestResult.ResultType.SUCCESS, 0, 0, 0, 0, 0, []))
        testListener.afterSuite(testSuiteDescriptor.parent, new DefaultTestResult(TestResult.ResultType.SUCCESS, 0, 0, 0, 0, 0, []))
        testListener.afterSuite(testSuiteDescriptor.parent.parent, new DefaultTestResult(TestResult.ResultType.SUCCESS, 0, 0, 0, 0, 0, []))
    }

    private void publishAdditionalMetadata(DecoratingTestDescriptor testCaseDescriptor, Object build) {
        List<String> outputs = []
        def scenarioId = getScenarioId(build)
        outputs.add("Scenario: ${scenarioId}")
        if (coordinatorBuild) {
            outputs.add("Performance report: " +
                "https://builds.gradle.org/repository/download/${coordinatorBuild.buildTypeId}/${coordinatorBuild.id}:id/results/performance/build/performance-tests/" +
                "report/tests/${scenarioId.replaceAll(" ", "-")}.html")
        }
        def buildUrl = build.@webUrl
        if (buildUrl) {
            outputs.add("Worker build url: ${buildUrl}")
        }
        testOutputListener.onOutput(testCaseDescriptor, new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, outputs.join("\n")))
    }

    private String getScenarioId(Object build) {
        NodeChildren properties = build.properties.children()
        properties.find { it.@name == 'scenario' }.@value.text()
    }


    void addTestListener(TestListener listener) {
        if (!listener.getClass().getName().startsWith('org.gradle.api.internal')) {
            testListenerBroadcast.add(listener)
        }
    }

    void addTestOutputListener(TestOutputListener listener) {
        if (!listener.getClass().getName().startsWith('org.gradle.api.internal')) {
            testOutputListenerBroadcast.add(listener)
        }
    }

    private TestListener getTestListener() {
        testListenerBroadcast.getSource()
    }

    private TestOutputListener getTestOutputListener() {
        testOutputListenerBroadcast.getSource()
    }

    void release() {
        testListenerBroadcast.removeAll()
        testOutputListenerBroadcast.removeAll()
    }

    private static TestDescriptorInternal createRootSuite() {
        new DefaultTestSuiteDescriptor(0, "rootSuite")
    }

    private static TestDescriptorInternal createWorkerSuite() {
        new DecoratingTestDescriptor(new DefaultTestSuiteDescriptor(0, "workerSuite"), createRootSuite())
    }
}
