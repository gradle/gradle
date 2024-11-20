/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.integtests.fixtures.HtmlTestExecutionResult

class TestSuiteAutoReportingIntegrationTest extends AbstractIntegrationSpec {

    def aggregateResults = new HtmlTestExecutionResult(testDirectory, "build/reports/tests/aggregated-results")

    def setup() {
        file("src/test/java/ExampleTest.java") << """
            import org.junit.jupiter.api.Test;

            class ExampleTest {
                @Test
                void test() {
                }
            }
        """
        file("src/test2/java/ExampleTest2.java") << """
            import org.junit.jupiter.api.Test;

            class ExampleTest2 {
                @Test
                void test2() {
                }
            }
        """

    }

    def "test task reports are generated for custom test tasks"() {
        given:
        buildFile << """
            plugins {
                id("test-suite-base")
            }

            tasks.register("test", Test) {
                finalizedBy(testing.results) {
                    binaryTestResults.from(binaryResultsDirectory)
                }

                testClassesDirs = objects.fileCollection()
                binaryResultsDirectory = layout.buildDirectory.dir("test-results")
            }

            tasks.register("test2", Test) {
                finalizedBy(testing.results) {
                    binaryTestResults.from(binaryResultsDirectory)
                }

                testClassesDirs = objects.fileCollection()
                binaryResultsDirectory = layout.buildDirectory.dir("test-results-2")
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":test", ":aggregateTestReport")

        when:
        succeeds("test2")

        then:
        result.assertTasksExecutedInOrder(":test2", ":aggregateTestReport")

        when:
        succeeds("test", "test2")

        then:
        result.assertTasksExecutedInOrder(":test", ":test2", ":aggregateTestReport")

        when:
        succeeds("help")

        then:
        result.assertTasksExecuted(":help")
        result.assertTaskNotExecuted(":aggregateTestReport")
    }

    def "test task reports are automatically generated for jvm test suites"() {
        given:
        buildFile << """
            plugins {
                id("jvm-test-suite")
            }

            ${mavenCentralRepository()}

            testing.suites.create("test", JvmTestSuite) {
                useJUnitJupiter()
            }
            testing.suites.create("test2", JvmTestSuite) {
                useJUnitJupiter()
            }
        """

        when:
        succeeds("test")

        then:
        result.assertTaskOrder(":test", ":aggregateTestReport")
        aggregateResults.totalNumberOfTestClassesExecuted == 1
        aggregateResults.testClass('ExampleTest').assertTestsExecuted('test')
        aggregateResults.testClass('ExampleTest').testCount == 1

        when:
        succeeds("test2")

        then:
        result.assertTaskOrder(":test2", ":aggregateTestReport")
        aggregateResults.totalNumberOfTestClassesExecuted == 1
        aggregateResults.testClass('ExampleTest2').assertTestsExecuted('test2')
        aggregateResults.testClass('ExampleTest2').testCount == 1

        when:
        succeeds("test", "test2")

        then:
        result.assertTaskOrder(":test", ":test2", ":aggregateTestReport")
        aggregateResults.totalNumberOfTestClassesExecuted == 2
        aggregateResults.testClass('ExampleTest').assertTestsExecuted('test')
        aggregateResults.testClass('ExampleTest').testCount == 1
        aggregateResults.testClass('ExampleTest2').assertTestsExecuted('test2')
        aggregateResults.testClass('ExampleTest2').testCount == 1

        when:
        succeeds("help")

        then:
        result.assertTasksExecuted(":help")
        result.assertTaskNotExecuted(":aggregateTestReport")
    }

    def "configuring tasks does not cause test aggregation task to execute"() {
        given:
        buildFile << """
            plugins {
                id("jvm-test-suite")
            }

            ${mavenCentralRepository()}

            testing.suites.create("test", JvmTestSuite) {
                useJUnitJupiter()
                targets.test.testTask.get()
            }
            testing.suites.create("test2", JvmTestSuite) {
                useJUnitJupiter()
                targets.test2.testTask.get()
            }

            tasks.named("aggregateTestReport").configure {
                throw new RuntimeException("Should not be executed")
            }
        """

        expect:
        succeeds("help")
    }

    def "can run aggregate report without requesting tests as well"() {
        given:
        buildFile << """
            plugins {
                id("jvm-test-suite")
            }

            ${mavenCentralRepository()}

            testing.suites.create("test", JvmTestSuite) {
                useJUnitJupiter()
            }
            testing.suites.create("test2", JvmTestSuite) {
                useJUnitJupiter()
            }
        """

        when:
        succeeds("aggregateTestReport")

        then:
        result.assertTasksExecuted(":aggregateTestReport")
        result.assertTaskNotExecuted(":test")
        result.assertTaskNotExecuted(":test2")
        assert !aggregateResults.indexExists()
    }

}
