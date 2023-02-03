/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.testsuites

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractTestFrameworkOptionsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            check.dependsOn testing.suites
        """
    }

    abstract void writeSources(TestFile sourcePath)

    void assertTestsWereExecutedAndExcluded() {
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("com.example.IncludedTest")
        result.assertTestClassesNotExecuted("com.example.ExcludedTest")
    }

    void assertIntegrationTestsWereExecutedAndExcluded() {
        DefaultTestExecutionResult result = new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'integrationTest')
        result.assertTestClassesExecuted("com.example.IncludedTest")
        result.assertTestClassesNotExecuted("com.example.ExcludedTest")
    }
}
