/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.TestOutcome

abstract class AbstractDryRunFilteringIntegrationTest extends AbstractTestFilteringIntegrationTest {

    @Override
    TestOutcome getTestOutcome() {
        return TestOutcome.SKIPPED
    }

    @Override
    List<String> getTestTaskArguments() {
        return ['--test-dry-run']
    }

    def "dry run test is skipping execution and considering as skipped in report"() {
        given:
        file("src/test/java/SomeTest.java") << """
        ${testFrameworkImports}

        public class SomeTest {
            @Test public void failingTest() {
                throw new RuntimeException();
            }
        }
        """
        TestExecutionResult executionResult = new DefaultTestExecutionResult(testDirectory)

        expect:
        run("test")
        executionResult.testClass("SomeTest").assertTestSkipped("failingTest")
    }
}
