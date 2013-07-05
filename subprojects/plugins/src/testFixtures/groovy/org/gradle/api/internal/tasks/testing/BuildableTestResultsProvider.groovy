/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.tasks.testing.TestOutputEvent

class BuildableTestResultsProvider implements TestResultsProvider {

    long timestamp = 0
    Map<String, BuildableTestClassResult> testClasses = [:]

    BuildableTestClassResult testClassResult(String className, Closure configClosure = {}) {
        BuildableTestClassResult testSuite = new BuildableTestClassResult(className, timestamp)
        testSuite.with(configClosure)
        testClasses[className] = testSuite
    }

    void writeOutputs(String className, TestOutputEvent.Destination destination, Writer writer) {
        writeOutputs(className, null, destination, writer)
    }

    void writeOutputs(String className, String testCaseName, TestOutputEvent.Destination destination, Writer writer) {
        BuildableTestClassResult testCase = testClasses[className]

        testCase.outputEvents.each { MethodTestOutputEvent event ->
            if (event.testOutputEvent.destination == destination && (testCaseName == null || testCaseName == event.testMethodName)) {
                writer.append(event.testOutputEvent.message)
            }
        }
    }

    void visitClasses(Action<? super TestClassResult> visitor) {
        testClasses.values().each {
            visitor.execute(it)
        }
    }

    boolean isHasResults() {
        !testClasses.isEmpty()
    }

    boolean hasOutput(String className, TestOutputEvent.Destination destination) {
        testClasses[className]?.outputEvents?.find { it.testOutputEvent.destination == destination }
    }

}



